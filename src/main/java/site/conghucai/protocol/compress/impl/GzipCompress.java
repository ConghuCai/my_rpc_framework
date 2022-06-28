package site.conghucai.protocol.compress.impl;

import site.conghucai.protocol.compress.Compress;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GzipCompress implements Compress {

    public GzipCompress() {
    }

    public byte[] compress(byte[] bytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(out);

        try {
            gzip.write(bytes);
        } catch (Throwable var7) {
            try {
                gzip.close();
            } catch (Throwable var6) {
                var7.addSuppressed(var6);
            }

            throw var7;
        }

        gzip.close();
        return out.toByteArray();
    }

    public byte[] decompress(byte[] bytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        byte[] var7;
        try {
            ByteArrayInputStream in = new ByteArrayInputStream(bytes);

            try {
                GZIPInputStream ungzip = new GZIPInputStream(in);
                byte[] buffer = new byte[2048];

                while(true) {
                    int n;
                    if ((n = ungzip.read(buffer)) < 0) {
                        var7 = out.toByteArray();
                        break;
                    }

                    out.write(buffer, 0, n);
                }
            } catch (Throwable var10) {
                try {
                    in.close();
                } catch (Throwable var9) {
                    var10.addSuppressed(var9);
                }

                throw var10;
            }

            in.close();
        } catch (Throwable var11) {
            try {
                out.close();
            } catch (Throwable var8) {
                var11.addSuppressed(var8);
            }

            throw var11;
        }

        out.close();
        return var7;
    }
}
