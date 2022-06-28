package site.conghucai.common.constant;

/**
 * 服务响应状态码，参考HTTP状态码
 */
public interface ResponseStatus {
    /**
     * 响应成功
     */
    Integer SUCCESS_CODE = 200;

    /**
     * 重复请求
     */
    Integer REQUEST_DUPLICATE = 201;

    /**
     * 服务器出错
     */
    Integer SERVER_ERROR_CODE = 500;

    /**
     * 客户端出错
     */
    Integer CLIENT_ERROR_CODE = 400;

    /**
     * 响应超时
     */
    Integer RESPONSE_TIMEOUT = 502;

    /**
     * 节点不可用
     */
    Integer NODE_UNAVAILABLE = 503;
}
