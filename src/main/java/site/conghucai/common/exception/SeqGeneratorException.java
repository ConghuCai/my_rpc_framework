package site.conghucai.common.exception;

public class SeqGeneratorException extends RuntimeException {
    public SeqGeneratorException() {
    }

    public SeqGeneratorException(String message) {
        super(message);
    }

    public SeqGeneratorException(String message, Throwable cause) {
        super(message, cause);
    }

    public SeqGeneratorException(Throwable cause) {
        super(cause);
    }
}
