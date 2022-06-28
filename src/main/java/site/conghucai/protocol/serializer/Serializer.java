package site.conghucai.protocol.serializer;

import site.conghucai.common.annotation.SPI;

@SPI    //SPI标记注解  表示此接口使用Spi进行服务扩展
public interface Serializer {
    byte[] serialize(Object obj);

    <T> T deserialize(byte[] bytes, Class<T> clazz);
}
