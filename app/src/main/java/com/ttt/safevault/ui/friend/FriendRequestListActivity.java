package com.ttt.safevault.ui.friend;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ttt.safevault.R;
import com.ttt.safevault.adapter.FriendRequestAdapter;
import com.ttt.safevault.core.ServiceLocator;
import com.ttt.safevault.data.Contact;
import com.ttt.safevault.data.FriendRequest;
import com.ttt.safevault.dto.response.FriendDto;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.ui.share.SafetyNumberVerificationDialog;
import com.ttt.safevault.viewmodel.FriendRequestListViewModel;

import java.security.PublicKey;
import java.util.List;

/**
 * Friend request list page.
 */
public class FriendRequestListActivity extends AppCompatActivity {

    private static final String TAG = "FriendRequestListActivity";

    private MaterialToolbar toolbar;
    private androidx.recyclerview.widget.RecyclerView recyclerView;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshLayout;
    private View emptyView;

    private FriendRequestAdapter adapter;
    private FriendRequestListViewModel viewModel;
    private BackendService backendService;

    private boolean markAsRead = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend_request_list);

        getWindow().setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        );

        markAsRead = getIntent().getBooleanExtra("mark_as_read", false);
        backendService = ServiceLocator.getInstance().getBackendService();
        viewModel = new ViewModelProvider(this).get(FriendRequestListViewModel.class);

        initViews();
        setupObservers();
        viewModel.loadPendingRequests();
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
        swipeRefreshLayout.setOnRefreshListener(() -> viewModel.loadPendingRequests());
        emptyView = findViewById(R.id.empty_view);
    }

    private void setupObservers() {
        viewModel.getLoading().observe(this, loading -> swipeRefreshLayout.setRefreshing(Boolean.TRUE.equals(loading)));

        viewModel.getPendingRequests().observe(this, requests -> {
            List<FriendRequest> safeRequests = requests == null ? java.util.Collections.emptyList() : requests;
            adapter.submitList(safeRequests);
            updateEmptyState(safeRequests.isEmpty());
            if (markAsRead) {
                markAsRead = false;
            }
        });

        viewModel.getSuccessMessage().observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                showMessage("成功", message);
                viewModel.clearMessages();
            }
        });

        viewModel.getErrorMessage().observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                showMessage("提示", message);
                viewModel.clearMessages();
            }
        });

        viewModel.getRequireRelogin().observe(this, needRelogin -> {
            if (Boolean.TRUE.equals(needRelogin)) {
                showReloginDialog();
            }
        });

        viewModel.getNewlyAddedContacts().observe(this, contacts -> {
            if (contacts != null && !contacts.isEmpty()) {
                showSafetyNumberVerificationForNewContacts(contacts);
                viewModel.consumeNewlyAddedContacts();
            }
        });
    }

    private void updateEmptyState(boolean empty) {
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.loadPendingRequests();
    }

    private void showConfirmDialog(FriendRequest request, boolean accept) {
        String action = accept ? "同意" : "拒绝";
        String name = request.fromDisplayName != null && !request.fromDisplayName.isEmpty()
            ? request.fromDisplayName
            : request.fromUsername;
        String message = accept
            ? "同意添加 " + name + " 为好友？"
            : "拒绝 " + name + " 的好友请求？";

        new MaterialAlertDialogBuilder(this)
            .setTitle(action + "好友请求")
            .setMessage(message)
            .setPositiveButton(action, (dialog, which) -> viewModel.respondToRequest(request, accept))
            .setNegativeButton("取消", null)
            .show();
    }

    private void showReloginDialog() {
        new MaterialAlertDialogBuilder(this)
            .setTitle("登录已过期")
            .setMessage("您的登录状态已过期，请重新登录")
            .setPositiveButton("确定", (dialog, which) -> {
                backendService.clearLocalCloudTokens();
                finish();
            })
            .setCancelable(false)
            .show();
    }

    private void showSafetyNumberVerificationForNewContacts(List<FriendDto> newContacts) {
        if (newContacts.isEmpty()) {
            return;
        }
        FriendDto firstContact = newContacts.get(0);

        try {
            String receiverPublicKeyBase64 = firstContact.getPublicKey();
            if (receiverPublicKeyBase64 == null || receiverPublicKeyBase64.isEmpty()) {
                android.util.Log.w(TAG, "New contact has empty public key, skip safety verification");
                return;
            }

            byte[] keyBytes = android.util.Base64.decode(receiverPublicKeyBase64, android.util.Base64.NO_WRAP);
            java.security.spec.X509EncodedKeySpec spec = new java.security.spec.X509EncodedKeySpec(keyBytes);
            java.security.KeyFactory factory = java.security.KeyFactory.getInstance("RSA");
            PublicKey receiverPublicKey = factory.generatePublic(spec);

            PublicKey senderPublicKey = backendService.getSessionRsaPublicKey();
            if (senderPublicKey == null) {
                android.util.Log.w(TAG, "Session locked or sender key unavailable, skip safety verification");
                return;
            }

            Contact contact = new Contact();
            contact.username = firstContact.getUsername() != null ? firstContact.getUsername() : "";
            contact.displayName = firstContact.getDisplayName() != null && !firstContact.getDisplayName().isEmpty()
                ? firstContact.getDisplayName()
                : firstContact.getUsername();
            contact.publicKey = receiverPublicKeyBase64;

            SafetyNumberVerificationDialog.show(
                this,
                contact,
                receiverPublicKey,
                senderPublicKey,
                new SafetyNumberVerificationDialog.Callback() {
                    @Override
                    public void onVerified() {
                    }

                    @Override
                    public void onNotMatch() {
                    }

                    @Override
                    public void onSkip() {
                    }
                }
            );
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to show safety number verification", e);
        }
    }

    private void showMessage(String title, String message) {
        new MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show();
    }
}
