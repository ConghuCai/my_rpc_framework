package site.conghucai.protocol.serializer.impl;

import lombok.extern.slf4j.Slf4j;
import site.conghucai.common.exception.SerializerException;
import site.conghucai.protocol.serializer.Serializer;

import java.io.*;

@Slf4j
public class JdkSerializer implements Serializer {

    public JdkSerializer() {
    }

    @Override
    public byte[] serialize(Object obj) {

        try(
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos)
                ){
            oos.writeObject(obj);
            byte[] bytes = bos.toByteArray();

            return bytes;

        } catch (Exception e) {
            log.error("jdk serialize exception", e);
            throw new SerializerException("jdk serialize exception", e);
        }
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))){
            return (T) ois.readObject();
        } catch (Exception e) {
            log.error("jdk deserialize exception", e);
            throw new SerializerException("jdk deserialize exception", e);
        }
    }
}
