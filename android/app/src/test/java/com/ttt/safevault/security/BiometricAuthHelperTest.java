package com.ttt.safevault.security;

import org.junit.Test;

import static org.junit.Assert.*;

public class BiometricAuthHelperTest {

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
    
    @Test
    public void testBiometricAuthCallback_ImplementationWorks() {
        // 测试回调接口实现
        final boolean[] successCalled = {false};
        final boolean[] failureCalled = {false};
        final boolean[] cancelCalled = {false};
        final String[] errorMessage = {null};
        
        BiometricAuthHelper.BiometricAuthCallback callback = new BiometricAuthHelper.BiometricAuthCallback() {
            @Override
            public void onSuccess() {
                successCalled[0] = true;
            }

            @Override
            public void onFailure(String error) {
                failureCalled[0] = true;
                errorMessage[0] = error;
            }

            @Override
            public void onCancel() {
                cancelCalled[0] = true;
            }
        };
        
        // 测试成功回调
        callback.onSuccess();
        assertTrue(successCalled[0]);
        
        // 测试失败回调
        callback.onFailure("测试错误");
        assertTrue(failureCalled[0]);
        assertEquals("测试错误", errorMessage[0]);
        
        // 测试取消回调
        callback.onCancel();
        assertTrue(cancelCalled[0]);
    }
}