package site.conghucai.common.constant;

public interface RpcConstant {

    int DEFAULT_IO_THREAD = Runtime.getRuntime().availableProcessors() * 2 + 1;

    short MAGIC_NUM = 6552;
    byte PROTOCOL_VERSION = 1;
    int MAX_FRAME_LENGTH = 1048576;

    int SO_BACKLOG = 2048;

    String THREAD_POOL_CUSTOM_IMPL = "CUSTOM";

}
