package site.conghucai.registry.zookeeper;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import site.conghucai.common.exception.RegistryException;
import site.conghucai.common.net.HostSocketAddress;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Zookeeper管理器。用于高效处理对于ZookeeperAPI的操作。
 * 将rpc服务名和对应的服务提供者节点地址按照zookeeper的路径式规则存到zookeeper中。
 * 如： /root_name/service_name/[provider1, provider2, ...]
 *
 * 基于curator.framework实现对zookeeper的操作。
 * 内部设有内存级缓存，加速对于Zookeeper的处理。
 */
@Slf4j
public class ZookeeperManager {
    private static volatile CuratorFramework zkClient;   //zk客户端

    //基本参数
    private static final int BASE_SLEEP_TIME = 1000;    //重试连接策略参数：重连等待基础时间
    private static final int MAX_RETRIES = 3;   //重试连接策略参数：最多重连次数
    private static final int TIMEOUT = 10;  //连接注册中心的超时时间(s)
    public static final String ZK_REGISTER_ROOT_PATH = "/myrpc";   //zookeeper使用的服务路径的根路径
    public static final String SEPARATOR = "/"; //路径分隔符

    //内存级缓存
    private static final Set<String> REGISTERED_PATH_SET = ConcurrentHashMap.newKeySet();   //缓存已经注册的RPC服务路径
    private static final Map<String, List<HostSocketAddress>> SERVICE_PROVIDERS_MAP = new ConcurrentHashMap<>();    //缓存RPC服务和对应的提供者集群

    //线程安全锁
    private static final Object lock = new Object();

    private ZookeeperManager() {
    }


    /**
     * 连接远程注册中心、获取注册中心客户端。
     * @param zkAddresses 注册中心地址。多个地址以","隔开。
     * @return 注册中心的客户端对象。
     */
    public static CuratorFramework getZkClient(String zkAddresses) {
        if(zkClient == null || zkClient.getState() != CuratorFrameworkState.STARTED) {
            synchronized (lock) {
                if(zkClient == null || zkClient.getState() != CuratorFrameworkState.STARTED) {
                    //单例下  进行注册中心的连接
                    //1.初始化连接失败的重试策略：最多重试MAX_RETRIES次，连接失败就会等待一定的时间，再重新发起连接。
                    //ExponentialBackoffRetry策略下的时间间隔 = baseSleepTimeMs * Math.max(1, random.nextInt(1 << (retryCount + 1)))
                    RetryPolicy retryPolicy = new ExponentialBackoffRetry(BASE_SLEEP_TIME, MAX_RETRIES);

                    //2.连接注册中心  获取注册中心的客户端对象
                    zkClient = CuratorFrameworkFactory.builder()
                            .connectString(zkAddresses) //如果有多个连接中心地址  使用,隔开。
                            .retryPolicy(retryPolicy).build();
                    zkClient.start();

                    try {
                        //3.阻塞式等待  设置超时时间为TIMEOUT
                        if(!zkClient.blockUntilConnected(TIMEOUT, TimeUnit.SECONDS)) {
                            //超时  连接注册中心失败
                            throw new RegistryException("[Registry] connect to Zookeeper timeout. registry init failed.");
                        }
                    } catch (InterruptedException e) {
                        log.error("[Registry] registry init failed.", e);
                        throw new RegistryException("[Registry] registry init failed.", e);
                    }
                }
            }
        }

        return zkClient;
    }

    /**
     * 将远程服务注册到注册中心。
     * 注册方式将采用持久化方法。无失效风险
     * @param zkClient zk客户端
     * @param servicePath zookeeper服务路径  如/rpc/serviceDemo/192.168.0.1:8081
     */
    public static void addPersistentServicePath(CuratorFramework zkClient, String servicePath) {
        try {
            if(REGISTERED_PATH_SET.contains(servicePath) || zkClient.checkExists().forPath(servicePath) != null) {
                //节点已经注册到缓存\注册中心
                log.info("rpc service path {} had already registered.", servicePath);

            } else {
                //节点注册到注册中心  注册使用持久化节点模式
                zkClient.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(servicePath);
                log.info("rpc service path {} has registered successfully!", servicePath);
            }
            //更新缓存
            REGISTERED_PATH_SET.add(servicePath);

        } catch (Exception e) {
            log.error("create persistent service path {} failed.", e);
        }
    }

    /**
     * 将提供者节点node的所有服务注册路径删除。
     * @param zkClient zk客户端
     * @param node 提供者地址
     */
    public static void clearRegistry(CuratorFramework zkClient, HostSocketAddress node) {
        //遍历注册中心path中带有node地址的路径
        REGISTERED_PATH_SET.stream().parallel().forEach(path -> {
            try {
                if(path.endsWith(node.getSocketStr())) {
                    zkClient.delete().forPath(path);    //zookeeper中删去
                    REGISTERED_PATH_SET.remove(path);   //缓存中删去
                }
            }catch (Exception e) {
                log.error("clear registry for node {} failed.", node, e);
            }
        });

        log.info("All registered path of node {} are cleared.", node);
    }


    /**
     * 将服务名转换为zookeeper使用的服务路径
     * ex: /myrpc/testService001/192.168.0.1:8080
     * @param serviceName 服务名
     * @param address 提供者地址
     * @return
     */
    public static String buildServicePath(String serviceName, HostSocketAddress address) {
        //ex: /MYRPC/testService001/192.168.0.1:8080
        return ZK_REGISTER_ROOT_PATH + SEPARATOR + serviceName + SEPARATOR + address.getSocketStr();
    }

    /**
     * 根据服务名称，查询zookeeper获取所有提供该服务实现的 服务提供者节点地址列表。
     *
     * 监听器并非是实时的  因此并不保证从本方法中获取的节点对象都一定可用。
     * @param zkClient
     * @param serviceName 服务名称
     * @return
     */
    public static List<HostSocketAddress> getAllProviderNode(CuratorFramework zkClient, String serviceName) {
        //先查缓存有没有
        if(SERVICE_PROVIDERS_MAP.containsKey(serviceName)) {
            return SERVICE_PROVIDERS_MAP.get(serviceName);
        }

        List<HostSocketAddress> nodes;
        String searchPath = ZK_REGISTER_ROOT_PATH + SEPARATOR + serviceName;

        try {
            List<String> result = zkClient.getChildren().forPath(searchPath);
            nodes = result.stream().map(HostSocketAddress::get).collect(Collectors.toList());   //将查询到的字符串转为节点对象

            SERVICE_PROVIDERS_MAP.put(serviceName, nodes);  //存入缓存

            registerWatcher(serviceName, zkClient); //监听path路径下的节点变化
        } catch (Exception e) {
            log.error("get service [{}]'s provider list failed.", serviceName, e);
            throw new RegistryException(e);
        }

        if(nodes == null || nodes.isEmpty()) {
            throw new RegistryException("rpc service " + serviceName + " does not have any provider registered in zookeeper! rpc failed!");
        }

        return nodes;
    }

    /**
     * 注册一个path的监听器，用于监听path路径下的节点变化
     * @param serviceName 监听的服务名
     * @param zkClient zk客户端
     */
    private static void registerWatcher(String serviceName, CuratorFramework zkClient) throws Exception {
        //根据服务名生成路径
        String path = ZK_REGISTER_ROOT_PATH + SEPARATOR + serviceName;

        //zk路径下子节点的本地缓存
        PathChildrenCache pathChildrenCache = new PathChildrenCache(zkClient, path, true);

        //初始化一个监听器  注册路径子节点发生变化时的操作
        PathChildrenCacheListener listener = ((curatorFramework, pathChildrenCacheEvent) -> {
            //发生变化  则重新获取子节点的list  并更新map缓存
            List<String> result = curatorFramework.getChildren().forPath(path);
            List<HostSocketAddress> nodes = result.stream().map(HostSocketAddress::get).collect(Collectors.toList());
            SERVICE_PROVIDERS_MAP.put(serviceName, nodes);  //更新缓存
        });

        //监听器注册
        pathChildrenCache.getListenable().addListener(listener);
        pathChildrenCache.start(PathChildrenCache.StartMode.BUILD_INITIAL_CACHE);
    }

}
