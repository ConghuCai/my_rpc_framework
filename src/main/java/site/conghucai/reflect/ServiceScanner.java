package site.conghucai.reflect;

import lombok.extern.slf4j.Slf4j;
import site.conghucai.common.annotation.RpcScan;
import site.conghucai.common.annotation.RpcService;
import site.conghucai.common.exception.RpcException;

import java.io.File;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Slf4j
public class ServiceScanner {

    private Class<?> sourceClazz;   //@RpcScan所在类的字节码
    private Set<String> basePackages = new HashSet<>(); //存放@RpcScan指定的服务包

    private ClassLoader classLoader = ServiceScanner.class.getClassLoader();    //用于加载类文件


    public ServiceScanner(Class<?> sourceClazz) {
        this.sourceClazz = sourceClazz;
    }

    /**
     * 扫描、注册服务器提供的服务方法。
     * 注意这个方法是线程互斥的。
     * 扫描服务方法的代码只会执行一次，一次全部加载进来。
     */
    public synchronized void serviceScan() {
        //加载服务包到set中
        if(basePackages.isEmpty()) {
            if(sourceClazz == null) {
                //未传入字节码
                throw new RpcException("please give a class with annotation @RpcScan.");
            }

            if(!sourceClazz.isAnnotationPresent(RpcScan.class)) {
                //字节码未进行@RpcScan注解
                log.warn("source class [{}] doesn't have annotation @RpcScan.", sourceClazz.getSimpleName());
                return;
            }

            String[] values = sourceClazz.getDeclaredAnnotation(RpcScan.class).value();

            if(values.length == 0) {
                //未在注解中加入包信息
                basePackages.add(sourceClazz.getPackage().getName());   //默认使用@Rpc注解所在的包
            } else {
                basePackages.addAll(Arrays.asList(values));
            }
        }

        //扫描这些包中的class文件
        for(String backPackage: basePackages) {
            backPackage = backPackage.replace(".", "/");
            URL resource = classLoader.getResource(backPackage);    //从根目录下，取得文件url

            if(resource == null) {
                log.warn("package {} does not exist.", backPackage);
                return;
            }

            File[] files = new File[0];
            try {
                String filePath = URLDecoder.decode(resource.getFile(), "UTF-8");   //URL解析为路径String
                File file = new File(filePath); //为路径 建立文件对象
                files = file.listFiles();//从路径中加载所有文件、子文件夹
                if(files == null || files.length == 0) {
                    log.warn("Package {} dose not own any class or childPackage.", backPackage);
                    return;
                }
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }

            loadAndRegister(files);
        }

        printScannedResult();   //打印结果
    }

    //加载并注册file\子目录中的class字节码，注册这些字节码文件中的服务方法
    private void loadAndRegister(File[] files) {
        if(files == null) {
            return;
        }

        for(File file : files) {
            if(file.isDirectory()) {
                //子目录
                loadAndRegister(file.listFiles());
                continue;
            }

            if(!file.getName().endsWith(".class")) {
                //非字节码文件
                continue;
            }

            //字节码文件
            String classPath = file.getAbsolutePath().replace(File.separator, "."); //字节码绝对路径
            String className = classPath.substring((classPath.indexOf("classes.") + "classes.".length()),
                    classPath.lastIndexOf(".class"));   //分割出类名

            Class<?> clazz;
            try {
                clazz = classLoader.loadClass(className);   //加载类的字节码对象
            }catch (ClassNotFoundException e) {
                log.error("Filed to load scanned class.", e);
                continue;
            }

            if(!clazz.isAnnotationPresent(RpcService.class)) {
                continue;
            }

            //有@RpcService注解的类
            ServiceRegister.register(clazz);    //注册
        }
    }


    private void printScannedResult() {
        int mappingSize = ServiceRegister.getMethodInvokerMap().size();
        if (mappingSize == 0) {
            log.warn("No ServiceMethodMapping was scanned");
        } else {
            log.info("[{}] ServiceMethodMapping was scanned", mappingSize);
        }

        Set<String> mappings = ServiceRegister.getMethodInvokerMap().keySet();
        log.debug("Scanned ServiceMethodMapping: {}", mappings);
    }

}
