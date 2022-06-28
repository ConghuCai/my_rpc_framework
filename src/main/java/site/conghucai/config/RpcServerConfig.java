package site.conghucai.config;

import lombok.Data;
import lombok.Getter;
import site.conghucai.common.constant.CompressType;
import site.conghucai.common.constant.RpcConstant;
import site.conghucai.common.constant.SerializeType;

/**
 * RpcServer配置类
 */
@Data
public class RpcServerConfig extends SSLConfig {
    private Integer port = 8080;  //服务端绑定端口
    private CompressType compressType = CompressType.Snappy;    //压缩策略
    private SerializeType serializeType = SerializeType.Protostuff; //序列化策略
    private Integer IOThread =  RpcConstant.DEFAULT_IO_THREAD;   //netty group使用的线程数

    //tcp设置
    private Integer backlog = RpcConstant.SO_BACKLOG; //连接等待队列、半连接队列总长度
    private Integer sendBufSize = 65535;  //tcp发送窗口大小
    private Integer recvBufSize = 65535;  //tcp接受窗口大小
    private Integer lowWaterMark = 1048576;   //写缓冲区低水位  //1M
    private Integer highWaterMark = 10485760; //写缓冲区高水位  //10M

    //服务器耗时任务线程池
    private Integer channelWorkerThreadNum = RpcConstant.DEFAULT_IO_THREAD;   //客户端channel处理的线程数   高连接下可调大

    //流量监控器设置
    private boolean trafficMonitorEnable = false; //启用流量控制
    private Long maxReadSpeed  = 10000000L;   //最大读速度带宽限制
    private Long maxWriteSpeed = 10000000L;   //最大写速度带宽限制

    //服务器TCP连接的IdleTime
    private Integer tcpIdleTime = 180; //3min

    //连接信息打印
    private int printConnectCountInterval = 60;   //检查服务器连接客户端信息的时间间隔。0为不检查。

    //业务线程池设置
    private int poolSize = 200; //业务线程池大小  0为不使用
    private int blockQueueSize = 500;   //阻塞队列大小设置

    private boolean enablePrintHeartbeatInfo = true;

    public RpcServerConfig() {
    }
}
