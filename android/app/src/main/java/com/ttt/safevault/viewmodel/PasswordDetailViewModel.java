package com.ttt.safevault.viewmodel;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.utils.ClipboardManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 密码详情页面的ViewModel
 * 负责管理单个密码条目的详情展示和操作
 */
public class PasswordDetailViewModel extends AndroidViewModel {

    private final BackendService backendService;
    private final ExecutorService executor;
    private final ClipboardManager secureClipboard;
    private final Handler mainHandler;

    // 密码显示状态的超时时间（毫秒）
    private static final int PASSWORD_HIDE_DELAY = 30000; // 30秒

    // LiveData用于UI状态管理
    private final MutableLiveData<PasswordItem> _passwordItem = new MutableLiveData<>();
    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> _isPasswordVisible = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> _copiedField = new MutableLiveData<>(null); // 0=用户名, 1=密码, 2=URL
    private final MutableLiveData<Boolean> _isDeleted = new MutableLiveData<>(false);

    public LiveData<PasswordItem> passwordItem = _passwordItem;
    public LiveData<Boolean> isLoading = _isLoading;
    public LiveData<String> errorMessage = _errorMessage;
    public LiveData<Boolean> isPasswordVisible = _isPasswordVisible;
    public LiveData<Integer> copiedField = _copiedField;
    public LiveData<Boolean> isDeleted = _isDeleted;

    private int passwordId = -1;
    private Runnable passwordHideRunnable;

    public PasswordDetailViewModel(@NonNull Application application, BackendService backendService) {
        super(application);
        this.backendService = backendService;
        this.executor = Executors.newSingleThreadExecutor();
        this.secureClipboard = new ClipboardManager(application);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 加载密码详情
     */
    public void loadPasswordItem(int id) {
        if (id < 0) {
            _errorMessage.setValue("无效的密码ID");
            return;
        }

        this.passwordId = id;
        _isLoading.setValue(true);
        _errorMessage.setValue(null);

        executor.execute(() -> {
            try {
                PasswordItem item = backendService.decryptItem(id);
                if (item != null) {
                    _passwordItem.postValue(item);
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
     * 切换密码显示/隐藏状态
     */
    public void togglePasswordVisibility() {
        boolean currentlyVisible = _isPasswordVisible.getValue() != null && _isPasswordVisible.getValue();

        if (currentlyVisible) {
            hidePassword();
        } else {
            showPassword();
        }
    }

    /**
     * 显示密码
     */
    public void showPassword() {
        _isPasswordVisible.setValue(true);

        // 设置自动隐藏
        if (passwordHideRunnable != null) {
            mainHandler.removeCallbacks(passwordHideRunnable);
        }

        passwordHideRunnable = this::hidePassword;
        mainHandler.postDelayed(passwordHideRunnable, PASSWORD_HIDE_DELAY);
    }

    /**
     * 隐藏密码
     */
    public void hidePassword() {
        _isPasswordVisible.setValue(false);
        if (passwordHideRunnable != null) {
            mainHandler.removeCallbacks(passwordHideRunnable);
            passwordHideRunnable = null;
        }
    }

    /**
     * 复制用户名到剪贴板
     */
    public void copyUsername() {
        PasswordItem item = _passwordItem.getValue();
        if (item != null && item.getUsername() != null && !item.getUsername().isEmpty()) {
            secureClipboard.copyText(item.getUsername(), "用户名");
            _copiedField.setValue(0);
            scheduleCopiedFieldClear();
        }
    }

    /**
     * 复制密码到剪贴板
     */
    public void copyPassword() {
        PasswordItem item = _passwordItem.getValue();
        if (item != null && item.getPassword() != null && !item.getPassword().isEmpty()) {
            secureClipboard.copySensitiveText(item.getPassword(), "密码");
            _copiedField.setValue(1);
            scheduleCopiedFieldClear();
        }
    }

    /**
     * 复制URL到剪贴板
     */
    public void copyUrl() {
        PasswordItem item = _passwordItem.getValue();
        if (item != null && item.getUrl() != null && !item.getUrl().isEmpty()) {
            secureClipboard.copyText(item.getUrl(), "网址");
            _copiedField.setValue(2);
            scheduleCopiedFieldClear();
        }
    }

    private void scheduleCopiedFieldClear() {
        mainHandler.postDelayed(() -> _copiedField.setValue(null), 2000);
    }

    /**
     * 删除密码条目
     */
    public void deletePassword() {
        if (passwordId < 0) {
            _errorMessage.setValue("无效的密码ID");
            return;
        }

        _isLoading.setValue(true);

        executor.execute(() -> {
            try {
                boolean success = backendService.deleteItem(passwordId);
                if (success) {
                    _isDeleted.postValue(true);
                } else {
                    _errorMessage.postValue("删除失败");
                }
            } catch (Exception e) {
                _errorMessage.postValue("删除失败: " + e.getMessage());
            } finally {
                _isLoading.postValue(false);
            }
        });
    }

    /**
     * 获取分享文本
     */
    public String getShareText() {
        PasswordItem item = _passwordItem.getValue();
        if (item == null) return null;

        StringBuilder sb = new StringBuilder();
        sb.append("标题: ").append(item.getTitle()).append("\n");

        if (item.getUsername() != null && !item.getUsername().isEmpty()) {
            sb.append("用户名: ").append(item.getUsername()).append("\n");
        }

        if (item.getUrl() != null && !item.getUrl().isEmpty()) {
            sb.append("网址: ").append(item.getUrl()).append("\n");
        }

        if (item.getNotes() != null && !item.getNotes().isEmpty()) {
            sb.append("备注: ").append(item.getNotes()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 清除复制状态
     */
    public void clearCopiedStatus() {
        _copiedField.setValue(null);
    }

    /**
     * 清除错误信息
     */
    public void clearError() {
        _errorMessage.setValue(null);
    }

    /**
     * 刷新数据
     */
    public void refresh() {
        if (passwordId >= 0) {
            loadPasswordItem(passwordId);
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // 清理密码自动隐藏任务
        if (passwordHideRunnable != null) {
            mainHandler.removeCallbacks(passwordHideRunnable);
        }
        executor.shutdown();
    }
}