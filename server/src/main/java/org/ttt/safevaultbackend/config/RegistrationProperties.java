package org.ttt.safevaultbackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 注册状态追踪配置属性
 * 从 application.yml 读取配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "registration")
public class RegistrationProperties {

    /**
     * 注册超时时间（分钟）
     * 用户验证邮箱后必须在此时限内完成注册，否则用户记录将被清理
     */
    private int cleanupTimeoutMinutes = 5;

    /**
     * 是否启用定时清理任务
     * true: 启用定时清理超时用户
     * false: 禁用定时清理（可通过管理接口手动触发）
     */
    private boolean cleanupScheduledEnabled = true;

    /**
     * 定时清理任务执行间隔（毫秒）
     * 默认 300000 毫秒（5分钟）
     */
    private long cleanupScheduledIntervalMs = 300000L;
}
