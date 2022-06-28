package site.conghucai.rpc.handler.process;

import com.google.common.base.Throwables;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import site.conghucai.hosts.node.NodeManager;
import site.conghucai.protocol.message.AbstractRpcMessage;

/**
 * 业务处理Handler的Handler模板  实际上是一个入站处理器
 */
@Slf4j
public abstract class AbstractProcessHandler extends SimpleChannelInboundHandler<AbstractRpcMessage> {

    protected NodeManager nodeManager;

    public AbstractProcessHandler(NodeManager nodeManager) {
        this.nodeManager = nodeManager;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("rpc processHandler exception caught. channel: {}, cause by: {}.",
                ctx.channel(), Throwables.getStackTraceAsString(cause));
        super.exceptionCaught(ctx, cause);
    }
}
