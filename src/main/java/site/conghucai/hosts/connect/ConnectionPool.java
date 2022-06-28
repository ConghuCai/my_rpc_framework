package site.conghucai.hosts.connect;

import java.util.List;

/**
 * Connection连接池，保存数个来自同一节点(远程主机)的不同的Connection对象
 */
public interface ConnectionPool {

    /**
     * 添加一个Connection对象到池中
     * @param connection
     */
    void addConnection(Connection connection);

    /**
     * 获取远程Server的connection对象
     * @return
     */
    Connection getConnection();

    /**
     * 释放一个connection对象
     * @param connectionID  connection对象ID
     */
    void releaseConnection(Long connectionID);

    /**
     * 获取和远程主机之间全部的connection对象
     * @return
     */
    List<Connection> getAllConnections();

    /**
     * 获取【当前】和远程主机之间全部的connection对象的数量
     * 注意线程间安全问题
     * @return
     */
    int getCurrentSize();

    /**
     * 关闭连接池
     */
    void close();
}
