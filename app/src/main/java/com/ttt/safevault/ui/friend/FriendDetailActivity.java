package com.ttt.safevault.ui.friend;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ttt.safevault.R;
import com.ttt.safevault.ServiceLocator;
import com.ttt.safevault.adapter.ShareTimelineAdapter;
import com.ttt.safevault.data.AppDatabase;
import com.ttt.safevault.data.Contact;
import com.ttt.safevault.data.ShareRecord;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.service.manager.ContactManager;
import com.ttt.safevault.service.manager.ShareRecordManager;
import com.ttt.safevault.ui.share.MyIdentityActivity;
import com.ttt.safevault.ui.share.ShareActivity;
import com.ttt.safevault.ui.share.ShareDetailDialog;
import com.ttt.safevault.utils.UIUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 好友详情/聊天界面
 * 显示与特定联系人的分享记录（聊天时间线样式）
 */
public class FriendDetailActivity extends AppCompatActivity implements
        ShareDetailDialog.ShareDetailListener {

    private static final String TAG = "FriendDetailActivity";
    public static final String EXTRA_CONTACT_ID = "contact_id";

    private BackendService backendService;
    private ContactManager contactManager;
    private ShareRecordManager recordManager;

    private com.google.android.material.appbar.MaterialToolbar toolbar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private View emptyView;
    private MaterialButton btnSharePassword;
    private MaterialButton btnViewIdentity;

    private ShareTimelineAdapter adapter;
    private Contact currentContact;
    private List<ShareRecord> shareRecords = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend_detail);

        // 防止截屏
        getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
        );

        initManagers();
        initViews();
        loadContact();
    }

    private void initManagers() {
        backendService = ServiceLocator.getInstance().getBackendService();
        contactManager = new ContactManager(this);
        recordManager = new ShareRecordManager(this);
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(this::loadShares);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        emptyView = findViewById(R.id.empty_view);

        btnSharePassword = findViewById(R.id.btnSharePassword);
        btnViewIdentity = findViewById(R.id.btnViewIdentity);

        // 初始化适配器
        adapter = new ShareTimelineAdapter(
                this::onShareViewClick,
                this::onShareLongClick,
                backendService
        );
        recyclerView.setAdapter(adapter);

        // 分享密码按钮
        if (btnSharePassword != null) {
            btnSharePassword.setOnClickListener(v -> showSharePasswordDialog());
        }

        // 查看身份码按钮
        if (btnViewIdentity != null) {
            btnViewIdentity.setOnClickListener(v -> viewIdentityCode());
        }
    }

    private void loadContact() {
        String contactId = getIntent().getStringExtra(EXTRA_CONTACT_ID);
        if (contactId == null) {
            finish();
            return;
        }

        executor.execute(() -> {
            List<Contact> contacts = contactManager.getAllContacts();
            for (Contact contact : contacts) {
                if (contact.contactId.equals(contactId)) {
                    currentContact = contact;
                    break;
                }
            }

            runOnUiThread(() -> {
                if (currentContact != null) {
                    updateToolbar();
                    loadShares();
                } else {
                    UIUtils.showError(this, "联系人不存在");
                    finish();
                }
            });
        });
    }

    private void updateToolbar() {
        if (currentContact != null) {
            String displayName = currentContact.myNote != null && !currentContact.myNote.isEmpty()
                    ? currentContact.myNote
                    : (currentContact.displayName != null && !currentContact.displayName.isEmpty()
                        ? currentContact.displayName
                        : currentContact.username);
            toolbar.setTitle(displayName);
        }
    }

    private void loadShares() {
        if (currentContact == null) return;

        swipeRefreshLayout.setRefreshing(true);
        executor.execute(() -> {
            // 获取与该联系人的所有分享记录
            List<ShareRecord> sentShares = recordManager.getSharesByContactId(currentContact.contactId, "sent");
            List<ShareRecord> receivedShares = recordManager.getSharesByContactId(currentContact.contactId, "received");

            // 合并并按时间排序
            shareRecords.clear();
            shareRecords.addAll(sentShares);
            shareRecords.addAll(receivedShares);

            Collections.sort(shareRecords, new Comparator<ShareRecord>() {
                @Override
                public int compare(ShareRecord o1, ShareRecord o2) {
                    return Long.compare(o2.createdAt, o1.createdAt); // 降序
                }
            });

            runOnUiThread(() -> {
                adapter.submitList(new ArrayList<>(shareRecords));
                updateEmptyState();
                swipeRefreshLayout.setRefreshing(false);
            });
        });
    }

    private void updateEmptyState() {
        if (shareRecords.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void onShareViewClick(ShareRecord record) {
        ShareDetailDialog dialog = ShareDetailDialog.newInstance(record, currentContact.contactId);
        dialog.show(getSupportFragmentManager(), "share_detail");
    }

    private void onShareLongClick(ShareRecord record) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("选择操作")
                .setItems(new CharSequence[]{"查看详情", "删除记录"}, (dialog, which) -> {
                    if (which == 0) {
                        onShareViewClick(record);
                    } else {
                        showDeleteRecordDialog(record);
                    }
                })
                .show();
    }

    private void showDeleteRecordDialog(ShareRecord record) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除记录")
                .setMessage("确定要删除这条分享记录吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    executor.execute(() -> {
                        boolean success = recordManager.deleteShareRecord(record.shareId);
                        runOnUiThread(() -> {
                            if (success) {
                                UIUtils.showSuccess(this, "记录已删除");
                                loadShares();
                            } else {
                                UIUtils.showError(this, "删除失败");
                            }
                        });
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showSharePasswordDialog() {
        // 显示密码列表让用户选择要分享的密码
        executor.execute(() -> {
            List<com.ttt.safevault.model.PasswordItem> passwords = backendService.getAllPasswords();

            runOnUiThread(() -> {
                if (passwords.isEmpty()) {
                    UIUtils.showError(this, "暂无密码可分享");
                    return;
                }

                String[] passwordTitles = new String[passwords.size()];
                for (int i = 0; i < passwords.size(); i++) {
                    passwordTitles[i] = passwords.get(i).getDisplayName();
                }

                new MaterialAlertDialogBuilder(this)
                        .setTitle("选择要分享的密码")
                        .setItems(passwordTitles, (dialog, which) -> {
                            int passwordId = passwords.get(which).getId();
                            startShareActivity(passwordId);
                        })
                        .show();
            });
        });
    }

    private void startShareActivity(int passwordId) {
        Intent intent = new Intent(this, ShareActivity.class);
        intent.putExtra(ShareActivity.EXTRA_PASSWORD_ID, passwordId);
        intent.putExtra(ShareActivity.EXTRA_CONTACT_ID, currentContact.contactId);
        startActivity(intent);
    }

    private void viewIdentityCode() {
        // 跳转到查看联系人身份码的界面（显示当前联系人的身份码）
        Intent intent = new Intent(this, MyIdentityActivity.class);
        // 可以传递参数来标记是查看他人身份码
        intent.putExtra("view_mode", "contact");
        intent.putExtra("contact_id", currentContact.contactId);
        startActivity(intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentContact != null) {
            loadShares();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    // ShareDetailDialog.ShareDetailListener 实现

    @Override
    public void onShareRevoked(ShareRecord record) {
        loadShares();
    }

    @Override
    public void onPasswordSaved(ShareRecord record) {
        // 密码保存后的回调
        loadShares();
    }
}
