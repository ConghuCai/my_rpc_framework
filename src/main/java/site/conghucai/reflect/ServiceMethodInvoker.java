package site.conghucai.reflect;

import com.google.common.base.Throwables;
import lombok.extern.slf4j.Slf4j;
import site.conghucai.common.exception.RpcException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 服务类实例方法的调用器
 */
@Slf4j
public class ServiceMethodInvoker {

    private Object instance;    //服务类实例
    private Method method;  //实例方法

    public ServiceMethodInvoker(Object instance, Method method) {
        this.instance = instance;
        this.method = method;
    }

    /**
     * 调用实例的method方法
     * @param args 方法参数
     * @return 方法返回值
     */
    public Object invoke(Object[] args) {
        if(instance == null || method == null) {
            throw new RpcException("service instance or method can not ne null.");
        }

        Object result = null;
        try {
            result = method.invoke(instance, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            log.error(Throwables.getStackTraceAsString(e));
        }

        return result;
    }
}
