package site.conghucai.common.spi;

import lombok.extern.slf4j.Slf4j;
import site.conghucai.common.annotation.SPI;
import site.conghucai.common.exception.ExtensionException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SPI服务接口扩展机制，参考了Dubbo对于SPI的改良
 * @param <T> 扩展点接口类
 */
@Slf4j
public final class ExtensionLoader<T> {
    /**
     * 接口实现类 名称文件位置  （从classes路径下开始）
     */
    private static final String SERVICE_DIRECTORY = "META-INF/spi/";
    /**
     * 存放扩展点的ExtensionLoader  防止重复创建实例
     * 注：多线程访问一个extension对象存在安全问题，注意使用线程安全的Map
     */
    private static final Map<Class<?>, ExtensionLoader<?>> EXTENSION_LOADERS = new ConcurrentHashMap();
    /**
     * 实例对象Map  存储实现类字节码 和 实例对象 的映射关系 缓存
     * 注：刚初始化的实现类实例 会优先存到这个Map中
     */
    private static final Map<Class<?>, Object> EXTENSION_INSTANCES = new ConcurrentHashMap();
    /**
     * 接口类型字节码
     */
    private final Class<?> type;
    /**
     * 实例对象缓存  存储实现类别名 和 实例对象 关系
     * 注1：为了保证接口的实例对象是单例的  这里用一个Holder类型将实例对象声明为volatile变量，配合double-check即可保证单例的线程安全
     * 注2：getExtension()会优先从此Map对象中查找缓存的实例对象(因为则可以通过实现类的name直接获取)
     */
    private final Map<String, Holder<Object>> cachedInstances = new ConcurrentHashMap();
    /**
     * spi资源文件中所有实现类的字节码对象缓存  缓存实现类映射名、实现类字节码
     * Holder对象包装成volatile类型配合单例模式  存储的是"Jdk" = JdkSerializerImpl.class 类似这种
     * 注1：不允许直接使用  必须通过getExtensionClasses()方法获取Map对象并使用！
     * 注2：一旦创建就加载所有的spi资源文件中的实现类字节码进来  因此不允许runtime实时改动spi文件
     */
    private final Holder<Map<String, Class<?>>> cachedClasses = new Holder();

    /**
     * （内部方法）初始化一个ExtensionLoader对象  不允许外部创建
     * @param type  接口类字节码对象
     */
    private ExtensionLoader(Class<?> type) {
        this.type = type;
    }

    /**
     * 获取一个扩展点接口的extensionLoader实例对象  初始化一个ExtensionLoader必须通过此方法
     * 此方法通过线程安全的Map  保证一个类型只会对应一个extensionLoader实例对象  防止重复创建
     * @param type  接口字节码对象
     * @param <T>   接口类型
     * @return  接口的extensionLoader对象
     */
    public static <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        if(type == null) {
            throw new IllegalArgumentException("Extension type should not be null.");
        } else if(!type.isInterface()) {
            throw new IllegalArgumentException("Extension type must be an interface.");
        } else if(type.getAnnotation(SPI.class) == null) {
            //使用此ExtensionLoader的接口要求必须加上@SPI注解！
            throw new IllegalArgumentException("Extension type must be annotated by @SPI");
        }

        ExtensionLoader<T> extensionLoader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        if(extensionLoader == null) {
            //此接口首次使用  使用内部构造方法初始化一个实例并写入MAP
            EXTENSION_LOADERS.putIfAbsent(type, new ExtensionLoader<T>(type));
            extensionLoader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        }

        return extensionLoader;
    }

    /**
     * 获取接口的服务实现类实例
     * @param name  实现类映射名称
     * @return  实现类的实例对象  如果不存在则返回null
     */
    public T getExtension(String name) {
        if(name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Extension name should not be null or empty.");
        }

        try {
            //dubbo将实际扩展类封装在Holder对象里
            //查看缓存中是否存在实例对象的Holder
            Holder<Object> holder = (Holder)cachedInstances.get(name);
            if(holder == null) {
                cachedInstances.put(name, new Holder<Object>());
                holder = cachedInstances.get(name);
            }

            //取出实例对象
            Object instance = holder.get();
            if(instance == null) {  //不存在实例对象  double-check单例一个实例对象
                synchronized (holder) {
                    instance = holder.get();
                    if(instance == null) {
                        instance = createExtension(name);
                        holder.set(instance);   //放入holder对象中
                    }
                }
            }

            return (T) instance;
        } catch (Exception e) {
            throw new ExtensionException(e);
        }
    }

    /**
     * 创建一个实现类的实例对象  本方法非单例构造  需要使用者进行单例构造
     * @param name 实现类的映射名  会从字节码缓存中取出
     * @return 实现类实例对象  如果创建失败返回null
     */
    private T createExtension(String name) {
        Map<String, Class<?>> classesCache = this.getExtensionClasses();    //获取class的缓存
        Class<?> clazz = classesCache.get(name);
        if(clazz == null) {
            // 未记录到 name 对应的class
            return null;
        }

        //从EXTENSION_INSTANCES缓存中找是否已经存在 已创建好的对象
        Object instance = EXTENSION_INSTANCES.get(clazz);
        if(instance == null) {
            try {
                //初始化实例对象  存入缓存
                EXTENSION_INSTANCES.putIfAbsent(clazz, clazz.getConstructor().newInstance());
                instance = EXTENSION_INSTANCES.get(clazz);
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }

        return (T) instance;
    }

    /**
     * 获取或创建cachedClasses属性的单例对象
     * 注意：cachedClasses不允许从Holder中取出  需要使用的话必须通过此方法！
     * @return this.cachedClasses.get()
     */
    private Map<String, Class<?>> getExtensionClasses() {
        Map<String, Class<?>> classes = this.cachedClasses.get();   //Holder中取出 是volatile变量
        //构造单例对象
        if (classes == null) {
            synchronized(this.cachedClasses) {
                classes = this.cachedClasses.get();
                if (classes == null) {
                    //1.初始化一个缓存Map对象
                    classes = new HashMap(8);   //当作缓存使用  指定初始缓存大小
                    //2.加载spi资源文件中的所有已知实现类 入刚初始化的缓存Map对象中
                    this.loadDirectory(classes);
                    //3.装回Holder对象
                    this.cachedClasses.set(classes);
                }
            }
        }

        return classes;
    }


    /**
     * 读取资源文件中的服务实现类名称，将服务实现类的映射名和类字节码对象存入extensionClasses缓存中
     * 如文件中一行：Cai=site.conghucai.service.XXServiceImpl  将会将"Cai"和XXServiceImpl.class存入extensionClasses缓存
     * @param extensionClasses  类的映射名和类字节码对象映射缓存
     */
    private void loadDirectory(Map<String, Class<?>> extensionClasses) {
        String fileName = SERVICE_DIRECTORY + this.type.getName();      //接口实现类 文件路径

        try {
            ClassLoader classLoader = ExtensionLoader.class.getClassLoader();
            Enumeration<URL> urls = classLoader.getResources(fileName); //从classes根路径下找文件
            if (urls != null) {
                while(urls.hasMoreElements()) {
                    URL resourceUrl = urls.nextElement();   //文件的url

                    //将此文件中所有的名称和实现类存入extensionClasses
                    this.loadResourceFile(extensionClasses, classLoader, resourceUrl);
                }
            }
        } catch (IOException e) {
            log.error("find interface spi resource exception.", e);
        }

    }

    /**
     * 从一个spi资源文件中解析 实现类映射名 和 实现类字节码对象，存储入参数extensionClasses中
     * @param extensionClasses  存储映射关系的Map
     * @param classLoader   用于加载类字节码的 类加载器
     * @param resourceUrl   资源文件url对象
     */
    private void loadResourceFile(Map<String, Class<?>> extensionClasses, ClassLoader classLoader, URL resourceUrl) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceUrl.openStream(), StandardCharsets.UTF_8));) {
            String line;
            while((line = reader.readLine()) != null) {
                line = line.trim(); //读取一行

                if (line.length() > 0) {
                    try {
                        int ei = line.indexOf(61);  //"="作为分隔符
                        String name = line.substring(0, ei).trim(); //映射名
                        String clazzName = line.substring(ei + 1).trim();   //实现类全限定名称
                        if (name.length() > 0 && clazzName.length() > 0) {
                            //使用loadClass进行类加载  注意静态对象、静态块是不会进行初始化加载的
                            Class<?> clazz = classLoader.loadClass(clazzName);
                            extensionClasses.put(name, clazz);  //存入
                        } else {
                            log.error("SPI load Class failed, name {}, class {}", name, clazzName);
                        }
                    } catch (ClassNotFoundException e) {
                        log.error(e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log.error("read resourceFile exception.", e);
        }

    }

}
