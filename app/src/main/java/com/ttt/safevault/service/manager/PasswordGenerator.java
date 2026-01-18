package com.ttt.safevault.service.manager;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * 密码生成器
 * 负责生成安全的随机密码
 */
public class PasswordGenerator {
    // 密码生成字符集
    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String NUMBERS = "0123456789";
    private static final String SYMBOLS = "!@#$%^&*()_+-=[]{}|;':\",./<>?";

    private final SecureRandom secureRandom;

    public PasswordGenerator() {
        this.secureRandom = new SecureRandom();
    }

    /**
     * 生成密码（带符号）
     */
    public String generatePassword(int length, boolean symbols) {
        return generatePassword(length, true, true, true, symbols);
    }

    /**
     * 生成密码（完全自定义）
     */
    public String generatePassword(int length, boolean useUppercase, boolean useLowercase,
                                   boolean useNumbers, boolean useSymbols) {
        StringBuilder charPool = new StringBuilder();

        if (useUppercase) charPool.append(UPPERCASE);
        if (useLowercase) charPool.append(LOWERCASE);
        if (useNumbers) charPool.append(NUMBERS);
        if (useSymbols) charPool.append(SYMBOLS);

        // 默认至少包含小写字母和数字
        if (charPool.length() == 0) {
            charPool.append(LOWERCASE).append(NUMBERS);
        }

        StringBuilder password = new StringBuilder(length);
        String pool = charPool.toString();

        // 确保密码包含所选的每种字符类型
        List<String> requiredChars = new ArrayList<>();
        if (useUppercase) requiredChars.add(UPPERCASE);
        if (useLowercase) requiredChars.add(LOWERCASE);
        if (useNumbers) requiredChars.add(NUMBERS);
        if (useSymbols) requiredChars.add(SYMBOLS);

        // 先添加每种类型至少一个字符
        for (String chars : requiredChars) {
            if (password.length() < length) {
                password.append(chars.charAt(secureRandom.nextInt(chars.length())));
            }
        }

        // 填充剩余长度
        while (password.length() < length) {
            password.append(pool.charAt(secureRandom.nextInt(pool.length())));
        }

        // 打乱顺序
        char[] passwordArray = password.toString().toCharArray();
        for (int i = passwordArray.length - 1; i > 0; i--) {
            int j = secureRandom.nextInt(i + 1);
            char temp = passwordArray[i];
            passwordArray[i] = passwordArray[j];
            passwordArray[j] = temp;
        }

        return new String(passwordArray);
    }

    /**
     * 检查密码是否为弱密码
     */
    public boolean isWeakPassword(String password) {
        if (password == null || password.length() < 8) {
            return true;
        }

        boolean hasUpper = password.matches(".*[A-Z].*");
        boolean hasLower = password.matches(".*[a-z].*");
        boolean hasNumber = password.matches(".*\\d.*");
        boolean hasSymbol = password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");

        int types = (hasUpper ? 1 : 0) + (hasLower ? 1 : 0) + (hasNumber ? 1 : 0) + (hasSymbol ? 1 : 0);
        return types < 3;
    }
}
