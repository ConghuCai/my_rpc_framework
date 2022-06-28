package site.conghucai.rpc.handler.process;

import io.netty.channel.ChannelHandlerContext;
import site.conghucai.config.RpcClientConfig;
import site.conghucai.future.ResponseMapping;
import site.conghucai.hosts.connect.Connection;
import site.conghucai.hosts.node.NodeManager;
import site.conghucai.protocol.message.AbstractRpcMessage;
import site.conghucai.rpc.handler.process.deal.DealingContext;
import site.conghucai.rpc.handler.process.deal.DealingUnit;
import site.conghucai.rpc.handler.process.deal.DealingUnitChain;
import site.conghucai.rpc.handler.process.deal.impl.DispatchDealingUnit;

/**
 * RPC客户端专用业务处理器
 */
public class ClientProcessHandler extends AbstractProcessHandler {

    ResponseMapping responseMapping;

    /**
     * @param nodeManager 节点管理器
     * @param responseMapping responseFuture的缓存
     */
    public ClientProcessHandler(NodeManager nodeManager, ResponseMapping responseMapping) {
        super(nodeManager);
        this.responseMapping = responseMapping;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, AbstractRpcMessage msg) throws Exception {
        //初始化处理链
        DealingUnitChain dealings = new DealingUnitChain();
        DealingUnit dealing = new DispatchDealingUnit(responseMapping); //使用一个分发处理器
        dealings.addDealingUnit(dealing);

        //初始化一个 处理上下文
        DealingContext context = new DealingContext();
        context.setClient(true);
        context.setConnection(ctx.channel().attr(Connection.KEY_CONN).get());   //当时加入的Connection对象属性
        context.setNodeManager(nodeManager);
        context.setDealings(dealings);
        context.setMessage(msg);

        //处理全部业务
        dealings.dealAll(context);
    }
}
