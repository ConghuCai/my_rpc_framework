package site.conghucai.common.id;

/**
 * ID自动生成算法
 */
public interface IdAlgorithm {
    /**
     * 获取下一个ID
     * @return
     */
    long nextId();

    /**
     * 通过时间戳获取ID
     * @param time
     * @return
     */
    long getIdWithTime(long time);
}
