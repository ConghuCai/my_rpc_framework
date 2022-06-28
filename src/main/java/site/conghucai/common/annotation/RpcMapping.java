package site.conghucai.common.annotation;

import java.lang.annotation.*;

/**
 * Rpc Service方法注解，标识这是一个Rpc客户端可以远程调用的方法。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RpcMapping {
    /**
     * 方法映射名
     * @return
     */
    String value() default "";
}
