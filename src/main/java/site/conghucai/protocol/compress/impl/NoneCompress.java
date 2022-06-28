package site.conghucai.protocol.compress.impl;

import site.conghucai.protocol.compress.Compress;

import java.io.IOException;

public class NoneCompress implements Compress {

    public NoneCompress() {
    }

    @Override
    public byte[] compress(byte[] bytes) throws IOException {
        return bytes;
    }

    @Override
    public byte[] decompress(byte[] bytes) throws IOException {
        return bytes;
    }
}
