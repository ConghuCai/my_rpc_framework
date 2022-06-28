package site.conghucai.common.constant;

public enum SerializeType {
    Jdk((byte)0, "Jdk"),
    Json((byte)1, "Json"),
    Protostuff((byte)2, "Protostuff");

    /**
     * 4bit 0-15
     */
    private byte code;
    private String name;

    SerializeType(byte code, String name) {
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
        SerializeType[] values = values();
        int length = values.length;

        for(int i = 0; i < length; ++i) {
            SerializeType type = values[i];
            if (type.getCode() == code) {
                return type.name;
            }
        }
        return null;
    }
}
