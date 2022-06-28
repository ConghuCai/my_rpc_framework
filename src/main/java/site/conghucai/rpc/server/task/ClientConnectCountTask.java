package site.conghucai.rpc.server.task;

import lombok.extern.slf4j.Slf4j;
import site.conghucai.common.net.HostSocketAddress;
import site.conghucai.hosts.connect.ConnectionPool;
import site.conghucai.hosts.node.NodeManager;

/**
 * 服务器统计接入的客户端、与客户端建立的连接总数的线程任务逻辑。
 */
@Slf4j
public class ClientConnectCountTask implements Runnable {

    private NodeManager nodeManager;

    public ClientConnectCountTask(NodeManager nodeManager) {
        this.nodeManager = nodeManager;
    }

    @Override
    public void run() {
        int count = nodeManager.getNodesCount();

        int connSum = 0;
        HostSocketAddress[] nodes = nodeManager.getAllNodes();
        for(HostSocketAddress node : nodes) {
            ConnectionPool pool = nodeManager.getConnectPool(node);
            connSum += pool == null ? 0 : pool.getCurrentSize();
        }

        log.info("[Server] Remote client accessing count: {}, connection constructed count: {}.", count, connSum);
        log.debug("access node list: {}", nodes);
    }
}
