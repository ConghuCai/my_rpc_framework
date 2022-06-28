package site.conghucai.hosts.connect;

import io.netty.util.AttributeKey;
import site.conghucai.common.net.HostSocketAddress;
import site.conghucai.protocol.message.AbstractRpcMessage;
import site.conghucai.rpc.AbstractRpc;

/**
 * RPC客户端、服务器共用的连接对象，本质上是对Channel对象的上级封装。
 * 客户端通过connect对象标识正在连接的某个服务器
 * 服务器通过connect对象标识正在连接的某个客户端
 *
 * 其成员属性至少包括id、channel
 */
public interface Connection {
    /**
     * 建议将Connect实现类对象设置为Channel的Attr
     * 此处提供了公用的KEY
     */
    AttributeKey<Connection> KEY_CONN = AttributeKey.valueOf("CONNECT");

    /**
     * 得到Connect对象的ID
     * @return
     */
    Long getID();

    /**
     * 当前的connect对象是否是可用的
     * @return
     */
    boolean isAvailable();

    /**
     * 获取远程连接主机的套接字
     * @return
     */
    HostSocketAddress getRemoteAddr();

    /**
     * 获取此连接对象最后一次发送数据的时间
     * @return
     */
    long getLastSendTime();

    /**
     * 向远程主机发送一个数据包
     * @param msg
     */
    void send(AbstractRpcMessage msg);

    /**
     * 将与远程主机的连接关闭
     */
    void close();

}
