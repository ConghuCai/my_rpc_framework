package site.conghucai.rpc.handler.process.deal;

import lombok.Data;
import lombok.ToString;
import site.conghucai.hosts.connect.Connection;
import site.conghucai.hosts.node.NodeManager;
import site.conghucai.protocol.message.AbstractRpcMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 业务上下文
 * 将业务处理需要的内容存放于此对象
 */
@Data
@ToString
public class DealingContext {

    private boolean isClient;   //使用者是否为客户端
    private AbstractRpcMessage message; //待处理的消息

    private NodeManager nodeManager;    //节点管理器
    private Connection connection;  //remote的连接对象
    private boolean printHeartbeatInfo; //是否要打印保活包的处理信息(服务器)

    DealingUnitChain dealings;  //业务处理链
}
