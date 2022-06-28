package site.conghucai.registry.impl;

import site.conghucai.common.exception.RegistryException;
import site.conghucai.registry.zookeeper.ZookeeperManager;

import java.util.List;

/**
 * Zookeeper实现发布器、发现器的适配器。提供二者公用的init方法。
 */
public abstract class ZookeeperAdapter {

    protected String zkAddresses; //注册中心addr  注册中心的集群下使用,隔开

    public void initRegistry(List<String> registryAddresses) {
        zkAddresses = String.join(",", registryAddresses);
        try {
            ZookeeperManager.getZkClient(zkAddresses);
        } catch (RegistryException e) {
            throw new RegistryException("Zookeeper init failed.", e);
        }
    }

}
