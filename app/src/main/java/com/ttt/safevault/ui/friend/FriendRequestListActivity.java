package com.ttt.safevault.ui.friend;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.ttt.safevault.R;
import com.ttt.safevault.adapter.FriendRequestAdapter;
import com.ttt.safevault.data.AppDatabase;
import com.ttt.safevault.data.FriendRequest;
import com.ttt.safevault.dto.request.RespondFriendRequestRequest;
import com.ttt.safevault.dto.response.FriendRequestDto;
import com.ttt.safevault.network.RetrofitClient;
import com.ttt.safevault.network.api.FriendServiceApi;
import com.ttt.safevault.network.TokenManager;

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

        // 加载本地数据库中的请求
        loadLocalRequests();

        new AlertDialog.Builder(this)
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

        new AlertDialog.Builder(this)
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

    private void showMessage(String title, String message) {
        new AlertDialog.Builder(this)
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
