package site.conghucai.registry;

import site.conghucai.common.annotation.SPI;
import site.conghucai.common.net.HostSocketAddress;

import java.util.List;

/**
 * rpc服务发布器，用于将可用的远程服务和提供者发布到注册中心中，供消费者进行服务调用。
 */
@SPI
public interface ServicePublisher {

    /**
     * 初始化注册中心
     * @param registryAddresses 注册中心地址
     */
    void initRegistry(List<String> registryAddresses);

    /**
     * 将服务发布到注册中心。
     * @param serviceName 服务名称
     * @param address 服务提供者地址
     */
    void publishService(String serviceName, HostSocketAddress address);

    /**
     * 将服务提供者从注册中心中清除。
     * @param address 提供者地址
     */
    void clearService(HostSocketAddress address);

}
