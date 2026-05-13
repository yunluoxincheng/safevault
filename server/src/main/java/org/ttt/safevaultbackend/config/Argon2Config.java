package org.ttt.safevaultbackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Argon2 密码哈希配置类
 *
 * 从 application.yml 读取 Argon2 配置参数：
 * - time-cost: 时间成本（迭代次数）
 * - memory-cost: 内存成本（KB）
 * - parallelism: 并行度
 * - output-length: 输出长度（字节）
 * - salt-length: 盐值长度（字节）
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "security.password-hash.argon2")
public class Argon2Config {

    /**
     * 时间成本（迭代次数）
     * 推荐：3-4次（平衡安全性和性能）
     */
    private int timeCost = 3;

    /**
     * 内存成本（单位：KB）
     * 推荐：131072（128MB）
     */
    private int memoryCost = 131072;

    /**
     * 并行度（线程数）
     * 推荐：2-4个线程
     */
    private int parallelism = 4;

    /**
     * 输出长度（字节）
     */
    private int outputLength = 32;

    /**
     * 盐值长度（字节）
     */
    private int saltLength = 16;
}
