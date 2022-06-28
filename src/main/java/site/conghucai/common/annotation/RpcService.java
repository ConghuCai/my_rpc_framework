package site.conghucai.common.annotation;

import java.lang.annotation.*;

/**
 * 标识此类为一个RPC服务类。内部有提供远程调用的方法。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RpcService {
}
