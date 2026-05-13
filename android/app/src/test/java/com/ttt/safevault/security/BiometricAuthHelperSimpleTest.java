package com.ttt.safevault.security;

import org.junit.Test;

import static org.junit.Assert.*;

public class BiometricAuthHelperSimpleTest {

    @Test
    public void testBiometricAuthCallback_InterfaceMethodsExist() {
        // 测试BiometricAuthCallback接口的方法是否存在
        BiometricAuthHelper.BiometricAuthCallback callback = new BiometricAuthHelper.BiometricAuthCallback() {
            @Override
            public void onSuccess() {
                // 成功回调
            }

            @Override
            public void onFailure(String error) {
                // 失败回调
            }

            @Override
            public void onCancel() {
                // 取消回调
            }
        };

        assertNotNull(callback);
    }
}