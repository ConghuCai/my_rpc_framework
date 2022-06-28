package site.conghucai.common.id;

import lombok.extern.slf4j.Slf4j;
import site.conghucai.common.exception.SeqGeneratorException;

/**
 * SnowflakeId雪花ID算法，分布式自增ID应用
 */
@Slf4j
public class SnowFlakeID implements IdAlgorithm {
    private static final long START_TIME = 1609430400000L;
    private static final long dataCenterIdBits = 4L;
    private static final long maxDataCenterId = 15L;
    private static final long workerIdBits = 5L;
    private static final long maxWorkerId = 15L;
    private static final long realMaxWorkerId = 31L;
    private static final long sequenceBits = 12L;
    private static final long workerIdShift = 12L;
    private static final long dataCenterIdShift = 17L;
    private static final long timestampLeftShift = 21L;
    private static final long sequenceMask = 4095L;
    private long dataCenterId;
    private transient long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowFlakeID(int dataCenterId, int workerId) {
        if ((long)dataCenterId <= 15L && dataCenterId >= 0) {
            if ((long)workerId <= 15L && workerId >= 0) {
                this.dataCenterId = dataCenterId;
                this.workerId = workerId;
            } else {
                throw new IllegalArgumentException(String.format("worker Id can't be greater than %d or less than 0", 15L));
            }
        } else {
            throw new IllegalArgumentException(String.format("dataCenter Id can't be greater than %d or less than 0", 15L));
        }
    }

    public synchronized long nextId() {
        long timestamp = this.timeGen();
        if (timestamp < this.lastTimestamp) {
            this.workerId = this.workerId + 15L + 1L & 31L;
            log.error("Clock moved backwards. current workerId is {}", this.workerId);
            throw new SeqGeneratorException();
        } else {
            if (this.lastTimestamp == timestamp) {
                this.sequence = this.sequence + 1L & 4095L;
                if (this.sequence == 0L) {
                    timestamp = this.tilNextMillis(this.lastTimestamp);
                }
            } else {
                this.sequence = 0L;
            }

            this.lastTimestamp = timestamp;
            return timestamp - 1609430400000L << 21 | this.dataCenterId << 17 | this.workerId << 12 | this.sequence;
        }
    }

    public long getIdWithTime(long timestamp) {
        return timestamp - 1609430400000L << 21 | this.dataCenterId << 17 | this.workerId << 12;
    }

    public long getDataCenterId() {
        return this.dataCenterId;
    }

    public long getWorkerId() {
        return this.workerId;
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp;
        for(timestamp = this.timeGen(); timestamp <= lastTimestamp; timestamp = this.timeGen()) {
        }

        return timestamp;
    }

    private long timeGen() {
        return System.currentTimeMillis();
    }

    public static SnowFlakeID parse(long id) {
        long sequence = 4095L & id;
        long workerId = parseWorkerId(id);
        long dataCenterId = parseDataCenterId(id);
        long lastTimestamp = (id >> 21) + 1609430400000L;
        SnowFlakeID generator = new SnowFlakeID((int)dataCenterId, (int)workerId);
        generator.lastTimestamp = lastTimestamp;
        generator.sequence = sequence;
        return generator;
    }

    public static long parseDataCenterId(long id) {
        return 15L & id >> 17;
    }

    public static long parseWorkerId(long id) {
        return 15L & id >> 12;
    }

    public static long parseSequence(long id) {
        return 4095L & id;
    }

    public static long parseLastTimestamp(long id) {
        return (id >> 21) + 1609430400000L;
    }
}
