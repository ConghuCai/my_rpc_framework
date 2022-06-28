package site.conghucai.hosts.node.impl;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import site.conghucai.common.exception.NodeException;
import site.conghucai.common.net.HostSocketAddress;
import site.conghucai.hosts.balance.NodeSelector;
import site.conghucai.hosts.connect.Connection;
import site.conghucai.hosts.connect.ConnectionPool;
import site.conghucai.hosts.connect.impl.SharableConnectionPool;
import site.conghucai.hosts.node.NodeManager;
import site.conghucai.protocol.message.AbstractRpcMessage;
import site.conghucai.rpc.Client;
import site.conghucai.rpc.client.RpcClient;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 线程安全的NodeManager实现
 */
@Slf4j
public class SharableNodeManager implements NodeManager {
    //成员变量
    private int serverNodePoolSize; //每个节点的连接池大小设置
    private RpcClient client;   //客户端对象
    private NodeSelector nodeSelector;  //节点均衡选择器
    private boolean ignoreDisableNode;  //是否需要过滤掉不可用节点

    /**
     * 存放所有的连接节点
     */
    private final Set<HostSocketAddress> nodes = Sets.newConcurrentHashSet();   //线程安全的HashSet

    /**
     * 存放连接节点对应的连接池
     */
    private final Map<HostSocketAddress, ConnectionPool> nodeConnectionPoolMap = new ConcurrentHashMap<>();

    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    //安全锁
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();

    //服务器端使用此构造器
    public SharableNodeManager(){
        //这些参数都是客户端连接服务器节点用到的，服务器使用时不需要。
        serverNodePoolSize = 0;
        client = null;
        nodeSelector = null;
    }

    //客户端使用的构造器
    public SharableNodeManager(int serverNodePoolSize, RpcClient client, NodeSelector nodeSelector) {
        this.serverNodePoolSize = serverNodePoolSize;
        this.client = client;
        this.nodeSelector = nodeSelector;
    }

    /**
     * 添加一个节点到节点表中，此处会自动为节点新建连接池对象。
     * @param node
     */
    @Override
    public void addNode(HostSocketAddress node) {
        assertNotClose();

        writeLock.lock();
        try {
            if(!nodes.contains(node)) {
                nodes.add(node);

                //查找之前有没有为远程主机node分配过连接池
                ConnectionPool oldConnectionPool = nodeConnectionPoolMap.get(node);
                if(oldConnectionPool != null) {
                    oldConnectionPool.close();  //如果发现旧纪录  则关闭旧的池 + 新建新的连接池
                }

                ConnectionPool newConnectionPool = new SharableConnectionPool(serverNodePoolSize, node, client);    //新的连接池
                nodeConnectionPoolMap.put(node, newConnectionPool);
            }
        } finally {
            writeLock.unlock();
        }

    }

    @Override
    public void addNodes(List<HostSocketAddress> nodes) {
        for(HostSocketAddress node : nodes) {
            this.addNode(node);
        }
    }

    /**
     * 移出节点、关闭连接池
     * @param node
     */
    @Override
    public void removeNode(HostSocketAddress node) {
        assertNotClose();

        writeLock.lock();
        try {
            nodes.remove(node); //移除远程主机节点

            ConnectionPool pool = nodeConnectionPoolMap.get(node);
            if(pool != null) {
                pool.close();   //关闭节点连接池
                nodeConnectionPoolMap.remove(node);
            }

            NODE_STATUS_MAP.remove(node); //移出节点状态信息

        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public HostSocketAddress[] getAllNodes() {
        readLock.lock();

        HostSocketAddress[] allNodes;
        try {
            allNodes = nodes.toArray(new HostSocketAddress[0]);
        } finally {
            readLock.unlock();
        }
        return allNodes;
    }

    @Override
    public int getNodesCount() {
        readLock.lock();

        int size;
        try {
            size = nodes.size();
        } finally {
            readLock.unlock();
        }
        return size;
    }

    /**
     * 获取指定节点的连接池对象
     * @param node
     * @return
     */
    @Override
    public ConnectionPool getConnectPool(HostSocketAddress node) {
        return nodeConnectionPoolMap.get(node);
    }

    /**
     * 关闭节点管理器，释放所有资源
     */
    @Override
    public void close() {
        writeLock.lock();

        try {
            if(isClosed.compareAndSet(false, true)) {
                Iterator<ConnectionPool> it = nodeConnectionPoolMap.values().iterator();

                while(it.hasNext()) {
                    ConnectionPool pool = it.next();
                    pool.close();   //关闭所有连接池
                }

                nodes.clear();  //清空节点、连接池记录
                nodeConnectionPoolMap.clear();
            }

        } catch (Exception e){
            log.error("NodeManager closed failed! ", e);
            throw new NodeException("NodeManager closed failed! ", e);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 是否在节点过滤时，根据NodeStatus忽略不可用节点
     * @param enable
     */
    @Override
    public void setIgnoreDisableNode(boolean enable) {
        ignoreDisableNode = enable;
    }

    /**
     * 从一群节点中获取一个可用连接对象
     * @param nodes 节点群
     * @param msg   消息
     * @return
     */
    @Override
    public Connection chooseConnection(List<HostSocketAddress> nodes, AbstractRpcMessage msg) {
        //节点过滤
        List<HostSocketAddress> filterNodes = nodeFilter(nodes);

        HostSocketAddress selectedNode = nodeSelector.selectNode(filterNodes, msg);
        return getConnectionFromPool(selectedNode);
    }

    /**
     * 可用节点过滤器
     * @param nodes
     * @return
     */
    private List<HostSocketAddress> nodeFilter(List<HostSocketAddress> nodes) {
        readLock.lock();
        try {
            if(CollectionUtils.isEmpty(nodes)) {
                throw new NodeException("please give at least 1 node.");
            }

            //节点只有一个  或  不需要检查失效节点   直接返回nodes
            if(nodes.size() == 1 || !ignoreDisableNode) {
                return nodes;
            }

            //过滤掉可不用的nodes （错误次数过多的）
            List<HostSocketAddress> availableNodes = nodes.stream().filter(
                    //由于只有节点出过错才会new 一个Status对象  所有Status对象为空就说明出错次数为0，节点可用
                    node -> (NODE_STATUS_MAP.get(node) == null || !NODE_STATUS_MAP.get(node).errorTooMuch())
            ).collect(Collectors.toList());

            if(availableNodes.isEmpty()) {
                throw new NodeException("No server nodes available!");
            }

            return availableNodes;

        } finally {
            readLock.unlock();
        }
    }

    /**
     * 根据节点  从节点的池中拿到连接对象
     * @param node
     * @return
     */
    private Connection getConnectionFromPool(HostSocketAddress node) {
        ConnectionPool pool = nodeConnectionPoolMap.get(node);
        if(pool == null) {
            //没有这个节点的连接池记录  节点添加进管理器
            addNode(node); //添加节点  分配连接池
            pool = nodeConnectionPoolMap.get(node);
        }
        return pool.getConnection();
    }


    @Override
    public Client getClient() {
        return client;
    }


    private void assertNotClose() {
        if(this.isClosed.get()) {
            throw new NodeException("nodeManager has closed. node operation failed.");
        }
    }
}
