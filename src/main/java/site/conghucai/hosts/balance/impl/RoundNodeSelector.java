package site.conghucai.hosts.balance.impl;

import org.apache.commons.lang3.StringUtils;
import site.conghucai.common.net.HostSocketAddress;
import site.conghucai.protocol.message.AbstractRpcMessage;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 基于轮询的节点选择器
 * 提供给节点列表  将轮询给出节点
 * 此方案是线程安全的！
 */
public class RoundNodeSelector extends NodeSelectorAdapter {
    private final Map<String, AtomicInteger> counterMap = new ConcurrentHashMap();  //保存节点群和对应的pointer
    private final Lock lock = (new ReentrantReadWriteLock()).writeLock();

    public RoundNodeSelector() {
    }

    @Override   //nodes.size >= 2
    protected HostSocketAddress doSelect(List<HostSocketAddress> nodes, AbstractRpcMessage msg) {
        //因为传入的节点list可能不同  因此不同的节点list要使用不同的轮询指针

        //1.这两步将节点规范排列  作为一个key来指定使用的pointer
        lock.lock();    //排序前注意先使用锁  否则并发下有ConcurrentModificationException风险
        try {
            nodes.sort(Comparator.comparing(HostSocketAddress::toString));
        }finally {
            lock.unlock();
        }
        String serverJoin = StringUtils.join(nodes.toArray(), ";");

        //2.为节点群生成一个单例pointer
        AtomicInteger pointer = this.counterMap.get(serverJoin);
        if (pointer == null) {
            synchronized(this.counterMap) {
                if ((pointer = this.counterMap.get(serverJoin)) == null) {
                    pointer = new AtomicInteger(0);
                    this.counterMap.put(serverJoin, pointer);
                }
            }
        }

        //返回节点
        return nodes.get(this.incrementAndGetModulo(nodes.size(), pointer));
    }

    //轮询找一个下一个节点的索引
    private int incrementAndGetModulo(int modulo, AtomicInteger pointer) {
        int current;
        int next;
        do {
            current = pointer.get();    //current为当前指针指向的节点
            next = (current + 1) % modulo;  //next为目标节点索引

            //while判断  要保证线程安全
            //如果发现当前的pointer不指向current  说明next被其他线程抢占了  重新查找下一个节点。
        } while(!pointer.compareAndSet(current, next));

        return next;
    }
}
