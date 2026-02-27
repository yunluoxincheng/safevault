package com.ttt.safevault.service.manager;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ttt.safevault.crypto.SecurePaddingUtil;
import com.ttt.safevault.data.EncryptedPasswordEntity;
import com.ttt.safevault.data.PasswordDao;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.security.CryptoSession;
import com.ttt.safevault.security.SessionGuard;
import com.ttt.safevault.security.SessionLockedException;

import org.json.JSONArray;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * 密码管理器
 * 负责密码的加密、解密、保存、删除和搜索
 *
 * 三层安全架构：使用 CryptoSession 的 DataKey 进行所有加密操作
 * Guarded Execution 模式：所有敏感操作通过 SessionGuard 门控
 *
 * @since SafeVault 3.4.0 (移除旧安全架构，完全迁移到三层架构)
 * @since SafeVault 3.4.1 (引入 Guarded Execution 模式)
 */
public class PasswordManager {
    private static final String TAG = "PasswordManager";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_SIZE = 12; // GCM推荐IV大小
    private static final int TAG_SIZE = 128; // GCM认证标签大小

    // 加密版本标识
    private static final String ENCRYPTION_VERSION_V1 = "v1"; // 无填充（旧格式）
    private static final String ENCRYPTION_VERSION_V2 = "v2"; // 带随机填充（新格式）
    private static final String CURRENT_VERSION = ENCRYPTION_VERSION_V2; // 当前使用的版本

    private final PasswordDao passwordDao;
    private final CryptoSession cryptoSession;
    private final SessionGuard sessionGuard;

    /**
     * 构造函数（三层架构 + Guarded Execution）
     *
     * @param passwordDao 密码数据访问对象
     * @param context 上下文（用于获取 CryptoSession）
     */
    public PasswordManager(@NonNull PasswordDao passwordDao, @NonNull Context context) {
        this.passwordDao = passwordDao;
        this.cryptoSession = CryptoSession.getInstance();
        this.sessionGuard = SessionGuard.getInstance();
        Log.i(TAG, "PasswordManager 初始化（三层架构 + Guarded Execution）");
    }

    /**
     * 构造函数（兼容旧接口）
     *
     * @deprecated 使用 {@link #PasswordManager(PasswordDao, Context)} 代替
     */
    @Deprecated
    public PasswordManager(@NonNull Object deprecatedCryptoManager, @NonNull PasswordDao passwordDao) {
        this.passwordDao = passwordDao;
        this.cryptoSession = CryptoSession.getInstance();
        this.sessionGuard = SessionGuard.getInstance();
        Log.w(TAG, "PasswordManager 使用旧构造函数（第一个参数已忽略）");
    }

    /**
     * 解密单个密码项
     */
    @Nullable
    public PasswordItem decryptItem(int id) {
        EncryptedPasswordEntity entity = passwordDao.getById(id);
        if (entity == null) {
            return null;
        }
        return decryptEntity(entity);
    }

    /**
     * 搜索密码
     */
    public List<PasswordItem> search(String query) {
        List<PasswordItem> results = new ArrayList<>();
        List<PasswordItem> allItems = getAllItems();

        if (query == null || query.trim().isEmpty()) {
            return allItems;
        }

        String lowerQuery = query.toLowerCase().trim();
        for (PasswordItem item : allItems) {
            if (matchesQuery(item, lowerQuery)) {
                results.add(item);
            }
        }

        return results;
    }

    /**
     * 保存密码项（Guarded Execution 模式）
     *
     * 安全保证：
     * - 通过 SessionGuard 确保会话已解锁
     * - 如果会话未解锁，抛出 SessionLockedException
     * - UI 层应捕获此异常并触发重新认证
     *
     * @param item 要保存的密码项
     * @return 保存成功返回 ID，失败返回 -1
     * @throws SessionLockedException 如果会话未解锁
     */
    public int saveItem(PasswordItem item) {
        // 使用 Guarded Execution 模式
        return sessionGuard.runWithUnlockedSession(() -> {
            try {
                EncryptedPasswordEntity entity = encryptItem(item);

                if (item.getId() > 0) {
                    // 更新现有记录
                    entity.setId(item.getId());
                    passwordDao.update(entity);
                    return item.getId();
                } else {
                    // 插入新记录
                    long newId = passwordDao.insert(entity);
                    return (int) newId;
                }
            } catch (SessionLockedException e) {
                // 重新抛出会话锁定异常
                throw e;
            } catch (Exception e) {
                Log.e(TAG, "Failed to save item", e);
                return -1;
            }
        });
    }

    /**
     * 删除密码项
     */
    public boolean deleteItem(int id) {
        try {
            return passwordDao.deleteById(id) > 0;
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete item", e);
            return false;
        }
    }

    /**
     * 获取所有密码项
     */
    public List<PasswordItem> getAllItems() {
        List<PasswordItem> items = new ArrayList<>();
        try {
            Log.d(TAG, "getAllItems: isUnlocked=" + isUnlocked());
            List<EncryptedPasswordEntity> entities = passwordDao.getAll();
            Log.d(TAG, "getAllItems: found " + entities.size() + " entities in database");

            for (EncryptedPasswordEntity entity : entities) {
                PasswordItem item = decryptEntity(entity);
                if (item != null) {
                    items.add(item);
                } else {
                    Log.w(TAG, "getAllItems: failed to decrypt entity id=" + entity.getId());
                }
            }
            Log.d(TAG, "getAllItems: successfully decrypted " + items.size() + " items");
        } catch (Exception e) {
            Log.e(TAG, "Failed to get all items", e);
        }
        return items;
    }

    /**
     * 加密PasswordItem为EncryptedPasswordEntity
     */
    private EncryptedPasswordEntity encryptItem(PasswordItem item) {
        EncryptedPasswordEntity entity = new EncryptedPasswordEntity();

        // 每个字段独立加密，IV拼接到密文前
        entity.setEncryptedTitle(encryptField(item.getTitle()));
        entity.setEncryptedUsername(encryptField(item.getUsername()));
        entity.setEncryptedPassword(encryptField(item.getPassword()));
        entity.setEncryptedUrl(encryptField(item.getUrl()));
        entity.setEncryptedNotes(encryptField(item.getNotes()));

        // 加密标签（将List转换为JSON字符串）
        entity.setEncryptedTags(encryptTags(item.getTags()));

        entity.setUpdatedAt(item.getUpdatedAt() > 0 ? item.getUpdatedAt() : System.currentTimeMillis());

        return entity;
    }

    /**
     * 获取当前用于加密的密钥（Guarded Execution 模式）
     *
     * 使用 requireDataKey() 而不是 getDataKey()：
     * - 如果会话未解锁，抛出 SessionLockedException
     * - 保证返回值非 null
     *
     * @return DataKey（保证非 null）
     * @throws SessionLockedException 如果会话未解锁
     */
    @NonNull
    private SecretKey getEncryptionKey() {
        return cryptoSession.requireDataKey();
    }

    /**
     * 检查是否已解锁（三层架构）
     */
    private boolean isUnlocked() {
        return cryptoSession.isUnlocked();
    }

    /**
     * 加密单个字段，返回格式: version:iv:ciphertext
     * 使用 CryptoSession 的 DataKey 进行加密（三层架构）
     * 版本 2.0：使用安全随机填充防止元数据泄露
     */
    @Nullable
    private String encryptField(@Nullable String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return null;
        }

        SecretKey key = getEncryptionKey();
        if (key == null) {
            Log.e(TAG, "encryptField: 无法获取加密密钥");
            return null;
        }

        try {
            // 生成随机IV
            byte[] iv = new byte[IV_SIZE];
            new SecureRandom().nextBytes(iv);

            // 初始化加密器
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_SIZE, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);

            // 版本 2.0：先进行安全随机填充，再加密
            byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            byte[] paddedBytes = SecurePaddingUtil.pad(plaintextBytes);

            // 加密填充后的数据
            byte[] encrypted = cipher.doFinal(paddedBytes);

            // 组合：version:iv:ciphertext
            String version = CURRENT_VERSION;
            String ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP);
            String ciphertextBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP);

            String result = version + ":" + ivBase64 + ":" + ciphertextBase64;
            Log.d(TAG, "encryptField: encrypted with " + version + ", "
                    + plaintextBytes.length + " -> " + paddedBytes.length + " -> "
                    + encrypted.length + " bytes");
            return result;
        } catch (Exception e) {
            Log.e(TAG, "encryptField: 加密失败", e);
            return null;
        }
    }

    /**
     * 解密EncryptedPasswordEntity为PasswordItem
     */
    @Nullable
    private PasswordItem decryptEntity(EncryptedPasswordEntity entity) {
        try {
            PasswordItem item = new PasswordItem();
            item.setId(entity.getId());
            item.setTitle(decryptField(entity.getEncryptedTitle()));
            item.setUsername(decryptField(entity.getEncryptedUsername()));
            item.setPassword(decryptField(entity.getEncryptedPassword()));
            item.setUrl(decryptField(entity.getEncryptedUrl()));
            item.setNotes(decryptField(entity.getEncryptedNotes()));
            item.setUpdatedAt(entity.getUpdatedAt());

            // 解密标签
            item.setTags(decryptTags(entity.getEncryptedTags()));

            return item;
        } catch (Exception e) {
            Log.e(TAG, "Failed to decrypt entity", e);
            return null;
        }
    }

    /**
     * 解密单个字段，输入格式: version:iv:ciphertext 或 iv:ciphertext（旧格式）
     * 使用 CryptoSession.DataKey 解密（三层架构）
     * 版本 2.0：解密后去除安全随机填充
     * 版本 1.0：直接解密（向后兼容）
     */
    @Nullable
    private String decryptField(@Nullable String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) {
            return null;
        }

        try {
            // 解析格式：version:iv:ciphertext 或 iv:ciphertext（旧格式）
            String[] parts = encrypted.split(":", 3);
            String version;
            String ivBase64;
            String ciphertextBase64;

            if (parts.length == 3) {
                // 新格式：version:iv:ciphertext
                version = parts[0];
                ivBase64 = parts[1];
                ciphertextBase64 = parts[2];
            } else if (parts.length == 2) {
                // 旧格式：iv:ciphertext（版本 1.0）
                version = ENCRYPTION_VERSION_V1;
                ivBase64 = parts[0];
                ciphertextBase64 = parts[1];
            } else {
                Log.e(TAG, "decryptField: 无效的加密格式");
                return null;
            }

            byte[] ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP);
            byte[] iv = Base64.decode(ivBase64, Base64.NO_WRAP);

            SecretKey dataKey = getEncryptionKey();

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_SIZE, iv);
            cipher.init(Cipher.DECRYPT_MODE, dataKey, spec);

            byte[] decrypted = cipher.doFinal(ciphertext);

            // 版本 2.0：去除填充
            if (ENCRYPTION_VERSION_V2.equals(version)) {
                try {
                    byte[] unpadded = SecurePaddingUtil.unpad(decrypted);
                    Log.d(TAG, "decryptField: decrypted with " + version + ", "
                            + ciphertext.length + " -> " + decrypted.length + " -> "
                            + unpadded.length + " bytes");
                    return new String(unpadded, StandardCharsets.UTF_8);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "decryptField: 去除填充失败", e);
                    return null;
                }
            } else {
                // 版本 1.0：直接返回
                Log.d(TAG, "decryptField: decrypted with " + version + ", "
                        + ciphertext.length + " -> " + decrypted.length + " bytes");
                return new String(decrypted, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            Log.e(TAG, "decryptField: 解密失败", e);
            return null;
        }
    }

    /**
     * 加密标签列表
     * 将List<String>转换为JSON字符串，然后加密
     */
    @Nullable
    private String encryptTags(@Nullable List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        try {
            JSONArray jsonArray = new JSONArray();
            for (String tag : tags) {
                jsonArray.put(tag);
            }
            String jsonString = jsonArray.toString();
            return encryptField(jsonString);
        } catch (Exception e) {
            Log.e(TAG, "Failed to encrypt tags", e);
            return null;
        }
    }

    /**
     * 解密标签列表
     * 解密JSON字符串，然后转换为List<String>
     */
    @Nullable
    private List<String> decryptTags(@Nullable String encryptedTags) {
        if (encryptedTags == null || encryptedTags.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            String jsonString = decryptField(encryptedTags);
            if (jsonString == null) {
                return new ArrayList<>();
            }
            JSONArray jsonArray = new JSONArray(jsonString);
            List<String> tags = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                tags.add(jsonArray.getString(i));
            }
            return tags;
        } catch (Exception e) {
            Log.e(TAG, "Failed to decrypt tags", e);
            return new ArrayList<>();
        }
    }

    /**
     * 检查密码项是否匹配查询条件
     */
    private boolean matchesQuery(PasswordItem item, String query) {
        return (item.getTitle() != null && item.getTitle().toLowerCase().contains(query)) ||
               (item.getUsername() != null && item.getUsername().toLowerCase().contains(query)) ||
               (item.getUrl() != null && item.getUrl().toLowerCase().contains(query)) ||
               (item.getNotes() != null && item.getNotes().toLowerCase().contains(query));
    }
}
