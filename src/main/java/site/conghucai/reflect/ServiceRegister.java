package site.conghucai.reflect;

import lombok.extern.slf4j.Slf4j;
import site.conghucai.common.annotation.RpcMapping;
import site.conghucai.common.exception.RpcException;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 服务注册器，提供了一系列静态方法。
 * 不允许实例化。
 * 提供register方法，将带有@RpcService注解的类文件中的@RpcMapping注解的方法注册进来，并提供查询接口。
 */
@Slf4j
public class ServiceRegister {

    //带@RpcService注解的类字节码对象集
    private static Set<Class<?>> serviceClasses = new CopyOnWriteArraySet<>();

    //类字节码对象 对应的实例
    private static Map<Class<?>, Object> factory = new ConcurrentHashMap<>();

    //方法映射名  对应的方法调用器
    private static Map<String, ServiceMethodInvoker> methodInvokerMap = new ConcurrentHashMap<>();

    private ServiceRegister() {
    }

    /**
     * 根据@RpcService的字节码对象，注册其中带@RpcMapping的方法。
     * 这个方法是线程互斥的，因此内部建立的存入map的实例、对象等都是单例的。
     * @param clazz @RpcService的字节码对象
     */
    public static synchronized void register(Class<?> clazz) {
        //1.获取一个clazz对应的instance.
        Object instance = factory.computeIfAbsent(clazz, key -> {
            try {
                //不存在key对应的instance   则新建一个instance并存入map
                return clazz.getConstructor().newInstance();
            } catch (Exception e) {
                //注意service类中可能不存在无参构造  存入null
                log.error("Instance construct exception occurred.", e);
                return null;
            }
        });

        Method[] declaredMethods = clazz.getDeclaredMethods();  //所有被注解的方法
        for(Method method : declaredMethods) {
            //2.遍历方法  生成其映射名mappingName
            String mappingName = null;
            if(method.isAnnotationPresent(RpcMapping.class)) {
                //被@RpcMapping注解的方法
                mappingName = method.getDeclaredAnnotation(RpcMapping.class).value();
                if(mappingName.length() == 0) {
                    //映射名不合法
                    throw new RpcException("Class [" + clazz + "] method [" + method + "] doesn't have legal mapping name. register end.");
                }

            } else {
                //普通无注解的方法
                continue;
            }

            if(mappingName == null) {
                throw new RpcException("mappingName init occurred exception.");
            }

            //3.根据方法对象、实例对象  获取一个执行器  放入map中
            ServiceMethodInvoker invoker = new ServiceMethodInvoker(instance, method);
            methodInvokerMap.put(mappingName, invoker);
        }
    }

    /**
     * 根据服务方法映射名，获取一个映射名对应方法的执行器。
     * @param mapping @RpcMapping注解的方法的映射名
     * @return
     */
    public static ServiceMethodInvoker getMethodInvoker(String mapping) {
        ServiceMethodInvoker invoker = methodInvokerMap.get(mapping);
        if(invoker == null) {
            throw new RpcException("Method mapping [" + mapping + "]'s invoker not found in map.");
        }
        return invoker;
    }

    public static Map<String, ServiceMethodInvoker> getMethodInvokerMap() {
        return methodInvokerMap;
    }

    public static Set<Class<?>> getAllServiceClazz() {
        return serviceClasses;
    }
}
