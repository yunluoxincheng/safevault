package com.ttt.safevault.ui.share;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.ttt.safevault.R;
import com.ttt.safevault.adapter.ContactAdapter;
import com.ttt.safevault.data.Contact;
import com.ttt.safevault.service.manager.ContactManager;

import java.util.List;

/**
 * 联系人列表界面
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
    private com.google.android.material.badge.BadgeDrawable friendRequestBadge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_list);

        contactManager = new ContactManager(this);

        initViews();
        loadContacts();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Inflate toolbar menu
        toolbar.inflateMenu(R.menu.contact_list_menu);
        toolbar.setOnMenuItemClickListener(this::onMenuItemClick);

        // Setup badge for friend requests (delay to ensure menu is prepared)
        toolbar.post(() -> {
            friendRequestBadge = com.google.android.material.badge.BadgeDrawable.create(this);
            friendRequestBadge.setHorizontalOffset(8);
            friendRequestBadge.setVerticalOffset(8);
            android.view.MenuItem friendRequestItem = toolbar.getMenu().findItem(R.id.action_friend_requests);
            android.view.View menuItemView = toolbar.findViewById(R.id.action_friend_requests);
            if (menuItemView != null) {
                com.google.android.material.badge.BadgeUtils.attachBadgeDrawable(
                    friendRequestBadge,
                    menuItemView,
                    (android.widget.FrameLayout) menuItemView.getParent()
                );
            }
        });

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));

        adapter = new ContactAdapter(this::onContactClick, this::onContactLongClick);
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(this::loadContacts);

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
        loadContacts();
        updateFriendRequestBadge();
    }

    private void loadContacts() {
        List<Contact> contacts = contactManager.getAllContacts();
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
    }

    private void onContactClick(Contact contact) {
        // 返回选中的联系人ID到调用者
        Intent result = new Intent();
        result.putExtra(EXTRA_CONTACT_ID, contact.contactId);
        setResult(RESULT_OK, result);
        finish();

        // 更新最后使用时间
        contactManager.updateLastUsed(contact.contactId);
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
                    boolean success = contactManager.deleteContact(contact.contactId);
                    if (success) {
                        loadContacts();
                    } else {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle("删除失败")
                                .setMessage("删除联系人时发生错误，请重试")
                                .setPositiveButton("确定", null)
                                .show();
                    }
                })
                .setNegativeButton("取消", null)
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
}
