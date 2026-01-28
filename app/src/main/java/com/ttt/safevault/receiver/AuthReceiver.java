package com.ttt.safevault.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.ttt.safevault.network.TokenManager;

/**
 * 认证状态接收器
 * 处理Token过期事件，清除本地数据并跳转到登录页
 */
public class AuthReceiver extends BroadcastReceiver {

    private static final String TAG = "AuthReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        Log.d(TAG, "Received broadcast: " + action);

        // 处理Token过期事件
        if (com.ttt.safevault.network.AuthInterceptor.ACTION_TOKEN_EXPIRED.equals(action)) {
            handleTokenExpired(context);
        }
    }

    /**
     * 处理Token过期事件
     * 清除token并提示用户重新登录
     */
    private void handleTokenExpired(Context context) {
        Log.d(TAG, "Handling token expired event");

        // 清除token
        TokenManager tokenManager = TokenManager.getInstance(context);
        tokenManager.clearTokens();

        // 清除邮箱验证状态
        tokenManager.clearEmailVerificationStatus();

        // 显示提示信息
        Toast.makeText(context, "登录已过期，请重新登录", Toast.LENGTH_LONG).show();

        // 跳转到登录页
        Intent loginIntent = new Intent(context, com.ttt.safevault.ui.LoginActivity.class);
        loginIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(loginIntent);

        Log.d(TAG, "Redirected to login page");
    }
}
