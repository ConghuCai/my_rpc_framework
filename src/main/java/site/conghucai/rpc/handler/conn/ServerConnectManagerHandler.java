package site.conghucai.rpc.handler.conn;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import site.conghucai.common.id.IdGenerator;
import site.conghucai.common.net.HostSocketAddress;
import site.conghucai.hosts.connect.Connection;
import site.conghucai.hosts.connect.ConnectionPool;
import site.conghucai.hosts.connect.impl.DefaultConnection;
import site.conghucai.hosts.node.NodeManager;

import java.net.InetSocketAddress;

@Slf4j
public class ServerConnectManagerHandler extends AbstractConnectManagerHandler {

    public ServerConnectManagerHandler(NodeManager nodeManager) {
        super(nodeManager);
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        log.debug("channel {} register.", ctx.channel());
        super.channelRegistered(ctx);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        log.debug("channel {} unregister.", ctx.channel());
        super.channelUnregistered(ctx);
    }

    //连接建立完毕时操作
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        log.debug("channel {} active.", channel);
        Connection conn = new DefaultConnection(IdGenerator.getId(), channel);
        HostSocketAddress node = HostSocketAddress.get((InetSocketAddress) channel.remoteAddress());

        nodeManager.addNode(node);  //远程主机节点加入  如果是第一次加入的节点 则分配给节点连接池
        ConnectionPool pool = nodeManager.getConnectPool(node);
        if(pool != null) {
            pool.addConnection(conn);   //连接对象加入节点连接池
        } else {
            log.warn("connectionPool is null, node:{}", node);
        }
        channel.attr(Connection.KEY_CONN).set(conn);    //连接对象设置为channel属性

        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.debug("channel {} inactive.", ctx.channel());
        ctx.channel().close();  //关闭客户端通道
        super.channelInactive(ctx);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        this.close(ctx);
        super.close(ctx, promise);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.channel().close();
        super.exceptionCaught(ctx, cause);
    }

    //限制状态关闭
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

}
