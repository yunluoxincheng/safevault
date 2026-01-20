package com.ttt.safevault.ui.share;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.ttt.safevault.R;
import com.ttt.safevault.ServiceLocator;
import com.ttt.safevault.adapter.PasswordSelectAdapter;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.viewmodel.PasswordListViewModel;
import com.ttt.safevault.viewmodel.ViewModelFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * 密码选择对话框
 * 用于选择要分享给联系人的密码
 */
public class PasswordSelectDialog extends DialogFragment {

    public static final String TAG = "PasswordSelectDialog";
    private static final String ARG_CONTACT_ID = "contact_id";
    private static final String ARG_CONTACT_NAME = "contact_name";
    private static final int SEARCH_DEBOUNCE_DELAY = 300; // 300ms防抖

    private String contactId;
    private String contactName;
    private BackendService backendService;
    private PasswordListViewModel viewModel;
    private PasswordSelectAdapter adapter;
    private RecyclerView recyclerView;
    private TextInputEditText searchInput;
    private View emptyLayout;
    private TextView emptyText;
    private MaterialButton btnNewPassword;
    private Handler searchHandler;
    private Runnable searchRunnable;

    /**
     * 密码选择监听器接口
     */
    public interface OnPasswordSelectedListener {
        /**
         * 当密码被选中时调用
         * @param passwordId 选中的密码ID
         */
        void onPasswordSelected(int passwordId);

        /**
         * 当请求新建密码时调用
         */
        void onNewPasswordRequested();
    }

    private OnPasswordSelectedListener listener;

    /**
     * 创建对话框实例
     * @param contactId 联系人ID
     * @param contactName 联系人名称
     * @return 对话框实例
     */
    public static PasswordSelectDialog newInstance(String contactId, String contactName) {
        PasswordSelectDialog dialog = new PasswordSelectDialog();
        Bundle args = new Bundle();
        args.putString(ARG_CONTACT_ID, contactId);
        args.putString(ARG_CONTACT_NAME, contactName);
        dialog.setArguments(args);
        return dialog;
    }

    /**
     * 设置密码选择监听器
     * @param listener 监听器
     */
    public void setOnPasswordSelectedListener(OnPasswordSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            contactId = getArguments().getString(ARG_CONTACT_ID);
            contactName = getArguments().getString(ARG_CONTACT_NAME);
        }

        backendService = ServiceLocator.getInstance().getBackendService();
        ViewModelFactory factory = new ViewModelFactory(requireActivity().getApplication(), backendService);
        viewModel = new ViewModelProvider(this, factory).get(PasswordListViewModel.class);

        // 设置对话框样式
        setStyle(STYLE_NORMAL, R.style.Theme_SafeVault);

        // 初始化搜索防抖Handler
        searchHandler = new Handler(Looper.getMainLooper());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_password_select, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        // 设置对话框宽度为全屏
        android.app.Dialog dialog = getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = WindowManager.LayoutParams.MATCH_PARENT;
                window.setAttributes(params);
            }
        }

        // FLAG_SECURE 防止截屏
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            );
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupSearch();
        loadPasswords();
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recyclerView);
        searchInput = view.findViewById(R.id.searchInput);
        emptyLayout = view.findViewById(R.id.emptyLayout);
        emptyText = view.findViewById(R.id.emptyText);
        btnNewPassword = view.findViewById(R.id.btnNewPassword);

        // 设置RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new PasswordSelectAdapter(this::onPasswordShareClick);
        recyclerView.setAdapter(adapter);

        // 新建密码按钮
        btnNewPassword.setOnClickListener(v -> onNewPasswordClick());
    }

    /**
     * 设置搜索功能（带300ms防抖）
     */
    private void setupSearch() {
        if (searchInput == null) return;

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // 移除之前的搜索任务
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 移除之前的搜索任务
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // 创建新的搜索任务（防抖300ms）
                final String query = s.toString().trim();
                searchRunnable = () -> performSearch(query);
                searchHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_DELAY);
            }
        });
    }

    /**
     * 加载所有密码
     */
    private void loadPasswords() {
        viewModel.getAllPasswords().observe(getViewLifecycleOwner(), passwords -> {
            if (passwords != null && !passwords.isEmpty()) {
                adapter.submitList(passwords);
                updateEmptyView(false);
            } else {
                adapter.submitList(new ArrayList<>());
                updateEmptyView(true);
            }
        });
    }

    /**
     * 执行搜索
     * @param query 搜索关键词
     */
    private void performSearch(String query) {
        if (query.isEmpty()) {
            // 搜索为空，显示所有密码
            loadPasswords();
        } else {
            // 执行搜索
            Executors.newSingleThreadExecutor().execute(() -> {
                List<PasswordItem> filteredList = backendService.search(query);
                requireActivity().runOnUiThread(() -> {
                    adapter.submitList(filteredList);
                    updateEmptyView(filteredList.isEmpty());
                });
            });
        }
    }

    /**
     * 更新空状态视图
     * @param isEmpty 是否为空
     */
    private void updateEmptyView(boolean isEmpty) {
        if (emptyLayout != null) {
            emptyLayout.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
        if (recyclerView != null) {
            recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }

        // 更新空状态文本
        if (isEmpty && emptyText != null && searchInput != null) {
            String query = searchInput.getText().toString().trim();
            if (query.isEmpty()) {
                emptyText.setText(R.string.no_passwords);
            } else {
                emptyText.setText(R.string.no_search_results);
            }
        }
    }

    /**
     * 处理密码分享点击
     * @param passwordItem 被点击的密码项
     */
    private void onPasswordShareClick(PasswordItem passwordItem) {
        if (listener != null) {
            listener.onPasswordSelected(passwordItem.getId());
        }

        // 打开ShareActivity并传入contactId和passwordId
        Intent intent = new Intent(requireContext(), ShareActivity.class);
        intent.putExtra(ShareActivity.EXTRA_PASSWORD_ID, passwordItem.getId());
        intent.putExtra(ShareActivity.EXTRA_CONTACT_ID, contactId);
        startActivity(intent);

        dismiss();
    }

    /**
     * 处理新建密码点击
     */
    private void onNewPasswordClick() {
        if (listener != null) {
            listener.onNewPasswordRequested();
        }

        dismiss();

        // 跳转到MainActivity，然后导航到EditPasswordFragment
        Intent intent = new Intent(requireContext(), com.ttt.safevault.ui.MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("navigate_to", "edit_password");
        intent.putExtra("password_id", -1); // -1表示新建
        startActivity(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 清理搜索任务
        if (searchHandler != null && searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
    }
}
