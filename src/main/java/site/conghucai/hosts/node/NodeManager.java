package site.conghucai.hosts.node;

import site.conghucai.common.net.HostSocketAddress;
import site.conghucai.config.RpcClientConfig;
import site.conghucai.hosts.connect.Connection;
import site.conghucai.hosts.connect.ConnectionPool;
import site.conghucai.protocol.message.AbstractRpcMessage;
import site.conghucai.rpc.Client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 远程服务器(node)管理器
 * 客户端，管理客户端所连接的多个服务器节点
 */
public interface NodeManager {

    /**
     * 存放节点的状态  静态共享量
     * 注意发生了超时等错误 节点才会有status记录 如果为空就说明节点正常可用
     */
    Map<HostSocketAddress, NodeStatus> NODE_STATUS_MAP = new ConcurrentHashMap<>();

    /**
     * 允许节点连接失败的最大次数
     */
    int NODE_ERROR_TIMES = RpcClientConfig.nodeErrorTimesLimit;

    /**
     * 添加一个主机节点到管理器
     * @param node
     */
    void addNode(HostSocketAddress node);

    /**
     * 添加多个主机节点
     * @param nodes
     */
    void addNodes(List<HostSocketAddress> nodes);

    /**
     * 移除一个主机节点
     * @param node
     */
    void removeNode(HostSocketAddress node);

    /**
     * 获取全部连接中的主机节点
     * @return
     */
    HostSocketAddress[] getAllNodes();

    /**
     * 获取全部连接中的主机节点数量
     * @return
     */
    int getNodesCount();

    /**
     * 获取远程主机的连接池
     * @param node
     * @return
     */
    ConnectionPool getConnectPool(HostSocketAddress node);


    /**
     * 关闭管理器，释放所有资源
     */
    void close();

    /**
     * 是否排除不可用节点    (超时节点、异常次数过多节点)
     * @param enable
     */
    void setIgnoreDisableNode(boolean enable);


    /**
     * 从给定节点和需要需要发送消息，给定一个被选择的 连接对象
     * @param nodes 节点群
     * @param msg   消息
     * @return
     */
    Connection chooseConnection(List<HostSocketAddress> nodes, AbstractRpcMessage msg);


    /**
     * 获取客户端对象
     * @return
     */
    Client getClient();

    /**
     * 记录一次节点连接失败记录
     * 内部会调用节点记录对象的失败记录自增器  达到了最大失败次数则设置节点失活
     * @param serverNode
     */
    static void serverErrorMemo(HostSocketAddress serverNode) {
        NodeStatus nodeStatus = NODE_STATUS_MAP.get(serverNode);
        if (nodeStatus == null) {
            synchronized (NODE_STATUS_MAP) {
                if ((nodeStatus = NODE_STATUS_MAP.get(serverNode)) == null) {
                    nodeStatus = new NodeStatus(serverNode, NODE_ERROR_TIMES);  //为节点创建状态对象
                    NODE_STATUS_MAP.put(serverNode, nodeStatus);
                }
            }
        }
        nodeStatus.errorIncrease(); //记录一次错误
    }
}
