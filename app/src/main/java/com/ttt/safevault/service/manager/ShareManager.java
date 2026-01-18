package com.ttt.safevault.service.manager;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.model.PasswordShare;
import com.ttt.safevault.model.SharePermission;
import com.ttt.safevault.model.ShareStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分享管理器
 * 负责本地密码分享功能，包括创建、接收、撤销分享等
 */
public class ShareManager {
    private static final String TAG = "ShareManager";
    private static final String PREF_USER_ID = "user_id";

    private final Context context;
    private final PasswordManager passwordManager;
    private final android.content.SharedPreferences prefs;

    // 分享功能相关的内存存储（简化实现，生产环境应使用数据库）
    private final Map<String, PasswordShare> sharesMap = new ConcurrentHashMap<>();

    public ShareManager(@NonNull Context context, @NonNull PasswordManager passwordManager) {
        this.context = context.getApplicationContext();
        this.passwordManager = passwordManager;
        this.prefs = context.getSharedPreferences("backend_prefs", Context.MODE_PRIVATE);
    }

    /**
     * 创建密码分享
     */
    public String createPasswordShare(int passwordId, String toUserId,
                                     int expireInMinutes, SharePermission permission) {
        try {
            // 验证密码是否存在
            PasswordItem item = passwordManager.decryptItem(passwordId);
            if (item == null) {
                Log.e(TAG, "Password not found: " + passwordId);
                return null;
            }

            // 生成分享ID
            String shareId = "share_" + UUID.randomUUID().toString();

            // 创建分享对象
            PasswordShare share = new PasswordShare();
            share.setShareId(shareId);
            share.setPasswordId(passwordId);
            share.setFromUserId(getCurrentUserId());
            share.setToUserId(toUserId);
            share.setCreatedAt(System.currentTimeMillis());

            // 计算过期时间
            if (expireInMinutes > 0) {
                long expireTime = System.currentTimeMillis() + (expireInMinutes * 60 * 1000L);
                share.setExpireTime(expireTime);
            } else {
                share.setExpireTime(0);
            }

            share.setPermission(permission);
            share.setStatus(ShareStatus.ACTIVE);

            // 加密密码数据（简化实现，直接存储JSON）
            String encryptedData = encryptPasswordForShare(item);
            share.setEncryptedData(encryptedData);

            // 保存分享
            sharesMap.put(shareId, share);
            Log.d(TAG, "Share created: " + shareId);

            return shareId;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create share", e);
            return null;
        }
    }

    /**
     * 创建直接密码分享（无指定用户）
     */
    public String createDirectPasswordShare(int passwordId, int expireInMinutes,
                                           SharePermission permission) {
        // 直接分享与toUserId为null的普通分享相同
        return createPasswordShare(passwordId, null, expireInMinutes, permission);
    }

    /**
     * 创建离线分享
     */
    public String createOfflineShare(int passwordId,
                                    int expireInMinutes, SharePermission permission) {
        try {
            // 获取密码数据
            PasswordItem item = passwordManager.decryptItem(passwordId);
            if (item == null) {
                Log.e(TAG, "Password not found: " + passwordId);
                return null;
            }

            // 使用OfflineShareUtils创建离线分享（版本2：嵌入密钥）
            com.ttt.safevault.utils.OfflineShareUtils.OfflineSharePacket packet =
                com.ttt.safevault.utils.OfflineShareUtils.createOfflineShare(
                    item, expireInMinutes, permission
                );

            if (packet == null) {
                Log.e(TAG, "Failed to create offline share");
                return null;
            }

            Log.d(TAG, "Offline share created successfully");
            return packet.qrContent;

        } catch (Exception e) {
            Log.e(TAG, "Failed to create offline share", e);
            return null;
        }
    }

    /**
     * 接收密码分享
     */
    public PasswordItem receivePasswordShare(String shareId) {
        try {
            PasswordShare share = getShareDetails(shareId);
            if (share == null) {
                Log.e(TAG, "Share not found: " + shareId);
                return null;
            }

            // 验证分享状态
            if (!share.isAvailable()) {
                Log.e(TAG, "Share not available: " + shareId);
                return null;
            }

            // 解密密码数据
            PasswordItem item = decryptPasswordFromShare(share.getEncryptedData());

            // 更新分享状态
            share.setStatus(ShareStatus.ACCEPTED);

            Log.d(TAG, "Share received: " + shareId);
            return item;
        } catch (Exception e) {
            Log.e(TAG, "Failed to receive share", e);
            return null;
        }
    }

    /**
     * 接收离线分享
     */
    public PasswordItem receiveOfflineShare(String qrContent) {
        try {
            // 使用OfflineShareUtils解析离线分享
            PasswordItem item = com.ttt.safevault.utils.OfflineShareUtils.parseOfflineShare(
                qrContent
            );

            if (item == null) {
                Log.e(TAG, "Failed to parse offline share");
                return null;
            }

            Log.d(TAG, "Offline share received successfully");
            return item;

        } catch (Exception e) {
            Log.e(TAG, "Failed to receive offline share", e);
            return null;
        }
    }

    /**
     * 撤销密码分享
     */
    public boolean revokePasswordShare(String shareId) {
        try {
            PasswordShare share = sharesMap.get(shareId);
            if (share == null) {
                return false;
            }

            // 验证所有权
            if (!share.getFromUserId().equals(getCurrentUserId())) {
                Log.e(TAG, "Not authorized to revoke share: " + shareId);
                return false;
            }

            // 验证是否可撤销
            if (!share.getPermission().isRevocable()) {
                Log.e(TAG, "Share is not revocable: " + shareId);
                return false;
            }

            // 更新状态
            share.setStatus(ShareStatus.REVOKED);
            Log.d(TAG, "Share revoked: " + shareId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to revoke share", e);
            return false;
        }
    }

    /**
     * 获取我创建的分享列表
     */
    public List<PasswordShare> getMyShares() {
        List<PasswordShare> myShares = new ArrayList<>();
        String currentUserId = getCurrentUserId();

        for (PasswordShare share : sharesMap.values()) {
            if (currentUserId.equals(share.getFromUserId())) {
                myShares.add(share);
            }
        }

        return myShares;
    }

    /**
     * 获取接收到的分享列表
     */
    public List<PasswordShare> getReceivedShares() {
        List<PasswordShare> receivedShares = new ArrayList<>();
        String currentUserId = getCurrentUserId();

        for (PasswordShare share : sharesMap.values()) {
            if (currentUserId.equals(share.getToUserId()) || share.getToUserId() == null) {
                receivedShares.add(share);
            }
        }

        return receivedShares;
    }

    /**
     * 保存分享的密码到本地
     */
    public int saveSharedPassword(String shareId) {
        try {
            PasswordItem item = receivePasswordShare(shareId);
            if (item == null) {
                return -1;
            }

            // 保存到密码库
            return passwordManager.saveItem(item);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save shared password", e);
            return -1;
        }
    }

    /**
     * 获取分享详情
     */
    public PasswordShare getShareDetails(String shareId) {
        return sharesMap.get(shareId);
    }

    /**
     * 生成分享数据
     */
    public String generateShareData(PasswordItem passwordItem,
                                   String receiverPublicKey,
                                   SharePermission permission) {
        try {
            // 简化实现：直接序列化为JSON（生产环境应使用真正的加密）
            return encryptPasswordForShare(passwordItem);
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate share data", e);
            return null;
        }
    }

    /**
     * 解析分享数据
     */
    public PasswordItem parseShareData(String shareData) {
        try {
            // 简化实现：从JSON解析（生产环境应使用真正的解密）
            return decryptPasswordFromShare(shareData);
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse share data", e);
            return null;
        }
    }

    /**
     * 加密密码用于分享（简化实现）
     */
    private String encryptPasswordForShare(PasswordItem item) {
        // 简化实现：返回JSON字符串
        // 生产环境应使用真正的端到端加密
        return "{\"title\":\"" + (item.getTitle() != null ? item.getTitle() : "") +
               "\",\"username\":\"" + (item.getUsername() != null ? item.getUsername() : "") +
               "\",\"password\":\"" + (item.getPassword() != null ? item.getPassword() : "") +
               "\",\"url\":\"" + (item.getUrl() != null ? item.getUrl() : "") +
               "\",\"notes\":\"" + (item.getNotes() != null ? item.getNotes() : "") + "\"}";
    }

    /**
     * 从分享数据解密密码（简化实现）
     */
    private PasswordItem decryptPasswordFromShare(String encryptedData) {
        // 简化实现：从JSON解析
        try {
            PasswordItem item = new PasswordItem();
            if (encryptedData.contains("\"title\":\"")) {
                String title = extractJsonValue(encryptedData, "title");
                String username = extractJsonValue(encryptedData, "username");
                String password = extractJsonValue(encryptedData, "password");
                String url = extractJsonValue(encryptedData, "url");
                String notes = extractJsonValue(encryptedData, "notes");

                item.setTitle(title);
                item.setUsername(username);
                item.setPassword(password);
                item.setUrl(url);
                item.setNotes(notes);
            }
            return item;
        } catch (Exception e) {
            Log.e(TAG, "Failed to decrypt password from share", e);
            return null;
        }
    }

    /**
     * 从JSON字符串提取值（简化实现）
     */
    private String extractJsonValue(String json, String key) {
        try {
            String searchKey = "\"" + key + "\":\"";
            int startIndex = json.indexOf(searchKey);
            if (startIndex == -1) {
                return "";
            }
            startIndex += searchKey.length();
            int endIndex = json.indexOf("\"", startIndex);
            if (endIndex == -1) {
                return "";
            }
            return json.substring(startIndex, endIndex);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 获取当前用户ID
     */
    private String getCurrentUserId() {
        String userId = prefs.getString(PREF_USER_ID, null);
        if (userId == null) {
            // 创建新用户
            userId = "user_" + UUID.randomUUID().toString();
            prefs.edit().putString(PREF_USER_ID, userId).apply();
        }
        return userId;
    }
}
