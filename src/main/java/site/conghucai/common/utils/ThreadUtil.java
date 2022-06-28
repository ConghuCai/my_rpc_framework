package site.conghucai.common.utils;

import site.conghucai.common.thread.RpcDaemonThreadFactory;

import java.util.concurrent.*;

/**
 * 线程工具类
 */
public class ThreadUtil {

    private ThreadUtil() {
    }

    /**
     * 判断线程池是否已关闭。
     * 传入的线程池对象务必是ExecutorService系的线程池  否则一定返回false
     * @param executor 线程池
     * @return true-已关闭
     */
    public static boolean isTerminated(Executor executor) {
        if(executor instanceof ExecutorService) {
            return ((ExecutorService)executor).isTerminated();
        }

        return false;
    }


    /**
     * 关闭线程池
     * @param executor 线程池
     * @param timeout 等待池中尚未执行完任务的线程的时间
     */
    public static void shutdownGracefully(Executor executor, int timeout) {
        if (!(executor instanceof ExecutorService) || isTerminated(executor)) {
            return;
        }
        final ExecutorService service = (ExecutorService) executor;
        try {
            service.shutdown(); // shutdown后不允许再提交新的任务
        } catch (SecurityException | NullPointerException ex2) {
            return;
        }
        try {
            // 指定时间内等待线程池处理完剩下的任务, 未执行完也立即关闭
            if (!service.awaitTermination(timeout, TimeUnit.MILLISECONDS)) {
                service.shutdownNow();
            }
        } catch (InterruptedException ex) {
            service.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 获取一个参数定制的、守护线程的、线程池。
     * 由于内部使用守护线程工厂生成线程，因此记得调用shutdown手动关闭。
     *
     * @param threadSize 线程数量  此方法中最低和最高线程数保持相等，因此线程数量是恒定的
     * @param blockQueueSize 阻塞队列大小，当设置值<1时，会不限制队列大小
     * @param threadFactoryPrefix 线程工厂名称。内部使用守护线程的工厂，在此指定线程的统一的前缀。
     * @param rejectedExecutionHandler 线程池满or已经shutdown时 会拒接新任务  并执行此处理器
     * @return
     */
    public static ThreadPoolExecutor getCustomThreadPoolExecutor(int threadSize, int blockQueueSize, String threadFactoryPrefix, RejectedExecutionHandler rejectedExecutionHandler) {
        return new ThreadPoolExecutor(
                threadSize,  //池核心线程数
                threadSize, //池 最大线程限制  和核心线程数相同。因此不存在多出来的线程。
                0, TimeUnit.MILLISECONDS,   //因为不存在多余的线程，所以不必设置超时时间
                blockQueueSize < 1 ? new LinkedBlockingQueue<>() : new LinkedBlockingQueue<>(blockQueueSize),  //如果blockQueueSize<=0  则不设置阻塞队列大小
                new RpcDaemonThreadFactory(threadFactoryPrefix),  //守护线程的工厂，线程池在此工厂中创建线程
                rejectedExecutionHandler    //任务拒绝处理器  线程池满、已经shutdown时 会拒接新任务  并执行此处理器
            );
    }

}
