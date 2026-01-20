package com.ttt.safevault.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ttt.safevault.data.AppDatabase;
import com.ttt.safevault.data.ShareRecord;
import com.ttt.safevault.data.ShareRecordDao;
import com.ttt.safevault.dto.response.FriendDto;
import com.ttt.safevault.dto.response.ReceivedShareResponse;
import com.ttt.safevault.model.SharePermission;
import com.ttt.safevault.network.RetrofitClient;
import com.ttt.safevault.network.TokenManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 好友详情ViewModel
 * 管理好友详情和分享历史
 */
public class FriendDetailViewModel extends AndroidViewModel {
    private static final String TAG = "FriendDetailViewModel";

    private final RetrofitClient retrofitClient;
    private final TokenManager tokenManager;
    private final CompositeDisposable disposables = new CompositeDisposable();

    // LiveData
    private final MutableLiveData<FriendDetail> friendDetail = new MutableLiveData<>();
    private final MutableLiveData<List<ShareTimelineItem>> shareTimeline = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> operationSuccess = new MutableLiveData<>(false);
    private final MutableLiveData<String> successMessage = new MutableLiveData<>();

    public LiveData<FriendDetail> getFriendDetail() {
        return friendDetail;
    }

    public LiveData<List<ShareTimelineItem>> getShareTimeline() {
        return shareTimeline;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<Boolean> getOperationSuccess() {
        return operationSuccess;
    }

    public LiveData<String> getSuccessMessage() {
        return successMessage;
    }

    public FriendDetailViewModel(@NonNull Application application) {
        super(application);
        this.retrofitClient = RetrofitClient.getInstance(application);
        this.tokenManager = retrofitClient.getTokenManager();
    }

    /**
     * 加载好友详情
     *
     * @param userId 好友用户ID
     */
    public void loadFriendDetail(String userId) {
        if (!tokenManager.isLoggedIn()) {
            errorMessage.setValue("请先登录");
            return;
        }

        isLoading.setValue(true);
        errorMessage.setValue(null);

        Disposable disposable = retrofitClient.getFriendServiceApi()
            .getFriendList()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                friends -> {
                    isLoading.setValue(false);
                    // 从好友列表中查找指定好友
                    FriendDto friendDto = null;
                    for (FriendDto friend : friends) {
                        if (friend.getUserId().equals(userId)) {
                            friendDto = friend;
                            break;
                        }
                    }

                    if (friendDto != null) {
                        FriendDetail detail = new FriendDetail(
                            friendDto.getUserId(),
                            friendDto.getUsername(),
                            friendDto.getDisplayName(),
                            friendDto.getPublicKey(),
                            friendDto.getAddedAt(),
                            friendDto.getIsOnline() != null ? friendDto.getIsOnline() : false
                        );
                        friendDetail.setValue(detail);
                        Log.d(TAG, "Loaded friend detail: " + userId);
                    } else {
                        errorMessage.setValue("未找到该好友");
                    }
                },
                error -> {
                    isLoading.setValue(false);
                    String message = "加载好友详情失败: " + error.getMessage();
                    errorMessage.setValue(message);
                    Log.e(TAG, "Failed to load friend detail", error);
                }
            );

        disposables.add(disposable);
    }

    /**
     * 加载与该好友的分享时间线（云端+本地合并）
     *
     * @param userId 好友用户ID
     */
    public void loadShareTimeline(String userId) {
        if (!tokenManager.isLoggedIn()) {
            errorMessage.setValue("请先登录");
            return;
        }

        isLoading.setValue(true);
        errorMessage.setValue(null);
        shareTimeline.setValue(new ArrayList<>());

        // 获取云端分享和本地分享，然后合并
        Disposable disposable = Observable.zip(
            // 获取我创建的云端分享
            retrofitClient.getShareServiceApi().getMyShares(),
            // 获取我接收的云端分享
            retrofitClient.getShareServiceApi().getReceivedShares(),
            // 获取本地分享记录
            Observable.fromCallable(() -> {
                ShareRecordDao dao = AppDatabase.getInstance(getApplication()).shareRecordDao();
                return dao.getSharesByContactId(userId);
            }),
            // 合并三个数据源
            (myShares, receivedShares, localShares) -> {
                // 合并云端和本地数据
                return mergeShareTimeline(myShares, receivedShares, localShares);
            }
        )
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
            timeline -> {
                isLoading.setValue(false);
                shareTimeline.setValue(timeline);
                Log.d(TAG, "Loaded " + timeline.size() + " share timeline items for user: " + userId);
            },
            error -> {
                isLoading.setValue(false);
                String message = "加载分享历史失败: " + error.getMessage();
                errorMessage.setValue(message);
                Log.e(TAG, "Failed to load share timeline", error);
            }
        );

        disposables.add(disposable);
    }

    /**
     * 合并云端和本地的分享记录
     */
    private List<ShareTimelineItem> mergeShareTimeline(
            List<ReceivedShareResponse> cloudMyShares,
            List<ReceivedShareResponse> cloudReceivedShares,
            List<ShareRecord> localShares) {

        Map<String, ShareTimelineItem> mergedMap = new LinkedHashMap<>();

        // 1. 添加云端记录 - 我创建的分享
        for (ReceivedShareResponse cloudShare : cloudMyShares) {
            ShareTimelineItem item = new ShareTimelineItem();
            item.shareId = cloudShare.getShareId();
            item.type = Type.SENT;
            item.passwordTitle = cloudShare.getTitle() != null
                ? cloudShare.getTitle()
                : "未知密码";
            item.passwordUsername = cloudShare.getUsername() != null
                ? cloudShare.getUsername()
                : "";
            item.timestamp = cloudShare.getCreatedAt();
            item.status = mapShareStatus(cloudShare.getStatus());
            item.permission = cloudShare.getPermission();
            item.isFromCloud = true;

            mergedMap.put(item.shareId, item);
        }

        // 2. 添加云端记录 - 我接收的分享
        for (ReceivedShareResponse cloudShare : cloudReceivedShares) {
            ShareTimelineItem item = new ShareTimelineItem();
            item.shareId = cloudShare.getShareId();
            item.type = Type.RECEIVED;
            item.passwordTitle = cloudShare.getTitle() != null
                ? cloudShare.getTitle()
                : "未知密码";
            item.passwordUsername = cloudShare.getUsername() != null
                ? cloudShare.getUsername()
                : "";
            item.timestamp = cloudShare.getCreatedAt();
            item.status = mapShareStatus(cloudShare.getStatus());
            item.permission = cloudShare.getPermission();
            item.isFromCloud = true;

            mergedMap.put(item.shareId, item);
        }

        // 3. 添加本地记录（云端没有的）
        for (ShareRecord localShare : localShares) {
            if (!mergedMap.containsKey(localShare.shareId)) {
                ShareTimelineItem item = new ShareTimelineItem();
                item.shareId = localShare.shareId;
                item.type = "sent".equals(localShare.type)
                    ? Type.SENT
                    : Type.RECEIVED;
                item.passwordTitle = "本地记录";
                item.passwordUsername = "";
                item.timestamp = localShare.createdAt;
                item.status = ShareStatus.ACTIVE;
                item.isFromCloud = false;

                mergedMap.put(item.shareId, item);
            }
        }

        // 4. 按时间排序
        List<ShareTimelineItem> result = new ArrayList<>(mergedMap.values());
        result.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));

        return result;
    }

    /**
     * 映射分享状态
     */
    private ShareStatus mapShareStatus(String status) {
        if (status == null) return ShareStatus.ACTIVE;

        switch (status.toUpperCase()) {
            case "PENDING": return ShareStatus.PENDING;
            case "ACTIVE": return ShareStatus.ACTIVE;
            case "ACCEPTED": return ShareStatus.ACCEPTED;
            case "EXPIRED": return ShareStatus.EXPIRED;
            case "REVOKED": return ShareStatus.REVOKED;
            default: return ShareStatus.ACTIVE;
        }
    }

    /**
     * 撤销分享
     *
     * @param shareId 分享ID
     */
    public void revokeShare(String shareId) {
        if (!tokenManager.isLoggedIn()) {
            errorMessage.setValue("请先登录");
            return;
        }

        isLoading.setValue(true);
        errorMessage.setValue(null);
        operationSuccess.setValue(false);

        Disposable disposable = retrofitClient.getShareServiceApi()
            .revokeShare(shareId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                response -> {
                    isLoading.setValue(false);
                    operationSuccess.setValue(true);
                    successMessage.setValue("已撤销分享");
                    Log.d(TAG, "Revoked share: " + shareId);

                    // 刷新时间线
                    FriendDetail currentDetail = friendDetail.getValue();
                    if (currentDetail != null) {
                        loadShareTimeline(currentDetail.userId);
                    }
                },
                error -> {
                    isLoading.setValue(false);
                    String message = "撤销失败: " + error.getMessage();
                    errorMessage.setValue(message);
                    Log.e(TAG, "Failed to revoke share", error);
                }
            );

        disposables.add(disposable);
    }

    /**
     * 清除错误消息
     */
    public void clearError() {
        errorMessage.setValue(null);
    }

    /**
     * 清除成功消息
     */
    public void clearSuccess() {
        operationSuccess.setValue(false);
        successMessage.setValue(null);
    }

    /**
     * 检查是否已登录
     */
    public boolean isLoggedIn() {
        return tokenManager.isLoggedIn();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        disposables.clear();
    }

    /**
     * 好友详情数据类
     */
    public static class FriendDetail {
        public final String userId;
        public final String username;
        public final String displayName;
        public final String publicKey;
        public final long addedAt;
        public final boolean isOnline;

        public FriendDetail(String userId, String username, String displayName,
                           String publicKey, long addedAt, boolean isOnline) {
            this.userId = userId;
            this.username = username;
            this.displayName = displayName;
            this.publicKey = publicKey;
            this.addedAt = addedAt;
            this.isOnline = isOnline;
        }
    }

    /**
     * 分享时间线项数据类
     */
    public static class ShareTimelineItem {
        public String shareId;
        public String passwordTitle;
        public String passwordUsername;
        public Type type; // SENT 或 RECEIVED
        public long timestamp;
        public ShareStatus status;
        public SharePermission permission; // 修改为SharePermission类型
        public boolean isFromCloud; // true=云端记录，false=本地记录

        // 旧的兼容字段（已废弃，使用timestamp）
        @Deprecated
        public long createdAt;

        // 旧的兼容字段（已废弃，使用type）
        @Deprecated
        public String direction;

        // 旧的兼容构造方法
        @Deprecated
        public ShareTimelineItem(String shareId, String passwordTitle, String direction,
                                long createdAt, long expireTime, String status) {
            this.shareId = shareId;
            this.passwordTitle = passwordTitle;
            this.direction = direction;
            this.createdAt = createdAt;
            this.timestamp = createdAt;
            this.type = "SENT".equals(direction) ? Type.SENT : Type.RECEIVED;
            this.status = mapStringToStatus(status);
            this.isFromCloud = true;
            this.permission = new SharePermission(); // 默认权限
        }

        public ShareTimelineItem() {
            this.permission = new SharePermission(); // 默认权限
        }

        private static ShareStatus mapStringToStatus(String status) {
            if (status == null) return ShareStatus.ACTIVE;
            switch (status.toUpperCase()) {
                case "PENDING": return ShareStatus.PENDING;
                case "ACTIVE": return ShareStatus.ACTIVE;
                case "ACCEPTED": return ShareStatus.ACCEPTED;
                case "EXPIRED": return ShareStatus.EXPIRED;
                case "REVOKED": return ShareStatus.REVOKED;
                default: return ShareStatus.ACTIVE;
            }
        }

        /**
         * 检查分享是否已过期
         */
        public boolean isExpired() {
            return status == ShareStatus.EXPIRED;
        }

        /**
         * 获取状态描述
         */
        public String getStatusDescription() {
            if (status == null) return "未知";
            switch (status) {
                case PENDING: return "待处理";
                case ACTIVE: return "活跃";
                case ACCEPTED: return "已接受";
                case EXPIRED: return "已过期";
                case REVOKED: return "已撤销";
                default: return "未知";
            }
        }

        /**
         * 获取方向描述
         */
        public String getDirectionDescription() {
            return type == Type.SENT ? "已发送" : "已接收";
        }
    }

    /**
     * 分享类型枚举
     */
    public enum Type {
        SENT,      // 已发送
        RECEIVED   // 已接收
    }

    /**
     * 分享状态枚举
     */
    public enum ShareStatus {
        PENDING,    // 待处理
        ACTIVE,     // 活跃
        ACCEPTED,   // 已接受
        EXPIRED,    // 已过期
        REVOKED     // 已撤销
    }
}
