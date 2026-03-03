package com.ttt.safevault.ui.friend;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ttt.safevault.R;
import com.ttt.safevault.adapter.FriendRequestAdapter;
import com.ttt.safevault.data.AppDatabase;
import com.ttt.safevault.data.Contact;
import com.ttt.safevault.data.FriendRequest;
import com.ttt.safevault.dto.request.RespondFriendRequestRequest;
import com.ttt.safevault.dto.response.FriendRequestDto;
import com.ttt.safevault.exception.AuthenticationException;
import com.ttt.safevault.exception.TokenExpiredException;
import com.ttt.safevault.network.RetrofitClient;
import com.ttt.safevault.network.api.FriendServiceApi;
import com.ttt.safevault.network.TokenManager;
import com.ttt.safevault.security.SafetyNumberManager;
import com.ttt.safevault.service.ContactSyncManager;
import com.ttt.safevault.ui.share.SafetyNumberVerificationDialog;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 好友请求列表界面
 * 显示待处理的好友请求，支持同意/拒绝操作
 */
public class FriendRequestListActivity extends AppCompatActivity {

    private static final String TAG = "FriendRequestListActivity";

    private MaterialToolbar toolbar;
    private androidx.recyclerview.widget.RecyclerView recyclerView;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshLayout;
    private View emptyView;

    private FriendRequestAdapter adapter;
    private CompositeDisposable disposables = new CompositeDisposable();
    private FriendServiceApi friendServiceApi;
    private TokenManager tokenManager;

    private boolean markAsRead = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend_request_list);

        // 防止截屏
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE);

        // 检查是否需要标记为已读
        markAsRead = getIntent().getBooleanExtra("mark_as_read", false);

        initViews();
        initNetwork();
        loadPendingRequests();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));

        adapter = new FriendRequestAdapter();
        adapter.setOnActionListener(new FriendRequestAdapter.OnActionListener() {
            @Override
            public void onAccept(FriendRequest request) {
                showConfirmDialog(request, true);
            }

            @Override
            public void onReject(FriendRequest request) {
                showConfirmDialog(request, false);
            }
        });
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(this::loadPendingRequests);

        emptyView = findViewById(R.id.empty_view);
    }

    private void initNetwork() {
        tokenManager = new TokenManager(this);
        friendServiceApi = RetrofitClient.getClient(tokenManager).create(FriendServiceApi.class);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPendingRequests();
    }

    private void loadPendingRequests() {
        swipeRefreshLayout.setRefreshing(true);

        disposables.add(
            friendServiceApi.getPendingRequests()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    this::handleRequestsSuccess,
                    this::handleRequestsError
                )
        );
    }

    private void handleRequestsSuccess(List<FriendRequestDto> dtos) {
        swipeRefreshLayout.setRefreshing(false);

        List<FriendRequest> requests = new ArrayList<>();
        for (FriendRequestDto dto : dtos) {
            FriendRequest request = new FriendRequest();
            request.requestId = dto.getRequestId();
            request.fromUserId = dto.getFromUserId();
            request.fromUsername = dto.getFromUsername();
            request.fromDisplayName = dto.getFromDisplayName();
            request.message = dto.getMessage();
            request.status = dto.getStatus();
            request.createdAt = dto.getCreatedAt() != null ? dto.getCreatedAt() : System.currentTimeMillis();
            request.respondedAt = dto.getRespondedAt() != null ? dto.getRespondedAt() : 0L;
            request.fromPublicKey = ""; // 从API获取的DTO中可能没有公钥，需要处理
            requests.add(request);

            // 保存到本地数据库
            saveToLocalDatabase(request);
        }

        adapter.submitList(requests);

        // 更新空状态视图
        if (requests.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }

        // 标记为已读（从通知跳转时）
        if (markAsRead) {
            markAsRead = false;
        }
    }

    private void handleRequestsError(Throwable throwable) {
        swipeRefreshLayout.setRefreshing(false);

        // 检查是否是认证错误
        if (throwable instanceof TokenExpiredException) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("登录已过期")
                    .setMessage("您的登录已过期，请重新登录")
                    .setPositiveButton("确定", (dialog, which) -> {
                        // 清除 token 并返回登录
                        tokenManager.clearTokens();
                        finish();
                    })
                    .setCancelable(false)
                    .show();
            return;
        } else if (throwable instanceof AuthenticationException) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("认证失败")
                    .setMessage("认证失败: " + throwable.getMessage())
                    .setPositiveButton("确定", null)
                    .show();
            return;
        }

        // 其他错误：加载本地数据库中的请求
        loadLocalRequests();

        new MaterialAlertDialogBuilder(this)
                .setTitle("加载失败")
                .setMessage("无法从服务器加载好友请求，正在显示本地缓存")
                .setPositiveButton("确定", null)
                .show();
    }

    private void loadLocalRequests() {
        new Thread(() -> {
            List<FriendRequest> localRequests = AppDatabase.getInstance(this)
                    .friendRequestDao()
                    .getPendingRequests();

            runOnUiThread(() -> {
                adapter.submitList(localRequests);

                if (localRequests.isEmpty()) {
                    emptyView.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    emptyView.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }
            });
        }).start();
    }

    private void saveToLocalDatabase(FriendRequest request) {
        new Thread(() -> {
            AppDatabase.getInstance(this)
                    .friendRequestDao()
                    .insertFriendRequest(request);
        }).start();
    }

    private void showConfirmDialog(FriendRequest request, boolean accept) {
        String action = accept ? "同意" : "拒绝";
        String message = accept
                ? "同意添加 " + (request.fromDisplayName != null && !request.fromDisplayName.isEmpty()
                    ? request.fromDisplayName : request.fromUsername) + " 为好友？"
                : "拒绝 " + (request.fromDisplayName != null && !request.fromDisplayName.isEmpty()
                    ? request.fromDisplayName : request.fromUsername) + " 的好友请求？";

        new MaterialAlertDialogBuilder(this)
                .setTitle(action + "好友请求")
                .setMessage(message)
                .setPositiveButton(action, (dialog, which) -> {
                    respondToRequest(request, accept);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void respondToRequest(FriendRequest request, boolean accept) {
        RespondFriendRequestRequest req = new RespondFriendRequestRequest();
        req.setAccept(accept);

        disposables.add(
            friendServiceApi.respondToFriendRequest(request.requestId, req)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(__ -> swipeRefreshLayout.setRefreshing(true))
                .subscribe(
                    unused -> {
                        swipeRefreshLayout.setRefreshing(false);
                        // 更新本地数据库
                        updateLocalRequestStatus(request, accept);

                        // 如果接受了好友请求，同步联系人列表
                        if (accept) {
                            syncContactList();
                        }

                        // 刷新列表
                        loadPendingRequests();
                        // 显示成功消息
                        String message = accept ? "已同意好友请求" : "已拒绝好友请求";
                        showMessage("成功", message);
                    },
                    throwable -> {
                        swipeRefreshLayout.setRefreshing(false);
                        showMessage("操作失败", "处理好友请求时发生错误，请重试");
                    }
                )
        );
    }

    private void updateLocalRequestStatus(FriendRequest request, boolean accept) {
        new Thread(() -> {
            request.status = accept ? "ACCEPTED" : "REJECTED";
            request.respondedAt = System.currentTimeMillis();
            AppDatabase.getInstance(this)
                    .friendRequestDao()
                    .updateFriendRequest(request);
        }).start();
    }

    /**
     * 同步联系人列表
     * 接受好友请求后，将新好友同步到本地数据库
     */
    private void syncContactList() {
        ContactSyncManager syncManager = new ContactSyncManager(this);
        syncManager.syncContacts()
                .subscribe(
                        result -> {
                            android.util.Log.d(TAG, "联系人同步成功: 新增=" + result.getToAdd().size()
                                    + ", 更新=" + result.getToUpdate().size());

                            // 如果有新添加的联系人，显示安全码验证对话框
                            if (!result.getToAdd().isEmpty()) {
                                showSafetyNumberVerificationForNewContacts(result.getToAdd());
                            }
                        },
                        error -> {
                            android.util.Log.e(TAG, "联系人同步失败", error);
                            // 检查是否是认证错误
                            if (error instanceof TokenExpiredException) {
                                runOnUiThread(() -> {
                                    new MaterialAlertDialogBuilder(this)
                                            .setTitle("登录已过期")
                                            .setMessage("您的登录已过期，请重新登录")
                                            .setPositiveButton("确定", (dialog, which) -> {
                                                tokenManager.clearTokens();
                                                finish();
                                            })
                                            .setCancelable(false)
                                            .show();
                                });
                            } else if (error instanceof AuthenticationException) {
                                runOnUiThread(() -> {
                                    new MaterialAlertDialogBuilder(this)
                                            .setTitle("认证失败")
                                            .setMessage("认证失败: " + error.getMessage())
                                            .setPositiveButton("确定", null)
                                            .show();
                                });
                            }
                        }
                );
    }

    /**
     * 为新添加的联系人显示安全码验证对话框
     */
    private void showSafetyNumberVerificationForNewContacts(List<com.ttt.safevault.dto.response.FriendDto> newContacts) {
        if (newContacts.isEmpty()) {
            return;
        }

        // 只为第一个新联系人显示验证对话框（避免多个对话框）
        com.ttt.safevault.dto.response.FriendDto firstContact = newContacts.get(0);

        try {
            // 解析接收方公钥
            String receiverPublicKeyBase64 = firstContact.getPublicKey();
            if (receiverPublicKeyBase64 == null || receiverPublicKeyBase64.isEmpty()) {
                android.util.Log.w(TAG, "新联系人公钥为空，跳过安全码验证");
                return;
            }

            byte[] keyBytes = android.util.Base64.decode(receiverPublicKeyBase64, android.util.Base64.NO_WRAP);
            java.security.spec.X509EncodedKeySpec spec =
                new java.security.spec.X509EncodedKeySpec(keyBytes);
            java.security.KeyFactory factory = java.security.KeyFactory.getInstance("RSA");
            PublicKey receiverPublicKey = factory.generatePublic(spec);

            // 获取发送方公钥
            com.ttt.safevault.security.SecureKeyStorageManager secureKeyStorage =
                com.ttt.safevault.security.SecureKeyStorageManager.getInstance(this);
            com.ttt.safevault.security.SessionGuard sessionGuard =
                com.ttt.safevault.security.SessionGuard.getInstance();

            if (!sessionGuard.isUnlocked()) {
                android.util.Log.w(TAG, "SessionGuard 未锁定，跳过安全码验证");
                return;
            }

            PublicKey senderPublicKey = secureKeyStorage.getRsaPublicKey();
            if (senderPublicKey == null) {
                android.util.Log.w(TAG, "无法获取发送方公钥，跳过安全码验证");
                return;
            }

            // 创建临时 Contact 对象
            Contact contact = new Contact();
            contact.username = firstContact.getUsername() != null ? firstContact.getUsername() : "";
            contact.displayName = firstContact.getDisplayName() != null && !firstContact.getDisplayName().isEmpty()
                ? firstContact.getDisplayName()
                : firstContact.getUsername();
            contact.publicKey = receiverPublicKeyBase64;

            // 显示安全码验证对话框
            runOnUiThread(() -> {
                SafetyNumberVerificationDialog.show(
                    this,
                    contact,
                    receiverPublicKey,
                    senderPublicKey,
                    new SafetyNumberVerificationDialog.Callback() {
                        @Override
                        public void onVerified() {
                            // 用户已验证
                        }

                        @Override
                        public void onNotMatch() {
                            // 安全码不匹配
                        }

                        @Override
                        public void onSkip() {
                            // 用户跳过验证
                        }
                    }
                );
            });

        } catch (Exception e) {
            android.util.Log.e(TAG, "显示安全码验证对话框失败", e);
        }
    }

    private void showMessage(String title, String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disposables.clear();
    }
}
