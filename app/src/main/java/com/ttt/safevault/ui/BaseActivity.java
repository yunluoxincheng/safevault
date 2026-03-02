package com.ttt.safevault.ui;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.network.AuthInterceptor;
import com.ttt.safevault.receiver.AuthReceiver;
import com.ttt.safevault.security.SecurityManager;

/**
 * 基础Activity
 * 提供安全管理器和安全措施的便捷访问
 *
 * 注意：会话锁定功能由 MainActivity 和 SafeVaultApplication 统一处理
 */
public abstract class BaseActivity extends AppCompatActivity {

    protected SecurityManager securityManager;
    protected BackendService backendService;
    protected AuthReceiver authReceiver;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 获取安全管理器单例
        securityManager = SecurityManager.getInstance(this);

        // 应用安全措施
        applySecurityMeasures();

        // 注册Token过期广播接收器
        registerAuthReceiver();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 注销Token过期广播接收器
        unregisterAuthReceiver();
    }

    /**
     * 注册Token过期广播接收器
     */
    private void registerAuthReceiver() {
        authReceiver = new AuthReceiver();
        IntentFilter filter = new IntentFilter(AuthInterceptor.ACTION_TOKEN_EXPIRED);
        // 使用LocalBroadcastManager确保应用内广播
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ 需要 RECEIVER_NOT_EXPORTED 标志
                registerReceiver(authReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(authReceiver, filter);
            }
        } catch (Exception e) {
            // 防止重复注册
            android.util.Log.e("BaseActivity", "Failed to register auth receiver", e);
        }
    }

    /**
     * 注销Token过期广播接收器
     */
    private void unregisterAuthReceiver() {
        if (authReceiver != null) {
            try {
                unregisterReceiver(authReceiver);
            } catch (IllegalArgumentException e) {
                // 接收器未注册，忽略
            }
            authReceiver = null;
        }
    }

    /**
     * 应用安全措施
     */
    protected void applySecurityMeasures() {
        // 防止截图
        securityManager.applySecurityMeasures(this);
    }

    /**
     * 导航到登录页面
     */
    protected void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * 手动锁定应用
     */
    protected void lockApp() {
        if (securityManager != null) {
            securityManager.lock();
        }
    }

    /**
     * 获取安全管理器
     */
    protected SecurityManager getSecurityManager() {
        return securityManager;
    }
}