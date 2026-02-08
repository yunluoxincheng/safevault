package com.ttt.safevault.service.manager;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ttt.safevault.crypto.CryptoManager;
import com.ttt.safevault.data.EncryptedPasswordEntity;
import com.ttt.safevault.data.PasswordDao;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.security.CryptoSession;

import org.json.JSONArray;
import org.json.JSONException;

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
 * 架构升级：使用 CryptoSession 的 DataKey 进行加密操作（三层架构）
 */
public class PasswordManager {
    private static final String TAG = "PasswordManager";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_SIZE = 12; // GCM推荐IV大小
    private static final int TAG_SIZE = 128; // GCM认证标签大小

    private final CryptoManager cryptoManager; // 保留用于向后兼容
    private final PasswordDao passwordDao;
    private final Context context;

    public PasswordManager(@NonNull CryptoManager cryptoManager, @NonNull PasswordDao passwordDao) {
        this.cryptoManager = cryptoManager;
        this.passwordDao = passwordDao;
        this.context = null; // 旧构造函数，不设置 context
    }

    /**
     * 新构造函数：支持三层架构
     */
    public PasswordManager(@NonNull CryptoManager cryptoManager, @NonNull PasswordDao passwordDao, @NonNull Context context) {
        this.cryptoManager = cryptoManager;
        this.passwordDao = passwordDao;
        this.context = context.getApplicationContext();
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
     * 保存密码项
     */
    public int saveItem(PasswordItem item) {
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
        } catch (Exception e) {
            Log.e(TAG, "Failed to save item", e);
            return -1;
        }
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
     * 获取当前用于加密的密钥
     * 优先使用 CryptoSession 的 DataKey（三层架构），降级使用 CryptoManager（向后兼容）
     */
    @Nullable
    private SecretKey getEncryptionKey() {
        // 优先使用新三层架构的 CryptoSession
        CryptoSession cryptoSession = CryptoSession.getInstance();
        SecretKey dataKey = cryptoSession.getDataKey();
        if (dataKey != null) {
            return dataKey;
        }

        // 降级使用旧 CryptoManager（向后兼容）
        return cryptoManager.getMasterKey();
    }

    /**
     * 尝试使用多个密钥解密（向后兼容）
     * 优先使用 CryptoSession.DataKey，失败则尝试 CryptoManager.masterKey
     */
    @Nullable
    private String decryptFieldWithMultipleKeys(@Nullable String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) {
            return null;
        }

        String[] parts = encrypted.split(":", 2);
        if (parts.length != 2) {
            return null;
        }

        byte[] ciphertext = Base64.decode(parts[1], Base64.NO_WRAP);
        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);

        // 1. 优先尝试使用 CryptoSession.DataKey（新架构）
        CryptoSession cryptoSession = CryptoSession.getInstance();
        SecretKey dataKey = cryptoSession.getDataKey();
        if (dataKey != null) {
            String result = tryDecryptWithKey(ciphertext, iv, dataKey);
            if (result != null) {
                Log.d(TAG, "decryptField: 使用 CryptoSession.DataKey 解密成功");
                return result;
            }
            Log.d(TAG, "decryptField: CryptoSession.DataKey 解密失败，尝试其他密钥");
        }

        // 2. 降级尝试使用 CryptoManager.masterKey（旧架构）
        SecretKey masterKey = cryptoManager.getMasterKey();
        if (masterKey != null) {
            String result = tryDecryptWithKey(ciphertext, iv, masterKey);
            if (result != null) {
                Log.d(TAG, "decryptField: 使用 CryptoManager.masterKey 解密成功（旧数据）");
                return result;
            }
            Log.d(TAG, "decryptField: CryptoManager.masterKey 解密失败");
        }

        Log.e(TAG, "decryptField: 所有密钥都尝试失败");
        return null;
    }

    /**
     * 尝试使用指定密钥解密
     */
    @Nullable
    private String tryDecryptWithKey(byte[] ciphertext, byte[] iv, @NonNull SecretKey key) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_SIZE, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            byte[] decrypted = cipher.doFinal(ciphertext);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 静默失败，返回 null 让调用者尝试下一个密钥
            return null;
        }
    }

    /**
     * 检查是否已解锁（三层架构）
     */
    private boolean isUnlocked() {
        CryptoSession cryptoSession = CryptoSession.getInstance();
        boolean sessionUnlocked = cryptoSession.isUnlocked();
        Log.d(TAG, "isUnlocked: CryptoSession状态=" + sessionUnlocked);
        return sessionUnlocked;
    }

    /**
     * 加密单个字段，返回格式: iv:ciphertext
     * 使用 CryptoSession 的 DataKey 进行加密（三层架构）
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

            // 加密
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            String ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP);
            String ciphertextBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP);

            return ivBase64 + ":" + ciphertextBase64;
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
     * 解密单个字段，输入格式: iv:ciphertext
     * 使用多重密钥解密（向后兼容：先尝试 DataKey，失败则尝试 masterKey）
     */
    @Nullable
    private String decryptField(@Nullable String encrypted) {
        return decryptFieldWithMultipleKeys(encrypted);
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
