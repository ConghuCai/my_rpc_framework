package site.conghucai.rpc.client.task;

import com.google.common.collect.Lists;
import site.conghucai.common.net.HostSocketAddress;
import site.conghucai.hosts.connect.Connection;
import site.conghucai.hosts.node.NodeManager;
import site.conghucai.hosts.node.NodeStatus;


/**
 * 维持服务器节点的连接状态的  任务逻辑
 * 节点选择器会根据设置，忽略掉错误次数太多的那些节点。
 * 维持服务器节点任务  就是发送心跳包并等待响应，如果成功拿到响应的话，就重置次数。失败的话也不会增加节点的出错次数。
 *
 * 建议使用守护线程执行此runnable
 */
public class NodeUpholdTask implements Runnable {

    private NodeManager nodeManager;

    /**
     * @param nodeManager 节点管理器
     */
    public NodeUpholdTask(NodeManager nodeManager) {
        this.nodeManager = nodeManager;
    }
    @Override
    public void run() {
        //检查NodeStatusMap，里面的Node都是曾经发生过错误的节点，遍历这些节点发送心跳包 来检查连接是否健康

        for(NodeStatus status : NodeManager.NODE_STATUS_MAP.values()) {
            if(status.getErrorTimes() > 0) {
                //status的节点发生过错误
                HostSocketAddress node = status.getNode();

                //1.拿到节点的连接对象
                Connection connection = nodeManager.chooseConnection(Lists.newArrayList(node), null);   //就一个节点  返回的肯定是这个节点的连接对象

                //2.发送心跳包
                boolean res = nodeManager.getClient().sendHeartBeat(connection);

                //3.判断心跳包是否发送成功
                if(res) {
                    status.resetErrorTimes();   //重置节点出错次数
                }
            }
        }

    }
}
