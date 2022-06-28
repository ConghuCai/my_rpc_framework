package site.conghucai.rpc.handler.process.deal.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import site.conghucai.future.ResponseFuture;
import site.conghucai.future.ResponseMapping;
import site.conghucai.protocol.message.AbstractRpcMessage;
import site.conghucai.protocol.message.HeartBeatMessage;
import site.conghucai.protocol.message.RpcRequestMessage;
import site.conghucai.protocol.message.RpcResponseMessage;
import site.conghucai.reflect.ServiceMethodInvoker;
import site.conghucai.reflect.ServiceRegister;
import site.conghucai.rpc.handler.process.deal.DealingContext;
import site.conghucai.rpc.handler.process.deal.DealingUnit;

/**
 * 分发处理器，任务是根据消息的类型，进行不同的业务处理
 * 【客户端、服务器公用】
 */
@Slf4j
public class DispatchDealingUnit implements DealingUnit {

    ResponseMapping responseMapping;

    public DispatchDealingUnit() {
    }

    /**
     * 分发处理器的构造器
     * @param responseMapping ResponseFuture对象的缓存
     */
    public DispatchDealingUnit(ResponseMapping responseMapping) {
        this.responseMapping = responseMapping;
    }

    /**
     * 客户端、服务器公用
     * @param context 上下文信息
     */
    @Override
    public void deal(DealingContext context) {
        AbstractRpcMessage message = context.getMessage();

        if(message.isRequest()) {
            //请求消息处理 -> 服务器使用
            requestDealing(context, (RpcRequestMessage)message);
        } else if(message.isHeartBeat()) {
            //心跳包处理 -> 服务器使用
            heartbeatDealing(context, (HeartBeatMessage)message);
        } else {
            //响应处理 -> 客户端使用
            responseDealing((RpcResponseMessage)message);
        }
    }


    //服务器处理心跳包的流程
    private void heartbeatDealing(DealingContext context, HeartBeatMessage heartbeat) {
        Long seq = heartbeat.getSequence();

        if(context.isPrintHeartbeatInfo()) {
            log.info("[HeartBeat] Server receive heartbeat packet from client {}.", context.getConnection().getRemoteAddr());
        }

        //构建响应pong包
        RpcResponseMessage pong = RpcResponseMessage.getSuccessResp(seq, null, null);
        context.getConnection().send(pong); //发送心跳响应
    }

    //服务器处理请求包的流程
    private void requestDealing(DealingContext context, RpcRequestMessage request) {
        String rpcMapping = request.getMapping();
        Long seq = request.getSequence();

        try {
            ServiceMethodInvoker invoker = ServiceRegister.getMethodInvoker(rpcMapping);
            Object result = invoker.invoke(request.getArgs());

            //服务器执行成功，返回给客户端成功的结果。
            context.getConnection().send(RpcResponseMessage.getSuccessResp(seq, rpcMapping, result));

        } catch (Exception e) {
            // log.info("");
            //执行出现异常，返回客户端异常信息
            context.getConnection().send(RpcResponseMessage.getServerExceptionResp(seq, rpcMapping, e));
        }
    }

    //客户端处理响应信息的流程
    private void responseDealing(RpcResponseMessage response) {
        //1.拿到当时发送request时的future
        Long sequence = response.getSequence();
        ResponseFuture future = responseMapping.getResponseFuture(sequence);

        if(future == null) {
            //没有对应的future对象  说明至少在收到响应前，缓存就失效了。
            //则流程上来说应该是属于服务器超时错误。
            //打印警告信息，处理结束
            log.warn("[client] Response {} mismatch its request. it's probably caused by long response time of server. process canceled. ",
                    response.getSequence());
            return;
        }

        //2.解除future的阻塞  表示已经收到对应的request
        //当前是nio处理channel的线程，在这里解除 发送消息的线程 的阻塞
        future.responseReceive(response);   //设置响应并执行回调
    }
}
