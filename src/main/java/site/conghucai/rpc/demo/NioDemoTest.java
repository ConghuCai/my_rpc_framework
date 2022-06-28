package site.conghucai.rpc.demo;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import site.conghucai.common.constant.CompressType;
import site.conghucai.common.constant.ResponseStatus;
import site.conghucai.common.constant.SerializeType;
import site.conghucai.protocol.codec.RpcMessageDecoder;
import site.conghucai.protocol.codec.RpcMessageEncoder;
import site.conghucai.protocol.message.HeartBeatMessage;
import site.conghucai.protocol.message.RpcRequestMessage;
import site.conghucai.protocol.message.RpcResponseMessage;

public class NioDemoTest {

    public static void main(String[] args) throws InterruptedException {
        Channel channel = new ServerBootstrap().group(new NioEventLoopGroup())
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel channel) throws Exception {

                        channel.pipeline().addLast(new LoggingHandler(),
                                new RpcMessageDecoder(),
                                new RpcMessageEncoder(SerializeType.Json, CompressType.None),

                                new SimpleChannelInboundHandler<RpcRequestMessage>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, RpcRequestMessage req) throws Exception {
                                        Long sequence = req.getSequence();

                                        RpcResponseMessage message = new RpcResponseMessage(sequence, System.currentTimeMillis(), "test",
                                                "res", null, ResponseStatus.SUCCESS_CODE);

                                        ctx.writeAndFlush(message);
                                    }
                                },

                                new SimpleChannelInboundHandler<HeartBeatMessage>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, HeartBeatMessage beat) throws Exception {
                                        Long sequence = beat.getSequence();
                                        RpcResponseMessage resp = new RpcResponseMessage(sequence, System.currentTimeMillis(), "heart-beat-resp",
                                                "heartbeat-resp", null, ResponseStatus.SUCCESS_CODE);
                                        ctx.writeAndFlush(resp);
                                    }
                                }
                        );
                    }
                }).bind(10086).sync().channel();


    }

}
