package com.ttt.safevault.ui.share;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.ttt.safevault.data.AppDatabase;
import com.ttt.safevault.data.Contact;
import com.ttt.safevault.service.manager.ContactManager;

import java.util.List;
import java.util.concurrent.Executors;

/**
 * 联系人列表Fragment
 * 用于显示和管理联系人列表
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
            swipeRefreshLayout.setOnRefreshListener(this::loadContacts);
        }

        // 空状态视图
        emptyView = view.findViewById(R.id.empty_view);

        // 添加联系人浮动按钮
        FloatingActionButton fabAdd = view.findViewById(R.id.fabAdd);
        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> openScanContact());
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
        loadContacts();
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

        new AlertDialog.Builder(requireContext())
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
        String displayName = contact.myNote != null && !contact.myNote.isEmpty()
                ? contact.myNote
                : (contact.displayName != null && !contact.displayName.isEmpty()
                        ? contact.displayName
                        : contact.username);

        StringBuilder info = new StringBuilder();
        info.append("用户名: ").append(contact.username).append("\n");
        if (contact.displayName != null && !contact.displayName.isEmpty()) {
            info.append("显示名称: ").append(contact.displayName).append("\n");
        }
        if (contact.myNote != null && !contact.myNote.isEmpty()) {
            info.append("备注: ").append(contact.myNote).append("\n");
        }

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",
                java.util.Locale.getDefault());
        info.append("添加时间: ").append(sdf.format(new java.util.Date(contact.addedAt))).append("\n");
        if (contact.lastUsedAt > 0) {
            info.append("最后使用: ").append(sdf.format(new java.util.Date(contact.lastUsedAt)));
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(displayName)
                .setMessage(info.toString())
                .setPositiveButton("关闭", null)
                .setNeutralButton("分享密码给TA", (dialog, which) -> {
                    // TODO: 启动分享流程，选择要分享的密码
                    Toast.makeText(requireContext(), "分享功能待实现",
                            Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showEditNoteDialog(Contact contact) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_edit_note, null);
        TextInputEditText editNote = dialogView.findViewById(R.id.edit_note);
        editNote.setText(contact.myNote != null ? contact.myNote : "");

        new AlertDialog.Builder(requireContext())
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

        new AlertDialog.Builder(requireContext())
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
                    new AlertDialog.Builder(requireContext())
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

    private void openMyIdentity() {
        Intent intent = new Intent(requireContext(), MyIdentityActivity.class);
        startActivity(intent);
    }
}
