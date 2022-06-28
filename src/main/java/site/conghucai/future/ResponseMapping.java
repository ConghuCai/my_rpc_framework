package site.conghucai.future;

import site.conghucai.common.cache.ExpireCache;
import site.conghucai.common.cache.impl.ScheduleCleanExpireCache;

import java.util.concurrent.TimeUnit;

/**
 * 请求的序号、响应的future对象的映射缓存
 * 可用于存放请求序号-future对、获得future对象等。
 * 是对ExpireCache的方法封装
 */
public class ResponseMapping {

    private ExpireCache<Long, ResponseFuture> cache;

    public ResponseMapping(int expireTime) {
        this.cache = new ScheduleCleanExpireCache<>(expireTime, TimeUnit.SECONDS);
    }

    /**
     * 存入一条sequence对应的responseFuture
     * @param sequence
     * @param future
     */
    public void putResponseFuture(Long sequence, ResponseFuture future) {
        cache.put(sequence, future);
    }

    /**
     * 取出一条sequence对应的responseFuture
     * @param sequence
     * @return responseFuture； 如果缓存中记录已经过期，则返回null
     */
    public ResponseFuture getResponseFuture(Long sequence) {
        ResponseFuture future = cache.get(sequence);
        cache.expire(sequence);
        return future;
    }

    /**
     * 启用已存入的future对象
     * @param sequence
     */
    public void futureAbandon(Long sequence) {
        cache.expire(sequence);
    }

    /**
     * 关闭futureMapping
     */
    public void closeMapping() {
        cache.close();
    }
}
