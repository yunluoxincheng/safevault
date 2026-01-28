package com.ttt.safevault.service;

import android.content.Context;
import android.util.Log;

import com.ttt.safevault.data.AppDatabase;
import com.ttt.safevault.data.Contact;
import com.ttt.safevault.data.ContactDao;
import com.ttt.safevault.dto.response.FriendDto;
import com.ttt.safevault.exception.AuthenticationException;
import com.ttt.safevault.exception.NetworkException;
import com.ttt.safevault.exception.TokenExpiredException;
import com.ttt.safevault.network.RetrofitClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import retrofit2.HttpException;

/**
 * 联系人同步管理器
 * 负责云端好友列表和本地Contact表的同步
 */
public class ContactSyncManager {
    private static final String TAG = "ContactSyncManager";
    private static final String PREFS_NAME = "contact_sync";
    private static final String KEY_LAST_SYNC = "last_sync_time";

    private final Context context;
    private final ContactDao contactDao;
    private final ExecutorService executor;
    private final RetrofitClient retrofitClient;

    public ContactSyncManager(Context context) {
        this.context = context.getApplicationContext();
        this.contactDao = AppDatabase.getInstance(context).contactDao();
        this.executor = Executors.newSingleThreadExecutor();
        this.retrofitClient = RetrofitClient.getInstance(context);
    }

    /**
     * 完整同步好友列表
     * @return Observable<SyncResult> 同步结果
     */
    public Observable<SyncResult> syncContacts() {
        return Observable.zip(
            // 1. 获取云端好友
            getCloudFriends(),
            // 2. 获取本地联系人
            Observable.fromCallable(() -> contactDao.getAllContacts()),
            (cloudFriends, localContacts) -> {
                SyncResult result = new SyncResult();
                result.setLocalContacts(localContacts);
                result.setCloudFriends(cloudFriends);
                return result;
            }
        )
        .map(this::processSyncDifferences)
        .doOnNext(result -> saveSyncResults(result))
        .subscribeOn(Schedulers.io());
    }

    /**
     * 获取云端好友列表（带重试机制）
     * 错误会传播到调用者，由调用者统一处理
     */
    private Observable<List<FriendDto>> getCloudFriends() {
        return retrofitClient.getFriendServiceApi()
            .getFriendList()
            .retry(3); // 网络失败重试3次
    }

    /**
     * 处理同步差异
     */
    private SyncResult processSyncDifferences(SyncResult result) {
        List<FriendDto> cloudFriends = result.getCloudFriends();
        List<Contact> localContacts = result.getLocalContacts();

        List<FriendDto> toAdd = new ArrayList<>();  // 需要添加到本地
        List<Contact> toUpload = new ArrayList<>();  // 需要上传到云端
        List<Contact> toUpdate = new ArrayList<>();   // 需要更新

        // 构建本地联系人映射（按cloudUserId）
        Map<String, Contact> localMap = new HashMap<>();
        for (Contact contact : localContacts) {
            if (contact.cloudUserId != null && !contact.cloudUserId.isEmpty()) {
                localMap.put(contact.cloudUserId, contact);
            }
        }

        // 找出差异
        for (FriendDto cloudFriend : cloudFriends) {
            Contact localContact = localMap.get(cloudFriend.getUserId());

            if (localContact == null) {
                // 云端有，本地没有 → 添加到本地
                toAdd.add(cloudFriend);
            } else {
                // 两边都有 → 检查是否需要更新
                if (shouldUpdateContact(localContact, cloudFriend)) {
                    localContact.displayName = cloudFriend.getDisplayName();
                    localContact.publicKey = cloudFriend.getPublicKey();
                    toUpdate.add(localContact);
                }
            }
        }

        // 找出本地有但云端没有的（可能是本地添加的联系人，不是好友）
        // 这些不需要上传，因为好友关系必须从云端建立

        result.setToAdd(toAdd);
        result.setToUpdate(toUpdate);
        result.setToUpload(toUpload);

        Log.i(TAG, "Sync result: add=" + toAdd.size() +
                   ", update=" + toUpdate.size() +
                   ", upload=" + toUpload.size());

        return result;
    }

    /**
     * 判断是否需要更新联系人
     */
    private boolean shouldUpdateContact(Contact local, FriendDto cloud) {
        // 如果显示名称或公钥不同，需要更新
        return !java.util.Objects.equals(local.displayName, cloud.getDisplayName()) ||
               !java.util.Objects.equals(local.publicKey, cloud.getPublicKey());
    }

    /**
     * 保存同步结果
     */
    private void saveSyncResults(SyncResult result) {
        // 1. 添加新联系人
        for (FriendDto friend : result.getToAdd()) {
            Contact contact = new Contact();
            contact.contactId = generateContactId();
            contact.cloudUserId = friend.getUserId();
            contact.userId = ""; // 本地派生ID留空
            contact.username = friend.getUsername() != null ? friend.getUsername() : "";
            // displayName 是 @NonNull 字段，需要提供默认值
            contact.displayName = friend.getDisplayName() != null && !friend.getDisplayName().isEmpty()
                    ? friend.getDisplayName()
                    : friend.getUsername();
            // publicKey 是 @NonNull 字段，需要提供默认值
            contact.publicKey = friend.getPublicKey() != null ? friend.getPublicKey() : "";
            contact.myNote = "";
            contact.addedAt = friend.getAddedAt() != null ? friend.getAddedAt() : System.currentTimeMillis();
            contact.lastUsedAt = 0;

            contactDao.insertContact(contact);
            Log.d(TAG, "Inserted contact: " + contact.displayName + " (cloudUserId: " + contact.cloudUserId + ")");
        }

        // 2. 更新现有联系人
        for (Contact contact : result.getToUpdate()) {
            contactDao.updateContact(contact);
            Log.d(TAG, "Updated contact: " + contact.displayName);
        }

        // 3. 保存最后同步时间
        saveLastSyncTime();
    }

    /**
     * 自动同步（后台执行）
     */
    public void autoSyncIfNeeded() {
        long lastSync = getLastSyncTime();
        long now = System.currentTimeMillis();

        // 如果超过1小时未同步，自动同步
        if (now - lastSync > 3600000) {
            syncContacts()
                .subscribe(
                    result -> Log.i(TAG, "Auto sync completed"),
                    error -> Log.e(TAG, "Auto sync failed", error)
                );
        }
    }

    private long getLastSyncTime() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_SYNC, 0);
    }

    private void saveLastSyncTime() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                .apply();
    }

    private String generateContactId() {
        return "contact_" + java.util.UUID.randomUUID().toString();
    }

    /**
     * 同步结果
     */
    public static class SyncResult {
        private List<Contact> localContacts;
        private List<FriendDto> cloudFriends;
        private List<FriendDto> toAdd;
        private List<Contact> toUpload;
        private List<Contact> toUpdate;

        // Getters and Setters
        public List<Contact> getLocalContacts() { return localContacts; }
        public void setLocalContacts(List<Contact> value) { localContacts = value; }

        public List<FriendDto> getCloudFriends() { return cloudFriends; }
        public void setCloudFriends(List<FriendDto> value) { cloudFriends = value; }

        public List<FriendDto> getToAdd() { return toAdd; }
        public void setToAdd(List<FriendDto> value) { toAdd = value; }

        public List<Contact> getToUpload() { return toUpload; }
        public void setToUpload(List<Contact> value) { toUpload = value; }

        public List<Contact> getToUpdate() { return toUpdate; }
        public void setToUpdate(List<Contact> value) { toUpdate = value; }
    }
}
