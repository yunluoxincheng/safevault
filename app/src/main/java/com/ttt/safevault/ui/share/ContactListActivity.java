package com.ttt.safevault.ui.share;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.ttt.safevault.R;
import com.ttt.safevault.adapter.ContactAdapter;
import com.ttt.safevault.data.Contact;
import com.ttt.safevault.service.ContactSyncManager;
import com.ttt.safevault.service.manager.ContactManager;
import com.ttt.safevault.network.TokenManager;
import com.ttt.safevault.network.RetrofitClient;
import com.ttt.safevault.network.api.FriendServiceApi;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 联系人列表界面
 * 支持本地联系人和云端好友的同步显示
 */
public class ContactListActivity extends AppCompatActivity {
    private static final String TAG = "ContactListActivity";

    public static final String EXTRA_CONTACT_ID = "contact_id";
    public static final String EXTRA_CONTACT_NAME = "contact_name";

    private com.google.android.material.appbar.MaterialToolbar toolbar;
    private androidx.recyclerview.widget.RecyclerView recyclerView;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshLayout;
    private View emptyView;
    private ContactAdapter adapter;
    private ContactManager contactManager;
    private ContactSyncManager contactSyncManager;
    private TokenManager tokenManager;
    private FriendServiceApi friendServiceApi;
    private CompositeDisposable disposables = new CompositeDisposable();
    private com.google.android.material.badge.BadgeDrawable friendRequestBadge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_list);

        contactManager = new ContactManager(this);
        contactSyncManager = new ContactSyncManager(this);
        tokenManager = new TokenManager(this);
        friendServiceApi = RetrofitClient.getInstance(this).getFriendServiceApi();

        initViews();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Inflate toolbar menu
        toolbar.inflateMenu(R.menu.contact_list_menu);
        toolbar.setOnMenuItemClickListener(this::onMenuItemClick);

        // Setup badge for friend requests (delay to ensure menu is prepared)
        toolbar.post(() -> {
            try {
                friendRequestBadge = com.google.android.material.badge.BadgeDrawable.create(this);
                friendRequestBadge.setHorizontalOffset(8);
                friendRequestBadge.setVerticalOffset(8);
                friendRequestBadge.setVisible(false);

                // 使用 Toolbar 的方式来附加 Badge (传入 Toolbar 和菜单项 ID)
                com.google.android.material.badge.BadgeUtils.attachBadgeDrawable(
                    friendRequestBadge,
                    toolbar,
                    R.id.action_friend_requests
                );
            } catch (Exception e) {
                Log.e(TAG, "Failed to setup friend request badge", e);
            }
        });

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));

        adapter = new ContactAdapter(this::onContactClick, this::onContactLongClick);
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(this::onRefresh);

        emptyView = findViewById(R.id.empty_view);

        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);
        fabAdd.setOnClickListener(v -> showAddContactBottomSheet());

        // 我的身份码按钮
        findViewById(R.id.btnMyIdentity).setOnClickListener(v -> {
            Intent intent = new Intent(this, MyIdentityActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        updateFriendRequestBadge();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 进入页面时同步云端好友并加载本地联系人
        syncAndLoadContacts();
        updateFriendRequestBadge();
    }

    private void loadContacts() {
        // 在后台线程查询数据库，避免主线程访问数据库异常
        disposables.add(
            io.reactivex.rxjava3.core.Observable.fromCallable(() -> contactManager.getAllContacts())
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    contacts -> {
                        Log.d(TAG, "loadContacts: loaded " + contacts.size() + " contacts");
                        adapter.submitList(contacts);

                        // 更新空状态视图
                        if (contacts.isEmpty()) {
                            emptyView.setVisibility(View.VISIBLE);
                            recyclerView.setVisibility(View.GONE);
                        } else {
                            emptyView.setVisibility(View.GONE);
                            recyclerView.setVisibility(View.VISIBLE);
                        }

                        swipeRefreshLayout.setRefreshing(false);
                    },
                    error -> {
                        Log.e(TAG, "Failed to load contacts", error);
                        swipeRefreshLayout.setRefreshing(false);
                        Toast.makeText(this, "加载联系人失败", Toast.LENGTH_SHORT).show();
                    }
                )
        );
    }

    private void onContactClick(Contact contact) {
        // 先更新最后使用时间（后台线程）
        new Thread(() -> contactManager.updateLastUsed(contact.contactId)).start();

        // 返回选中的联系人ID到调用者
        Intent result = new Intent();
        result.putExtra(EXTRA_CONTACT_ID, contact.contactId);
        setResult(RESULT_OK, result);
        finish();
    }

    private void onContactLongClick(Contact contact) {
        // 显示操作菜单（编辑备注/删除）
        new MaterialAlertDialogBuilder(this)
                .setTitle(contact.displayName != null && !contact.displayName.isEmpty()
                    ? contact.displayName
                    : contact.username)
                .setItems(new CharSequence[]{"编辑备注", "删除联系人"}, (dialog, which) -> {
                    if (which == 0) {
                        showEditNoteDialog(contact);
                    } else {
                        showDeleteConfirmDialog(contact);
                    }
                })
                .show();
    }

    private void showEditNoteDialog(Contact contact) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_note, null);
        TextInputEditText editNote = dialogView.findViewById(R.id.edit_note);
        editNote.setText(contact.myNote != null ? contact.myNote : "");

        new MaterialAlertDialogBuilder(this)
                .setTitle("编辑备注")
                .setView(dialogView)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newNote = editNote.getText().toString();
                    contact.myNote = newNote.isEmpty() ? null : newNote;
                    // 更新联系人备注
                    new Thread(() -> {
                        com.ttt.safevault.data.AppDatabase.getInstance(this)
                                .contactDao()
                                .updateContact(contact);
                        runOnUiThread(this::loadContacts);
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDeleteConfirmDialog(Contact contact) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除联系人")
                .setMessage("确定要删除联系人 " +
                    (contact.displayName != null && !contact.displayName.isEmpty()
                        ? contact.displayName
                        : contact.username) + " 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    deleteContactAndFriend(contact);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 删除联系人（同时删除云端和本地数据）
     * @param contact 要删除的联系人
     */
    private void deleteContactAndFriend(Contact contact) {
        // 如果是云端好友，先调用云端 API 删除
        if (contact.cloudUserId != null && !contact.cloudUserId.isEmpty() && friendServiceApi != null) {
            disposables.add(
                friendServiceApi.deleteFriend(contact.cloudUserId)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        response -> {
                            // 云端删除成功后，删除本地数据
                            boolean success = contactManager.deleteContact(contact.contactId);
                            if (success) {
                                loadContacts();
                                Toast.makeText(this, "已删除好友", Toast.LENGTH_SHORT).show();
                            } else {
                                showDeleteErrorDialog();
                            }
                        },
                        error -> {
                            Log.e(TAG, "Failed to delete friend from cloud", error);
                            showDeleteErrorDialog("删除失败: " + error.getMessage());
                        }
                    )
            );
        } else {
            // 本地联系人，直接删除
            boolean success = contactManager.deleteContact(contact.contactId);
            if (success) {
                loadContacts();
                Toast.makeText(this, "已删除联系人", Toast.LENGTH_SHORT).show();
            } else {
                showDeleteErrorDialog();
            }
        }
    }

    /**
     * 显示删除错误对话框
     */
    private void showDeleteErrorDialog() {
        showDeleteErrorDialog("删除联系人时发生错误，请重试");
    }

    /**
     * 显示删除错误对话框
     * @param message 错误消息
     */
    private void showDeleteErrorDialog(String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除失败")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
    }

    private boolean onMenuItemClick(android.view.MenuItem item) {
        if (item.getItemId() == R.id.action_friend_requests) {
            // 打开好友请求列表
            Intent intent = new Intent(this, com.ttt.safevault.ui.friend.FriendRequestListActivity.class);
            startActivity(intent);
            return true;
        }
        return false;
    }

    private void showAddContactBottomSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheet =
            new com.google.android.material.bottomsheet.BottomSheetDialog(this);

        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_add_contact, null);

        // 扫码添加
        sheetView.findViewById(R.id.btn_scan_qr).setOnClickListener(v -> {
            bottomSheet.dismiss();
            Intent intent = new Intent(this, ScanContactActivity.class);
            startActivity(intent);
        });

        // 搜索添加好友
        sheetView.findViewById(R.id.btn_search_friend).setOnClickListener(v -> {
            bottomSheet.dismiss();
            checkLoginAndNavigateToSearch();
        });

        // 取消
        sheetView.findViewById(R.id.btn_cancel).setOnClickListener(v -> bottomSheet.dismiss());

        bottomSheet.setContentView(sheetView);
        bottomSheet.show();
    }

    private void checkLoginAndNavigateToSearch() {
        com.ttt.safevault.network.TokenManager tokenManager =
            new com.ttt.safevault.network.TokenManager(this);

        if (!tokenManager.isLoggedIn()) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("需要登录")
                .setMessage("搜索添加好友需要先登录云端账号")
                .setPositiveButton("去登录", (dialog, which) -> {
                    Intent intent = new Intent(this, com.ttt.safevault.ui.LoginActivity.class);
                    startActivity(intent);
                })
                .setNegativeButton("取消", null)
                .show();
            return;
        }

        // 已登录，跳转到搜索页面
        Intent intent = new Intent(this, com.ttt.safevault.ui.friend.ContactSearchActivity.class);
        startActivity(intent);
    }

    private void updateFriendRequestBadge() {
        new Thread(() -> {
            int count = com.ttt.safevault.data.AppDatabase.getInstance(this)
                .friendRequestDao()
                .getPendingCount();

            runOnUiThread(() -> {
                if (friendRequestBadge == null) return;
                if (count > 0) {
                    friendRequestBadge.setVisible(true);
                    friendRequestBadge.setNumber(Math.min(count, 9));
                    if (count > 9) {
                        friendRequestBadge.setBadgeTextColor(
                            getResources().getColor(android.R.color.white, getTheme())
                        );
                    }
                } else {
                    friendRequestBadge.setVisible(false);
                }
            });
        }).start();
    }

    /**
     * 下拉刷新回调：触发云端同步并重新加载
     */
    private void onRefresh() {
        syncAndLoadContacts();
    }

    /**
     * 同步云端好友并加载本地联系人
     * 如果已登录，先同步云端数据，再加载本地列表
     * 如果未登录，直接加载本地数据
     */
    private void syncAndLoadContacts() {
        swipeRefreshLayout.setRefreshing(true);
        Log.d(TAG, "syncAndLoadContacts: Starting contact sync process");
        Log.d(TAG, "syncAndLoadContacts: Is logged in? " + tokenManager.isLoggedIn());

        if (tokenManager.isLoggedIn()) {
            // 已登录：先同步云端好友，再加载本地列表
            Log.d(TAG, "User logged in, syncing cloud contacts...");
            disposables.add(
                contactSyncManager.syncContacts()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        result -> {
                            Log.d(TAG, "Cloud sync completed: added=" + result.getToAdd().size()
                                + ", updated=" + result.getToUpdate().size());
                            if (result.getToAdd().size() > 0) {
                                Toast.makeText(this, "已同步 " + result.getToAdd().size() + " 位云端好友",
                                    Toast.LENGTH_SHORT).show();
                            }
                            Log.d(TAG, "Cloud sync completed, now loading local contacts");
                            loadContacts();
                        },
                        error -> {
                            Log.e(TAG, "Cloud sync failed, loading local contacts only", error);
                            // 同步失败时仍然加载本地数据
                            Log.d(TAG, "Attempting to load local contacts despite sync failure");
                            loadContacts();
                        }
                    )
            );
        } else {
            // 未登录：直接加载本地数据
            Log.d(TAG, "User not logged in, loading local contacts only");
            loadContacts();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 清理 RxJava 订阅
        disposables.clear();
    }
}
