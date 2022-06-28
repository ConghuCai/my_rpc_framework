package site.conghucai.protocol.compress;

import site.conghucai.common.annotation.SPI;

import java.io.IOException;

@SPI
public interface Compress {
    byte[] compress(byte[] bytes) throws IOException;

    byte[] decompress(byte[] bytes) throws IOException;
}
