package site.conghucai.hosts.connect.impl;

import lombok.extern.slf4j.Slf4j;
import site.conghucai.common.exception.ConnectionException;
import site.conghucai.common.net.HostSocketAddress;
import site.conghucai.hosts.connect.Connection;
import site.conghucai.hosts.connect.ConnectionPool;
import site.conghucai.hosts.node.NodeManager;
import site.conghucai.rpc.Client;
import site.conghucai.rpc.client.RpcClient;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 线程安全的连接池，锁机制支持多个线程之间的复用
 */
@Slf4j
public class SharableConnectionPool implements ConnectionPool {

    //成员字段
    private int maxPoolSize;    //池大小限制
    private HostSocketAddress remoteAddr;   //远程节点地址
    private Client client;   //客户端对象

    //连接池  使用一个安全Map实现
    private final Map<Long, Connection> connections = new ConcurrentHashMap<>();

    //原子标志量
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final AtomicInteger pointer = new AtomicInteger(0); //轮询指针  用于从池中轮流取出连接对象

    //锁
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();

    public SharableConnectionPool(int maxPoolSize, HostSocketAddress remoteAddr, RpcClient client) {
        this.maxPoolSize = maxPoolSize;
        this.remoteAddr = remoteAddr;
        this.client = client;

    }

    /**
     * 添加连接对象到池中
     * @param connection
     */
    @Override
    public void addConnection(Connection connection) {
        writeLock.lock();
        try {
            connections.put(connection.getID(), connection);    //线程安全地添加connection对象
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 从连接池中获取一个可用连接对象。
     * @return
     */
    @Override
    public Connection getConnection() {
        if(isClosed.get()) {
            throw new ConnectionException("connectionPool already closed. remote addr: " + remoteAddr);
        }

        connectionPoolInit();   //初始化 连接池  进行节点连接、将成功的连接对象放入池中
        List<Connection> connList = new CopyOnWriteArrayList<>(connections.values());

        if(!connections.isEmpty()) {
            Connection conn = connList.get(incrementAndGetModulo(getCurrentSize())); //轮询从池中取连接对象
            if(!conn.isAvailable()) {   //发现不可用的连接对象  释放掉
                releaseConnection(conn.getID());
                conn = getConnection(); //重新在池中找新的连接对象
            }
            return conn;
        }

        //走到这一步说明没有连接对象可用
        throw new ConnectionException("no connection available. remoteArr: " +remoteAddr);
    }

    /**
     * 客户端的连接池初始化，接受最多三次失败
     */
    private void connectionPoolInit() {
        if(getCurrentSize() >= maxPoolSize) {
            //连接已满
            //服务器使用连接池  则直接将maxPoolSize设为0即可。  因为这个方法是面向客户端的。
            return;
        }

        writeLock.lock();
        try {
            if(getCurrentSize() >= maxPoolSize) {   //double-check
                return;
            }

            int retryTimes = 0;
            do {
                try {
                    Connection conn = client.connectRemoteNode(remoteAddr.getAddr(), remoteAddr.getPort());

                    if(conn != null && conn.isAvailable()) {
                        addConnection(conn);    //加入池
                        retryTimes = 0; //重试次数清零  跳出循环

                    } else {
                        retryTimes++;
                    }
                } catch (Exception e) {
                    retryTimes++;
                    NodeManager.serverErrorMemo(remoteAddr);
                    log.error("serverNode {} connectPool init failed", remoteAddr, e);
                }

            } while(retryTimes > 0 && retryTimes < 3); //3次尝试连接

        } finally {
            writeLock.unlock();
        }
    }

    //轮询取指针索引
    private int incrementAndGetModulo(int modulo) {
        for (; ; ) {
            int current = pointer.get();
            int next = (current + 1) % modulo;
            if (pointer.compareAndSet(current, next)) {
                return next;
            }
        }
    }

    /**
     * 从池中释放一个连接对象
     * @param connectionID  connection对象ID
     */
    @Override
    public void releaseConnection(Long connectionID) {
        writeLock.lock();

        try {
            Connection connection = connections.get(connectionID);
            if(connection != null && connection.isAvailable()) {
                connection.close(); //连接有效，则关闭连接
            }
            connections.remove(connectionID);   //移出连接记录

        } catch (Exception e) {
            log.error("releaseConnection failed", e);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public List<Connection> getAllConnections() {
        return new CopyOnWriteArrayList<>(connections.values());
    }


    /**
     * 拿到当前连接池中连接对象的数量
     * @return
     */
    @Override
    public int getCurrentSize() {
        readLock.lock();
        try {
            return connections.size();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 关闭连接池
     */
    @Override
    public void close() {
        if(isClosed.compareAndSet(false, true)) {

            writeLock.lock();
            try {
                Connection connection;
                Iterator<Connection> it = connections.values().iterator();
                while(it.hasNext()) {
                    connection = it.next();
                    if(connection.isAvailable()) {
                        connection.close(); //若连接仍然可用 则关闭连接
                    }

                    it.remove();    //移出连接对象
                }

                connections.clear();    //清空连接记录

                log.info("ConnectionPool of node {} closed. All Connection has released.", remoteAddr);

            } catch (Exception e) {
                log.error("ConnectionPool of node {} closed failed!", remoteAddr, e);
            } finally {
                writeLock.unlock();
            }


        } else {
            log.warn("ConnectionPool has already closed. remoteAddr: " + remoteAddr);
        }
    }
}
