package site.conghucai.rpc.handler.conn;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import site.conghucai.common.net.HostSocketAddress;
import site.conghucai.hosts.connect.Connection;
import site.conghucai.hosts.connect.ConnectionPool;
import site.conghucai.hosts.node.NodeManager;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * 客户端使用的连接Handler
 */
@Slf4j
public class ClientConnectManagerHandler extends AbstractConnectManagerHandler {
    public ClientConnectManagerHandler(NodeManager nodeManager) {
        super(nodeManager);
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        log.debug("Rpc client connect to server node: {}", remoteAddress);
        super.connect(ctx, remoteAddress, localAddress, promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        log.debug("server node {} disconnect to client.", ctx.channel().remoteAddress());
        super.disconnect(ctx, promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        close(ctx);
        super.close(ctx, promise);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        //接受闲置Handler抛出的  闲置时间事件
        if(evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;

            //闲置时间超时  关闭channel
            if(event.state().equals(IdleState.ALL_IDLE)) {
                log.warn("connect to node {} idle timeout, channel is closing...", ctx.channel().remoteAddress());
            }
            ctx.channel().close();
        }

        ctx.fireUserEventTriggered(evt);    //向下传递事件
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.channel().close();
        super.exceptionCaught(ctx, cause);
    }


    // /**
    //  * channel关闭时的具体操作
    //  * @param ctx
    //  */
    // protected void close(ChannelHandlerContext ctx) {
    //     Channel channel = ctx.channel();
    //     Connection connection = channel.attr(Connection.KEY_CONN).get();  //取出channel的连接对象
    //     HostSocketAddress node = HostSocketAddress.get((InetSocketAddress) channel.remoteAddress());
    //     ConnectionPool pool = nodeManager.getConnectPool(node);//channel所在节点的连接池
    //
    //     pool.releaseConnection(connection.getID()); //连接池释放连接对象
    //     log.info("client channel {} is release from the pool of node {}.", channel, node);
    // }
}
