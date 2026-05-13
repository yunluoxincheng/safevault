package org.ttt.safevaultbackend.aspect;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.ttt.safevaultbackend.annotation.RateLimit;
import org.ttt.safevaultbackend.exception.RateLimitExceededException;

import java.time.Duration;

/**
 * 速率限制切面
 * 安全加固第三阶段：使用Redis实现分布式速率限制
 *
 * 限制策略：
 * - 登录API: 5次/分钟
 * - 注册API: 3次/分钟
 * - 邮箱验证: 10次/小时
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private HttpServletRequest request;

    /**
     * 速率限制拦截
     */
    @Around("@annotation(rateLimit)")
    public Object rateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        // 获取客户端标识（IP地址）
        String clientIp = getClientIp(request);

        // 生成速率限制键
        String key = generateKey(rateLimit.key(), joinPoint, clientIp);

        // 获取限制参数
        int maxRequests = rateLimit.requests();
        Duration duration = parseDuration(rateLimit.per());

        try {
            // 尝试增加计数
            Long currentCount = redisTemplate.opsForValue().increment(key);

            if (currentCount == null) {
                // 如果键不存在，设置初始值
                redisTemplate.opsForValue().set(key, "1", duration);
                return joinPoint.proceed();
            }

            if (currentCount == 1) {
                // 第一次访问，设置过期时间
                redisTemplate.opsForValue().set(key, String.valueOf(currentCount), duration);
            }

            if (currentCount > maxRequests) {
                // 超过限制
                log.warn("速率限制触发: key={}, current={}, max={}, window={}",
                    key, currentCount, maxRequests, rateLimit.per());

                throw new RateLimitExceededException(
                    String.format("请求过于频繁，请在%s后重试", formatWindow(rateLimit.per())),
                    key, maxRequests, rateLimit.per()
                );
            }

            // 允许请求
            return joinPoint.proceed();

        } catch (RateLimitExceededException e) {
            throw e;
        } catch (Exception e) {
            log.error("速率限制检查失败", e);
            // 出错时允许请求通过（降级策略）
            return joinPoint.proceed();
        }
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 处理多个IP的情况（X-Forwarded-For可能包含多个IP）
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }

    /**
     * 生成速率限制键
     */
    private String generateKey(String customKey, ProceedingJoinPoint joinPoint, String clientIp) {
        if (customKey != null && !customKey.isEmpty()) {
            return "rate_limit:" + customKey + ":" + clientIp;
        }

        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        return "rate_limit:" + className + ":" + methodName + ":" + clientIp;
    }

    /**
     * 解析时间窗口
     */
    private Duration parseDuration(String per) {
        return switch (per.toLowerCase()) {
            case "minute" -> Duration.ofMinutes(1);
            case "hour" -> Duration.ofHours(1);
            case "day" -> Duration.ofDays(1);
            default -> Duration.ofMinutes(1);
        };
    }

    /**
     * 格式化时间窗口（用于错误消息）
     */
    private String formatWindow(String per) {
        return switch (per.toLowerCase()) {
            case "minute" -> "1分钟";
            case "hour" -> "1小时";
            case "day" -> "1天";
            default -> "一段时间";
        };
    }
}
