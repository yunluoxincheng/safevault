package com.ttt.safevault.model;

/**
 * 密码强度记录类
 * 使用Java 17的record特性，自动生成equals、hashCode、toString等方法
 */
public record PasswordStrength(
    int score,          // 强度分数 (0-5)
    Level level,        // 强度等级
    String description  // 描述文本
) {

    /**
     * 密码强度枚举
     */
    public enum Level {
        WEAK(0, "弱", "密码过于简单，容易被破解"),
        MEDIUM(1, "中", "密码有一定强度，建议增加复杂度"),
        STRONG(2, "强", "密码强度良好");

        private final int value;
        private final String label;
        private final String advice;

        Level(int value, String label, String advice) {
            this.value = value;
            this.label = label;
            this.advice = advice;
        }

        public int getValue() { return value; }
        public String getLabel() { return label; }
        public String getAdvice() { return advice; }
    }

    /**
     * 静态工厂方法 - 创建弱密码强度
     */
    public static PasswordStrength weak() {
        return new PasswordStrength(0, Level.WEAK, "密码强度：弱");
    }

    /**
     * 静态工厂方法 - 创建中等密码强度
     */
    public static PasswordStrength medium(int score) {
        return new PasswordStrength(score, Level.MEDIUM, "密码强度：中");
    }

    /**
     * 静态工厂方法 - 创建强密码强度
     */
    public static PasswordStrength strong(int score) {
        return new PasswordStrength(score, Level.STRONG, "密码强度：强");
    }

    /**
     * 从分数计算密码强度
     */
    public static PasswordStrength fromScore(int score) {
        if (score >= 4) return strong(score);
        if (score >= 2) return medium(score);
        return weak();
    }

    /**
     * 紧凑构造器 - 用于验证
     */
    public PasswordStrength {
        if (score < 0 || score > 5) {
            throw new IllegalArgumentException("分数必须在0-5之间");
        }
        if (level == null) {
            throw new IllegalArgumentException("强度等级不能为空");
        }
    }

    /**
     * 获取改进建议
     */
    public String getImprovementAdvice() {
        return switch (level) {
            case WEAK -> "建议改进：\n• 使用至少12个字符\n• 包含大小写字母\n• 添加数字和特殊符号\n• 避免使用常见词汇";
            case MEDIUM -> "建议：\n• 增加密码长度\n• 添加更多特殊字符";
            case STRONG -> "密码强度良好，请继续保持！";
        };
    }
}