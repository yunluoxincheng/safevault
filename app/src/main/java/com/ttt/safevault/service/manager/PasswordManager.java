package com.ttt.safevault.service.manager;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ttt.safevault.crypto.CryptoManager;
import com.ttt.safevault.data.EncryptedPasswordEntity;
import com.ttt.safevault.data.PasswordDao;
import com.ttt.safevault.model.PasswordItem;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * 密码管理器
 * 负责密码的加密、解密、保存、删除和搜索
 */
public class PasswordManager {
    private static final String TAG = "PasswordManager";

    private final CryptoManager cryptoManager;
    private final PasswordDao passwordDao;

    public PasswordManager(@NonNull CryptoManager cryptoManager, @NonNull PasswordDao passwordDao) {
        this.cryptoManager = cryptoManager;
        this.passwordDao = passwordDao;
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
            Log.d(TAG, "getAllItems: isUnlocked=" + cryptoManager.isUnlocked());
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
     * 加密单个字段，返回格式: iv:ciphertext
     */
    @Nullable
    private String encryptField(@Nullable String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return null;
        }
        CryptoManager.EncryptedData data = cryptoManager.encrypt(plaintext);
        if (data == null) {
            return null;
        }
        return data.iv + ":" + data.ciphertext;
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
     */
    @Nullable
    private String decryptField(@Nullable String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) {
            return null;
        }
        String[] parts = encrypted.split(":", 2);
        if (parts.length != 2) {
            return null;
        }
        return cryptoManager.decrypt(parts[1], parts[0]);
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
