package site.conghucai.common.utils;

import site.conghucai.rpc.demo.ssl.SSLServer;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

public class IOStreamUtil {
    private static final String CLASSPATH = "classpath:";

    private IOStreamUtil() {}

    public static InputStream getInputStreamFromPath(String path) throws FileNotFoundException {
        if(path.startsWith(CLASSPATH)) {
            path = path.replaceFirst(CLASSPATH, "");
            return SSLServer.class.getClassLoader().getResourceAsStream(path);
        }

        return new FileInputStream(path);
    }
}
