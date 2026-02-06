package com.ttt.safevault.ui.share;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.ttt.safevault.R;
import com.ttt.safevault.adapter.ShareRecordAdapter;
import com.ttt.safevault.data.ShareRecord;
import com.ttt.safevault.data.AppDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * 分享历史界面
 * 显示我创建的和我接收的分享记录
 */
public class ShareHistoryActivity extends AppCompatActivity {

    private static final String TAG = "ShareHistoryActivity";

    private MaterialToolbar toolbar;
    private TabLayout tabLayout;
    private androidx.recyclerview.widget.RecyclerView recyclerView;
    private View emptyView;
    private ShareRecordAdapter adapter;

    private int currentTab = 0; // 0: 我分享的, 1: 我接收的

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置FLAG_SECURE防止截屏
        com.ttt.safevault.security.SecurityConfig securityConfig =
                new com.ttt.safevault.security.SecurityConfig(this);
        if (securityConfig.isScreenshotProtectionEnabled()) {
            getWindow().setFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SECURE,
                    android.view.WindowManager.LayoutParams.FLAG_SECURE
            );
        }

        setContentView(R.layout.activity_share_history);

        initViews();
        loadShareHistory(currentTab);
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tabLayout = findViewById(R.id.tab_layout);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                adapter.setIsMyShares(currentTab == 0);
                loadShareHistory(currentTab);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                loadShareHistory(currentTab);
            }
        });

        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));

        adapter = new ShareRecordAdapter(this::onShareClick, this::onShareLongClick);
        adapter.setIsMyShares(currentTab == 0);
        recyclerView.setAdapter(adapter);

        emptyView = findViewById(R.id.empty_view);

        // 下拉刷新
        androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshLayout =
                findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            loadShareHistory(currentTab);
            swipeRefreshLayout.setRefreshing(false);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadShareHistory(currentTab);
    }

    private void loadShareHistory(int tab) {
        new Thread(() -> {
            List<ShareRecord> records;
            if (tab == 0) {
                // 我分享的
                records = AppDatabase.getInstance(this)
                        .shareRecordDao()
                        .getMySentShares();
            } else {
                // 我接收的
                records = AppDatabase.getInstance(this)
                        .shareRecordDao()
                        .getMyReceivedShares();
            }

            List<ShareRecord> finalRecords = records != null ? records : new ArrayList<>();
            runOnUiThread(() -> {
                adapter.submitList(finalRecords);

                // 更新空状态视图
                if (finalRecords.isEmpty()) {
                    emptyView.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                    updateEmptyViewText(tab);
                } else {
                    emptyView.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }
            });
        }).start();
    }

    private void updateEmptyViewText(int tab) {
        TextView emptyTitle = emptyView.findViewById(R.id.empty_title);
        TextView emptySubtitle = emptyView.findViewById(R.id.empty_subtitle);

        if (tab == 0) {
            emptyTitle.setText(R.string.no_sent_shares);
            emptySubtitle.setText(R.string.start_sharing_hint);
        } else {
            emptyTitle.setText(R.string.no_received_shares);
            emptySubtitle.setText(R.string.wait_for_shares_hint);
        }
    }

    private void onShareClick(ShareRecord record) {
        // 打开分享详情对话框
        ShareDetailDialog dialog = ShareDetailDialog.newInstance(record, record.contactId);
        dialog.show(getSupportFragmentManager(), "share_detail");
    }

    private void onShareLongClick(ShareRecord record) {
        // 长按显示操作菜单
        if (currentTab == 0 && !"revoked".equals(record.status) && !"expired".equals(record.status)) {
            // 我分享的且未撤销、未过期的，可以撤销
            showRevokeConfirmDialog(record);
        } else if (currentTab == 1 && !"saved".equals(record.status) && !"expired".equals(record.status)) {
            // 我接收的且未保存、未过期的，可以保存
            showSaveConfirmDialog(record);
        }
    }

    private void showRevokeConfirmDialog(ShareRecord record) {
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.confirm_revoke_share)
                .setMessage(R.string.confirm_revoke_share_message)
                .setPositiveButton(R.string.revoke, (dialog, which) -> {
                    revokeShare(record);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void revokeShare(ShareRecord record) {
        new Thread(() -> {
            record.status = "revoked";
            AppDatabase.getInstance(this)
                    .shareRecordDao()
                    .updateShareRecord(record);

            runOnUiThread(() -> {
                android.widget.Toast.makeText(this,
                        R.string.share_revoked_success,
                        android.widget.Toast.LENGTH_SHORT).show();
                loadShareHistory(currentTab);
            });
        }).start();
    }

    private void showSaveConfirmDialog(ShareRecord record) {
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.save_to_local)
                .setMessage(R.string.confirm_save_share_message)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    saveShareToLocal(record);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * 保存分享的密码到本地密码库
     *
     * @issue 需要实现将收到的分享密码保存到密码库的功能
     * @description 当前显示"即将推出"对话框
     * @requires 需要解密分享数据并通过 BackendService 保存
     */
    private void saveShareToLocal(ShareRecord record) {
        // 当前实现：显示"即将推出"对话框
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.coming_soon)
                .setMessage(R.string.save_to_local_coming_soon)
                .setPositiveButton(R.string.ok, null)
                .show();
    }
}
