package site.conghucai.protocol.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;
import site.conghucai.common.constant.CompressType;
import site.conghucai.common.constant.RpcConstant;
import site.conghucai.common.constant.SerializeType;
import site.conghucai.common.exception.EncodeException;
import site.conghucai.common.spi.ExtensionLoader;
import site.conghucai.protocol.compress.Compress;
import site.conghucai.protocol.message.AbstractRpcMessage;
import site.conghucai.protocol.serializer.Serializer;

@Slf4j
public class RpcMessageEncoder extends MessageToByteEncoder<AbstractRpcMessage> {

    public static final byte VERSION = RpcConstant.PROTOCOL_VERSION;
    public static final short MAGIC_NUM = RpcConstant.MAGIC_NUM;

    private SerializeType serializeType;
    private CompressType compressType;

    private Serializer serializer;
    private Compress compressor;

    public RpcMessageEncoder(SerializeType serializeType, CompressType compressType) {
        this.serializeType = serializeType;
        this.compressType = compressType;

        serializer = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension(serializeType.getName());
        compressor = ExtensionLoader.getExtensionLoader(Compress.class).getExtension(compressType.getName());
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, AbstractRpcMessage msg, ByteBuf byteBuf) throws Exception {
        //2 1 1 8 4   16长度的RPC报头

        try {
            //2 byte 魔数
            byteBuf.writeShort(MAGIC_NUM);

            //1 byte 版本号、消息类型   (5bit reserved)
            // 4bit Version  1bit reserved  3bit type
            if(msg.isRequest()) {
                byteBuf.writeByte((VERSION <<4) | 0x01);
            } else if(msg.isHeartBeat()) {
                byteBuf.writeByte((VERSION <<4) | 0x04);
            } else {
                byteBuf.writeByte((VERSION <<4) | 0x02);
            }

            //1 byte 序列、压缩方法
            //前4bit 序列方法    后4bit 压缩方法
            byteBuf.writeByte((serializeType.getCode() << 4) | (compressType.getCode() & 0x0F));

            //8 byte 序列号
            byteBuf.writeLong(msg.getSequence());

            byte[] serializeBytes = serializer.serialize(msg);
            byte[] compressBytes = compressor.compress(serializeBytes);

            //4 byte 报文长度
            byteBuf.writeInt(compressBytes.length);

            //正文
            byteBuf.writeBytes(compressBytes);
        } catch(Exception e) {
            throw new EncodeException("Encode process exception.", e);
        }
    }
}
