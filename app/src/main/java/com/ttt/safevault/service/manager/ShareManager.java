package com.ttt.safevault.service.manager;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.ttt.safevault.crypto.CryptoManager;
import com.ttt.safevault.data.AppDatabase;
import com.ttt.safevault.data.EncryptedPasswordEntity;
import com.ttt.safevault.data.PasswordDao;
import com.ttt.safevault.data.ShareRecord;
import com.ttt.safevault.data.ShareRecordDao;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.model.PasswordShare;
import com.ttt.safevault.model.SharePermission;
import com.ttt.safevault.model.ShareStatus;
import com.ttt.safevault.network.RetrofitClient;
import com.ttt.safevault.network.api.ShareServiceApi;
import com.ttt.safevault.dto.request.CreateShareRequest;
import com.ttt.safevault.dto.response.ReceivedShareResponse;
import com.ttt.safevault.dto.response.ShareResponse;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 分享管理器
 * 负责处理密码分享的创建、接收和撤销
 * 支持云端分享和离线分享
 */
public class ShareManager {
    private static final String TAG = "ShareManager";

    private final Context context;
    private final CryptoManager cryptoManager;
    private final PasswordDao passwordDao;
    private final ShareRecordDao shareRecordDao;
    private final ShareServiceApi shareServiceApi;
    private final Gson gson;

    // 离线分享加密配置
    private static final String OFFLINE_SHARE_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int SHARE_KEY_LENGTH = 256; // bits

    public ShareManager(@NonNull Context context, @NonNull CryptoManager cryptoManager,
                       @NonNull RetrofitClient retrofitClient) {
        this.context = context.getApplicationContext();
        this.cryptoManager = cryptoManager;
        this.passwordDao = AppDatabase.getInstance(context).passwordDao();
        this.shareRecordDao = AppDatabase.getInstance(context).shareRecordDao();
        this.shareServiceApi = retrofitClient.getShareServiceApi();
        this.gson = new Gson();
    }

    // ==================== 云端分享相关 ====================

    /**
     * 创建云端分享
     */
    @Nullable
    public ShareResponse createCloudShare(int passwordId, @Nullable String toUserId,
                                         int expireInMinutes, @NonNull SharePermission permission,
                                         @NonNull String shareType) {
        try {
            // 获取密码条目
            PasswordItem item = getPasswordById(passwordId);
            if (item == null) {
                Log.e(TAG, "Password not found: " + passwordId);
                return null;
            }

            // 构建请求
            CreateShareRequest request = new CreateShareRequest();
            request.setPasswordId(String.valueOf(passwordId));
            request.setTitle(item.getTitle());
            request.setUsername(item.getUsername());
            request.setEncryptedPassword(item.getPassword()); // PasswordItem存储的是明文密码
            request.setUrl(item.getUrl());
            request.setNotes(item.getNotes());
            request.setToUserId(toUserId);
            request.setExpireInMinutes(expireInMinutes > 0 ? expireInMinutes : null);
            request.setPermission(permission);
            request.setShareType(shareType);

            // 同步调用API
            ShareResponse response = shareServiceApi.createShare(request)
                    .timeout(30, TimeUnit.SECONDS)
                    .blockingFirst();

            if (response != null) {
                Log.d(TAG, "Cloud share created: " + response.getShareId());
            }

            return response;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create cloud share", e);
            return null;
        }
    }

    /**
     * 接收云端分享
     */
    @Nullable
    public ReceivedShareResponse receiveCloudShare(@NonNull String shareId) {
        try {
            ReceivedShareResponse response = shareServiceApi.receiveShare(shareId)
                    .timeout(30, TimeUnit.SECONDS)
                    .blockingFirst();

            if (response != null) {
                Log.d(TAG, "Cloud share received: " + response.getShareId());
            }

            return response;
        } catch (Exception e) {
            Log.e(TAG, "Failed to receive cloud share: " + shareId, e);
            return null;
        }
    }

    /**
     * 撤销云端分享
     */
    public boolean revokeCloudShare(@NonNull String shareId) {
        try {
            Void result = shareServiceApi.revokeShare(shareId)
                    .timeout(30, TimeUnit.SECONDS)
                    .blockingFirst();
            Log.d(TAG, "Cloud share revoked: " + shareId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to revoke cloud share: " + shareId, e);
            return false;
        }
    }

    /**
     * 保存云端分享到本地
     */
    public boolean saveCloudShare(@NonNull String shareId) {
        try {
            Void result = shareServiceApi.saveSharedPassword(shareId)
                    .timeout(30, TimeUnit.SECONDS)
                    .blockingFirst();
            Log.d(TAG, "Cloud share saved: " + shareId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to save cloud share: " + shareId, e);
            return false;
        }
    }

    /**
     * 获取我创建的云端分享列表
     */
    @NonNull
    public List<ReceivedShareResponse> getMyCloudShares() {
        try {
            List<ReceivedShareResponse> shares = shareServiceApi.getMyShares()
                    .timeout(30, TimeUnit.SECONDS)
                    .blockingFirst();
            return shares != null ? shares : new ArrayList<>();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get my cloud shares", e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取我接收的云端分享列表
     */
    @NonNull
    public List<ReceivedShareResponse> getReceivedCloudShares() {
        try {
            List<ReceivedShareResponse> shares = shareServiceApi.getReceivedShares()
                    .timeout(30, TimeUnit.SECONDS)
                    .blockingFirst();
            return shares != null ? shares : new ArrayList<>();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get received cloud shares", e);
            return new ArrayList<>();
        }
    }

    // ==================== 离线分享相关 ====================

    /**
     * 创建直接密码分享（离线）
     * 生成分享Token供后续访问
     */
    @Nullable
    public String createDirectPasswordShare(int passwordId, int expireMinutes,
                                          @NonNull SharePermission permission) {
        try {
            PasswordItem item = getPasswordById(passwordId);
            if (item == null) {
                Log.e(TAG, "Password not found: " + passwordId);
                return null;
            }

            // 生成分享ID
            String shareId = "share_" + UUID.randomUUID().toString();

            // 计算过期时间
            long expireTime = expireMinutes > 0
                    ? System.currentTimeMillis() + (expireMinutes * 60 * 1000L)
                    : 0;

            // 保存分享记录到本地数据库
            ShareRecord record = new ShareRecord();
            record.shareId = shareId;
            record.passwordId = passwordId;
            record.type = "sent";
            record.encryptedData = item.getPassword(); // 存储明文密码
            record.permission = serializePermission(permission);
            record.expireAt = expireTime;
            record.status = "active";
            record.createdAt = System.currentTimeMillis();
            record.accessedAt = 0;

            long result = shareRecordDao.insertShareRecord(record);
            if (result <= 0) {
                Log.e(TAG, "Failed to save share record");
                return null;
            }

            // 返回分享Token
            String token = Base64.getEncoder().encodeToString(
                    (shareId + ":" + System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));
            Log.d(TAG, "Direct share created: " + shareId);
            return token;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create direct share", e);
            return null;
        }
    }

    /**
     * 创建离线分享（版本2：密钥已嵌入，无需密码）
     * 生成QR码内容，包含加密数据
     */
    @Nullable
    public String createOfflineShare(int passwordId, int expireMinutes,
                                    @NonNull SharePermission permission) {
        try {
            PasswordItem item = getPasswordById(passwordId);
            if (item == null) {
                Log.e(TAG, "Password not found: " + passwordId);
                return null;
            }

            // 生成分享密钥（用于加密分享数据）
            byte[] shareKeyBytes = new byte[SHARE_KEY_LENGTH / 8];
            new SecureRandom().nextBytes(shareKeyBytes);
            SecretKey shareKey = new SecretKeySpec(shareKeyBytes, "AES");

            // 生成IV
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);

            // 构建分享数据
            ShareData shareData = new ShareData();
            shareData.title = item.getTitle();
            shareData.username = item.getUsername();
            shareData.password = item.getPassword(); // PasswordItem已经包含明文密码
            shareData.url = item.getUrl();
            shareData.notes = item.getNotes();
            shareData.permission = permission;
            shareData.expireTime = expireMinutes > 0
                    ? System.currentTimeMillis() + (expireMinutes * 60 * 1000L)
                    : 0;

            // 序列化分享数据
            String jsonData = gson.toJson(shareData);
            byte[] dataBytes = jsonData.getBytes(StandardCharsets.UTF_8);

            // 使用分享密钥加密数据
            Cipher cipher = Cipher.getInstance(OFFLINE_SHARE_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, shareKey, spec);
            byte[] encryptedData = cipher.doFinal(dataBytes);

            // 组合：IV + 密钥 + 加密数据（全部Base64编码）
            String ivBase64 = Base64.getEncoder().encodeToString(iv);
            String keyBase64 = Base64.getEncoder().encodeToString(shareKeyBytes);
            String dataBase64 = Base64.getEncoder().encodeToString(encryptedData);

            // QR码内容：safevault:offline:v2|iv|key|data
            String qrContent = "safevault:offline:v2|" + ivBase64 + "|" + keyBase64 + "|" + dataBase64;

            // 保存本地记录
            String shareId = "offline_" + UUID.randomUUID().toString();
            ShareRecord record = new ShareRecord();
            record.shareId = shareId;
            record.passwordId = passwordId;
            record.type = "sent";
            record.encryptedData = qrContent;
            record.permission = serializePermission(permission);
            record.expireAt = shareData.expireTime;
            record.status = "active";
            record.createdAt = System.currentTimeMillis();
            record.accessedAt = 0;

            shareRecordDao.insertShareRecord(record);

            Log.d(TAG, "Offline share created: " + shareId);
            return qrContent;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create offline share", e);
            return null;
        }
    }

    /**
     * 接收离线分享
     * 解密QR码内容并返回密码条目
     */
    @Nullable
    public PasswordItem receiveOfflineShare(@NonNull String encryptedData) {
        try {
            // 解析QR码内容：safevault:offline:v2|iv|key|data
            if (!encryptedData.startsWith("safevault:offline:v2|")) {
                Log.e(TAG, "Invalid offline share format");
                return null;
            }

            String[] parts = encryptedData.split("\\|");
            if (parts.length != 4) {
                Log.e(TAG, "Invalid offline share data");
                return null;
            }

            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] keyBytes = Base64.getDecoder().decode(parts[2]);
            byte[] encrypted = Base64.getDecoder().decode(parts[3]);

            // 解密数据
            SecretKey shareKey = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance(OFFLINE_SHARE_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, shareKey, spec);
            byte[] decryptedData = cipher.doFinal(encrypted);

            // 反序列化分享数据
            String jsonData = new String(decryptedData, StandardCharsets.UTF_8);
            ShareData shareData = gson.fromJson(jsonData, ShareData.class);

            // 检查过期
            if (shareData.expireTime > 0 && System.currentTimeMillis() > shareData.expireTime) {
                Log.e(TAG, "Offline share has expired");
                return null;
            }

            // 创建密码条目
            PasswordItem item = new PasswordItem();
            item.setTitle(shareData.title);
            item.setUsername(shareData.username);
            item.setPassword(shareData.password);
            item.setUrl(shareData.url);
            item.setNotes(shareData.notes);

            Log.d(TAG, "Offline share received successfully");
            return item;
        } catch (Exception e) {
            Log.e(TAG, "Failed to receive offline share", e);
            return null;
        }
    }

    // ==================== 本地分享记录相关 ====================

    /**
     * 获取分享详情（本地）
     */
    @Nullable
    public PasswordShare getShareDetails(@NonNull String shareId) {
        try {
            ShareRecord record = shareRecordDao.getShareRecord(shareId);
            if (record == null) {
                return null;
            }

            PasswordShare share = new PasswordShare();
            share.setShareId(record.shareId);
            share.setPasswordId(record.passwordId);
            share.setEncryptedData(record.encryptedData);
            share.setCreatedAt(record.createdAt);
            share.setExpireTime(record.expireAt);
            share.setPermission(parsePermission(record.permission));
            share.setStatus(ShareStatus.valueOf(record.status.toUpperCase()));

            return share;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get share details", e);
            return null;
        }
    }

    /**
     * 获取我创建的分享列表（本地）
     */
    @NonNull
    public List<PasswordShare> getMyShares() {
        try {
            List<ShareRecord> records = shareRecordDao.getMySentShares();
            List<PasswordShare> shares = new ArrayList<>();

            for (ShareRecord record : records) {
                PasswordShare share = new PasswordShare();
                share.setShareId(record.shareId);
                share.setPasswordId(record.passwordId);
                share.setEncryptedData(record.encryptedData);
                share.setCreatedAt(record.createdAt);
                share.setExpireTime(record.expireAt);
                share.setPermission(parsePermission(record.permission));
                share.setStatus(ShareStatus.valueOf(record.status.toUpperCase()));
                shares.add(share);
            }

            return shares;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get my shares", e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取我接收的分享列表（本地）
     */
    @NonNull
    public List<PasswordShare> getReceivedShares() {
        try {
            List<ShareRecord> records = shareRecordDao.getMyReceivedShares();
            List<PasswordShare> shares = new ArrayList<>();

            for (ShareRecord record : records) {
                PasswordShare share = new PasswordShare();
                share.setShareId(record.shareId);
                share.setPasswordId(record.passwordId);
                share.setEncryptedData(record.encryptedData);
                share.setCreatedAt(record.createdAt);
                share.setExpireTime(record.expireAt);
                share.setPermission(parsePermission(record.permission));
                share.setStatus(ShareStatus.valueOf(record.status.toUpperCase()));
                shares.add(share);
            }

            return shares;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get received shares", e);
            return new ArrayList<>();
        }
    }

    /**
     * 撤销密码分享（本地）
     */
    public boolean revokePasswordShare(@NonNull String shareId) {
        try {
            int result = shareRecordDao.revokeShare(shareId);
            Log.d(TAG, "Revoke share result: " + result);
            return result > 0;
        } catch (Exception e) {
            Log.e(TAG, "Failed to revoke share", e);
            return false;
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取密码条目
     */
    @Nullable
    private PasswordItem getPasswordById(int passwordId) {
        try {
            // 使用cryptoManager来解密密码
            EncryptedPasswordEntity entity = passwordDao.getById(passwordId);
            if (entity == null) {
                return null;
            }

            // 简化方案：由于PasswordManager处理加密/解密，我们这里只返回基本结构
            // 实际应用中应该通过PasswordManager.decryptItem()来获取解密后的数据
            PasswordItem item = new PasswordItem();
            item.setId(entity.getId());

            // 尝试使用cryptoManager解密各个字段
            try {
                String iv = entity.getIv();
                if (entity.getEncryptedTitle() != null) {
                    item.setTitle(cryptoManager.decrypt(entity.getEncryptedTitle(), iv));
                }
                if (entity.getEncryptedUsername() != null) {
                    item.setUsername(cryptoManager.decrypt(entity.getEncryptedUsername(), iv));
                }
                if (entity.getEncryptedPassword() != null) {
                    item.setPassword(cryptoManager.decrypt(entity.getEncryptedPassword(), iv));
                }
                if (entity.getEncryptedUrl() != null) {
                    item.setUrl(cryptoManager.decrypt(entity.getEncryptedUrl(), iv));
                }
                if (entity.getEncryptedNotes() != null) {
                    item.setNotes(cryptoManager.decrypt(entity.getEncryptedNotes(), iv));
                }
            } catch (Exception e) {
                Log.e(TAG, "Decryption failed", e);
                // 如果解密失败，返回null
                return null;
            }

            item.setUpdatedAt(entity.getUpdatedAt());
            return item;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get password by id", e);
            return null;
        }
    }

    /**
     * 序列化权限为JSON
     */
    @NonNull
    private String serializePermission(@NonNull SharePermission permission) {
        return gson.toJson(permission);
    }

    /**
     * 解析权限JSON
     */
    @NonNull
    private SharePermission parsePermission(@NonNull String json) {
        try {
            return gson.fromJson(json, SharePermission.class);
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "Failed to parse permission", e);
            return new SharePermission();
        }
    }

    /**
     * 分享数据内部类
     */
    private static class ShareData {
        String title;
        String username;
        String password;
        String url;
        String notes;
        SharePermission permission;
        long expireTime;
    }
}
