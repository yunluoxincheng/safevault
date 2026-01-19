package com.ttt.safevault.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.model.PasswordStrength;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 编辑密码页面的ViewModel
 * 负责处理密码的创建和编辑逻辑
 */
public class EditPasswordViewModel extends AndroidViewModel {

    private final BackendService backendService;
    private final ExecutorService executor;

    // 密码生成器默认参数
    private static final int DEFAULT_PASSWORD_LENGTH = 16;
    private static final boolean DEFAULT_INCLUDE_UPPERCASE = true;
    private static final boolean DEFAULT_INCLUDE_LOWERCASE = true;
    private static final boolean DEFAULT_INCLUDE_NUMBERS = true;
    private static final boolean DEFAULT_INCLUDE_SYMBOLS = false;

    // LiveData用于UI状态管理
    private final MutableLiveData<PasswordItem> _passwordItem = new MutableLiveData<>();
    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> _isSaved = new MutableLiveData<>(false);
    private final MutableLiveData<String> _generatedPassword = new MutableLiveData<>();
    private final MutableLiveData<Boolean> _isNewPassword = new MutableLiveData<>(true);

    public LiveData<PasswordItem> passwordItem = _passwordItem;
    public LiveData<Boolean> isLoading = _isLoading;
    public LiveData<String> errorMessage = _errorMessage;
    public LiveData<Boolean> isSaved = _isSaved;
    public LiveData<String> generatedPassword = _generatedPassword;
    public LiveData<Boolean> isNewPassword = _isNewPassword;

    private int passwordId = -1;
    private boolean hasChanges = false;

    // 密码生成参数
    private int passwordLength = DEFAULT_PASSWORD_LENGTH;
    private boolean includeUppercase = DEFAULT_INCLUDE_UPPERCASE;
    private boolean includeLowercase = DEFAULT_INCLUDE_LOWERCASE;
    private boolean includeNumbers = DEFAULT_INCLUDE_NUMBERS;
    private boolean includeSymbols = DEFAULT_INCLUDE_SYMBOLS;

    public EditPasswordViewModel(@NonNull Application application, BackendService backendService) {
        super(application);
        this.backendService = backendService;
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * 加载密码条目（编辑模式）
     */
    public void loadPasswordItem(int id) {
        // 重置保存状态
        _isSaved.setValue(false);
        hasChanges = false;

        if (id < 0) {
            // 新建密码
            passwordId = -1;
            _isNewPassword.setValue(true);
            _passwordItem.setValue(new PasswordItem());
            return;
        }

        _isNewPassword.setValue(false);
        _isLoading.setValue(true);
        _errorMessage.setValue(null);

        executor.execute(() -> {
            try {
                PasswordItem item = backendService.decryptItem(id);
                if (item != null) {
                    _passwordItem.postValue(item);
                    this.passwordId = id;
                } else {
                    _errorMessage.postValue("未找到密码条目");
                }
            } catch (Exception e) {
                _errorMessage.postValue("加载失败: " + e.getMessage());
            } finally {
                _isLoading.postValue(false);
            }
        });
    }

    /**
     * 保存密码条目
     */
    public void savePassword(String title, String username, String password, String url, String notes, List<String> tags) {
        // 验证输入
        String validationError = validateInput(title, username, password);
        if (validationError != null) {
            _errorMessage.setValue(validationError);
            return;
        }

        _isLoading.setValue(true);
        _errorMessage.setValue(null);

        PasswordItem item;
        if (passwordId >= 0) {
            // 更新现有条目
            item = _passwordItem.getValue();
            if (item == null) item = new PasswordItem();
        } else {
            // 创建新条目
            item = new PasswordItem();
        }

        // 更新数据
        item.setTitle(title);
        item.setUsername(username);
        item.setPassword(password);
        item.setUrl(url);
        item.setNotes(notes);
        item.setTags(tags);
        item.updateTimestamp();

        final PasswordItem finalItem = item;
        executor.execute(() -> {
            try {
                int savedId = backendService.saveItem(finalItem);
                if (savedId > 0) {
                    _isSaved.postValue(true);
                    if (passwordId < 0) {
                        passwordId = savedId;
                    }
                } else {
                    _errorMessage.postValue("保存失败");
                }
            } catch (Exception e) {
                _errorMessage.postValue("保存失败: " + e.getMessage());
            } finally {
                _isLoading.postValue(false);
            }
        });
    }

    /**
     * 生成密码
     */
    public void generatePassword() {
        executor.execute(() -> {
            try {
                String password = backendService.generatePassword(
                        passwordLength,
                        includeUppercase,
                        includeLowercase,
                        includeNumbers,
                        includeSymbols
                );
                _generatedPassword.postValue(password);
            } catch (Exception e) {
                _errorMessage.postValue("生成密码失败: " + e.getMessage());
            }
        });
    }

    /**
     * 生成简单密码（不包含特殊字符）
     */
    public void generateSimplePassword(int length) {
        executor.execute(() -> {
            try {
                String password = backendService.generatePassword(length, false);
                _generatedPassword.postValue(password);
            } catch (Exception e) {
                _errorMessage.postValue("生成密码失败: " + e.getMessage());
            }
        });
    }

    /**
     * 设置密码生成参数
     */
    public void setPasswordGenerationParams(int length, boolean uppercase, boolean lowercase,
                                            boolean numbers, boolean symbols) {
        this.passwordLength = length;
        this.includeUppercase = uppercase;
        this.includeLowercase = lowercase;
        this.includeNumbers = numbers;
        this.includeSymbols = symbols;
    }

    /**
     * 验证输入
     */
    private String validateInput(String title, String username, String password) {
        if (title == null || title.trim().isEmpty()) {
            return "请输入标题";
        }

        if (username == null || username.trim().isEmpty()) {
            return "请输入用户名";
        }

        if (password == null || password.trim().isEmpty()) {
            return "请输入密码";
        }

        if (password.length() < 4) {
            return "密码长度至少4位";
        }

        return null;
    }

    /**
     * 检查密码强度
     */
    public PasswordStrength checkPasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            return PasswordStrength.weak();
        }

        var hasUpper = false;
        var hasLower = false;
        var hasDigit = false;
        var hasSymbol = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSymbol = true;
        }

        var score = 0;
        if (hasUpper) score++;
        if (hasLower) score++;
        if (hasDigit) score++;
        if (hasSymbol) score++;
        if (password.length() >= 12) score++;

        return PasswordStrength.fromScore(score);
    }

    /**
     * 获取密码强度描述
     */
    public String getPasswordStrengthDescription(PasswordStrength strength) {
        return strength.description();
    }

    /**
     * 获取密码强度改进建议
     */
    public String getPasswordStrengthAdvice(PasswordStrength strength) {
        return strength.getImprovementAdvice();
    }

    /**
     * 清除生成的密码
     */
    public void clearGeneratedPassword() {
        _generatedPassword.setValue(null);
    }

    /**
     * 清除错误信息
     */
    public void clearError() {
        _errorMessage.setValue(null);
    }

    /**
     * 标记有更改
     */
    public void markChanges() {
        hasChanges = true;
    }

    /**
     * 检查是否有未保存的更改
     */
    public boolean hasUnsavedChanges() {
        return hasChanges;
    }

    // Getter方法
    public int getPasswordLength() {
        return passwordLength;
    }

    public boolean isIncludeUppercase() {
        return includeUppercase;
    }

    public boolean isIncludeLowercase() {
        return includeLowercase;
    }

    public boolean isIncludeNumbers() {
        return includeNumbers;
    }

    public boolean isIncludeSymbols() {
        return includeSymbols;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}