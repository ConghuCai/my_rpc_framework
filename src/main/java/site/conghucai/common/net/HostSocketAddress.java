package site.conghucai.common.net;

import lombok.Data;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Objects;

@Data
public class HostSocketAddress {

    private String addr;
    private Integer port;

    private HostSocketAddress(String addr, Integer port) {
        this.addr = addr;
        this.port = port;
    }

    public static HostSocketAddress get(String addr, Integer port) {
        return new HostSocketAddress(addr, port);
    }

    public static HostSocketAddress get(InetSocketAddress addr) {
        return new HostSocketAddress(addr.getHostString(), addr.getPort());
    }

    public static HostSocketAddress get(String socketAddr) {
        if(socketAddr == null || socketAddr.equals("")) {
            throw null;
        }
        String[] split = socketAddr.split(":");
        return new HostSocketAddress(split[0], Integer.parseInt(split[1]));
    }

    public static HostSocketAddress get(InetAddress address, int port) {
        return new HostSocketAddress(address.getHostAddress(), port);
    }

    public static HostSocketAddress getLocalSocket(int port) {
        HostSocketAddress local;
        try {
            local = HostSocketAddress.get(InetAddress.getLocalHost(), port);
        } catch (UnknownHostException e) {
            throw new RuntimeException("get localhost socket failed!", e);
        }
        return local;
    }


    public String getSocketStr() {
        return addr + ":" + port;
    }

    @Override
    public String toString() {
        return "[" + addr + ":" + port + "]";
    }

    //注意equals方法要覆写！
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            HostSocketAddress that = (HostSocketAddress)o;
            return addr.equals(that.addr) && port.equals(that.port);
        } else {
            return false;
        }
    }

    //只要地址、端口相同  就是同一对象
    @Override
    public int hashCode() {
        return Objects.hash(addr, port);
    }
}
