package site.conghucai.pool;

import site.conghucai.common.annotation.SPI;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 自定义的业务线程池
 * 如果你需要实现自定义的线程池，请在MATA-INF/spi目录下的site.conghucai.pool.BusinessThreadPool文件中配置以下内容：
 * CUSTOM=你的实现类的全限定名称。
 * 如：CUSTOM=site.conghucai.pool.impl.BusinessThreadPoolImpl
 */
@SPI
public interface BusinessThreadPool {

    /**
     * 获取自定义的业务线程池
     * @return
     */
    ThreadPoolExecutor getThreadPool();

}
