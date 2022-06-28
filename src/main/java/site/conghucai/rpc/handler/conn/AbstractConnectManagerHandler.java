package site.conghucai.rpc.handler.conn;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import site.conghucai.common.net.HostSocketAddress;
import site.conghucai.hosts.connect.Connection;
import site.conghucai.hosts.connect.ConnectionPool;
import site.conghucai.hosts.node.NodeManager;

import java.net.InetSocketAddress;

/**
 * 客户端服务公用的连接管理Handler，本质上是一个双向Handler
 */
@Slf4j
public abstract class AbstractConnectManagerHandler extends ChannelDuplexHandler {

    protected NodeManager nodeManager;

    public AbstractConnectManagerHandler(NodeManager nodeManager) {
        this.nodeManager = nodeManager;
    }

    /**
     * channel关闭时的具体操作
     * @param ctx
     */
    protected void close(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        Connection connection = channel.attr(Connection.KEY_CONN).get();  //取出channel的连接对象
        HostSocketAddress node = HostSocketAddress.get((InetSocketAddress) channel.remoteAddress());
        ConnectionPool pool = nodeManager.getConnectPool(node);//channel所在节点的连接池

        pool.releaseConnection(connection.getID()); //连接池释放连接对象
        log.info("client channel {} is release from the pool of node {}.", channel, node);

        //最后一个连接关闭  节点从nodeManager中移出
        if(pool.getCurrentSize() == 0) {
            nodeManager.removeNode(node);
            log.info("client {} has no active conn, node is removed from nodeManager.", node);
        }
    }
}
