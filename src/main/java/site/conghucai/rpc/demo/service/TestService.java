package site.conghucai.rpc.demo.service;

import site.conghucai.common.annotation.RpcMapping;
import site.conghucai.common.annotation.RpcService;

@RpcService
public class TestService {

    @RpcMapping("demo1")
    public void funcDemoVoid() {
        System.out.println("funcDemoVoid...");
    }

    @RpcMapping("demo2")
    public void funcDemoInt(int a, String b) {
        System.out.println("funcDemoInt..." + a + "..." + b);
    }

    public void innerFunc() {
        System.out.println("...");
    }


    @RpcMapping("registryDemo1")
    public String funcDemoString(String name) {
        return "Hello, " + name;
    }
}
