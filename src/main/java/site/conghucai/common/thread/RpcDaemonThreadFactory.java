package site.conghucai.common.thread;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RPC项目的自定义守护线程的Factory，可以用此来生成单例的守护线程工厂并提供给执行器
 * 应用于 节点状态检测 等需要设为守护线程的任务
 */
public class RpcDaemonThreadFactory implements ThreadFactory {

    private AtomicInteger threadCounter = new AtomicInteger(1);
    private String prefix;  //线程名前缀

    /**
     * defaultFactory  单例的线程工厂
     */
    private static volatile RpcDaemonThreadFactory singleFactory;

    public RpcDaemonThreadFactory(String prefix) {
        this.prefix = prefix + "-thread-";
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, prefix + threadCounter.getAndIncrement());
        t.setDaemon(true);  //设为守护线程
        return t;
    }


    /**
     * 获取一个守护线程工厂的单例
     * @return
     */
    public static RpcDaemonThreadFactory getSingleFactory() {
        if(singleFactory == null) {
            synchronized (RpcDaemonThreadFactory.class) {
                if(singleFactory == null) {
                    singleFactory = new RpcDaemonThreadFactory("rpc_daemon");
                }
            }
        }
        return singleFactory;
    }

}
