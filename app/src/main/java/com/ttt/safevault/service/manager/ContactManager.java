package com.ttt.safevault.service.manager;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.ttt.safevault.crypto.KeyDerivationManager;
import com.ttt.safevault.data.AppDatabase;
import com.ttt.safevault.data.Contact;
import com.ttt.safevault.data.ContactDao;
import com.ttt.safevault.network.TokenManager;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 联系人管理器
 * 负责管理用户的联系人列表（其他SafeVault用户的公钥）
 */
public class ContactManager {
    private static final String TAG = "ContactManager";

    private static final String IDENTITY_QR_PREFIX = "safevault://identity/";
    private static final String IDENTITY_VERSION = "2.0";

    private final Context context;
    private final ContactDao contactDao;
    private final KeyDerivationManager keyDerivationManager;
    private final Gson gson;

    public ContactManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.contactDao = AppDatabase.getInstance(context).contactDao();
        this.keyDerivationManager = new KeyDerivationManager(context);
        this.gson = new Gson();
    }

    /**
     * 生成我的身份QR码内容
     *
     * @param userEmail 用户邮箱
     * @param masterPassword 主密码
     * @return QR码内容（safevault://identity/{base64(json)}）
     */
    @Nullable
    public String generateMyIdentityQR(
            @NonNull String userEmail,
            @NonNull String masterPassword
    ) {
        try {
            // 1. 获取我的公钥
            PublicKey publicKey = keyDerivationManager.getPublicKey(masterPassword, userEmail);
            String publicKeyBase64 = Base64.encodeToString(
                publicKey.getEncoded(),
                Base64.NO_WRAP
            );

            // 2. 获取云端用户ID
            TokenManager tokenManager = TokenManager.getInstance(context);
            String cloudUserId = tokenManager.getUserId();

            // 3. 构建身份数据
            IdentityQRData identityData = new IdentityQRData();
            identityData.version = IDENTITY_VERSION;
            identityData.userId = generateUserId(userEmail);
            identityData.cloudUserId = cloudUserId;  // 新增云端用户ID
            identityData.username = userEmail;
            identityData.displayName = extractDisplayName(userEmail);
            identityData.publicKey = publicKeyBase64;
            identityData.generatedAt = System.currentTimeMillis();

            // 4. 序列化为JSON
            String json = gson.toJson(identityData);

            // 5. Base64编码
            String base64Data = Base64.encodeToString(
                json.getBytes(),
                Base64.NO_WRAP | Base64.URL_SAFE
            );

            // 6. 构建QR码内容
            String qrContent = IDENTITY_QR_PREFIX + base64Data;

            Log.d(TAG, "Generated identity QR code for user: " + userEmail);
            return qrContent;

        } catch (Exception e) {
            Log.e(TAG, "Failed to generate identity QR", e);
            return null;
        }
    }

    /**
     * 从QR码内容添加联系人
     *
     * @param qrContent QR码内容
     * @param note 备注名称（如"妈妈"）
     * @return true表示添加成功
     */
    public boolean addContactFromQR(
            @NonNull String qrContent,
            @NonNull String note
    ) {
        try {
            // 1. 验证前缀
            if (!qrContent.startsWith(IDENTITY_QR_PREFIX)) {
                Log.e(TAG, "Invalid QR code format");
                return false;
            }

            // 2. 提取Base64数据
            String base64Data = qrContent.substring(IDENTITY_QR_PREFIX.length());

            // 3. Base64解码
            byte[] jsonBytes = Base64.decode(
                base64Data,
                Base64.NO_WRAP | Base64.URL_SAFE
            );
            String json = new String(jsonBytes);

            // 4. 解析JSON
            IdentityQRData identityData = gson.fromJson(json, IdentityQRData.class);

            if (identityData == null) {
                Log.e(TAG, "Failed to parse identity data");
                return false;
            }

            // 5. 版本兼容性检查
            if (!IDENTITY_VERSION.equals(identityData.version) && !"1.0".equals(identityData.version)) {
                Log.e(TAG, "Invalid identity data version: " + identityData.version);
                return false;
            }

            // 兼容旧版本（1.0）：如果没有 cloudUserId 字段，设为空字符串
            if (identityData.cloudUserId == null) {
                identityData.cloudUserId = "";
                Log.d(TAG, "Legacy QR code detected (v1.0), cloudUserId set to empty");
            }

            // 6. 检查是否已存在
            Contact existing = contactDao.getContactByUserId(identityData.userId);
            if (existing != null) {
                Log.w(TAG, "Contact already exists: " + identityData.userId);
                return false;
            }

            // 7. 创建联系人
            Contact contact = new Contact();
            contact.contactId = generateContactId();
            contact.userId = identityData.userId;
            contact.cloudUserId = identityData.cloudUserId;  // 新增云端用户ID
            contact.username = identityData.username;
            contact.displayName = identityData.displayName;
            contact.publicKey = identityData.publicKey;
            contact.myNote = note;
            contact.addedAt = System.currentTimeMillis();
            contact.lastUsedAt = 0;

            // 8. 保存到数据库
            long result = contactDao.insertContact(contact);

            if (result > 0) {
                Log.d(TAG, "Successfully added contact: " + contact.displayName);
                return true;
            } else {
                Log.e(TAG, "Failed to insert contact");
                return false;
            }

        } catch (JsonSyntaxException e) {
            Log.e(TAG, "Failed to parse identity JSON", e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to add contact", e);
            return false;
        }
    }

    /**
     * 获取所有联系人
     */
    @NonNull
    public List<Contact> getAllContacts() {
        return contactDao.getAllContacts();
    }

    /**
     * 搜索联系人
     *
     * @param query 搜索关键词
     * @return 匹配的联系人列表
     */
    @NonNull
    public List<Contact> searchContacts(@NonNull String query) {
        try {
            if (query.isEmpty()) {
                return getAllContacts();
            }
            return contactDao.searchContacts(query);
        } catch (Exception e) {
            Log.e(TAG, "Failed to search contacts", e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取联系人的公钥
     *
     * @param contactId 联系人ID
     * @return Base64编码的公钥，失败返回null
     */
    @Nullable
    public String getContactPublicKey(@NonNull String contactId) {
        try {
            Contact contact = contactDao.getContact(contactId);
            return contact != null ? contact.publicKey : null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get contact public key", e);
            return null;
        }
    }

    /**
     * 删除联系人
     *
     * @param contactId 联系人ID
     * @return true表示删除成功
     */
    public boolean deleteContact(@NonNull String contactId) {
        try {
            int result = contactDao.deleteContactById(contactId);
            Log.d(TAG, "Delete contact result: " + result);
            return result > 0;
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete contact", e);
            return false;
        }
    }

    /**
     * 更新联系人的最后使用时间
     *
     * @param contactId 联系人ID
     */
    public void updateLastUsed(@NonNull String contactId) {
        try {
            contactDao.updateLastUsed(contactId, System.currentTimeMillis());
        } catch (Exception e) {
            Log.e(TAG, "Failed to update last used time", e);
        }
    }

    /**
     * 生成联系人ID
     */
    @NonNull
    private String generateContactId() {
        return "contact_" + java.util.UUID.randomUUID().toString();
    }

    /**
     * 从邮箱生成用户ID
     */
    @NonNull
    private String generateUserId(@NonNull String email) {
        // 处理空邮箱的情况
        if (email == null || email.isEmpty()) {
            Log.e(TAG, "generateUserId: email is empty, using random ID");
            // 返回一个随机ID（UUID去掉横线后取前16位）
            String uuid = UUID.randomUUID().toString().replace("-", "");
            return "user_" + uuid.substring(0, Math.min(16, uuid.length()));
        }

        String encoded = Base64.encodeToString(
            email.getBytes(),
            Base64.NO_WRAP | Base64.URL_SAFE
        );

        // 确保有足够的字符
        if (encoded.length() < 16) {
            // 如果编码结果太短，用UUID补足
            String uuidSuffix = UUID.randomUUID().toString().replace("-", "");
            encoded = encoded + uuidSuffix;
        }

        // 安全地截取前16个字符
        return "user_" + encoded.substring(0, Math.min(16, encoded.length()));
    }

    /**
     * 从邮箱提取显示名称
     */
    @NonNull
    private String extractDisplayName(@NonNull String email) {
        // 简单实现：使用@前的部分
        int atIndex = email.indexOf('@');
        return atIndex > 0 ? email.substring(0, atIndex) : email;
    }

    /**
     * 身份QR码数据结构
     */
    private static class IdentityQRData {
        String version;           // "2.0"
        String userId;            // 本地派生的用户ID（用于离线分享）
        String cloudUserId;       // 云端用户ID（用于云端分享）
        String username;          // 邮箱
        String displayName;       // 显示名称
        String publicKey;         // RSA公钥（Base64）
        long generatedAt;         // 生成时间
    }
}
