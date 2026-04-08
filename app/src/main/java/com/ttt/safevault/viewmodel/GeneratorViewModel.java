package com.ttt.safevault.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.GeneratedPassword;
import com.ttt.safevault.core.ServiceLocator;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 密码生成器 ViewModel
 * 负责处理密码生成逻辑和状态管理
 */
public class GeneratorViewModel extends AndroidViewModel {

    private final BackendService backendService;
    private final MutableLiveData<String> generatedPassword = new MutableLiveData<>();
    private final MutableLiveData<Integer> passwordStrength = new MutableLiveData<>();
    private final MutableLiveData<List<GeneratedPassword>> generatedHistory = new MutableLiveData<>();
    private final SecureRandom secureRandom;

    // 当前配置
    private int currentLength = 16;
    private boolean currentUppercase = true;
    private boolean currentLowercase = true;
    private boolean currentNumbers = true;
    private boolean currentSymbols = false;

    // 生成历史
    private static final int MAX_HISTORY_SIZE = 10;
    private final List<GeneratedPassword> historyList = new ArrayList<>();

    public enum Preset {
        PIN,        // 4-6 位数字
        STRONG,     // 16 位所有字符
        MEMORABLE   // 12 位字母+数字
    }

    public GeneratorViewModel(@NonNull Application application) {
        super(application);
        this.backendService = ServiceLocator.getInstance().getBackendService();
        this.secureRandom = new SecureRandom();

        // 初始化历史记录
        generatedHistory.setValue(new ArrayList<>());
    }

    /**
     * 生成密码
     */
    public void generatePassword(int length, boolean uppercase, boolean lowercase,
                                 boolean numbers, boolean symbols) {
        // 保存当前配置
        currentLength = length;
        currentUppercase = uppercase;
        currentLowercase = lowercase;
        currentNumbers = numbers;
        currentSymbols = symbols;

        // 验证至少选择一种字符类型
        if (!uppercase && !lowercase && !numbers && !symbols) {
            return; // 或者可以显示错误
        }

        String password = generateSecurePassword(length, uppercase, lowercase, numbers, symbols);
        generatedPassword.setValue(password);

        // 计算密码强度
        int strength = calculatePasswordStrength(password);
        passwordStrength.setValue(strength);
    }

    /**
     * 使用 SecureRandom 生成安全密码
     */
    private String generateSecurePassword(int length, boolean uppercase, boolean lowercase,
                                          boolean numbers, boolean symbols) {
        List<Character> allChars = new ArrayList<>();
        List<Character> requiredChars = new ArrayList<>();

        if (uppercase) {
            addChars(allChars, "ABCDEFGHIJKLMNOPQRSTUVWXYZ");
            addChar(requiredChars, getRandomChar("ABCDEFGHIJKLMNOPQRSTUVWXYZ"));
        }
        if (lowercase) {
            addChars(allChars, "abcdefghijklmnopqrstuvwxyz");
            addChar(requiredChars, getRandomChar("abcdefghijklmnopqrstuvwxyz"));
        }
        if (numbers) {
            addChars(allChars, "0123456789");
            addChar(requiredChars, getRandomChar("0123456789"));
        }
        if (symbols) {
            addChars(allChars, "!@#$%^&*()_+-=[]{}|;:,.<>?");
            addChar(requiredChars, getRandomChar("!@#$%^&*()_+-=[]{}|;:,.<>?"));
        }

        // 确保包含至少一个每种选中的字符类型
        List<Character> passwordChars = new ArrayList<>(requiredChars);

        // 填充剩余位置
        for (int i = requiredChars.size(); i < length; i++) {
            passwordChars.add(allChars.get(secureRandom.nextInt(allChars.size())));
        }

        // 打乱顺序
        Collections.shuffle(passwordChars, secureRandom);

        // 转换为字符串
        StringBuilder password = new StringBuilder();
        for (char c : passwordChars) {
            password.append(c);
        }

        return password.toString();
    }

    private void addChars(List<Character> list, String chars) {
        for (char c : chars.toCharArray()) {
            list.add(c);
        }
    }

    private void addChar(List<Character> list, char c) {
        list.add(c);
    }

    private char getRandomChar(String chars) {
        return chars.charAt(secureRandom.nextInt(chars.length()));
    }

    /**
     * 计算密码强度
     * 返回: 0-100 的分数
     * 评分规则：
     * - 长度：最高 30 分（8位10分，12位10分，16位10分，20位+5分）
     * - 字符类型：最高 40 分（每种类型 10 分）
     * - 复杂度奖励：最高 30 分（3种类型15分，4种类型15分）
     */
    private int calculatePasswordStrength(String password) {
        int score = 0;

        // 长度评分 (0-35分)
        if (password.length() >= 8) score += 10;
        if (password.length() >= 12) score += 10;
        if (password.length() >= 16) score += 10;
        if (password.length() >= 20) score += 5;

        // 字符类型评分 (0-40分)
        boolean hasLowercase = password.matches(".*[a-z].*");
        boolean hasUppercase = password.matches(".*[A-Z].*");
        boolean hasDigit = password.matches(".*\\d.*");
        boolean hasSymbol = password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{}|;:,.<>?].*");

        int typeCount = 0;
        if (hasLowercase) { typeCount++; score += 10; }
        if (hasUppercase) { typeCount++; score += 10; }
        if (hasDigit) { typeCount++; score += 10; }
        if (hasSymbol) { typeCount++; score += 10; }

        // 复杂度奖励 (0-25分)
        if (typeCount >= 3) score += 15;
        if (typeCount == 4) score += 10;

        return Math.min(score, 100);
    }

    /**
     * 应用预设配置
     */
    public void applyPreset(Preset preset) {
        switch (preset) {
            case PIN:
                generatePassword(6, false, false, true, false);
                break;
            case STRONG:
                generatePassword(16, true, true, true, true);
                break;
            case MEMORABLE:
                generatePassword(12, true, true, true, false);
                break;
        }
    }

    /**
     * 添加到生成历史
     */
    public void addToHistory(String password) {
        // 检查是否已存在
        for (GeneratedPassword gp : historyList) {
            if (gp.getPassword().equals(password)) {
                return; // 已存在，不重复添加
            }
        }

        GeneratedPassword generatedPassword = new GeneratedPassword(
                password,
                currentLength,
                currentUppercase,
                currentLowercase,
                currentNumbers,
                currentSymbols,
                System.currentTimeMillis()
        );

        historyList.add(0, generatedPassword);

        // 限制历史记录数量
        if (historyList.size() > MAX_HISTORY_SIZE) {
            historyList.remove(MAX_HISTORY_SIZE);
        }

        generatedHistory.setValue(new ArrayList<>(historyList));
    }

    /**
     * 清除历史记录
     */
    public void clearHistory() {
        historyList.clear();
        generatedHistory.setValue(new ArrayList<>());
    }

    // LiveData getters
    public LiveData<String> getGeneratedPassword() {
        return generatedPassword;
    }

    public LiveData<Integer> getPasswordStrength() {
        return passwordStrength;
    }

    public LiveData<List<GeneratedPassword>> getGeneratedHistory() {
        return generatedHistory;
    }
}
