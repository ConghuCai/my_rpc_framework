package site.conghucai.registry.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import site.conghucai.common.exception.RegistryException;
import site.conghucai.common.net.HostSocketAddress;
import site.conghucai.registry.ServicePublisher;
import site.conghucai.registry.zookeeper.ZookeeperManager;

import java.util.List;

/**
 * 基于Zookeeper的服务发布器实现。
 */
@Slf4j
public class ZookeeperServicePublisher extends ZookeeperAdapter implements ServicePublisher {

    @Override
    public void publishService(String serviceName, HostSocketAddress address) {
        try {
            CuratorFramework zkClient = ZookeeperManager.getZkClient(zkAddresses);
            String servicePath = ZookeeperManager.buildServicePath(serviceName, address);    //生成zookeeper服务路径
            ZookeeperManager.addPersistentServicePath(zkClient, servicePath);   //服务注册到zookeeper

        } catch (Exception e) {
            log.error("Publish RPC service {} failed.", serviceName, e);
            throw new RegistryException("Publish RPC service failed.", e);
        }
    }


    @Override
    public void clearService(HostSocketAddress address) {
        try {
            CuratorFramework zkClient = ZookeeperManager.getZkClient(zkAddresses);
            ZookeeperManager.clearRegistry(zkClient, address);
        } catch (Exception e) {
            log.error("Zookeeper clear provider {} failed", address, e);
            throw new RegistryException(e);
        }
    }
}
