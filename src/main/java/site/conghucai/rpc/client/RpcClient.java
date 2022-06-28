package site.conghucai.rpc.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import lombok.extern.slf4j.Slf4j;
import site.conghucai.common.constant.NodeSelectorRule;
import site.conghucai.common.constant.ResponseStatus;
import site.conghucai.common.constant.RpcConstant;
import site.conghucai.common.exception.ConnectionException;
import site.conghucai.common.exception.NodeException;
import site.conghucai.common.exception.RegistryException;
import site.conghucai.common.exception.RpcException;
import site.conghucai.common.id.IdGenerator;
import site.conghucai.common.net.HostSocketAddress;
import site.conghucai.common.spi.ExtensionLoader;
import site.conghucai.common.thread.RpcDaemonThreadFactory;
import site.conghucai.common.utils.ThreadUtil;
import site.conghucai.config.RpcClientConfig;
import site.conghucai.future.ResponseCallBack;
import site.conghucai.future.ResponseFuture;
import site.conghucai.future.ResponseMapping;
import site.conghucai.hosts.balance.NodeSelector;
import site.conghucai.hosts.connect.Connection;
import site.conghucai.hosts.connect.impl.DefaultConnection;
import site.conghucai.hosts.node.NodeManager;
import site.conghucai.hosts.node.impl.SharableNodeManager;
import site.conghucai.pool.CallbackTaskThreadPool;
import site.conghucai.protocol.codec.RpcMessageDecoder;
import site.conghucai.protocol.codec.RpcMessageEncoder;
import site.conghucai.protocol.message.AbstractRpcMessage;
import site.conghucai.protocol.message.HeartBeatMessage;
import site.conghucai.protocol.message.RpcRequestMessage;
import site.conghucai.protocol.message.RpcResponseMessage;
import site.conghucai.registry.ServiceDiscover;
import site.conghucai.rpc.AbstractRpc;
import site.conghucai.rpc.Client;
import site.conghucai.rpc.handler.conn.ClientConnectManagerHandler;
import site.conghucai.rpc.handler.process.ClientProcessHandler;
import site.conghucai.rpc.client.task.HeartBeatTask;
import site.conghucai.rpc.client.task.NodeUpholdTask;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class RpcClient extends AbstractRpc implements Client {

    private final Bootstrap bootstrap = new Bootstrap();
    private EventLoopGroup eventLoops;
    private EventExecutorGroup eventExecutors;

    private RpcClientConfig clientConfig;
    private NodeManager nodeManager;

    private AtomicBoolean isStart = new AtomicBoolean(false);
    private ResponseMapping responseMapping;

    private ServiceDiscover discover;

    private ThreadPoolExecutor callBackTaskThreadPool;

    private ScheduledExecutorService nodeHoldExecutor;
    private ScheduledExecutorService heartbeatSendExecutor;

    /**
     * 传入客户端参数对象
     * @param config
     * @return
     */
    @Override
    public Client config(RpcClientConfig config) {
        this.clientConfig = config;
        return this;
    }

    /**
     * 开启客户端
     * @return
     */
    @Override
    public Client start() {
        if(isStart.compareAndSet(false, true)) {
            initConfig();
            initClient();
            initRegistry();

        } else {
            log.warn("RpcClient has already started.");
        }
        return null;
    }


    /********************************初始化变量、参数等***************************************************************/
    private void initConfig() {
        //0.检查客户端的设置是否为空
        checkNotNull(clientConfig, "clientConfig can't be null, Please confirm that you have configured");

        //1.设置响应消息的回调线程池
        callbackThreadPoolInit();

        //2.初始化nodeManager
        NodeSelectorRule selectorRule = clientConfig.getNodeSelectorRule(); //节点均衡规则
        NodeSelector nodeSelector = ExtensionLoader.getExtensionLoader(NodeSelector.class)
                .getExtension(selectorRule.name());   //遵循负载均衡规则的节点选择器
        this.nodeManager = new SharableNodeManager(clientConfig.getConnectionPoolSizePerNode(), this, nodeSelector);

        //3.requestSeq对应responseFuture的Map  初始化  设定future的失效时间（即为超时时间）
        this.responseMapping = new ResponseMapping(clientConfig.getRequestTimeout());

        //4.是否需要开启服务节点的错误限制机制
        if(clientConfig.isEnableNodeErrorTimesLimit()) {
            enableNodeErrorLimitMechanism();    //这里开始跑其他线程了
        }
    }

    //回调线程池的初始化
    private void callbackThreadPoolInit() {
        //1.用户实现了CallbackTaskThreadPool接口自定义了回调线程池，则使用自定义的。
        CallbackTaskThreadPool callbackTaskThreadPoolImpl = ExtensionLoader.getExtensionLoader(CallbackTaskThreadPool.class)
                .getExtension(RpcConstant.THREAD_POOL_CUSTOM_IMPL); //如果没有实现或设置SPI文件  则返回null
        if(callbackTaskThreadPoolImpl != null) {
            //客户端设置了回调线程池  则设置为ResponseFuture的公共回调线程池  让所有的响应回调都在线程池中进行
            this.callBackTaskThreadPool = callbackTaskThreadPoolImpl.getThreadPool();   //设置到变量  以便其他方法关闭线程池
            log.info("Use client custom callback thread pool: {}", callbackTaskThreadPoolImpl.getClass().getCanonicalName());

        } else {
            int poolSize = clientConfig.getCallbackThreadPoolSize();
            if(poolSize <= 0) {
                //2.没有自定义的回调线程池，且poolSize<=0 则使不使用线程池跑回调，都在nio线程里完成。
                this.callBackTaskThreadPool = null;
                log.warn("Client forbid callback tread pool, all callback tasks will run in nio thread.");

            } else {
                //3.没有自定义的回调线程池，且poolSize>0，使用默认构造的回调线程池
                this.callBackTaskThreadPool = ThreadUtil.getCustomThreadPoolExecutor(
                        poolSize,
                        clientConfig.getCallbackBlockQueueSize(),
                        "rpc-client-tasks",
                        new ThreadPoolExecutor.AbortPolicy()    //拒绝新任务处理器： 拒绝、抛异常
                );
                log.info("Use default callback task thread pool: rpc-client-tasks.");
            }
        }
        ResponseFuture.setPublicCallbackPool(callBackTaskThreadPool);   //线程池设置到ResponseFuture中
    }

    //开启节点错误次数限制机制
    private void enableNodeErrorLimitMechanism() {
        //nodeManager将忽略掉超出错误次数限制的节点
        nodeManager.setIgnoreDisableNode(true);

        //定期检查节点的连接、检查正常的节点将重置出错次数  注意使用守护线程
        RpcDaemonThreadFactory daemonFactory = RpcDaemonThreadFactory.getSingleFactory();   //获取一个单例 守护线程的工厂
        nodeHoldExecutor = Executors
                .newSingleThreadScheduledExecutor(daemonFactory);//使用单线程定时任务 执行守护线程。
        nodeHoldExecutor.scheduleAtFixedRate(new NodeUpholdTask(nodeManager),
                0, clientConfig.getNodeAutoUpholdPeriod(), TimeUnit.SECONDS);   //开启守护线程
    }




    /********************************初始化bootStrap 添加handler*********************************************************/
    private void initClient() {
        log.info("rpc client init...");

        //1.决定使用的eventLoop实现  和  executorGroup
        if(useEpoll()) {
            eventLoops = new EpollEventLoopGroup(clientConfig.getIOThread());
        } else {
            eventLoops = new NioEventLoopGroup(clientConfig.getIOThread());
        }
        eventExecutors = new DefaultEventExecutorGroup(clientConfig.getChannelWorkerThread());

        //2.初始化流量监控器、SSL控制器
        this.initTrafficMonitor(eventExecutors, clientConfig.isTrafficMonitorEnable(),
                clientConfig.getMaxWriteSpeed(), clientConfig.getMaxReadSpeed());
        if(clientConfig.isUseSSL()) {
            try {
                log.info("client ssl-context-init...");
                buildSSLContext(clientConfig, true);
            } catch (Exception e) {
                throw new RpcException("Rpc client SSLContext init failed.", e);
            }
        }

        //3.初始化bootStrap
        bootstrap.group(eventLoops);
        bootstrap.channel(useEpoll() ? EpollSocketChannel.class : NioSocketChannel.class);
        initBootstrapOptions();

        //4.channel内添加处理器
        bootstrap.handler(new ClientHandler());

        //5.开启定时心跳保活任务
        heartbeatSendExecutor = Executors.newSingleThreadScheduledExecutor(RpcDaemonThreadFactory.getSingleFactory());
        heartbeatSendExecutor.scheduleAtFixedRate(new HeartBeatTask(nodeManager, this),
                3, clientConfig.getHeartBeatInterval(), TimeUnit.SECONDS);

        log.info("client init complete.");
    }

    //设置bootstrap.option
    private void initBootstrapOptions() {
        bootstrap.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, false)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) TimeUnit.SECONDS.toMillis(clientConfig.getConnectionTimeout()))
                .option(ChannelOption.SO_SNDBUF, clientConfig.getSendBufSize())
                .option(ChannelOption.SO_RCVBUF, clientConfig.getRecvBufSize())
                .option(ChannelOption.WRITE_BUFFER_WATER_MARK,
                        new WriteBufferWaterMark(clientConfig.getLowWaterMark(), clientConfig.getHighWaterMark()));
    }

    //ChannelInitializer  向channel内添加处理器
    class ClientHandler extends ChannelInitializer<SocketChannel> {
        @Override
        protected void initChannel(SocketChannel channel) throws Exception {
            ChannelPipeline pipeline = channel.pipeline();

            //流控器
            if(trafficShapingHandler != null) {
                pipeline.addLast(eventExecutors, "traffic_shaping_handler", trafficShapingHandler);
            }
            //ssl加密控制器
            if(sslContext != null) {
                pipeline.addLast(eventExecutors, "ssl_handler", sslContext.newHandler(channel.alloc()));
            }

            pipeline.addLast(eventExecutors,
                    new RpcMessageDecoder(),
                    new RpcMessageEncoder(clientConfig.getSerializeType(), clientConfig.getCompressType()),
                    new IdleStateHandler(clientConfig.getTcpIdleTime(), clientConfig.getTcpIdleTime(), 0),
                    new ClientConnectManagerHandler(nodeManager),
                    new ClientProcessHandler(nodeManager, responseMapping)
                );
        }
    }



    /********************************初始化注册中心*********************************************************/
    private void initRegistry() {
        if(!clientConfig.isUseRegistry()) {
            return;
        }

        String registrySchema = clientConfig.getRegistrySchema();
        if(registrySchema == null || registrySchema.isEmpty()) {
            registrySchema = "zookeeper";
        }

        //得到发现器实现类实例
        discover = ExtensionLoader.getExtensionLoader(ServiceDiscover.class).getExtension(registrySchema);
        if(discover == null) {
            throw new RpcException("The implement of registry schema [" + registrySchema + "] not found. please make sure that you have implemented that by SPI.");
        }

        try {
            //服务发现器初始化
            discover.initRegistry(clientConfig.getRegistryAddresses());
        } catch (Exception e) {
            throw new RpcException("registry-serviceDiscover init failed.", e);
        }

        log.info("RPC Client use {} registry [{}].", registrySchema, clientConfig.getRegistryAddresses());
    }


    /**
     * 客户端关闭  同时释放所有已经建立的资源
     */
    @Override
    public void stop() {
        if(isStart.compareAndSet(true, false)) {
            log.info("client ready to close.");

            try {
                if(eventLoops != null) {
                    eventLoops.shutdownGracefully();
                }
                if(eventExecutors != null) {
                    eventExecutors.shutdownGracefully();
                }
                if(callBackTaskThreadPool != null) {
                    ThreadUtil.shutdownGracefully(callBackTaskThreadPool, 5);   //最多等5s
                }

                if(nodeHoldExecutor != null ) {
                    ThreadUtil.shutdownGracefully(nodeHoldExecutor, 5);
                }
                if(heartbeatSendExecutor != null) {
                    ThreadUtil.shutdownGracefully(heartbeatSendExecutor, 5);
                }
                if(responseMapping != null) {
                    responseMapping.closeMapping();
                }

                nodeManager.close();    //关闭所有节点的连接
            } catch (Exception e) {
                log.error("Failed to close client.", e);
            }
            log.info("Client has closed. Bye.");

        } else {
            log.warn("Client had already closed, don't close again.");
        }
    }

    /**
     * 发送一个心跳包  等待服务器响应  返回响应成功与否
     * 注意这是一个同步方法。调用此方法的线程会在这里阻塞。
     * @param connection 节点连接对象
     * @return
     */
    @Override
    public boolean sendHeartBeat(Connection connection) {
        //1.构造心跳包和响应future
        HeartBeatMessage ping = new HeartBeatMessage(IdGenerator.getId(), System.currentTimeMillis());
        ResponseFuture future;

        try {
            //2.发送心跳包  设置超时时间为5s
            future = sendMessage(ping, connection, null, clientConfig.getHeartbeatTimeout());
        } catch (Exception e) {
            log.error("send heartbeat error [sync].", e);
            responseMapping.futureAbandon(ping.getSequence());  //出错要记得从mapping里面删除掉多余的future
            return false;
        }

        //3.【有限阻塞】等待processHandler收到响应并解除阻塞  返回结果
        RpcResponseMessage pong = future.waitResponse();    //阻塞部分
        return pong.isSuccess();
    }

    /**
     * 连接远程服务器节点，返回连接对象
     * @param host
     * @param port
     * @return 连接对象
     */
    @Override
    public Connection connectRemoteNode(String host, Integer port) {
        log.debug("Client connect to rpc host {}:{}", host, port);

        ChannelFuture future = bootstrap.connect(host, port);
        int timeout = clientConfig.getConnectionTimeout();
        Connection conn = null;

        if(future.awaitUninterruptibly(TimeUnit.SECONDS.toMillis(timeout))) {
            //带超时时间的 阻塞式连接等待
            Channel channel = future.channel();
            if(channel != null && channel.isActive()) {
                //连接的channel有效  封装连接对象
                conn = new DefaultConnection(IdGenerator.getId(), channel);
                channel.attr(Connection.KEY_CONN).set(conn);  //添加connection对象为属性
            } else {
                //连接失效  节点记录一次错误
                NodeManager.serverErrorMemo(HostSocketAddress.get(host, port));
                log.error("Node connect failed. remote server: {}:{}", host, port);
            }
        } else {
            //超时  记录一次错误
            NodeManager.serverErrorMemo(HostSocketAddress.get(host, port));
            log.error("Node connect failed. remote server: {}:{}", host, port);
        }

        return conn;
    }



    /**
     * 同步调用远程方法
     * @param methodName 方法名
     * @param returnType 返回值类型的要求
     * @param args 参数
     * @param retryTimes 失败后重试次数。0为不进行重试
     * @param nodes 发起请求的节点群
     * @param <T> 返回值类型
     * @return
     */
    @Override
    public <T> T invoke(String methodName, Class<T> returnType, Object[] args, int retryTimes, HostSocketAddress... nodes) throws Exception {
        checkNotNull(nodes, "please give nodes arg!");
        RpcResponseMessage response;
        do{
            //同步等待响应
            response = send(methodName, args, nodes, null, false);

        } while(response.needRetried() && retryTimes-- > 0);

        Integer status = response.getStatus();
        if(status.equals(ResponseStatus.SUCCESS_CODE)){
            //成功获得响应
            Object returnValue = response.getReturnValue();
            log.info("RPC method {}, remote called successfully, get response value: {}.", methodName, returnValue);

            //返回值类型转换
            if (returnType == null || returnType.isAssignableFrom(Void.TYPE)) {
                return null;
            }
            if(returnType.isInstance(returnValue)) {
                //成功匹配
                return (T) returnValue;
            } else {
                //匹配失败
                log.error("RPC {} response result type don't match the type given.", methodName);
                throw new RpcException("RPC " + methodName + " response result type don't match the type given.");
            }

        } else if(status.equals(ResponseStatus.REQUEST_DUPLICATE)) {
            //请求重复
            log.warn("RPC {} request duplicated.", methodName);
            return null;
        } else if(status.equals(ResponseStatus.SERVER_ERROR_CODE)) {
            //服务器传回的异常
            log.warn("RPC {} occurred exception in remote server.", methodName);
            throw response.getExceptionValue();    //抛出远程方法的异常对象
        } else if(status.equals(ResponseStatus.CLIENT_ERROR_CODE)) {
            //客户端的异常
            log.error("RPC {} occurred exception in your client.", methodName);
            throw response.getExceptionValue();
        } else if(status.equals(ResponseStatus.NODE_UNAVAILABLE) || status.equals(ResponseStatus.RESPONSE_TIMEOUT)) {
            //尝试次数用完仍超时、连接不上
            throw new RpcException("RPC server node connect exception.");
        } else {
            return null;
        }
    }

    /**
     * 异步远程调用
     * @param methodName
     * @param callBack
     * @param args
     * @param nodes
     */
    @Override
    public void invokeAsync(String methodName, Object[] args, ResponseCallBack callBack, HostSocketAddress... nodes) {
        checkNotNull(nodes, "please give nodes arg!");
        send(methodName, args, nodes, callBack, true);
    }

    /**
     * 向节点集群发送请求。
     * @param async 同步/异步设置
     * @return response, 异步情况下为null
     */
    private RpcResponseMessage send(String methodName, Object[] args, HostSocketAddress[] nodes, ResponseCallBack callback, boolean async) {
        //1.构造请求包
        RpcRequestMessage request = new RpcRequestMessage(IdGenerator.getId(),
                System.currentTimeMillis(),
                methodName, args);
        RpcResponseMessage response = null;

        ResponseFuture future;
        try {
            //2.从节点中选择连接对象 （节点均衡选择器选中可用节点、轮询查找节点中的可用连接对象）
            Connection connection = getConnectionFromNodes(Arrays.asList(nodes), request);
            future = sendMessage(request, connection, callback, clientConfig.getRequestTimeout());
        } catch (ConnectionException | NodeException e) {
            responseMapping.futureAbandon(request.getSequence());   //3.如果出错  记得从responseMapping中删去失效的future  以免冗余
            log.error("request send failed.", e);
            return RpcResponseMessage.getNodeUnavailableResp(request.getSequence(), methodName);
        } catch (Exception e) {
            responseMapping.futureAbandon(request.getSequence());
            log.error("request send failed.", e);
            return RpcResponseMessage.getClientExceptionResp(request.getSequence(), methodName, e);
        }

        if(!async) {
            //4.指定同步模式  则阻塞等待
            response = future.waitResponse();
        }

        return response;    //5.异步情况下，response为空，对response的操作请封装在callback中。
    }

    private Connection getConnectionFromNodes(List<HostSocketAddress> nodes, RpcRequestMessage msg) {
        Connection conn = nodeManager.chooseConnection(nodes, msg);
        if(conn == null || !conn.isAvailable()) {
            throw new ConnectionException("No available connection from nodes!");
        }
        return conn;
    }



    /**
     * 发送消息、执行回调的方法实现  返回的是future
     * @return ResponseFuture
     */
    private ResponseFuture sendMessage(AbstractRpcMessage msg, Connection conn, ResponseCallBack callBack, Integer timeout) {
        //1.根据参数初始化一个future
        Long sequence = msg.getSequence();
        ResponseFuture future = new ResponseFuture(sequence, timeout, conn.getRemoteAddr(), callBack);

        //2.future放到缓存里  提供给processHandler在将来收到response后去查找对应的future并解除阻塞
        responseMapping.putResponseFuture(sequence, future);

        //3.使用conn发送即可  底层使用nio的channel.writeAndFlush
        conn.send(msg);

        return future;
    }

    /**
     * 通过注册中心，以同步模式进行远程方法调用。
     * @param methodName
     * @param returnType
     * @param args
     * @param retryTimes
     * @param <T>
     * @return
     */
    @Override
    public <T> T invokeWithRegistry(String methodName, Class<T> returnType, Object[] args, int retryTimes) throws Exception {
        HostSocketAddress[] nodes = getAvailableNodeArrayFromRegistry(methodName);
        return invoke(methodName, returnType, args, retryTimes, nodes);
    }


    /**
     * 通过注册中心，以异步方式进行远程方法调用。
     * @param methodName
     * @param args
     * @param callBack
     */
    @Override
    public void invokeAsyncWithRegistry(String methodName, Object[] args, ResponseCallBack callBack) {
        HostSocketAddress[] nodes = getAvailableNodeArrayFromRegistry(methodName);
        invokeAsync(methodName, args, callBack, nodes);
    }

    HostSocketAddress[] getAvailableNodeArrayFromRegistry(String serviceName) {
        List<HostSocketAddress> nodes;
        try {
            nodes = discover.discoverService(serviceName);
            if(nodes == null || nodes.isEmpty()) {
                throw new RegistryException("get empty node list from registry.");
            }
        } catch (Exception e) {
            throw new RegistryException("No available node for service [" + serviceName + "] in registry!", e);
        }
        return nodes.toArray(new HostSocketAddress[]{});
    }
}
