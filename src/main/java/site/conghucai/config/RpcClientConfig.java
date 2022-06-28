package site.conghucai.config;

import lombok.Data;
import site.conghucai.common.constant.CompressType;
import site.conghucai.common.constant.NodeSelectorRule;
import site.conghucai.common.constant.RpcConstant;
import site.conghucai.common.constant.SerializeType;

@Data
public class RpcClientConfig extends SSLConfig {

    private CompressType compressType = CompressType.Snappy;    //压缩策略
    private SerializeType serializeType = SerializeType.Protostuff; //序列化策略

    //tcp设置
    private Integer backlog = RpcConstant.SO_BACKLOG; //连接等待队列、半连接队列总长度
    private Integer sendBufSize = 65535;  //tcp发送窗口大小
    private Integer recvBufSize = 65535;  //tcp接受窗口大小
    private Integer lowWaterMark = 1024 * 1024;   //写缓冲区低水位(B)  //1M
    private Integer highWaterMark = 10 * 1024 * 1024; //写缓冲区高水位(B)  //10M

    //流量监控器设置
    private boolean trafficMonitorEnable = false; //启用流量控制
    private Long maxReadSpeed  = 10000000L;   //最大读速度带宽限制
    private Long maxWriteSpeed = 10000000L;   //最大写速度带宽限制

    //单个节点设置
    private int connectionPoolSizePerNode = 3;    //为每个服务器节点分配的连接池大小（维持的最大连接数）
    public static int nodeErrorTimesLimit = 3; //节点最大出错容忍次数  超过次数会认为节点连接异常  会设置失活

    //节点集群设置
    private NodeSelectorRule nodeSelectorRule = NodeSelectorRule.ROUND ;    //节点负载均衡选择策略
    private boolean enableNodeErrorTimesLimit = true;   //选择集群节点时，忽略失活连接点，开启节点健康定时检查任务
    private long nodeAutoUpholdPeriod = 10;   //10s进行一次节点连接检查和重置节点状态

    //客户端设置
    private int requestTimeout = 10; //普通请求的超时时间限制(s)
    private int heartbeatTimeout = 5; //心跳包超时时间
    private int connectionTimeout = 5; //连接服务器的超时时间(s)
    private int tcpIdleTime = 180; //3min
    private int heartBeatInterval = 30; //发送心跳包间隔时间(秒)

    //线程设置
    private int callbackThreadPoolSize = 200; //回调任务线程池大小，0为不使用线程池，在nio线程中执行回调
    private int callbackBlockQueueSize = 500; //回调任务阻塞队列大小。<1为不限制阻塞队列。

    private int IOThread =  RpcConstant.DEFAULT_IO_THREAD;   //netty group使用的线程数
    private int channelWorkerThread = RpcConstant.DEFAULT_IO_THREAD;  //channel处理工作的线程数量

    public RpcClientConfig() {
    }
}
