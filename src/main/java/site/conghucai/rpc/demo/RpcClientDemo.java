package site.conghucai.rpc.demo;
import lombok.extern.slf4j.Slf4j;
import site.conghucai.common.net.HostSocketAddress;
import site.conghucai.config.RpcClientConfig;
import site.conghucai.rpc.Client;
import site.conghucai.rpc.client.RpcClient;

@Slf4j
public class RpcClientDemo {
    public static void main(String[] args) throws Exception {
        Client client = new RpcClient();
        RpcClientConfig config = new RpcClientConfig();
        config.setUseRegistry(true);

        client.config(config).start();

        // client.invoke("registryDemo1", void.class, new Object[] {1,"hello world!"}, 3,
        //         HostSocketAddress.get("127.0.0.1:10001"));

        String res = client.invokeWithRegistry("registryDemo1", String.class, new Object[]{"Jack"}, 3);
        System.out.println(res);


        Thread.sleep(60000);

        client.stop();

    }
}
