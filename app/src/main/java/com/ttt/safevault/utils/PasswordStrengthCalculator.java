package com.ttt.safevault.utils;

import com.ttt.safevault.model.PasswordStrength;

/**
 * 密码强度计算工具类
 * 提供静态方法计算密码强度
 */
public class PasswordStrengthCalculator {

    /**
     * 计算密码强度
     *
     * @param password 要计算的密码
     * @return 密码强度对象
     */
    public static PasswordStrength calculate(String password) {
        if (password == null || password.isEmpty()) {
            return new PasswordStrength(0, PasswordStrength.Level.WEAK, "密码不能为空");
        }

        int score = calculateScore(password);
        return PasswordStrength.fromScore(score);
    }

    /**
     * 计算密码强度分数
     * 返回: 0-5 的分数
     * 评分规则：
     * - 长度：最高 2 分（8位1分，12位0.5分，16位0.5分）
     * - 字符类型：最高 2 分（每种类型 0.5 分）
     * - 复杂度奖励：最高 1 分（3种类型0.5分，4种类型0.5分）
     *
     * @param password 要计算的密码
     * @return 强度分数 (0-5)
     */
    private static int calculateScore(String password) {
        double score = 0;

        // 长度评分 (0-2分)
        if (password.length() >= 8) score += 1;
        if (password.length() >= 12) score += 0.5;
        if (password.length() >= 16) score += 0.5;

        // 字符类型评分 (0-2分)
        boolean hasLowercase = password.matches(".*[a-z].*");
        boolean hasUppercase = password.matches(".*[A-Z].*");
        boolean hasDigit = password.matches(".*\\d.*");
        boolean hasSymbol = password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{}|;:,.<>?].*");

        int typeCount = 0;
        if (hasLowercase) { typeCount++; score += 0.5; }
        if (hasUppercase) { typeCount++; score += 0.5; }
        if (hasDigit) { typeCount++; score += 0.5; }
        if (hasSymbol) { typeCount++; score += 0.5; }

        // 复杂度奖励 (0-1分)
        if (typeCount >= 3) score += 0.5;
        if (typeCount == 4) score += 0.5;

        // 转换为整数分数 (0-5)
        return Math.min((int) Math.round(score), 5);
    }

    /**
     * 获取密码强度百分比（用于进度条）
     *
     * @param password 要计算的密码
     * @return 强度百分比 (0-100)
     */
    public static int getStrengthPercentage(String password) {
        int score = calculateScore(password);
        return (score * 100) / 5;
    }

    /**
     * 获取强度等级对应的颜色资源 ID
     *
     * @param level 强度等级
     * @return 颜色资源 ID
     */
    public static int getStrengthColor(PasswordStrength.Level level) {
        // 这个方法需要在实际使用时通过 Context 获取资源
        // 这里返回颜色值，调用方可以转换为资源 ID
        return switch (level) {
            case WEAK -> 0xFFFF5252;      // 红色
            case MEDIUM -> 0xFFFFC107;    // 橙色
            case STRONG -> 0xFF4CAF50;    // 绿色
        };
    }

    /**
     * 检查密码是否满足最小强度要求
     *
     * @param password 要检查的密码
     * @param minScore 最小分数要求
     * @return 是否满足要求
     */
    public static boolean meetsMinimumStrength(String password, int minScore) {
        return calculateScore(password) >= minScore;
    }
}
