package org.ttt.safevaultbackend.annotation;

import java.lang.annotation.*;

/**
 * 速率限制注解
 * 安全加固第三阶段：API速率限制
 *
 * 使用示例：
 * @RateLimit(requests = 5, per = "minute")
 * public ResponseEntity<?> login(...) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 时间窗口内允许的最大请求数
     */
    int requests() default 10;

    /**
     * 时间窗口单位：minute, hour, day
     */
    String per() default "minute";

    /**
     * 速率限制的键前缀（可选，默认使用类名+方法名）
     */
    String key() default "";
}
