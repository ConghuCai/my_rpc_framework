package site.conghucai.rpc.demo.ssl;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.*;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import site.conghucai.common.exception.RpcException;
import site.conghucai.common.utils.IOStreamUtil;
import site.conghucai.config.SSLConfig;

import java.io.*;

public class SSLServer {

    private static SslContext sslContext;

    public static void main(String[] args) throws Exception {
        SSLConfig config = new SSLConfig();

        buildSSLContext(config, false);

        new ServerBootstrap()
                .group(new NioEventLoopGroup())
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel channel) throws Exception {
                        channel.pipeline().addLast(new LoggingHandler());
                        channel.pipeline().addLast(sslContext.newHandler(channel.alloc()));
                        channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                ctx.writeAndFlush("Hello!".getBytes());
                            }
                        });
                    }
                })
                .bind(8080).sync();

    }

    private static void buildSSLContext(SSLConfig config, boolean forClient) throws IOException {
        InputStream keyIn = null;
        InputStream crtIn = null;
        InputStream trustIn = null;

        try {
            keyIn = IOStreamUtil.getInputStreamFromPath(config.getKeyPath());
            crtIn = IOStreamUtil.getInputStreamFromPath(config.getCertPath());
            SslContextBuilder sslContextBuilder;

            if(forClient) {
                //客户端配置
                sslContextBuilder = SslContextBuilder.forClient().keyManager(crtIn, keyIn, config.getKeyPwd());
            } else {
                //服务器配置
                sslContextBuilder = SslContextBuilder.forServer(crtIn, keyIn, config.getKeyPwd());
                sslContextBuilder.clientAuth(ClientAuth.NONE);  //服务器单向认证
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


    private static SslProvider getSSLProvider() {
        if(OpenSsl.isAvailable()) {
            return SslProvider.OPENSSL;
        }

        return SslProvider.JDK;
    }

}

