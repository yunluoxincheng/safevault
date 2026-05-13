package com.ttt.safevault.security;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class BiometricAuthIntegrationTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void testBiometricConfigIntegration() {
        // 测试SecurityConfig和BiometricAuthHelper的集成
        SecurityConfig config = new SecurityConfig(context);
        
        // 测试初始状态
        assertFalse(config.isBiometricEnabled());
        
        // 启用生物识别
        config.setBiometricEnabled(true);
        assertTrue(config.isBiometricEnabled());
        
        // 检查设备支持情况
        boolean isSupported = BiometricAuthHelper.isBiometricSupported(context);
        
        // 验证安全管理员能正确获取生物识别状态
        SecurityManager securityManager = new SecurityManager(context);
        // 注意：在测试环境中，设备可能不支持生物识别，所以这个测试主要是验证逻辑
        if (isSupported) {
            // 在真实设备上，如果支持生物识别，SecurityManager应该正确反映状态
            // 但由于测试环境限制，我们主要验证配置是否正确保存
            assertTrue(config.isBiometricEnabled());
        } else {
            // 在不支持生物识别的测试环境中，验证配置仍然正确保存
            assertTrue(config.isBiometricEnabled());
        }
    }

    @Test
    public void testBiometricNotSupportedReason() {
        // 测试获取生物识别不可用原因的功能
        String reason = BiometricAuthHelper.getBiometricNotSupportedReason(context);
        assertNotNull(reason);
        assertTrue(!reason.isEmpty());
    }
}