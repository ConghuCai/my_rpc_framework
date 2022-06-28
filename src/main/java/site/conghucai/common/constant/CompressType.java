package site.conghucai.common.constant;

public enum CompressType {
    None((byte)0, "None"),
    Gzip((byte)1, "Gzip"),
    Snappy((byte)2, "Snappy");

    /**
     * 4bit 0-15
     */
    private byte code;
    private String name;

    private CompressType(byte code, String name) {
        this.code = code;
        this.name = name;
    }

    public byte getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public static String getName(int code) {
        CompressType[] values = values();
        int length = values.length;

        for(int i = 0; i < length; ++i) {
            CompressType type = values[i];
            if (type.getCode() == code) {
                return type.name;
            }
        }
        return null;
    }
}
