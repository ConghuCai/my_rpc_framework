package site.conghucai.hosts.node;

import site.conghucai.common.net.HostSocketAddress;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 用于保存远程主机节点状态信息的对象
 */
public class NodeStatus {
    private HostSocketAddress node;
    private Integer maxErrorTimes;  //最大允许失败次数
    private AtomicInteger errorTimes = new AtomicInteger(0);    //已失败的次数

    public NodeStatus(HostSocketAddress node, Integer maxErrorTimes) {
        this.node = node;
        this.maxErrorTimes = maxErrorTimes;
    }

    /**
     * 增加一次错误记录
     * @return
     */
    public int errorIncrease() {
        return errorTimes.incrementAndGet();
    }

    /**
     * 节点是否超出出错容忍上限
     * @return
     */
    public boolean errorTooMuch() {
        return errorTimes.get() >= maxErrorTimes;    //没到错误lmt  认为可用
    }

    public HostSocketAddress getNode() {
        return node;
    }

    public Integer getMaxErrorTimes() {
        return maxErrorTimes;
    }

    public Integer getErrorTimes() {
        return errorTimes.get();
    }

    public void resetErrorTimes() {
        errorTimes.set(0);
    }
}
