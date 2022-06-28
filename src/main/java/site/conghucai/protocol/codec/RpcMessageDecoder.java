package site.conghucai.protocol.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import site.conghucai.common.constant.CompressType;
import site.conghucai.common.constant.RpcConstant;
import site.conghucai.common.constant.SerializeType;
import site.conghucai.common.exception.DecodeException;
import site.conghucai.common.spi.ExtensionLoader;
import site.conghucai.protocol.compress.Compress;
import site.conghucai.protocol.message.AbstractRpcMessage;
import site.conghucai.protocol.message.HeartBeatMessage;
import site.conghucai.protocol.message.RpcRequestMessage;
import site.conghucai.protocol.message.RpcResponseMessage;
import site.conghucai.protocol.serializer.Serializer;

/**
 * 解码器  基于Netty的字段长度帧解码器
 */
@Slf4j
public class RpcMessageDecoder extends LengthFieldBasedFrameDecoder {
    public static final byte VERSION = RpcConstant.PROTOCOL_VERSION;
    public static final short MAGIC_NUM = RpcConstant.MAGIC_NUM;

    private ExtensionLoader<Serializer> serializerLoader;
    private ExtensionLoader<Compress> compressLoader;

    public RpcMessageDecoder() {
        this(RpcConstant.MAX_FRAME_LENGTH,12,4,0,0);
    }

    private RpcMessageDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength, int lengthAdjustment, int initialBytesToStrip) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip);
        this.serializerLoader = ExtensionLoader.getExtensionLoader(Serializer.class);
        this.compressLoader = ExtensionLoader.getExtensionLoader(Compress.class);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf byteBuf = (ByteBuf) super.decode(ctx, in);    //帧解码器的解码结果
        if(byteBuf == null) {
            return null;
        }

        try {
            short magicNum = byteBuf.readShort();
            if(magicNum != MAGIC_NUM) {
                throw new DecoderException("Unknown magic number: " + magicNum);
            }

            byte flag = byteBuf.readByte();
            byte version = (byte) ((flag & 0xF0) >>> 4);
            if(version != VERSION) {
                throw new DecodeException("Not compatible version: " + version);
            }
            byte type = (byte) (flag & 0x0F);

            //获取解压算法、反序列化算法
            byte msgProcess = byteBuf.readByte();
            byte compressCode = (byte) (msgProcess & 0x0F);
            byte serializeCode = (byte) (msgProcess >>> 4);
            String compressName = CompressType.getName(compressCode);   //查找code对应的压缩算法名称
            if(compressName == null) {
                throw new DecodeException("No such a compressor code: " + compressCode);
            }
            String serializerName = SerializeType.getName(serializeCode);   //查找code对应的序列化算法名称
            if(serializerName == null) {
                throw new DecodeException("No such a serializer code: " + serializeCode);
            }
            Compress compressor = compressLoader.getExtension(compressName);    //SPI通过名称拿到序列化器和压缩器
            Serializer serializer = serializerLoader.getExtension(serializerName);

            long sequence = byteBuf.readLong();

            int length = byteBuf.readInt();
            if(length <= 0) {
                return null;
            }

            byte[] bytes = new byte[length];
            byteBuf.readBytes(bytes);    //消息正文

            byte[] decompress = compressor.decompress(bytes);   //先进行解压
            AbstractRpcMessage message;
            if(type == 0x01) {
                //request
                message = serializer.deserialize(decompress, RpcRequestMessage.class);
            } else if(type == 0x02) {
                //response
                message = serializer.deserialize(decompress, RpcResponseMessage.class);
            } else if(type == 0x04) {
                //heartbeat
                message = serializer.deserialize(decompress, HeartBeatMessage.class);
            } else {
                throw new DecoderException("No such a message type: " + type);
            }
            // log.debug("decode res: magicNum {}, version {}, type {}, compress {}, serialize {}, sequence {}, length {}",
            //         magicNum, version, type, compressCode, serializeCode, sequence, length);
            log.debug(message.toString());

            return message;
        } catch (Exception e) {
            throw new DecodeException("Decode frame exception.", e);
        } finally {
            //Inbound的byteBuf要手动释放！
            ReferenceCountUtil.release(byteBuf);
        }
    }
}
