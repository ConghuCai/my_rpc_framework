package site.conghucai.rpc.demo;

import site.conghucai.common.annotation.RpcScan;
import site.conghucai.config.RpcServerConfig;
import site.conghucai.rpc.Server;
import site.conghucai.rpc.server.RpcServer;

@RpcScan({"site.conghucai.rpc.demo.service"})
public class RpcServerDemo {
    public static void main(String[] args) {
        Server server = new RpcServer();
        RpcServerConfig config = new RpcServerConfig();
        config.setUseRegistry(true);
        config.addRpcService("registryDemo1");

        server.config(config)
                .source(RpcServerDemo.class)
                .bind(10001)
                .start();

        // server.close();
    }
}
