package com.ttt.safevault.ui.share;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.ttt.safevault.R;
import com.ttt.safevault.adapter.ContactAdapter;
import com.ttt.safevault.ui.MainActivity;
import com.ttt.safevault.data.AppDatabase;
import com.ttt.safevault.data.Contact;
import com.ttt.safevault.service.ContactSyncManager;
import com.ttt.safevault.service.manager.ContactManager;
import com.ttt.safevault.network.TokenManager;

import java.util.List;
import java.util.concurrent.Executors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * 联系人列表Fragment
 * 用于显示和管理联系人列表
 * 支持本地联系人和云端好友的同步显示
 */
public class ContactListFragment extends Fragment {

    private static final String TAG = "ContactListFragment";
    private static final String ARG_MODE = "mode";

    /**
     * Fragment模式
     */
    public enum Mode {
        /**
         * 浏览模式 - 点击联系人查看详情
         */
        BROWSE,
        /**
         * 选择模式 - 点击联系人返回选中结果
         */
        SELECT
    }

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private View emptyView;
    private ContactAdapter adapter;
    private ContactManager contactManager;
    private ContactSyncManager contactSyncManager;
    private TokenManager tokenManager;
    private CompositeDisposable disposables;
    private Mode mode = Mode.BROWSE;
    private String searchQuery = "";

    public static ContactListFragment newInstance(Mode mode) {
        ContactListFragment fragment = new ContactListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MODE, mode.name());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            String modeStr = getArguments().getString(ARG_MODE, Mode.BROWSE.name());
            mode = Mode.valueOf(modeStr);
        }
        contactManager = new ContactManager(requireContext());
        contactSyncManager = new ContactSyncManager(requireContext());
        tokenManager = new TokenManager(requireContext());
        disposables = new CompositeDisposable();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_contact_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupSearchView(view);
        loadContacts();
    }

    private void initViews(View view) {
        // 设置RecyclerView
        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        // 设置适配器
        adapter = new ContactAdapter(this::onContactClick, this::onContactLongClick);
        recyclerView.setAdapter(adapter);

        // 设置下拉刷新
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(this::onRefresh);
        }

        // 空状态视图
        emptyView = view.findViewById(R.id.empty_view);

        // 添加联系人浮动按钮
        FloatingActionButton fabAdd = view.findViewById(R.id.fabAdd);
        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> showAddContactBottomSheet());
        }

        // 我的身份码按钮
        View btnMyIdentity = view.findViewById(R.id.btnMyIdentity);
        if (btnMyIdentity != null) {
            btnMyIdentity.setOnClickListener(v -> openMyIdentity());
        }
    }

    private void setupSearchView(View view) {
        TextInputEditText searchInput = view.findViewById(R.id.search_input);
        if (searchInput != null) {
            searchInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    searchQuery = s.toString().trim();
                    performSearch();
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // 进入页面时同步云端好友并加载本地联系人
        syncAndLoadContacts();
        // 刷新 MainActivity 的菜单以更新好友请求 Badge
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).refreshOptionsMenu();
        }
    }

    private void loadContacts() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(true);
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            List<Contact> contacts;
            if (searchQuery.isEmpty()) {
                contacts = contactManager.getAllContacts();
            } else {
                contacts = contactManager.searchContacts(searchQuery);
            }

            requireActivity().runOnUiThread(() -> {
                adapter.submitList(contacts);
                updateEmptyView(contacts.isEmpty());

                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            });
        });
    }

    private void performSearch() {
        if (searchQuery.isEmpty()) {
            loadContacts();
        } else {
            Executors.newSingleThreadExecutor().execute(() -> {
                List<Contact> contacts = contactManager.searchContacts(searchQuery);
                requireActivity().runOnUiThread(() -> {
                    adapter.submitList(contacts);
                    updateEmptyView(contacts.isEmpty());
                });
            });
        }
    }

    private void updateEmptyView(boolean isEmpty) {
        if (emptyView != null) {
            emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
        if (recyclerView != null) {
            recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
    }

    private void onContactClick(Contact contact) {
        // 更新最后使用时间
        contactManager.updateLastUsed(contact.contactId);

        if (mode == Mode.SELECT) {
            // 选择模式：返回结果给调用者
            if (getActivity() != null) {
                Intent result = new Intent();
                result.putExtra(ContactListActivity.EXTRA_CONTACT_ID, contact.contactId);
                result.putExtra(ContactListActivity.EXTRA_CONTACT_NAME,
                        contact.myNote != null && !contact.myNote.isEmpty()
                                ? contact.myNote
                                : (contact.displayName != null && !contact.displayName.isEmpty()
                                        ? contact.displayName
                                        : contact.username));
                getActivity().setResult(android.app.Activity.RESULT_OK, result);
                getActivity().finish();
            }
        } else {
            // 浏览模式：显示详情菜单
            showContactDetailDialog(contact);
        }
    }

    private void onContactLongClick(Contact contact) {
        // 显示操作菜单
        String displayName = contact.myNote != null && !contact.myNote.isEmpty()
                ? contact.myNote
                : (contact.displayName != null && !contact.displayName.isEmpty()
                        ? contact.displayName
                        : contact.username);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(displayName)
                .setItems(new CharSequence[]{"编辑备注", "查看详情", "删除联系人"},
                        (dialog, which) -> {
                            switch (which) {
                                case 0:
                                    showEditNoteDialog(contact);
                                    break;
                                case 1:
                                    showContactDetailDialog(contact);
                                    break;
                                case 2:
                                    showDeleteConfirmDialog(contact);
                                    break;
                            }
                        })
                .show();
    }

    private void showContactDetailDialog(Contact contact) {
        // 打开好友详情/聊天界面
        Intent intent = new Intent(requireContext(), com.ttt.safevault.ui.friend.FriendDetailActivity.class);
        intent.putExtra(com.ttt.safevault.ui.friend.FriendDetailActivity.EXTRA_CONTACT_ID, contact.contactId);
        startActivity(intent);
    }

    private void showEditNoteDialog(Contact contact) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_edit_note, null);
        TextInputEditText editNote = dialogView.findViewById(R.id.edit_note);
        editNote.setText(contact.myNote != null ? contact.myNote : "");

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("编辑备注")
                .setView(dialogView)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newNote = editNote.getText().toString().trim();
                    updateContactNote(contact, newNote);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateContactNote(Contact contact, String newNote) {
        Executors.newSingleThreadExecutor().execute(() -> {
            contact.myNote = newNote.isEmpty() ? null : newNote;
            AppDatabase.getInstance(requireContext())
                    .contactDao()
                    .updateContact(contact);

            requireActivity().runOnUiThread(() -> {
                loadContacts();
                Toast.makeText(requireContext(), "备注已更新", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void showDeleteConfirmDialog(Contact contact) {
        String displayName = contact.myNote != null && !contact.myNote.isEmpty()
                ? contact.myNote
                : (contact.displayName != null && !contact.displayName.isEmpty()
                        ? contact.displayName
                        : contact.username);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除联系人")
                .setMessage("确定要删除联系人 \"" + displayName + "\" 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    deleteContact(contact);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteContact(Contact contact) {
        Executors.newSingleThreadExecutor().execute(() -> {
            boolean success = contactManager.deleteContact(contact.contactId);

            requireActivity().runOnUiThread(() -> {
                if (success) {
                    loadContacts();
                    Toast.makeText(requireContext(), "联系人已删除", Toast.LENGTH_SHORT).show();
                } else {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("删除失败")
                            .setMessage("删除联系人时发生错误，请重试")
                            .setPositiveButton("确定", null)
                            .show();
                }
            });
        });
    }

    private void openScanContact() {
        Intent intent = new Intent(requireContext(), ScanContactActivity.class);
        startActivity(intent);
    }

    private void showAddContactBottomSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheet =
            new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());

        View sheetView = LayoutInflater.from(requireContext())
            .inflate(R.layout.bottom_sheet_add_contact, null);

        // 扫码添加
        sheetView.findViewById(R.id.btn_scan_qr).setOnClickListener(v -> {
            bottomSheet.dismiss();
            openScanContact();
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
            new com.ttt.safevault.network.TokenManager(requireContext());

        if (!tokenManager.isLoggedIn()) {
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("需要登录")
                .setMessage("搜索添加好友需要先登录云端账号")
                .setPositiveButton("去登录", (dialog, which) -> {
                    Intent intent = new Intent(requireContext(), com.ttt.safevault.ui.LoginActivity.class);
                    startActivity(intent);
                })
                .setNegativeButton("取消", null)
                .show();
            return;
        }

        // 已登录，跳转到搜索页面
        Intent intent = new Intent(requireContext(), com.ttt.safevault.ui.friend.ContactSearchActivity.class);
        startActivity(intent);
    }

    private void openMyIdentity() {
        Intent intent = new Intent(requireContext(), MyIdentityActivity.class);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 清理引用，避免内存泄漏
        recyclerView = null;
        swipeRefreshLayout = null;
        emptyView = null;
        adapter = null;
        // 清理 RxJava 订阅
        if (disposables != null) {
            disposables.clear();
        }
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
                            if (result.getToAdd().size() > 0 && getActivity() != null) {
                                Toast.makeText(getActivity(), "已同步 " + result.getToAdd().size() + " 位云端好友",
                                    Toast.LENGTH_SHORT).show();
                            }
                            loadContacts();
                        },
                        error -> {
                            Log.e(TAG, "Cloud sync failed, loading local contacts only", error);
                            // 同步失败时仍然加载本地数据
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
}
