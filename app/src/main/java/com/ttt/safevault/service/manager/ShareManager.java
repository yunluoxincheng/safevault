package com.ttt.safevault.service.manager;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.ttt.safevault.crypto.ShareEncryptionManager;
import com.ttt.safevault.crypto.CryptoConstants;
import com.ttt.safevault.dto.PasswordData;
import com.ttt.safevault.dto.request.AcceptShareRequest;
import com.ttt.safevault.dto.request.CreateShareRequest;
import com.ttt.safevault.dto.request.RejectShareRequest;
import com.ttt.safevault.dto.response.ReceivedShareResponse;
import com.ttt.safevault.dto.response.ShareResponse;
import com.ttt.safevault.dto.response.UserKeyInfoResponse;
import com.ttt.safevault.model.EncryptedSharePacket;
import com.ttt.safevault.model.EncryptedSharePacketV3;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.model.ShareDataPacket;
import com.ttt.safevault.model.SharePermission;
import com.ttt.safevault.model.UserKeyInfo;
import com.ttt.safevault.network.RetrofitClient;
import com.ttt.safevault.network.api.ShareServiceApi;
import com.ttt.safevault.network.api.UserServiceApi;
import com.ttt.safevault.security.SecureKeyStorageManager;
import com.ttt.safevault.security.SessionGuard;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 分享功能统一管理器
 * 封装云端分享与离线分享的端到端加密逻辑，委托给 ShareEncryptionManager 与 ShareServiceApi
 */
public class ShareManager {
    private static final String TAG = "ShareManager";

    private final Context context;
    private final RetrofitClient retrofitClient;
    private final ShareEncryptionManager encryptionManager;
    private final PasswordManager passwordManager;
    private final SessionGuard sessionGuard;
    private final SecureKeyStorageManager secureKeyStorage;
    private final Gson gson = new Gson();

    public ShareManager(
            @NonNull Context context,
            @NonNull RetrofitClient retrofitClient,
            @NonNull PasswordManager passwordManager
    ) {
        this.context = context.getApplicationContext();
        this.retrofitClient = retrofitClient;
        this.encryptionManager = new ShareEncryptionManager();
        this.encryptionManager.setContext(this.context);
        this.passwordManager = passwordManager;
        this.sessionGuard = SessionGuard.getInstance();
        this.secureKeyStorage = SecureKeyStorageManager.getInstance(this.context);
    }

    /**
     * 创建云端加密分享（v2.0 或 v3.0 协议自动协商）
     */
    @Nullable
    public com.ttt.safevault.dto.response.ShareResponse createCloudShare(
            int passwordId,
            String toUserId,
            int expireInMinutes,
            SharePermission permission
    ) {
        try {
            PasswordItem item = passwordManager.decryptItem(passwordId);
            if (item == null) {
                Log.e(TAG, "Password not found: " + passwordId);
                return null;
            }

            String senderUserId = retrofitClient.getTokenManager().getUserId();
            if (senderUserId == null || senderUserId.isEmpty()) {
                Log.e(TAG, "Not logged in");
                return null;
            }

            UserKeyInfo receiverKeyInfo = fetchUserKeyInfo(toUserId);
            if (receiverKeyInfo == null) {
                Log.e(TAG, "Could not get receiver keys for: " + toUserId);
                return null;
            }

            UserKeyInfo senderKeyInfo = buildSenderKeyInfo(senderUserId);
            if (senderKeyInfo == null) {
                Log.e(TAG, "Could not build sender key info");
                return null;
            }

            long expireAt = expireInMinutes > 0
                    ? System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(expireInMinutes)
                    : 0L;

            ShareDataPacket data = new ShareDataPacket(
                    senderUserId,
                    senderKeyInfo.getRsaPublicKey() != null ? senderKeyInfo.getRsaPublicKey() : "",
                    item,
                    permission != null ? permission : new SharePermission(true, true, true)
            );
            data.version = CryptoConstants.PROTOCOL_VERSION_V2;
            data.createdAt = System.currentTimeMillis();
            data.expireAt = expireAt;

            SecretKey dataKey = sessionGuard.getDataKey();
            if (dataKey == null) {
                Log.e(TAG, "Session locked");
                return null;
            }

            PrivateKey senderRsaPrivate = secureKeyStorage.decryptRsaPrivateKey(dataKey);
            PrivateKey senderEd25519Private = null;
            try {
                if (secureKeyStorage.getEd25519PublicKeyBase64() != null) {
                    senderEd25519Private = secureKeyStorage.decryptEd25519PrivateKey(dataKey);
                }
            } catch (Exception e) {
                Log.d(TAG, "No Ed25519 key, will use v2 only", e);
            }

            Object encryptedPacket = encryptionManager.createEncryptedPacketAuto(
                    data,
                    senderKeyInfo,
                    receiverKeyInfo,
                    senderEd25519Private,
                    senderRsaPrivate,
                    context
            );
            if (encryptedPacket == null) {
                Log.e(TAG, "Encryption failed");
                return null;
            }

            String encryptedPayload = encryptedPacket instanceof EncryptedSharePacketV3
                    ? gson.toJson(encryptedPacket)
                    : gson.toJson(encryptedPacket);

            CreateShareRequest request = new CreateShareRequest();
            request.setPasswordId(String.valueOf(passwordId));
            request.setTitle(item.getTitle());
            request.setUsername(item.getUsername());
            request.setEncryptedPassword(encryptedPayload);
            request.setUrl(item.getUrl() != null ? item.getUrl() : "");
            request.setNotes(item.getNotes() != null ? item.getNotes() : "");
            request.setToUserId(toUserId);
            request.setExpireInMinutes(expireInMinutes);
            request.setPermission(permission != null ? permission : new SharePermission(true, true, true));

            ShareServiceApi api = retrofitClient.getShareServiceApi();
            com.ttt.safevault.dto.response.ShareResponse response = api.createShare(request).blockingFirst();
            return response;
        } catch (Exception e) {
            Log.e(TAG, "createCloudShare failed", e);
            return null;
        }
    }

    /**
     * 接收云端分享并解密
     */
    @Nullable
    public ReceivedShareResponse receiveCloudShare(String shareId) {
        try {
            ShareServiceApi api = retrofitClient.getShareServiceApi();
            ReceivedShareResponse response = api.receiveShare(shareId).blockingFirst();
            if (response == null) return null;

            String encryptedPayload = response.getPasswordData() != null
                    ? response.getPasswordData().getPassword() : null;
            if (encryptedPayload == null || encryptedPayload.isEmpty()) {
                return response;
            }

            try {
                JsonObject json = gson.fromJson(encryptedPayload, JsonObject.class);
                String version = json.has("version") ? json.get("version").getAsString() : "2.0";
                String receiverUserId = retrofitClient.getTokenManager().getUserId();
                if (receiverUserId == null) return response;

                SecretKey dataKey = sessionGuard.getDataKey();
                if (dataKey == null) return response;

                ShareDataPacket data = null;
                if (CryptoConstants.PROTOCOL_VERSION_V3.equals(version)) {
                    EncryptedSharePacketV3 packetV3 = gson.fromJson(encryptedPayload, EncryptedSharePacketV3.class);
                    if (packetV3 != null && packetV3.isValid()) {
                        PrivateKey receiverX25519 = secureKeyStorage.decryptX25519PrivateKey(dataKey);
                        UserKeyInfo senderKeyInfo = fetchUserKeyInfo(packetV3.getSenderId());
                        if (senderKeyInfo != null && senderKeyInfo.getEd25519PublicKey() != null) {
                            PublicKey senderEd25519 = parseEd25519PublicKey(senderKeyInfo.getEd25519PublicKey());
                            if (senderEd25519 != null) {
                                data = encryptionManager.openEncryptedPacketV3(
                                        packetV3,
                                        receiverX25519,
                                        senderEd25519,
                                        packetV3.getSenderId(),
                                        receiverUserId,
                                        context
                                );
                            }
                        }
                    }
                }
                if (data == null) {
                    EncryptedSharePacket packet = gson.fromJson(encryptedPayload, EncryptedSharePacket.class);
                    if (packet != null && packet.isValid()) {
                        PrivateKey receiverRsa = secureKeyStorage.decryptRsaPrivateKey(dataKey);
                        UserKeyInfo senderKeyInfo = fetchUserKeyInfo(packet.getSenderId());
                        if (senderKeyInfo != null && senderKeyInfo.getRsaPublicKey() != null) {
                            PublicKey senderRsa = parseRsaPublicKey(senderKeyInfo.getRsaPublicKey());
                            if (senderRsa != null) {
                                data = encryptionManager.openEncryptedPacket(packet, receiverRsa, senderRsa);
                            }
                        }
                    }
                }

                if (data != null && data.password != null) {
                    PasswordData pd = new PasswordData();
                    pd.setTitle(data.password.getTitle());
                    pd.setUsername(data.password.getUsername());
                    pd.setPassword(data.password.getPassword());
                    pd.setUrl(data.password.getUrl());
                    pd.setNotes(data.password.getNotes());
                    response.setPasswordData(pd);
                    if (data.permission != null) {
                        response.setPermission(data.permission);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Decrypt share payload failed, returning opaque response", e);
            }
            return response;
        } catch (Exception e) {
            Log.e(TAG, "receiveCloudShare failed", e);
            return null;
        }
    }

    /**
     * 接受云端分享
     */
    public boolean acceptCloudShare(String shareId) {
        try {
            retrofitClient.getShareServiceApi()
                    .acceptShare(shareId, new AcceptShareRequest())
                    .blockingFirst();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "acceptCloudShare failed", e);
            return false;
        }
    }

    /**
     * 拒绝云端分享
     */
    public boolean rejectCloudShare(String shareId) {
        try {
            retrofitClient.getShareServiceApi()
                    .rejectShare(shareId, new RejectShareRequest())
                    .blockingFirst();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "rejectCloudShare failed", e);
            return false;
        }
    }

    /**
     * 撤销云端分享
     */
    public boolean revokeCloudShare(String shareId) {
        try {
            retrofitClient.getShareServiceApi().revokeShare(shareId).blockingFirst();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "revokeCloudShare failed", e);
            return false;
        }
    }

    /**
     * 保存云端分享到本地
     */
    public boolean saveCloudShare(String shareId) {
        try {
            retrofitClient.getShareServiceApi().saveSharedPassword(shareId).blockingFirst();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "saveCloudShare failed", e);
            return false;
        }
    }

    /**
     * 获取我创建的云端分享列表
     */
    public List<ReceivedShareResponse> getMyCloudShares() {
        try {
            List<ReceivedShareResponse> list = retrofitClient.getShareServiceApi().getMyShares().blockingFirst();
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            Log.e(TAG, "getMyCloudShares failed", e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取我接收的云端分享列表
     */
    public List<ReceivedShareResponse> getReceivedCloudShares() {
        try {
            List<ReceivedShareResponse> list = retrofitClient.getShareServiceApi().getReceivedShares().blockingFirst();
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            Log.e(TAG, "getReceivedCloudShares failed", e);
            return Collections.emptyList();
        }
    }

    /**
     * 创建离线分享（返回 QR 码内容字符串）
     */
    @Nullable
    public String createOfflineShare(int passwordId, int expireInMinutes, SharePermission permission) {
        try {
            PasswordItem item = passwordManager.decryptItem(passwordId);
            if (item == null) return null;

            long expireAt = expireInMinutes > 0
                    ? System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(expireInMinutes)
                    : 0L;

            String senderUserId = retrofitClient.getTokenManager().getUserId();
            if (senderUserId == null) senderUserId = "offline";

            ShareDataPacket data = new ShareDataPacket(
                    senderUserId,
                    secureKeyStorage.getRsaPublicKeyBase64() != null ? secureKeyStorage.getRsaPublicKeyBase64() : "",
                    item,
                    permission != null ? permission : new SharePermission(true, true, true)
            );
            data.version = CryptoConstants.PROTOCOL_VERSION_V2;
            data.createdAt = System.currentTimeMillis();
            data.expireAt = expireAt;

            SecretKey dataKey = sessionGuard.getDataKey();
            if (dataKey == null) return null;

            PrivateKey senderRsa = secureKeyStorage.decryptRsaPrivateKey(dataKey);
            PublicKey receiverRsa = secureKeyStorage.getRsaPublicKey();
            if (receiverRsa == null) return null;

            EncryptedSharePacket packet = encryptionManager.createEncryptedPacket(data, receiverRsa, senderRsa);
            if (packet == null) return null;

            return gson.toJson(packet);
        } catch (Exception e) {
            Log.e(TAG, "createOfflineShare failed", e);
            return null;
        }
    }

    /**
     * 接收离线分享并解密
     */
    @Nullable
    public PasswordItem receiveOfflineShare(String encryptedData) {
        if (encryptedData == null || encryptedData.isEmpty()) return null;
        try {
            JsonObject json = gson.fromJson(encryptedData, JsonObject.class);
            String version = json.has("version") ? json.get("version").getAsString() : "2.0";
            SecretKey dataKey = sessionGuard.getDataKey();
            if (dataKey == null) return null;

            if (CryptoConstants.PROTOCOL_VERSION_V3.equals(version)) {
                EncryptedSharePacketV3 packetV3 = gson.fromJson(encryptedData, EncryptedSharePacketV3.class);
                if (packetV3 != null && packetV3.isValid()) {
                    PrivateKey receiverX25519 = secureKeyStorage.decryptX25519PrivateKey(dataKey);
                    UserKeyInfo senderKeyInfo = fetchUserKeyInfo(packetV3.getSenderId());
                    if (senderKeyInfo != null && senderKeyInfo.getEd25519PublicKey() != null) {
                        PublicKey senderEd25519 = parseEd25519PublicKey(senderKeyInfo.getEd25519PublicKey());
                        if (senderEd25519 != null) {
                            ShareDataPacket data = encryptionManager.openEncryptedPacketV3(
                                    packetV3,
                                    receiverX25519,
                                    senderEd25519,
                                    packetV3.getSenderId(),
                                    retrofitClient.getTokenManager().getUserId() != null
                                            ? retrofitClient.getTokenManager().getUserId() : "offline",
                                    context
                            );
                            return data != null ? data.password : null;
                        }
                    }
                }
            }

            EncryptedSharePacket packet = gson.fromJson(encryptedData, EncryptedSharePacket.class);
            if (packet == null || !packet.isValid()) return null;
            PrivateKey receiverRsa = secureKeyStorage.decryptRsaPrivateKey(dataKey);
            UserKeyInfo senderKeyInfo = fetchUserKeyInfo(packet.getSenderId());
            if (senderKeyInfo == null || senderKeyInfo.getRsaPublicKey() == null) return null;
            PublicKey senderRsa = parseRsaPublicKey(senderKeyInfo.getRsaPublicKey());
            if (senderRsa == null) return null;
            ShareDataPacket data = encryptionManager.openEncryptedPacket(packet, receiverRsa, senderRsa);
            return data != null ? data.password : null;
        } catch (Exception e) {
            Log.e(TAG, "receiveOfflineShare failed", e);
            return null;
        }
    }

    @Nullable
    private UserKeyInfo fetchUserKeyInfo(String userId) {
        if (userId == null || userId.isEmpty()) return null;
        try {
            UserServiceApi api = retrofitClient.getUserServiceApi();
            UserKeyInfoResponse resp = api.getUserKeys(userId).blockingFirst();
            if (resp == null) return null;
            UserKeyInfo info = new UserKeyInfo();
            info.setUserId(resp.getUserId());
            info.setRsaPublicKey(resp.getRsaPublicKey());
            info.setX25519PublicKey(resp.getX25519PublicKey());
            info.setEd25519PublicKey(resp.getEd25519PublicKey());
            info.setKeyVersion(resp.getKeyVersion() != null ? resp.getKeyVersion() : "v2");
            return info;
        } catch (Exception e) {
            Log.w(TAG, "fetchUserKeyInfo failed for " + userId, e);
            return null;
        }
    }

    @Nullable
    private UserKeyInfo buildSenderKeyInfo(String senderUserId) {
        UserKeyInfo info = new UserKeyInfo();
        info.setUserId(senderUserId);
        info.setRsaPublicKey(secureKeyStorage.getRsaPublicKeyBase64());
        info.setKeyVersion("v2");
        try {
            if (secureKeyStorage.getX25519PublicKeyBase64() != null) {
                info.setX25519PublicKey(secureKeyStorage.getX25519PublicKeyBase64());
            }
            if (secureKeyStorage.getEd25519PublicKeyBase64() != null) {
                info.setEd25519PublicKey(secureKeyStorage.getEd25519PublicKeyBase64());
            }
            if (info.getX25519PublicKey() != null && info.getEd25519PublicKey() != null) {
                info.setKeyVersion("v3");
            }
        } catch (Exception e) {
            Log.d(TAG, "Sender v3 keys not available", e);
        }
        return info;
    }

    @Nullable
    private PublicKey parseRsaPublicKey(String base64) {
        try {
            byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
            java.security.spec.X509EncodedKeySpec spec = new java.security.spec.X509EncodedKeySpec(bytes);
            return java.security.KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            Log.e(TAG, "parseRsaPublicKey failed", e);
            return null;
        }
    }

    @Nullable
    private PublicKey parseEd25519PublicKey(String base64) {
        try {
            byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
            java.security.spec.X509EncodedKeySpec spec = new java.security.spec.X509EncodedKeySpec(bytes);
            return java.security.KeyFactory.getInstance("EdDSA").generatePublic(spec);
        } catch (Exception e) {
            Log.e(TAG, "parseEd25519PublicKey failed", e);
            return null;
        }
    }
}
