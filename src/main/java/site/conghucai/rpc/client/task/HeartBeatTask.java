package site.conghucai.rpc.client.task;

import lombok.extern.slf4j.Slf4j;
import site.conghucai.common.net.HostSocketAddress;
import site.conghucai.hosts.connect.Connection;
import site.conghucai.hosts.connect.ConnectionPool;
import site.conghucai.hosts.node.NodeManager;
import site.conghucai.rpc.Client;

import java.util.concurrent.TimeUnit;

/**
 * 心跳保活任务逻辑。
 * 向节点管理器中，所有已经建立的连接对象进行心跳保活。
 */
@Slf4j
public class HeartBeatTask implements Runnable {

    private NodeManager nodeManager;
    private Client client;

    private static final long SENT_INTERVAL = TimeUnit.SECONDS.toMillis(10);   //距上次发送数据时间超过10s，就会发送心跳包

    public HeartBeatTask(NodeManager nodeManager, Client client) {
        this.nodeManager = nodeManager;
        this.client = client;
    }

    @Override
    public void run() {
        try {
            HostSocketAddress[] allNodes = nodeManager.getAllNodes();
            for(HostSocketAddress node: allNodes) {
                ConnectionPool pool = nodeManager.getConnectPool(node);

                for(Connection conn : pool.getAllConnections()) {
                    long lastSendTime = conn.getLastSendTime();
                    if(System.currentTimeMillis() - lastSendTime > SENT_INTERVAL) {
                        client.sendHeartBeat(conn); //阻塞式方法
                    }
                }
            }
        } catch (Exception e) {
            log.error("heart beat task exception.", e);
        }

    }
}
