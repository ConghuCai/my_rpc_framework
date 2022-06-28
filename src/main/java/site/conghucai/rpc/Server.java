package site.conghucai.rpc;

import site.conghucai.config.RpcServerConfig;

/**
 * 服务器接口，定义了服务器最一般的行为
 */
public interface Server {
    /**
     * 设置RpcServerConfig
     * @param config
     * @return
     */
    Server config(RpcServerConfig config);

    /**
     * 绑定端口
     * @param port
     * @return
     */
    Server bind(Integer port);

    /**
     * 绑定带有@RpcScan注解的类的字节码，用于Rpc服务包的扫描。
     * @param clazz 带有@RpcScan注解的类的字节码
     * @return
     */
    Server source(Class<?> clazz);

    /**
     * 开启Rpc服务器
     * @return
     */
    Server start();

    /**
     * 关闭Rpc服务器
     */
    void close();
}
