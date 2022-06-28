package site.conghucai.common.id;

public abstract class IdGenerator {
    private static volatile SnowFlakeID snowFlakeId;    //单例

    public static Long getId() {
        if (snowFlakeId == null) {
            synchronized(IdGenerator.class) {
                if (snowFlakeId == null) {
                    snowFlakeId = new SnowFlakeID(1, 1);
                }
            }
        }

        return snowFlakeId.nextId();
    }
}
