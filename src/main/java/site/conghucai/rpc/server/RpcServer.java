package site.conghucai.rpc.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollMode;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import lombok.extern.slf4j.Slf4j;
import site.conghucai.common.constant.RpcConstant;
import site.conghucai.common.exception.RegistryException;
import site.conghucai.common.exception.RpcException;
import site.conghucai.common.exception.RpcServerException;
import site.conghucai.common.net.HostSocketAddress;
import site.conghucai.common.spi.ExtensionLoader;
import site.conghucai.common.thread.RpcDaemonThreadFactory;
import site.conghucai.common.utils.ThreadUtil;
import site.conghucai.config.RpcServerConfig;
import site.conghucai.hosts.node.NodeManager;
import site.conghucai.hosts.node.impl.SharableNodeManager;
import site.conghucai.pool.BusinessThreadPool;
import site.conghucai.protocol.codec.RpcMessageDecoder;
import site.conghucai.protocol.codec.RpcMessageEncoder;
import site.conghucai.reflect.ServiceScanner;
import site.conghucai.registry.ServicePublisher;
import site.conghucai.rpc.AbstractRpc;
import site.conghucai.rpc.Server;
import site.conghucai.rpc.handler.conn.ServerConnectManagerHandler;
import site.conghucai.rpc.handler.process.ServerProcessHandler;
import site.conghucai.rpc.server.task.ClientConnectCountTask;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class RpcServer extends AbstractRpc implements Server {

    private final ServerBootstrap serverBootstrap = new ServerBootstrap();
    private RpcServerConfig serverConfig;
    private Integer port;
    private Class<?> sourceClass = Void.class;

    private EventLoopGroup boss;
    private EventLoopGroup workers;
    private EventExecutorGroup eventExecutors;

    private AtomicBoolean hasOpened = new AtomicBoolean(false);

    private NodeManager nodeManager = new SharableNodeManager();   //远程客户端节点群管理

    private ServicePublisher publisher; //注册中心-服务发行器


    //线程池
    private ScheduledExecutorService connCountService;  //统计连接信息的守护线程池
    private ThreadPoolExecutor businessThreadPool;      //处理请求、执行rpc方法的业务线程池


    public RpcServer() {
    }

    @Override
    public Server config(RpcServerConfig config) {
        this.serverConfig = config;
        return this;
    }

    @Override
    public Server bind(Integer port) {
        this.port = port;
        return this;
    }

    @Override
    public Server source(Class<?> clazz) {
        this.sourceClass = clazz;
        return this;
    }

    /**
     * 开启服务器
     * @return
     */
    @Override
    public Server start() {
        if(hasOpened.compareAndSet(false, true)) {
            try {
                initConfig();   //加载配置对象

                scanRpcServices();  //扫描服务器提供的所有远程服务

                initServer();   //服务器Socket初始化

                initRegistry();

            } catch (Exception e) {
                log.error("rpc server load failed.", e);
                close();
                throw e;
            }
        } else {
            log.warn("Rpc server has already opened!");
        }

        return this;
    }

    /********************************初始化服务器设置*****************************************************/
    private void initConfig() {
        //0.检查是否有对象
        checkNotNull(serverConfig, "config is null. please .config() first!");
        checkNotNull(sourceClass, "sourceClass is null. please .source() first!");

        //1.设置port
        if(this.port == null) {
            this.port = serverConfig.getPort(); //未调用port  则采用默认port。
        }

        //2.初始化业务线程池
        initBusinessThreadPool();

        //3.开启一个守护线程，统计接入的节点、连接总数。
        if(serverConfig.getPrintConnectCountInterval() > 0) {
            enableConnectCountThread(); //开始跑线程了
        }
    }

    //初始化业务线程池
    private void initBusinessThreadPool() {
        BusinessThreadPool customPool =
                ExtensionLoader.getExtensionLoader(BusinessThreadPool.class)
                        .getExtension(RpcConstant.THREAD_POOL_CUSTOM_IMPL);
        if(customPool != null) {
            //用户自定义了业务线程池，使用自定义的。
            businessThreadPool = customPool.getThreadPool();
        } else {
            //用户未自定义，使用默认的。
            int poolSize = serverConfig.getPoolSize();
            if(poolSize <= 0) {
                businessThreadPool = null;  //不使用业务线程池
                log.warn("Server forbid business tread pool, all business tasks will run in nio thread.");
            } else {
                businessThreadPool = ThreadUtil.getCustomThreadPoolExecutor(
                        poolSize,
                        serverConfig.getBlockQueueSize(),
                        "rpc-server-business-pool",
                        new ThreadPoolExecutor.AbortPolicy()
                );
            }
        }
    }

    //开启统计节点、连接的线程。
    private void enableConnectCountThread() {
        connCountService = Executors.newSingleThreadScheduledExecutor(RpcDaemonThreadFactory.getSingleFactory());
        connCountService.scheduleAtFixedRate(new ClientConnectCountTask(nodeManager),
                5, serverConfig.getPrintConnectCountInterval(), TimeUnit.SECONDS);
    }

    /********************************service包扫描*****************************************************/
    private void scanRpcServices() {
        if(sourceClass.equals(Void.class)) {
            log.warn("you should init sourceClass by function source(clazz).");
            return;
        }

        new ServiceScanner(sourceClass).serviceScan();  //进行服务包扫描，所有扫描到的rpc方法都将存在ServiceRegister中。
    }

    /********************************初始化serverBootStrap 添加handler*****************************************************/
    private void initServer() {
        log.debug("rpc server init...");
        //0.根据系统，初始化eventLoopGroup
        boss = new NioEventLoopGroup(1);
        Integer defaultThreadNum = serverConfig.getIOThread();
        if(defaultThreadNum == null) {
            defaultThreadNum = RpcConstant.DEFAULT_IO_THREAD;
        }
        if(this.useEpoll()) {
            workers = new EpollEventLoopGroup(defaultThreadNum);   //linux上且epoll可用 使用基于epoll的eventLoop实现  效率更高
        } else {
            workers = new NioEventLoopGroup(defaultThreadNum);
        }

        //1.初始化eventExecutorGroup
        this.eventExecutors = new DefaultEventExecutorGroup(this.serverConfig.getChannelWorkerThreadNum());

        //2.初始化流量监控器，限制读写速度
        initTrafficMonitor(eventExecutors, serverConfig.isTrafficMonitorEnable(),
                serverConfig.getMaxWriteSpeed(), serverConfig.getMaxReadSpeed());

        //3.SSL加密
        if(serverConfig.isUseSSL()) {
            try {
                log.info("server ssl-context-init...");
                buildSSLContext(serverConfig, false);
            } catch (Exception e) {
                throw new RpcException("Rpc server sslContext init failed.", e);
            }
        }

        //3.绑定group、指定服务器Socket的实现
        serverBootstrap.group(boss, workers)
            .channel(useEpoll() ? EpollServerSocketChannel.class : NioServerSocketChannel.class);

        //4.服务器设置\客户端连接设置
        initServerBootStrapOptions();

        //6.初始化客户端Channel的Handler
        serverBootstrap.childHandler(new SocketChannelHandlerInitializer());

        //5.绑定端口
        try {
            serverBootstrap.bind(port).sync();
        } catch (InterruptedException e) {
            log.error("rpc server bind interrupt!", e);
            throw new RpcServerException("rpc server bind interrupt", e);
        }

        log.info("Rpc Server is running, bind port [" + port + "].");
    }

    //初始化serverBootstrap的参数
    private void initServerBootStrapOptions() {
        //服务器设置
        serverBootstrap.option(ChannelOption.SO_BACKLOG, serverConfig.getBacklog()) //连接等待队列、半连接队列总长度
            .option(ChannelOption.SO_REUSEADDR, true)   //允许重复使用本地地址和端口
            .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);    //ByteBuf分配策略
        if(useEpoll()) {
            //epoll模式下，采用ET模式
            //ET模式,那么仅当状态发生变化时才会通知
            serverBootstrap.option(EpollChannelOption.EPOLL_MODE, EpollMode.EDGE_TRIGGERED);
        }

        //客户端连接设置
        serverBootstrap.childOption(ChannelOption.SO_KEEPALIVE, false) //保活，使用自定义的心跳Handler
            .childOption(ChannelOption.TCP_NODELAY, true)   //禁用Nagle算法，降低延迟
            .childOption(ChannelOption.SO_RCVBUF, serverConfig.getRecvBufSize())    //TCP接收滑动窗口
            .childOption(ChannelOption.SO_SNDBUF,serverConfig.getSendBufSize())     //TCP发送滑动窗口
            .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)   //Buf分配策略  采用池化的直接内存buf
            .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
                    new WriteBufferWaterMark(serverConfig.getLowWaterMark(), serverConfig.getHighWaterMark()));   //写缓冲区高低水位标志  实现流量控制  默认32KB, 64KB
        //当写缓冲区高于高水位  channel.isWritable() -> false   低于低水位返回true
    }

    //ChannelInitializer  向channel内添加处理器
    class SocketChannelHandlerInitializer extends ChannelInitializer<NioSocketChannel> {
        @Override
        protected void initChannel(NioSocketChannel channel) throws Exception {
            ChannelPipeline pipeline = channel.pipeline();
            //流控处理器
            if(trafficShapingHandler != null ) {
                pipeline.addLast(eventExecutors, "traffic_shaping_handler", trafficShapingHandler);
            }

            //ssl处理器
            if(sslContext != null ) {
                pipeline.addLast(eventExecutors, "ssl_handler", sslContext.newHandler(channel.alloc()));
            }

            pipeline.addLast(eventExecutors,
                new RpcMessageDecoder(),    //帧解析、解码器
                new RpcMessageEncoder(serverConfig.getSerializeType(),
                        serverConfig.getCompressType()),    //编码器
                new IdleStateHandler(serverConfig.getTcpIdleTime(),
                        serverConfig.getTcpIdleTime(), 0),  //channel闲置状态检测
                new ServerConnectManagerHandler(nodeManager),  //连接管理Handler
                new ServerProcessHandler(nodeManager, businessThreadPool, serverConfig.isEnablePrintHeartbeatInfo())
            );
        }
    }

    /********************************初始化注册中心*****************************************************/
    private void initRegistry() {
        if(!serverConfig.isUseRegistry()) {
            return;
        }

        String registrySchema = serverConfig.getRegistrySchema();
        if(registrySchema == null || registrySchema.isEmpty()) {
            registrySchema = "zookeeper";
        }

        //1.得到服务发行器的实现类实例
        publisher = ExtensionLoader.getExtensionLoader(ServicePublisher.class).getExtension(registrySchema);
        if(publisher == null) {
            throw new RpcException("The implement of registry schema [" + registrySchema + "] not found. please make sure that you have implemented that by SPI.");
        }

        try {
            //2.服务发行器初始化
            publisher.initRegistry(serverConfig.getRegistryAddresses());
            log.info("RPC Server use {} registry [{}].", registrySchema, serverConfig.getRegistryAddresses());

            //3.发行远程服务
            HostSocketAddress local = HostSocketAddress.getLocalSocket(port);
            serverConfig.getRpcServices().forEach(serviceName -> {
                publisher.publishService(serviceName, local);
                log.info("RPC Server publish service [{}] to the registry.", serviceName);
            });

        } catch (Exception e) {
            throw new RegistryException("registry-publisher init or publish failed.", e);
        }
    }


    /**
     * 关闭服务器
     */
    @Override
    public void close() {
        if(hasOpened.compareAndSet(true, false)) {
            try {
                if(workers != null) {
                    workers.shutdownGracefully();
                }
                if(eventExecutors != null) {
                    workers.shutdownGracefully();
                }
                if(connCountService != null) {
                    ThreadUtil.shutdownGracefully(connCountService, 5);
                }
                if(businessThreadPool != null) {
                    ThreadUtil.shutdownGracefully(businessThreadPool, 5);
                }
                nodeManager.close();

                //将本服务器节点信息从registry中清除
                if(publisher != null) {
                    clearRegistry();
                }

            } catch (Exception e) {
                log.error("Failed to close server.", e);
            }
            log.info("Server has closed. Bye.");
        } else {
            log.warn("Server has already closed!");
        }
    }

    //清除注册中心的本节点路径
    private void clearRegistry() {
        HostSocketAddress local = HostSocketAddress.getLocalSocket(port);
        try {
            publisher.clearService(local);
        } catch (Exception e) {
            log.error("clear node from registry failed. node: {}", local);
            throw new RegistryException("clear node from registry failed.", e);
        }
    }
}
