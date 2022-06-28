package site.conghucai.protocol.message;

import lombok.Data;
import lombok.ToString;
import site.conghucai.common.constant.ResponseStatus;

@Data
@ToString(callSuper = true)
public class RpcResponseMessage extends AbstractRpcMessage {

    private Object returnValue;
    private Exception exceptionValue;


    /**
     * 响应状态码，使用HTTP状态码
     */
    private Integer status;


    public RpcResponseMessage(Long sequence, Long timeStamp, String mapping , Object returnValue, Exception exceptionValue, Integer status) {
        super(sequence, timeStamp, false, false, mapping);
        this.returnValue = returnValue;
        this.exceptionValue = exceptionValue;
        this.status = status;
    }

    public RpcResponseMessage(Long sequence) {
        super(sequence, null, false, false, null);
    }

    public RpcResponseMessage(Long sequence, String mapping, Integer status) {
        this(sequence, getNow(),mapping, null, null, status);

    }

    public boolean isSuccess() {
        return this.status.equals(ResponseStatus.SUCCESS_CODE);
    }

    /**
     * 请求成功的响应
     * @param sequence
     * @param mapping
     * @param returnValue
     * @return
     */
    public static RpcResponseMessage getSuccessResp(Long sequence, String mapping, Object returnValue) {
        return new RpcResponseMessage(sequence, getNow(), mapping,
                returnValue, null,
                ResponseStatus.SUCCESS_CODE);
    }

    /**
     * 服务器端出现异常时的响应
     * @param sequence
     * @param mapping
     * @param exceptionValue
     * @return
     */
    public static RpcResponseMessage getServerExceptionResp(Long sequence, String mapping, Exception exceptionValue) {
        return new RpcResponseMessage(sequence, getNow(), mapping,
                null, exceptionValue,
                ResponseStatus.SERVER_ERROR_CODE);
    }

    /**
     * 客户端出现异常时的响应
     * @param sequence
     * @param mapping
     * @param exceptionValue
     * @return
     */
    public static RpcResponseMessage getClientExceptionResp(Long sequence, String mapping, Exception exceptionValue) {
        return new RpcResponseMessage(sequence, getNow(), mapping,
                null, exceptionValue,
                ResponseStatus.CLIENT_ERROR_CODE);
    }


    /**
     * 超时错误的响应
     * @param sequence
     * @param mapping
     * @return
     */
    public static RpcResponseMessage getTimeoutResp(Long sequence, String mapping) {
        return new RpcResponseMessage(sequence,mapping,
                ResponseStatus.RESPONSE_TIMEOUT);
    }

    /**
     * 节点不可用时的响应
     * @param sequence
     * @param mapping
     * @return
     */
    public static RpcResponseMessage getNodeUnavailableResp(Long sequence, String mapping) {
        return new RpcResponseMessage(sequence, mapping, ResponseStatus.NODE_UNAVAILABLE);
    }

    /**
     * 重复请求时的响应
     * @param sequence
     * @param mapping
     * @return
     */
    public static RpcResponseMessage getDuplicateRequestResp(Long sequence, String mapping) {
        return new RpcResponseMessage(sequence, mapping, ResponseStatus.REQUEST_DUPLICATE);
    }

    private static long getNow() {
        return System.currentTimeMillis();
    }


    /**
     * 根据错误响应的状态码，判断是否需要重试请求。
     * 只有节点不可用、请求超时需要。
     * @return
     */
    public boolean needRetried() {
        return this.getStatus().equals(ResponseStatus.RESPONSE_TIMEOUT) ||
                this.getStatus().equals(ResponseStatus.NODE_UNAVAILABLE);
    }

}
