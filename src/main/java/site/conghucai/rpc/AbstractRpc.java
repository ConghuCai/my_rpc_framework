package site.conghucai.rpc;

import io.netty.channel.epoll.Epoll;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.*;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import org.apache.commons.lang3.SystemUtils;
import site.conghucai.common.constant.RpcConstant;
import site.conghucai.common.utils.IOStreamUtil;
import site.conghucai.config.SSLConfig;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ScheduledExecutorService;

public abstract class AbstractRpc {

    /**
     * 流量控制器(线程安全的)
     */
    protected GlobalTrafficShapingHandler trafficShapingHandler;

    /**
     * SSL容器  用于生成SSL控制器
     */
    protected SslContext sslContext;

    public AbstractRpc() {
    }

    protected void checkNotNull(Object object, String msg) {
        if(object == null) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 决定是否使用基于Epoll的Socket实现。
     * linux上使用EpollEventLoopGroup会有较少的gc有更高级的特性，性能更好。
     * @return
     */
    protected boolean useEpoll() {
        return SystemUtils.IS_OS_LINUX && Epoll.isAvailable();
    }

    /**
     * 初始化流量控制器
     */
    protected void initTrafficMonitor(ScheduledExecutorService executor, boolean enableMonitor, Long writeLmt, Long readLmt) {
        if(enableMonitor) {
            this.trafficShapingHandler = new GlobalTrafficShapingHandler(executor, writeLmt, readLmt);
        } else {
            this.trafficShapingHandler = null;
        }
    }


    /**
     * 初始化SSL容器
     * @param config 配置类
     * @param forClient 是否是客户端使用
     * @throws Exception
     */
    protected void buildSSLContext(SSLConfig config, boolean forClient) throws Exception {
        InputStream keyIn = null;
        InputStream crtIn = null;
        InputStream trustIn = null;

        try {
            keyIn = IOStreamUtil.getInputStreamFromPath(config.getKeyPath());   //服务器密钥
            crtIn = IOStreamUtil.getInputStreamFromPath(config.getCertPath());  //服务器自签名证书(含公钥)
            SslContextBuilder sslContextBuilder;

            if(forClient) {
                //客户端配置
                sslContextBuilder = SslContextBuilder.forClient().keyManager(crtIn, keyIn, config.getKeyPwd());
            } else {
                //服务器配置
                sslContextBuilder = SslContextBuilder.forServer(crtIn, keyIn, config.getKeyPwd());
                sslContextBuilder.clientAuth(config.getClientAuth() == null ?
                        ClientAuth.NONE: config.getClientAuth());  //默认服务器单向认证
            }
            sslContextBuilder.sslProvider(OpenSsl.isAvailable() ? SslProvider.OPENSSL :  SslProvider.JDK);

            if(config.getTrustCertPath() == null || config.getTrustCertPath().trim().isEmpty()) {
                //没有ca证书  则使用不受信的私有证书
                sslContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
            } else {
                trustIn = IOStreamUtil.getInputStreamFromPath(config.getTrustCertPath());
                sslContextBuilder.trustManager(trustIn);
            }
            sslContext = sslContextBuilder.build();

        } finally {
            if(keyIn != null) {
                keyIn.close();
            }

            if(crtIn != null) {
                crtIn.close();
            }

            if(trustIn != null) {
                trustIn.close();
            }
        }
    }
}
