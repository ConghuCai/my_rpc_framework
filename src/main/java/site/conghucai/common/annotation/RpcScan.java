package site.conghucai.common.annotation;

import java.lang.annotation.*;

/**
 * 用此注解声明RPC服务器需要扫描的服务包
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RpcScan {
    /**
     * 用此注解声明RPC服务器需要扫描的服务包。默认为空数组，将扫描服务器同目录下的包。
     * @return
     */
    String[] value() default {};
}
