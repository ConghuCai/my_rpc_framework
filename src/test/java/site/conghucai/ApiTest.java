package site.conghucai;

import com.google.common.collect.Lists;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.junit.Test;
import site.conghucai.common.cache.ExpireCache;
import site.conghucai.common.cache.impl.ScheduleCleanExpireCache;
import site.conghucai.common.constant.CompressType;
import site.conghucai.common.constant.NodeSelectorRule;
import site.conghucai.common.constant.SerializeType;
import site.conghucai.common.id.IdGenerator;
import site.conghucai.common.net.HostSocketAddress;
import site.conghucai.common.spi.ExtensionLoader;
import site.conghucai.config.RpcClientConfig;
import site.conghucai.config.RpcServerConfig;
import site.conghucai.hosts.balance.NodeSelector;
import site.conghucai.hosts.balance.impl.RoundNodeSelector;
import site.conghucai.pool.BusinessThreadPool;
import site.conghucai.protocol.codec.RpcMessageDecoder;
import site.conghucai.protocol.codec.RpcMessageEncoder;
import site.conghucai.protocol.compress.Compress;
import site.conghucai.protocol.message.AbstractRpcMessage;
import site.conghucai.protocol.message.HeartBeatMessage;
import site.conghucai.protocol.message.RpcRequestMessage;
import site.conghucai.protocol.message.RpcResponseMessage;
import site.conghucai.protocol.serializer.Serializer;
import site.conghucai.reflect.ServiceMethodInvoker;
import site.conghucai.reflect.ServiceRegister;
import site.conghucai.reflect.ServiceScanner;
import site.conghucai.registry.ServiceDiscover;
import site.conghucai.registry.ServicePublisher;
import site.conghucai.registry.impl.ZookeeperServiceDiscover;
import site.conghucai.registry.impl.ZookeeperServicePublisher;
import site.conghucai.registry.zookeeper.ZookeeperManager;
import site.conghucai.rpc.Client;
import site.conghucai.rpc.Server;
import site.conghucai.rpc.client.RpcClient;
import site.conghucai.rpc.demo.RpcServerDemo;
import site.conghucai.rpc.demo.service.TestService;
import site.conghucai.rpc.server.RpcServer;
import site.conghucai.hosts.connect.Connection;
import site.conghucai.hosts.connect.impl.DefaultConnection;

import java.io.*;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@Slf4j
public class ApiTest {

    Class<?> clazz = Void.class;

    @Test
    public void testDemo() {
        Set<String> testSet = ConcurrentHashMap.newKeySet();
        testSet.add("a/192");
        testSet.add("b/192");
        testSet.add("c/192");
        testSet.add("d/192");
        testSet.add("e/192");
        testSet.add("a/193");
        testSet.add("a/194");
        testSet.forEach( s -> {
            System.out.println(s);
        });

        System.out.println("del...");

        testSet.stream().parallel().forEach(s -> {
            if(s.endsWith("192")) {
                System.out.println("del: " + s);
                testSet.remove(s);
            }
        });
        testSet.forEach( s -> {
            System.out.println(s);
        });

    }

    @Test
    public void testRegex() {
        String num = "(25[0-5]|2[0-4]\\d|[0-1]\\d{2}|[1-9]?\\d)";
        String port = "()";
        String regex = "^" + num + "\\." + num + "\\." + num + "\\." + num ;
        Pattern p = Pattern.compile(regex);
        boolean matches = p.matcher("255.13.0.1").matches();
        System.out.println(matches);
    }

    @Test
    public void testLoadDirectory() throws IOException, ClassNotFoundException {
        String fileName = "META-INF/spi/" + Serializer.class.getName();
        Map<String, Class<?>> extensionClasses = new HashMap<>();

        ClassLoader classLoader = ExtensionLoader.class.getClassLoader();
        Enumeration<URL> urls = classLoader.getResources(fileName);

        if(urls != null) {
            while(urls.hasMoreElements()) {
                URL url = urls.nextElement();
                System.out.println(url);

                BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8));
                String line;

                while((line = reader.readLine()) != null) {
                    //
                    int ci = line.indexOf(35);
                    if (ci >= 0) {
                        line = line.substring(0, ci);
                    }
                    line = line.trim();

                    //解析一行
                    if (line.length() > 0) {
                        int ei = line.indexOf(61);
                        String name = line.substring(0, ei).trim();
                        String clazzName = line.substring(ei + 1).trim();
                        if (name.length() > 0 && clazzName.length() > 0) {
                            Class<?> clazz = classLoader.loadClass(clazzName);  //加载实现类的字节码为class对象
                            log.debug("SPI load Class [{}]", clazz.getCanonicalName());
                            extensionClasses.put(name, clazz);
                        } else {
                            log.error("SPI load Class failed, name {}", name);
                        }
                    }
                }
            }
        }

    }

    @Test
    public void testExtensionLoaderApi() {
        Serializer jdk = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension("Jdk");
        Serializer json = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension("Json");
        System.out.println(jdk+ ", " + json);

        ExtensionLoader.getExtensionLoader(Serializer.class).getExtension("Xxx");

        Compress none1 = ExtensionLoader.getExtensionLoader(Compress.class).getExtension("None");
        Compress none2 = ExtensionLoader.getExtensionLoader(Compress.class).getExtension("None");
        Compress gzip = ExtensionLoader.getExtensionLoader(Compress.class).getExtension("Gzip");
        System.out.println(none1 == none2);
        System.out.println(none1 + " " + gzip);
    }

    @Test
    public void testMessageApi() {
    }

    @Test
    public void testMessageEncoder() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new LoggingHandler(),
                new RpcMessageEncoder(SerializeType.Jdk, CompressType.None)
        );

        RpcRequestMessage req = new RpcRequestMessage(1L);
        RpcResponseMessage resp = new RpcResponseMessage(8122123L);
        HeartBeatMessage heart = new HeartBeatMessage(13183L);

        channel.writeOutbound(req);
        channel.writeOutbound(resp);
        channel.writeOutbound(heart);
    }

    @Test
    public void testMessageDecoder() throws IOException {
        RpcRequestMessage req = new RpcRequestMessage(1L);
        RpcResponseMessage resp = new RpcResponseMessage(8122123L);
        HeartBeatMessage heart = new HeartBeatMessage(13183L);

        EmbeddedChannel channel = new EmbeddedChannel(
                new LoggingHandler(),
                new RpcMessageDecoder(),
                new RpcMessageEncoder(SerializeType.Jdk, CompressType.None)
        );

        AbstractRpcMessage msg = req;
        short MAGIC_NUM = 6552;
        byte VERSION = 1;
        SerializeType serializeType = SerializeType.Json;
        CompressType compressType = CompressType.Gzip;


        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer();
        //2 byte 魔数
        byteBuf.writeShort(MAGIC_NUM);
        //1 byte 版本号、消息类型   (5bit reserved)
        // 4bit Version  1bit reserved  3bit type
        if(msg.isRequest()) {
            byteBuf.writeByte((VERSION <<4) | 0x01);
        } else if(msg.isHeartBeat()) {
            byteBuf.writeByte((VERSION <<4) | 0x04);
        } else {
            byteBuf.writeByte((VERSION <<4) | 0x02);
        }
        //1 byte 序列、压缩方法
        //前4bit 序列方法    后4bit 压缩方法
        byteBuf.writeByte((serializeType.getCode() << 4) | (compressType.getCode() & 0x0F));

        //8 byte 序列号
        byteBuf.writeLong(msg.getSequence());

        byte[] serializeBytes = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension(serializeType.getName()).serialize(msg);
        byte[] compressBytes = ExtensionLoader.getExtensionLoader(Compress.class).getExtension(compressType.getName()).compress(serializeBytes);
        //4 byte 报文长度
        byteBuf.writeInt(compressBytes.length);
        //正文
        byteBuf.writeBytes(compressBytes);

        channel.writeInbound(byteBuf);
    }

    @Test
    public void testServerApi() {
        Server server = new RpcServer();
        server.config(new RpcServerConfig())
                .bind(8080)
                .start();
    }

    @Test
    public void testConnectApi() {
        Connection connection = new DefaultConnection(1L, new EmbeddedChannel());
        Connection connection0 = new DefaultConnection(1L, new EmbeddedChannel());
        System.out.println(connection.hashCode());
        System.out.println(connection.equals(connection0));
    }

    @Test
    public void testNetArrApi() {
        HostSocketAddress a = HostSocketAddress.get("111.111.111.111:8080");
        HostSocketAddress b = HostSocketAddress.get(new InetSocketAddress("111.111.111.111", 8080));

        HashSet<HostSocketAddress> set = new HashSet<>();
        set.add(a);
        set.add(b)
                ;
        System.out.println(set.size());
    }

    @Test
    public void testIDGenerator() {
        NodeSelector selector1 = ExtensionLoader.getExtensionLoader(NodeSelector.class).getExtension(NodeSelectorRule.RANDOM.name());
        NodeSelector selector2 = ExtensionLoader.getExtensionLoader(NodeSelector.class).getExtension(NodeSelectorRule.ROUND.name());
        System.out.println(selector1+" " + selector2);
        for (int i = 0; i < 10; i++) {
            System.out.println(IdGenerator.getId());
        }
    }

    @Test
    public void testBalancerApi() {
        List<HostSocketAddress> nodes = new ArrayList<>();
        nodes.add(HostSocketAddress.get("192.1.0.1:8080"));
        nodes.add(HostSocketAddress.get("192.1.0.1:8081"));
        nodes.add(HostSocketAddress.get("192.1.0.1:8082"));
        nodes.add(HostSocketAddress.get("192.1.0.1:8083"));
        nodes.add(HostSocketAddress.get("192.1.0.1:8084"));
        nodes.add(HostSocketAddress.get("192.1.0.1:8085"));
        nodes.add(HostSocketAddress.get("192.1.0.1:8086"));

        NodeSelector balancer = new RoundNodeSelector();

        Runnable r = () -> {
            for (int i = 0; i < 100; i++) {
                System.out.println(Thread.currentThread().getName() + ": " +  balancer.selectNode(nodes, null));
            }
        };

        new Thread(r, "1").start();
        // new Thread(r, "2").start();
        // new Thread(r, "3").start();
        // new Thread(r, "4").start();
        // new Thread(r, "5").start();

        while(true);
    }

    @Test
    public void testExpireCacheApi() throws InterruptedException {
        ExpireCache<Integer, String> cache = new ScheduleCleanExpireCache<>(5, 20, TimeUnit.SECONDS, 10);
        AtomicInteger id = new AtomicInteger(1);

        Runnable r = () -> {
            for (int i = 0; i < 8; i++) {
                cache.put(id.getAndIncrement(), Thread.currentThread().getName());

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };

        Thread.sleep(20000);

        new Thread(r, "t1").start();
        Thread.sleep(2000);
        new Thread(r, "t2").start();
        Thread.sleep(2000);
        new Thread(r, "t3").start();
        Thread.sleep(2000);
        new Thread(r, "t4").start();
        Thread.sleep(2000);

        cache.close();

        while(true);
    }


    @Test
    public void testExecutorGroup() {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new DefaultEventExecutorGroup(1), new LoggingHandler());
        channel.writeInbound("123".getBytes());

    }

    @Test
    public void testClientApi() {
        Client client = new RpcClient();
        client.config(new RpcClientConfig())
                .start();

        client.stop();
    }

    @Test
    public void testServiceMethodInvoker() throws NoSuchMethodException {
        TestService instance = new TestService();
        Method method1 = instance.getClass().getMethod("funcDemoVoid", null);
        Method method2 = instance.getClass().getMethod("funcDemoInt", new Class[] {int.class, String.class});
        new ServiceMethodInvoker(instance, method1).invoke(null);
        new ServiceMethodInvoker(instance, method2).invoke(new Object[] {1, "b"});
    }

    @Test
    public void testServiceScanner() {
        ServiceScanner scanner = new ServiceScanner(RpcServerDemo.class);
        scanner.serviceScan();

        ServiceMethodInvoker invoker1 = ServiceRegister.getMethodInvoker("demo1");
        ServiceMethodInvoker invoker2 = ServiceRegister.getMethodInvoker("demo2");
        // ServiceMethodInvoker invoker3 = ServiceRegister.getMethodInvoker("demo3");

        System.out.println(invoker1+ ", "+ invoker2 + ", ");

        invoker1.invoke(new Object[] {});
        invoker1.invoke(null);
        invoker2.invoke(new Object[] {1, "abc"});
    }

    @Test
    public void testZookeeperClientConn() {
        int BASE_SLEEP_TIME = 1000;
        int MAX_RETRIES = 3;
        String zkAddr = "192.168.0.13:2181,";
        int timeout = 10;

        //初始化连接失败的重试策略：最多重试MAX_RETRIES次，连接失败就会等待一定的时间，再重新发起连接。
        //ExponentialBackoffRetry策略下的时间间隔 = baseSleepTimeMs * Math.max(1, random.nextInt(1 << (retryCount + 1)))
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(BASE_SLEEP_TIME, MAX_RETRIES);

        //连接注册中心  获取注册中心的客户端对象
        CuratorFramework zkClient = CuratorFrameworkFactory.builder().connectString(zkAddr).retryPolicy(retryPolicy).build();
        zkClient.start();

        System.out.println(zkClient.getState());

        try {
            //阻塞式等待  设置超时时间为timeout
            if(!zkClient.blockUntilConnected(timeout, TimeUnit.SECONDS)) {
                System.out.println("timeout...");
            } else {
                System.out.println("success...");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testZookeeperServicePublisherApi() throws Exception {
        // ZookeeperManager.getZkClient("192.168.0.13:2181").delete().forPath("/de /192.168.0.1:8080");

        ZookeeperServicePublisher publisher = new ZookeeperServicePublisher();
        HostSocketAddress node1 = HostSocketAddress.get("192.168.0.1:8080");
        HostSocketAddress node2 = HostSocketAddress.get("192.168.0.1:8081");

        publisher.initRegistry(Lists.newArrayList("192.168.0.13:2181"));

        publisher.publishService("testService1", node1);
        publisher.publishService("testService1", node2);
        publisher.publishService("testService2", node2);
        publisher.clearService(node1);
        publisher.clearService(node2);
    }


    @Test
    public void testZookeeperApis() {
        HostSocketAddress node1 = HostSocketAddress.get("192.168.0.1:8080");
        HostSocketAddress node2 = HostSocketAddress.get("192.168.0.1:8081");
        ArrayList<String> addr = Lists.newArrayList("192.168.0.13:2181");

        //提供者发布服务
        ServicePublisher publisher = new ZookeeperServicePublisher();
        publisher.initRegistry(addr);
        publisher.publishService("test1", node1);
        publisher.publishService("test1", node2);

        //消费者发现服务
        ServiceDiscover discover = new ZookeeperServiceDiscover();
        discover.initRegistry(addr);
        List<HostSocketAddress> providers = discover.discoverService("test1");

        //检查提供者列表
        providers.forEach((p) -> {
            System.out.println(p);
        });

        //清除提供者
        publisher.clearService(node1);
        //检查是否清除
        providers = discover.discoverService("test1");
        providers.forEach((p) -> {
            System.out.println(p);
        });

        publisher.clearService(node2);
        //检查是否清除
        providers = discover.discoverService("test1");
        providers.forEach((p) -> {
            System.out.println(p);
        });


    }
}
