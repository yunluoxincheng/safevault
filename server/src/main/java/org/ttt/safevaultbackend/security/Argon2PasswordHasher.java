package org.ttt.safevaultbackend.security;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ttt.safevaultbackend.config.Argon2Config;

/**
 * Argon2 密码哈希服务
 *
 * 使用 Argon2id 算法进行密码哈希，提供：
 * - 抗 GPU/ASIC 攻击能力（内存硬哈希）
 * - 抗侧信道攻击（Argon2id 混合模式）
 * - 可配置的安全参数
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Argon2PasswordHasher {

    private final Argon2Config config;

    /**
     * Argon2 实例（线程安全）
     */
    private final Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);

    /**
     * 对密码进行哈希
     *
     * @param password 明文密码（char[]，使用后会被清空）
     * @return 哈希后的密码字符串（包含参数和盐值）
     */
    public String hash(char[] password) {
        try {
            log.debug("使用 Argon2id 哈希密码: timeCost={}, memoryCost={}KB, parallelism={}",
                config.getTimeCost(), config.getMemoryCost(), config.getParallelism());

            // Argon2 会自动生成随机盐值并包含在返回的哈希字符串中
            // 格式：$argon2id$v=19$m=65536,t=3,p=4$<salt>$<hash>
            String hash = argon2.hash(
                config.getTimeCost(),      // 迭代次数
                config.getMemoryCost(),    // 内存成本（KB）
                config.getParallelism(),   // 并行度
                password                   // 密码
            );

            log.debug("密码哈希完成，长度: {} 字符", hash.length());
            return hash;

        } catch (Exception e) {
            log.error("密码哈希失败", e);
            throw new SecurityException("密码哈希失败", e);
        } finally {
            // 安全清空密码数组
            if (password != null) {
                java.util.Arrays.fill(password, '\0');
            }
        }
    }

    /**
     * 对密码进行哈希（String 重载版本）
     *
     * @param password 明文密码字符串
     * @return 哈希后的密码字符串
     */
    public String hash(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return hash(password.toCharArray());
    }

    /**
     * 验证密码
     *
     * @param hash     存储的哈希值
     * @param password 待验证的密码（char[]，使用后会被清空）
     * @return 密码是否匹配
     */
    public boolean verify(String hash, char[] password) {
        if (hash == null || hash.isEmpty()) {
            throw new IllegalArgumentException("哈希值不能为空");
        }
        if (password == null) {
            return false;
        }

        try {
            boolean verified = argon2.verify(hash, password);

            if (verified) {
                log.debug("密码验证成功");
            } else {
                log.warn("密码验证失败");
            }

            return verified;

        } catch (Exception e) {
            log.error("密码验证失败", e);
            return false;
        } finally {
            // 安全清空密码数组
            if (password != null) {
                java.util.Arrays.fill(password, '\0');
            }
        }
    }

    /**
     * 验证密码（String 重载版本）
     *
     * @param hash     存储的哈希值
     * @param password 待验证的密码字符串
     * @return 密码是否匹配
     */
    public boolean verify(String hash, String password) {
        if (password == null) {
            return false;
        }
        return verify(hash, password.toCharArray());
    }

    /**
     * 检查哈希值是否需要重新计算
     * 当 Argon2 参数配置发生变化时，用于判断是否需要更新用户密码哈希
     *
     * @param hash 存储的哈希值
     * @return 是否需要重新哈希
     */
    public boolean needsRehash(String hash) {
        try {
            // Argon2 哈希格式：$argon2id$v=19$m=65536,t=3,p=4$<salt>$<hash>
            String[] parts = hash.split("\\$");
            if (parts.length < 5) {
                log.warn("无效的 Argon2 哈希格式");
                return true;
            }

            // 解析参数部分
            String paramsPart = parts[3]; // m=65536,t=3,p=4
            String[] params = paramsPart.split(",");

            int currentMemoryCost = 0;
            int currentTimeCost = 0;
            int currentParallelism = 0;

            for (String param : params) {
                String[] kv = param.split("=");
                if (kv.length == 2) {
                    switch (kv[0]) {
                        case "m":
                            currentMemoryCost = Integer.parseInt(kv[1]);
                            break;
                        case "t":
                            currentTimeCost = Integer.parseInt(kv[1]);
                            break;
                        case "p":
                            currentParallelism = Integer.parseInt(kv[1]);
                            break;
                    }
                }
            }

            // 比较参数
            boolean needsRehash = currentMemoryCost != config.getMemoryCost() ||
                                 currentTimeCost != config.getTimeCost() ||
                                 currentParallelism != config.getParallelism();

            if (needsRehash) {
                log.info("哈希参数已过时，需要重新哈希: 当前 m={},t={},p={}, 配置 m={},t={},p={}",
                    currentMemoryCost, currentTimeCost, currentParallelism,
                    config.getMemoryCost(), config.getTimeCost(), config.getParallelism());
            }

            return needsRehash;

        } catch (Exception e) {
            log.error("检查哈希是否需要重新计算时出错", e);
            return true;  // 出错时重新哈希以确保安全
        }
    }

    /**
     * 获取当前配置的哈希参数信息（用于日志和调试）
     *
     * @return 参数信息字符串
     */
    public String getParametersInfo() {
        return String.format("Argon2id(timeCost=%d, memoryCost=%dKB, parallelism=%d, outputLength=%d)",
            config.getTimeCost(),
            config.getMemoryCost(),
            config.getParallelism(),
            config.getOutputLength());
    }
}
