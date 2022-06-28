package site.conghucai.registry.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import site.conghucai.common.exception.RegistryException;
import site.conghucai.common.net.HostSocketAddress;
import site.conghucai.registry.ServiceDiscover;
import site.conghucai.registry.zookeeper.ZookeeperManager;

import java.util.List;

@Slf4j
public class ZookeeperServiceDiscover extends ZookeeperAdapter implements ServiceDiscover {

    @Override
    public List<HostSocketAddress> discoverService(String serviceName) {
        try {
            CuratorFramework zkClient = ZookeeperManager.getZkClient(zkAddresses);
            List<HostSocketAddress> providers = ZookeeperManager.getAllProviderNode(zkClient, serviceName); //查询提供者列表
            return providers;
        } catch (Exception e) {
            log.error("Zookeeper discover remote service failed.", e);
            throw new RegistryException(e);
        }
    }
}
