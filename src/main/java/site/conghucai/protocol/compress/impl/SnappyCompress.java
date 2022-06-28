package site.conghucai.protocol.compress.impl;

import org.xerial.snappy.Snappy;
import site.conghucai.protocol.compress.Compress;

import java.io.IOException;

public class SnappyCompress implements Compress {
    @Override
    public byte[] compress(byte[] bytes) throws IOException {
        return Snappy.compress(bytes);
    }

    @Override
    public byte[] decompress(byte[] bytes) throws IOException {
        return Snappy.uncompress(bytes);
    }
}
