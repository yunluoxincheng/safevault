package com.ttt.safevault.service.manager;

import com.ttt.safevault.model.UserKeyInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * ShareManager 单元测试
 *
 * 覆盖与 ShareManager 相关的逻辑：
 * - UserKeyInfo 协议版本支持（supportsV2/supportsV3）
 * - 协议版本常量
 *
 * 注意：ShareManager 完整流程依赖 Context、RetrofitClient、SessionGuard 与 native 库，
 * 端到端测试请在 Android 设备或 ShareEncryptionIntegrationTest 中验证。
 */
@RunWith(JUnit4.class)
public class ShareManagerTest {

    /**
     * UserKeyInfo.supportsV3：双方都有 X25519+Ed25519 时才支持 v3
     */
    @Test
    public void userKeyInfo_supportsV3_whenBothKeysPresent() {
        UserKeyInfo info = new UserKeyInfo();
        info.setUserId("u1");
        info.setX25519PublicKey("x");
        info.setEd25519PublicKey("e");
        assertTrue(info.supportsV3());
        assertFalse(info.supportsV2()); // 未设置 RSA 时 supportsV2 为 false
    }

    @Test
    public void userKeyInfo_supportsV2_whenRsaPresent() {
        UserKeyInfo info = new UserKeyInfo();
        info.setUserId("u1");
        info.setRsaPublicKey("rsa");
        assertTrue(info.supportsV2());
    }

    @Test
    public void userKeyInfo_supportsV3_false_whenMissingKey() {
        UserKeyInfo info = new UserKeyInfo();
        info.setUserId("u1");
        info.setX25519PublicKey("x");
        assertFalse(info.supportsV3());
    }

    @Test
    public void userKeyInfo_getBestProtocolVersion() {
        UserKeyInfo v3 = new UserKeyInfo();
        v3.setUserId("u1");
        v3.setX25519PublicKey("x");
        v3.setEd25519PublicKey("e");
        assertEquals("3.0", v3.getBestProtocolVersion());

        UserKeyInfo v2 = new UserKeyInfo();
        v2.setUserId("u2");
        v2.setRsaPublicKey("r");
        assertEquals("2.0", v2.getBestProtocolVersion());
    }
}
