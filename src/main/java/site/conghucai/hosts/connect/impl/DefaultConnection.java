package site.conghucai.hosts.connect.impl;

import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import site.conghucai.common.net.HostSocketAddress;
import site.conghucai.protocol.message.AbstractRpcMessage;
import site.conghucai.hosts.connect.Connection;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class DefaultConnection implements Connection {

    private Long id;    //ID
    private Channel channel;    //netty-Channel对象
    private AtomicBoolean isClosed = new AtomicBoolean(false);  //连接关闭标志
    private long lastSendTime = System.currentTimeMillis(); //最后发送数据的时间

    public DefaultConnection(Long id, Channel channel) {
        this.id = id;
        this.channel = channel;
    }

    @Override
    public String toString() {
        return "DefaultConnection{" +
                "id=" + id +
                ", channel=" + channel +
                ", isClosed=" + isClosed +
                ", lastSendTime=" + lastSendTime +
                '}';
    }

    @Override
    public Long getID() {
        return this.id;
    }

    /**
     * 判断连接是否可用，直接使用netty提供的channel.isActive()即可
     * @return
     */
    @Override
    public boolean isAvailable() {
        return channel != null && !isClosed.get() && channel.isActive();
    }

    @Override
    public HostSocketAddress getRemoteAddr() {
        InetSocketAddress address = (InetSocketAddress) channel.remoteAddress();
        return HostSocketAddress.get(address);
    }

    @Override
    public long getLastSendTime() {
        return this.lastSendTime;
    }

    /**
     * 发送数据，使用netty的channel.writeAndFlush()即可
     * @param msg
     */
    @Override
    public void send(AbstractRpcMessage msg) {
        if(!channel.isWritable() || !isAvailable()) {
            log.warn("Connection is not writable now, or connection is not available. connection={}, msg={}",
                    this, msg);
            this.close();
            return;
        }

        //发送数据
        channel.writeAndFlush(msg);
        lastSendTime = System.currentTimeMillis();  //更新最后一次活动的时间
    }

    /**
     * 关闭连接，关闭通道。调用的是netty的channel.close()
     */
    @Override
    public void close() {
        if(isClosed.compareAndSet(false, true)) {
            log.info("Connect close! id={}, addr={}", id, getRemoteAddr());

            try {
                channel.close();    //将channel关闭、isClose置为true即可
            } catch ( Exception e) {
                log.error("connection close failed.", e);
            }

        } else {
            log.warn("Connect already closed. connection={}", this);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);    //将id的hash视为对象hash
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            DefaultConnection that = (DefaultConnection)obj;
            return Objects.equals(this.id, that.id);    //id相同，对象不同  也认为相同
        } else {
            return false;
        }
    }
}
