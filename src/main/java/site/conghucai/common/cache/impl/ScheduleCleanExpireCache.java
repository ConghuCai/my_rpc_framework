package site.conghucai.common.cache.impl;

import lombok.extern.slf4j.Slf4j;
import site.conghucai.common.cache.ExpireCache;
import site.conghucai.common.thread.RpcDaemonThreadFactory;
import site.conghucai.common.utils.ThreadUtil;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 定时清理失效记录的缓存Api
 * @param <K>
 * @param <V>
 */
@Slf4j
public class ScheduleCleanExpireCache<K, V> implements ExpireCache<K, V> {
    //成员变量
    private long expireTimeMS;  //失效时间
    private long cleanPeriodMS; //清理失效缓存的周期
    private long cacheSizeLmt;  //缓存大小限制

    private static volatile ThreadFactory cleanThreadFactory;   //自动清理缓存的线程工厂  需要：守护线程实现 + 单例构造

    private final Map<K, V> cache = new ConcurrentHashMap<>();    //缓存  用线程安全的Map实现
    private ScheduledExecutorService cleanTaskPool; //定时清理缓存的线程池

    private TimeNode<K> earlyNode, latestNode;  //时间节点链表的头尾节点  使得时间节点链表在时间上是有序的

    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock readLock = readWriteLock.readLock();
    private final Lock writeLock = readWriteLock.writeLock();



    /*------------------------------------------------inner class------------------------------------------------------------*/
    /**
     * 内部类TimeNode
     * 代表缓存加入的时间节点对象  可组成一条时间节点链  代表【缓存记录加入的先后顺序】
     * 链表本身需要保持时间上是有序的。
     *
     * 其中包含加入缓存时的时间戳，存放 在一个【清理周期】内 加入缓存的key的集合
     * @param <K> 对应缓存中的键
     */
    class TimeNode<K> {
        /**
         * 时间节点的时间戳
         */
        private long timestamp;

        /**
         * 在一个清理周期内的缓存key的集合
         * 在[timestamp, cleanPeriod + timestamp) 时间范围内加入缓存的 记录的key 的集合
         */
        //这里的设计很关键！！
        //keys是： 1.缓存记录的key  2.处在一个清理周期内的其他记录的key
        //比如设定10s进行一次清理。
        //  有一个节点在Ts时建立的，那在[T,T+10s)这段时间内加入的记录都存在一个节点对象里。
        //这样设计的原因：清理方便。 在清理时，认为一个时间周期内的节点全部是timestamp时加入的  同时失效
        private Set<K> keys = new LinkedHashSet<>();

        /**
         * 下一个时间节点对象
         */
        private TimeNode<K> next;

        /**
         * 根据新加入缓存的记录的key  建立一个新时间节点
         * @param key 新加入缓存的记录的key
         */
        private TimeNode(K key) {
            this.timestamp = now();
            addKey(key);
        }

        /**
         * 根据新加入缓存的记录的key  加入这个时间节点
         * 使用前务必使用isInCurInterval()判断当前时间 和此时间节点是否在一个清理周期内！
         * 表示是在[this.timestamp, cleanPeriod + this.timestamp) 时间范围内加入缓存的
         * @param key
         */
        private void addKey(K key) {
            keys.add(key);
        }

        /**
         * 判断时间节点记录的时间戳是否已过期
         * @return true过期  false未过期
         */
        private boolean isExpire() {
            return now() - timestamp > expireTimeMS;
        }

        /**
         * 判断【当前时间】 和【此时间节点记录的时间】是否在一个清理周期内
         * 判断当前时间是否在[this.timestamp, cleanPeriod + this.timestamp)范围内
         * @return
         */
        private boolean isInCurInterval() {
            return now() - timestamp < cleanPeriodMS;
        }

        private long now() {    //当前时间
            return System.currentTimeMillis();
        }
    }
    /*-----------------------------------------------------------------------------------------------------------------------*/






    /*---------------------------------------------functions-----------------------------------------------------------------*/
    /**
     * 自动清理的缓存构造器
     * @param sizeLmt   缓存大小限制
     * @param expireTime    记录失效时间
     * @param unit  失效时间单位
     * @param cleanFreq 一个失效时间长度内，自动清理失效缓存的次数，最低为10次.
     */
    public ScheduleCleanExpireCache(long sizeLmt, int expireTime, TimeUnit unit, int cleanFreq) {
        if(expireTime <= 0) {
            throw new IllegalArgumentException("expireTime can't be 0 or lower.");
        }

        expireTimeMS = unit.toMillis(expireTime);  //统一转毫秒单位
        cacheSizeLmt = sizeLmt;

        cleanFreq = Math.max(10, cleanFreq);
        cleanPeriodMS = expireTimeMS / cleanFreq;   //清理次数转成清理时间周期
        // log.debug("cache init, expireTimeMS={}, cleanPeriodMS={}", expireTimeMS, cleanPeriodMS);

        initScheduleExecutor(); //开启自动清理线程
    }

    /**
     * 默认参数构造器，不考虑缓存尺寸限制，自动清理次数为10次/失效时间
     * 只需指定失效时间即可。
     * @param expireTime
     * @param unit
     */
    public ScheduleCleanExpireCache(int expireTime, TimeUnit unit) {
        this(Long.MAX_VALUE, expireTime, unit, 10);
    }



    // 初始化一个自动清理失效缓存记录的线程并周期性执行
    private void initScheduleExecutor() {
        //1.单例出来一个线程工厂
        if(cleanThreadFactory == null) {
            synchronized (ScheduledExecutorService.class) {
                if(cleanThreadFactory == null) {
                    cleanThreadFactory = new RpcDaemonThreadFactory("rpcCacheExpire");  //守护线程工厂
                }
            }
        }

        //2.交给周期线程池运行
        cleanTaskPool = Executors.newScheduledThreadPool(1, cleanThreadFactory);//使用单线程运行

        //3.向线程池写入 清理任务逻辑  并周期性执行
        Runnable r = () -> {
            clean();
        };
            //expireTimeMS作为开始执行的时间
            //cleanPeriodMS作为执行周期
        cleanTaskPool.scheduleAtFixedRate(r, expireTimeMS, cleanPeriodMS, TimeUnit.MILLISECONDS);
    }

    //清理缓存中  已经过期的缓存对象，如果缓存满了将未过期的、最早加入的缓存清理掉
    //由于TimeNode和put的api设计  TimeNode链表在时间上是有序的  顺序遍历即可
    private void clean() {
        writeLock.lock();
        try {
            //按照时间顺序遍历时间节点链表  将过期的、超过大小的  移出即可
            while(earlyNode != null &&
                    (earlyNode.isExpire() || cache.size() > cacheSizeLmt)) {
                //将最早的时间点earlyNode所记录的【全部缓存key】  从缓存中清理
                for(K key : earlyNode.keys) {
                    cache.remove(key);
                    // log.debug("key {} was removed, current size: {}", key, cache.size());
                }
                earlyNode = earlyNode.next; //向后遍历
            }

        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public V put(K key, V value) {
        V res = cache.put(key, value);
        if(res != null) {
            //put不返回null  说明这是一个key的覆盖  则不需要其他改动
            //因为key仍然有效  时间节点也仍然有效
            return res;
        }

        //res == null  这个key是新加入的  需要记录加入的时间信息
        writeLock.lock();
        try {
            if(earlyNode == null) {
                //不存在时间节点链  则初始化整个时间节点链
                earlyNode = new TimeNode<>(key);
                latestNode = earlyNode;
                // log.debug("key {} establish a new node {}. value {}", key, latestNode.timestamp, cache.get(key));
            } else {
                //时间节点链已经初始化
                if(latestNode.isInCurInterval()) {
                    //和最近的时间节点处在一个清理周期内
                    latestNode.addKey(key);
                    // log.debug("key {} join time node {}. value {}", key, latestNode.timestamp, cache.get(key));
                } else {
                    //已经超过一个清理周期了 建立新的时间节点，代表其加入的时间
                    latestNode.next = new TimeNode<>(key);
                    latestNode = latestNode.next;   //最为最新的时间节点  保持在时间上的有序性。
                    // log.debug("key {} establish a new node {}. value {}", key, latestNode.timestamp, cache.get(key));
                }
            }

        } finally {
            writeLock.unlock();
        }

        return null;
    }

    @Override
    public V get(K key) {
        readLock.lock();
        try {
            return cache.get(key);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void expire(K key) {
        writeLock.lock();
        try {
            cache.remove(key);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Set<K> keySet() {
        readLock.lock();
        try {
            return cache.keySet();
        }finally {
            readLock.unlock();
        }
    }


    @Override
    public void close() {
        writeLock.lock();
        try {
            ThreadUtil.shutdownGracefully(cleanTaskPool, 5);
            cache.clear();
        } finally {
            writeLock.unlock();
        }
    }
}
