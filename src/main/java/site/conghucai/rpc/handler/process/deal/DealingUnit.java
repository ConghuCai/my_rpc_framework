package site.conghucai.rpc.handler.process.deal;

/**
 * 处理单元，提供接口以实现自定义的处理逻辑
 */
public interface DealingUnit {

    /**
     * 执行处理逻辑
     * @param context 上下文信息
     */
    void deal(DealingContext context);

}
