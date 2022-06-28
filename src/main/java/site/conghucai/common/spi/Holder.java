package site.conghucai.common.spi;

public class Holder<T> {
    private volatile T value;

    public Holder() {
    }

    public T get() {
        return this.value;
    }

    public void set(T value) {
        this.value = value;
    }
}
