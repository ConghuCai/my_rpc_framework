package site.conghucai.protocol.serializer.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.serializer.SerializerFeature;
import lombok.extern.slf4j.Slf4j;
import site.conghucai.common.exception.SerializerException;
import site.conghucai.protocol.serializer.Serializer;

import java.nio.charset.StandardCharsets;

@Slf4j
public class JsonSerializer implements Serializer {

    public JsonSerializer() {
    }

    @Override
    public byte[] serialize(Object obj) {
        try{
            String json = JSON.toJSONString(obj, new SerializerFeature[] {SerializerFeature.WriteClassName});
            return json.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("json serialize exception", e);
            throw new SerializerException("json serialize exception", e);
        }
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        try {
            String json = new String(bytes, StandardCharsets.UTF_8);
            T obj = JSON.parseObject(json, clazz, new Feature[]{Feature.SupportAutoType});
            return obj;
        } catch (Exception e) {
            log.error("json deserialize exception", e);
            throw new SerializerException("json deserialize exception", e);
        }
    }
}
