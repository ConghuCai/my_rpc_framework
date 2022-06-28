package site.conghucai.config;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
public class RegistryConfig {

    private boolean useRegistry = false;    //是否启用注册中心

    private String registrySchema = "zookeeper";    //注册中心模式(缺省、默认为zookeeper)

    private List<String> registryAddresses = Lists.newArrayList("192.168.0.13:2181");   //注册中心地址(群)

    private Set<String> rpcServices = Sets.newHashSet("testService");


    public RegistryConfig() {
    }

    /**
     * 添加一个供注册中心发现的rpc服务名
     * @param serviceName
     * @return
     */
    public boolean addRpcService(String serviceName) {
        return rpcServices.add(serviceName);
    }

    /**
     * 批量加入。
     * @param serviceNames
     */
    public void addRpcServiceBatch(String ... serviceNames) {
        for(String service :  serviceNames) {
            addRpcService(service);
        }
    }

    /**
     * 清除一个rpc服务名
     * @param serviceName
     * @return
     */
    public boolean removeRpcService(String serviceName) {
        return rpcServices.remove(serviceName);
    }
}
