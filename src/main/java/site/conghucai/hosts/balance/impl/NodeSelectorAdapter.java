package site.conghucai.hosts.balance.impl;

import org.apache.commons.collections.CollectionUtils;
import site.conghucai.common.net.HostSocketAddress;
import site.conghucai.hosts.balance.NodeSelector;
import site.conghucai.protocol.message.AbstractRpcMessage;
import site.conghucai.protocol.message.RpcRequestMessage;

import java.util.List;

/**
 * NodeSelector接口实现的模板模式
 */
public abstract class NodeSelectorAdapter implements NodeSelector {

    public NodeSelectorAdapter() {
    }

    /**
     * 节点选择模板  确认节点不为空、如果节点只有一个那就返回这个节点
     * @param nodes
     * @param msg
     * @return
     */
    @Override
    public HostSocketAddress selectNode(List<HostSocketAddress> nodes, AbstractRpcMessage msg) {
        if(CollectionUtils.isEmpty(nodes)) {
            return null;
        }

        //如果nodeList只有一个节点  直接返回那个节点；有多个节点则进行选择。
        return nodes.size() == 1 ? nodes.get(0) : doSelect(nodes, msg);
    }

    protected abstract HostSocketAddress doSelect(List<HostSocketAddress> nodes, AbstractRpcMessage msg);
}
