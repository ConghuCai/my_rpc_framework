package site.conghucai.future;

import site.conghucai.protocol.message.RpcResponseMessage;

/**
 * 回调任务接口，方法式接口，提供了一个回调任务接口
 */
@FunctionalInterface
public interface ResponseCallBack {

    /**
     * 对响应进行回调处理
     * @param responseMessage
     */
    void callback(RpcResponseMessage responseMessage);
}
