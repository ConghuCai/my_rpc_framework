package site.conghucai.registry;

import site.conghucai.common.annotation.SPI;
import site.conghucai.common.net.HostSocketAddress;

import java.util.List;

/**
 * 服务发现
 * 根据所需要rpc的服务名，得到提供该服务的rpc服务器节点(群)。
 */
@SPI
public interface ServiceDiscover {

    /**
     * 初始化注册中心
     * @param registryAddresses 注册中心地址列表
     */
    void initRegistry(List<String> registryAddresses);


    /**
     * 根据服务名获取提供该服务的rpc服务器节点群。
     * @param serviceName
     * @return
     */
    List<HostSocketAddress> discoverService(String serviceName);

}
