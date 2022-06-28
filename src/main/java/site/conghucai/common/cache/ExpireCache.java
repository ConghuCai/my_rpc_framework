package site.conghucai.common.cache;

import java.util.Set;

/**
 * 带有效期的缓存
 * @param <K> 缓存对象的键
 * @param <V> 缓存对象
 */
public interface ExpireCache<K, V> {

    /**
     * 向缓存中添加一条缓存记录
     * @param key 值的键
     * @param value 存入的值
     * @return key映射的、被value替换掉的那个值  如果之前不存在key  则返回null
     */
    V put(K key, V value);

    /**
     * 获取一条缓存记录
     * @param key 记录的键
     * @return  缓存对象  null如果key在cache中不存在
     */
    V get(K key);

    /**
     * 使key对应的缓存记录失效
     * @param key 待失效缓存对象的key
     */
    void expire(K key);

    /**
     * 获取cache中所有键的集合
     * @return
     */
    Set<K> keySet();

    /**
     * 关闭缓存
     */
    void close();
}
