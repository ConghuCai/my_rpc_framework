package site.conghucai.future;

import lombok.extern.slf4j.Slf4j;
import site.conghucai.common.net.HostSocketAddress;
import site.conghucai.hosts.node.NodeManager;
import site.conghucai.protocol.message.RpcResponseMessage;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 响应Future，用于请求、响应任务之间的同步
 * 发送响应后创建响应seq对应的future，可用于阻塞等待响应，和获得响应后解除阻塞并执行回调任务。
 * 执行回调任务既可以通过传入专门的线程池来执行，也可以在主线程中顺序执行。
 */
@Slf4j
public class ResponseFuture {

    private Long requestSequence;   //请求序列号---根据此序列号 找到日后收到的对应的响应，从而实现异步
    private int timeout;    //超时时间
    private HostSocketAddress remoteAddr;   //接受端目标主机地址
    private ResponseCallBack callBack;  //收到响应后拟进行的回调任务

    //得到的响应
    private RpcResponseMessage response;

    private final CountDownLatch latch = new CountDownLatch(1);

    //公共的回调线程池
    private static ThreadPoolExecutor PUBLIC_CALLBACK_POOL;

    public ResponseFuture(Long requestSequence, int timeout, HostSocketAddress remoteAddr, ResponseCallBack callBack) {
        this.requestSequence = requestSequence;
        this.timeout = timeout;
        this.remoteAddr = remoteAddr;
        this.callBack = callBack;
    }

    /**
     * 阻塞式等待服务器响应到来
     * @return
     */
    public RpcResponseMessage waitResponse() {
        boolean await;
        try {
            await = latch.await(timeout, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("wait for response interrupted.", e);
            return RpcResponseMessage.getClientExceptionResp(requestSequence, null, e);
        }

        if(await && response != null) {
            //正常结束  正常获得一个response
            return response;
        }

        //结束等待  但response为空 说明超时
        log.error("Request timeout! request seq: {}.", requestSequence);
        NodeManager.serverErrorMemo(remoteAddr);    //服务节点记一次错误
        return RpcResponseMessage.getTimeoutResp(requestSequence, null);
    }


    /**
     * 获取响应对象并执行回调任务
     */
    public void responseReceive(RpcResponseMessage response) {
        this.response = response;

        latch.countDown();  //解除等待的阻塞

        if(callBack == null) {
            //没有给回调任务，结束
            return;
        }

        //执行回调任务
        try {
            if(PUBLIC_CALLBACK_POOL == null) {
                //在当前NIO线程中执行回调
                callBack.callback(this.response);
            } else {
                //在指定的回调线程池中执行
                PUBLIC_CALLBACK_POOL.execute(() -> {
                    callBack.callback(this.response);
                });
            }
        } catch (Exception e) {
            log.error("Exception occurred when callback task called.", e);
        }
    }

    /**
     * 设置专门的线程池来执行回调任务。
     * 如果不设置，回调任务将会在调用responseReceive方法的线程中执行。
     * @param pool 回调线程池
     */
    public static void setPublicCallbackPool(ThreadPoolExecutor pool) {
        PUBLIC_CALLBACK_POOL = pool;
    }
}
