package com.ttt.safevault.ui;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.ttt.safevault.security.SecurityManager;

/**
 * 基础Fragment
 * 提供安全管理器的便捷访问
 *
 * 注意：自动锁定功能由 MainActivity 和 SafeVaultApplication 统一处理
 */
public abstract class BaseFragment extends Fragment {

    protected SecurityManager securityManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 获取Activity的SecurityManager
        if (getActivity() instanceof BaseActivity) {
            securityManager = ((BaseActivity) getActivity()).getSecurityManager();
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 应用安全措施
        applySecurityMeasures(view);
    }

    /**
     * 应用安全措施
     */
    protected void applySecurityMeasures(@NonNull View view) {
        if (securityManager != null) {
            securityManager.applySecurityMeasures(view);
        }
    }

    /**
     * 获取安全管理器
     */
    protected SecurityManager getSecurityManager() {
        return securityManager;
    }
}