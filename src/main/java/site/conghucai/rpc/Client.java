package site.conghucai.rpc;

import site.conghucai.common.net.HostSocketAddress;
import site.conghucai.config.RpcClientConfig;
import site.conghucai.future.ResponseCallBack;
import site.conghucai.future.ResponseFuture;
import site.conghucai.hosts.connect.Connection;
import site.conghucai.protocol.message.AbstractRpcMessage;

/**
 * RPC客户端接口
 */
public interface Client {

    Client config(RpcClientConfig config);

    /**
     * 开启客户端
     * @return 客户端对象
     */
    Client start();

    /**
     * 关闭客户端
     */
    void stop();


    /**
     * 向节点连接对象发送一个心跳包  判断是否收到正常响应
     *
     * @param connection 节点连接对象
     * @return true:正常  false:连接失活
     */
    boolean sendHeartBeat(Connection connection);

    /**
     * 向一个RPC服务器节点发起连接
     * @param host
     * @param port
     * @return  连接对象  如果失败则返回null
     */
    Connection connectRemoteNode(String host, Integer port);

    /**
     * 以以下参数，以同步形式调用远程方法。
     * 1.要求远程方法返回值类型必须与指定类型匹配。
     * 2.调用失败情况下，允许retryTimes次数内重试。
     * @param methodName 方法名
     * @param returnType 返回值类型字节码
     * @param args 参数
     * @param retryTimes 失败重试次数限制
     * @param nodes 发起请求的节点/节点群
     * @param <T> 返回值类型
     * @return 远程方法返回值
     *
     * @throws
     */
    <T> T invoke(String methodName, Class<T> returnType, Object[] args, int retryTimes, HostSocketAddress... nodes) throws Exception;

    /**
     * 以以下参数，以异步形式调用远程方法。
     * 请给定回调线程的逻辑实现(实现ResponseCallBack方法式接口)，否则收到响应后什么都不会做。
     * @param methodName
     * @param callBack 回调任务
     * @param args
     * @param nodes
     */
    void invokeAsync(String methodName, Object[] args, ResponseCallBack callBack, HostSocketAddress... nodes);


    /**
     * 通过注册中心，以同步模式进行远程方法调用。
     * @param methodName
     * @param returnType
     * @param args
     * @param retryTimes
     * @param <T>
     * @return
     */
    <T> T invokeWithRegistry(String methodName, Class<T> returnType, Object[] args, int retryTimes) throws Exception;

    /**
     * 通过注册中心，以异步方式进行远程方法调用。
     * @param methodName
     * @param args
     * @param callBack
     */
    void invokeAsyncWithRegistry(String methodName, Object[] args, ResponseCallBack callBack);
}
