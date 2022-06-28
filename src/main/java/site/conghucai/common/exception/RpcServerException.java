package site.conghucai.common.exception;

public class RpcServerException extends RuntimeException {
    public RpcServerException() {
    }

    public RpcServerException(String message) {
        super(message);
    }

    public RpcServerException(String message, Throwable cause) {
        super(message, cause);
    }

    public RpcServerException(Throwable cause) {
        super(cause);
    }
}
