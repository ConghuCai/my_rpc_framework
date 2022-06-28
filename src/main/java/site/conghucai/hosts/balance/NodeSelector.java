package site.conghucai.hosts.balance;

import site.conghucai.common.annotation.SPI;
import site.conghucai.common.net.HostSocketAddress;
import site.conghucai.protocol.message.AbstractRpcMessage;
import site.conghucai.protocol.message.RpcRequestMessage;

import java.util.List;

/**
 * 节点选择器，根据某种规则选择Rpc服务节点
 */
@SPI
public interface NodeSelector {
    HostSocketAddress selectNode(List<HostSocketAddress> nodes, AbstractRpcMessage msg);
}
