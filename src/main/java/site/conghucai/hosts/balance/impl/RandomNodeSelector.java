package site.conghucai.hosts.balance.impl;

import site.conghucai.common.net.HostSocketAddress;
import site.conghucai.protocol.message.AbstractRpcMessage;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机选择器
 */
public class RandomNodeSelector extends NodeSelectorAdapter {

    public RandomNodeSelector() {
    }

    @Override
    protected HostSocketAddress doSelect(List<HostSocketAddress> nodes, AbstractRpcMessage msg) {
        int randomCur = ThreadLocalRandom.current().nextInt(nodes.size());
        return nodes.get(randomCur);
    }
}
