package site.conghucai.rpc.handler.process;

import io.netty.channel.ChannelHandlerContext;
import site.conghucai.hosts.connect.Connection;
import site.conghucai.hosts.node.NodeManager;
import site.conghucai.protocol.message.AbstractRpcMessage;
import site.conghucai.rpc.handler.process.deal.DealingContext;
import site.conghucai.rpc.handler.process.deal.DealingUnitChain;
import site.conghucai.rpc.handler.process.deal.impl.DispatchDealingUnit;

import java.util.concurrent.ExecutorService;

public class ServerProcessHandler extends AbstractProcessHandler {

    private ExecutorService businessThreadPool; //执行业务的线程池
    private boolean enablePintHeartbeatInfo;

    public ServerProcessHandler(NodeManager nodeManager, ExecutorService businessThreadPool, boolean enablePintHeartbeatInfo) {
        super(nodeManager);
        this.businessThreadPool = businessThreadPool;
        this.enablePintHeartbeatInfo = enablePintHeartbeatInfo;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, AbstractRpcMessage msg) throws Exception {
        //初始化处理链
        DealingUnitChain dealings = new DealingUnitChain();
        dealings.addDealingUnit(new DispatchDealingUnit());    //添加一个处理单元到处理链中

        //初始化上下文
        DealingContext context = new DealingContext();
        context.setClient(false);
        context.setMessage(msg);
        context.setDealings(dealings);
        context.setNodeManager(nodeManager);
        context.setConnection(ctx.channel().attr(Connection.KEY_CONN).get());
        context.setPrintHeartbeatInfo(enablePintHeartbeatInfo);

        //执行业务处理链
        if(businessThreadPool != null) {
            businessThreadPool.execute(() -> {
                dealings.dealAll(context);
            });
        } else {
            //没有设置业务线程池  则在nio线程里面执行业务处理
            dealings.dealAll(context);
        }
    }
}
