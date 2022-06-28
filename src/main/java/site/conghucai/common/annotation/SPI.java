package site.conghucai.common.annotation;

import java.lang.annotation.*;

/**
 * 标记式注解，用于声明接口支持SPI扩展。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface SPI {
}
