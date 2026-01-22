package com.ttt.safevault.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ttt.safevault.R;
import com.ttt.safevault.adapter.PasswordListAdapter;
import com.ttt.safevault.sync.SyncTrigger;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.utils.AnimationUtils;
import com.ttt.safevault.viewmodel.PasswordListViewModel;

import java.util.List;

/**
 * 密码列表Fragment
 * 展示所有密码条目
 */
public class PasswordListFragment extends Fragment implements PasswordListAdapter.OnItemClickListener {

    private PasswordListViewModel viewModel;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private View emptyLayout;
    private TextView emptyText;
    private TextView emptySubtext;
    private Button emptyAddButton;
    private Button clearSearchButton;
    private View loadingLayout;
    private PasswordListAdapter adapter;
    private BackendService backendService;

    // 标签筛选相关
    private ChipGroup tagsChipGroup;
    private Chip allTagsChip;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 获取BackendService实例
        backendService = com.ttt.safevault.ServiceLocator.getInstance().getBackendService();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_password_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        initViewModel();
        setupRecyclerView();
        setupObservers();
        setupSwipeRefresh();
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recycler_view);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh);
        emptyLayout = view.findViewById(R.id.empty_layout);
        emptyText = view.findViewById(R.id.empty_text);
        emptySubtext = view.findViewById(R.id.empty_subtext);
        emptyAddButton = view.findViewById(R.id.empty_add_button);
        clearSearchButton = view.findViewById(R.id.clear_search_button);
        loadingLayout = view.findViewById(R.id.loading_layout);

        // 初始化标签筛选视图
        tagsChipGroup = view.findViewById(R.id.tags_chip_group);
        allTagsChip = view.findViewById(R.id.all_tags_chip);

        // 设置"全部"标签 Chip 点击事件
        if (allTagsChip != null) {
            allTagsChip.setOnClickListener(v -> {
                viewModel.clearTagFilter();
            });
        }

        // 设置空状态按钮点击事件
        if (emptyAddButton != null) {
            emptyAddButton.setOnClickListener(v -> {
                AnimationUtils.buttonPressFeedback(v);
                navigateToAddPassword();
            });
        }

        // 设置清除搜索按钮点击事件
        if (clearSearchButton != null) {
            clearSearchButton.setOnClickListener(v -> {
                AnimationUtils.buttonPressFeedback(v);
                viewModel.clearSearch();
            });
        }
    }

    private void navigateToAddPassword() {
        // 导航到添加密码页面（passwordId = -1 表示新增）
        Bundle bundle = new Bundle();
        bundle.putInt("passwordId", -1);

        Navigation.findNavController(requireView())
                .navigate(R.id.action_passwordListFragment_to_editPasswordFragment, bundle);
    }

    private void initViewModel() {
        // 通过ViewModelFactory创建ViewModel
        ViewModelProvider.Factory factory = new com.ttt.safevault.viewmodel.ViewModelFactory(requireActivity().getApplication());
        viewModel = new ViewModelProvider(requireActivity(), factory).get(PasswordListViewModel.class);
    }

    private void setupRecyclerView() {
        adapter = new PasswordListAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        // 添加分割线装饰
        // recyclerView.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
    }

    private void setupObservers() {
        // 观察密码列表
        viewModel.passwordItems.observe(getViewLifecycleOwner(), items -> {
            adapter.submitList(items);
            updateEmptyState(items);
        });

        // 观察所有标签
        viewModel.allTags.observe(getViewLifecycleOwner(), tags -> {
            updateTagsChips(tags);
        });

        // 观察选中的标签
        viewModel.selectedTag.observe(getViewLifecycleOwner(), selectedTag -> {
            updateSelectedTagChip(selectedTag);
        });

        // 观察加载状态
        viewModel.isLoading.observe(getViewLifecycleOwner(), isLoading -> {
            swipeRefreshLayout.setRefreshing(isLoading);
            if (loadingLayout != null) {
                loadingLayout.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            }
        });

        // 观察错误信息
        viewModel.errorMessage.observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                showError(error);
                viewModel.clearError();
            }
        });

        // 观察空状态
        viewModel.isEmpty.observe(getViewLifecycleOwner(), isEmpty -> {
            if (isEmpty != null) {
                updateEmptyState(isEmpty);
            }
        });
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            // 触发云端同步
            SyncTrigger.getInstance(requireContext()).triggerSyncOnRefresh();
            // 同时刷新本地数据
            viewModel.refresh();
        });
    }

    private void updateEmptyState(List<PasswordItem> items) {
        boolean isEmpty = items == null || items.isEmpty();

        if (emptyLayout != null) {
            emptyLayout.setVisibility(isEmpty ? View.VISIBLE : View.GONE);

            if (isEmpty && emptyText != null) {
                Boolean isSearching = viewModel.isSearching.getValue();
                boolean isSearchActive = isSearching != null && isSearching;

                if (isSearchActive) {
                    // 搜索无结果状态
                    emptyText.setText(R.string.no_search_results);
                    if (emptySubtext != null) {
                        emptySubtext.setText(R.string.no_search_results_description);
                    }
                    if (emptyAddButton != null) {
                        emptyAddButton.setVisibility(View.GONE);
                    }
                    if (clearSearchButton != null) {
                        clearSearchButton.setVisibility(View.VISIBLE);
                    }
                } else {
                    // 空密码库状态
                    emptyText.setText(R.string.no_passwords);
                    if (emptySubtext != null) {
                        emptySubtext.setText(R.string.add_first_password);
                    }
                    if (emptyAddButton != null) {
                        emptyAddButton.setVisibility(View.VISIBLE);
                    }
                    if (clearSearchButton != null) {
                        clearSearchButton.setVisibility(View.GONE);
                    }
                }

                // 添加淡入动画
                if (emptyLayout.getVisibility() == View.VISIBLE) {
                    AnimationUtils.fadeIn(emptyLayout, 300);
                }
            }
        }

        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void updateEmptyState(boolean isEmpty) {
        if (emptyLayout != null) {
            emptyLayout.setVisibility(isEmpty ? View.VISIBLE : View.GONE);

            if (isEmpty && emptyText != null) {
                Boolean isSearching = viewModel.isSearching.getValue();
                boolean isSearchActive = isSearching != null && isSearching;

                if (isSearchActive) {
                    // 搜索无结果状态
                    emptyText.setText(R.string.no_search_results);
                    if (emptySubtext != null) {
                        emptySubtext.setText(R.string.no_search_results_description);
                    }
                    if (emptyAddButton != null) {
                        emptyAddButton.setVisibility(View.GONE);
                    }
                    if (clearSearchButton != null) {
                        clearSearchButton.setVisibility(View.VISIBLE);
                    }
                } else {
                    // 空密码库状态
                    emptyText.setText(R.string.no_passwords);
                    if (emptySubtext != null) {
                        emptySubtext.setText(R.string.add_first_password);
                    }
                    if (emptyAddButton != null) {
                        emptyAddButton.setVisibility(View.VISIBLE);
                    }
                    if (clearSearchButton != null) {
                        clearSearchButton.setVisibility(View.GONE);
                    }
                }

                // 添加淡入动画
                if (emptyLayout.getVisibility() == View.VISIBLE) {
                    AnimationUtils.fadeIn(emptyLayout, 300);
                }
            }
        }

        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void showError(String error) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("错误")
                .setMessage(error)
                .setPositiveButton("确定", null)
                .show();
    }

    /**
     * 显示更多选项弹出菜单（使用 Material Design 3 样式）
     */
    private void showMoreOptionsMenu(PasswordItem item, View anchorView) {
        String[] options = {"复制密码", "编辑", "删除"};

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(item.getDisplayName())
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            onItemCopyClick(item);
                            break;
                        case 1:
                            onItemEditClick(item);
                            break;
                        case 2:
                            onItemDeleteClick(item);
                            break;
                    }
                })
                .show();
    }

    // PasswordListAdapter.OnItemClickListener 实现

    @Override
    public void onItemClick(PasswordItem item) {
        // 导航到密码详情页面
        Bundle bundle = new Bundle();
        bundle.putInt("passwordId", item.getId());

        Navigation.findNavController(requireView())
                .navigate(R.id.action_passwordListFragment_to_passwordDetailFragment, bundle);
    }

    @Override
    public void onItemCopyClick(PasswordItem item) {
        viewModel.copyPassword(item.getId());
    }

    @Override
    public void onItemEditClick(PasswordItem item) {
        // 导航到编辑页面
        Bundle bundle = new Bundle();
        bundle.putInt("passwordId", item.getId());

        Navigation.findNavController(requireView())
                .navigate(R.id.action_passwordListFragment_to_editPasswordFragment, bundle);
    }

    @Override
    public void onItemDeleteClick(PasswordItem item) {
        // 确认删除对话框
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除密码")
                .setMessage("确定要删除 \"" + item.getDisplayName() + "\" 吗？此操作无法撤销。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    viewModel.deletePasswordItem(item.getId());
                })
                .show();
    }

    @Override
    public void onItemLongClick(PasswordItem item) {
        // 长按显示上下文菜单（可以添加振动反馈）
        showItemContextMenu(item);
    }

    @Override
    public void onItemMoreClick(PasswordItem item, View anchorView) {
        // 点击更多选项按钮显示弹出菜单
        showMoreOptionsMenu(item, anchorView);
    }

    /**
     * 显示项目上下文菜单
     */
    private void showItemContextMenu(PasswordItem item) {
        // 显示复制、编辑、删除选项
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(item.getDisplayName())
                .setItems(new CharSequence[]{"复制密码", "编辑", "删除"}, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            onItemCopyClick(item);
                            break;
                        case 1:
                            onItemEditClick(item);
                            break;
                        case 2:
                            onItemDeleteClick(item);
                            break;
                    }
                })
                .show();
    }

    /**
     * 更新标签 Chip 列表
     */
    private void updateTagsChips(List<String> tags) {
        if (tagsChipGroup == null) return;

        // 清除除了"全部"以外的所有 Chip
        for (int i = tagsChipGroup.getChildCount() - 1; i >= 0; i--) {
            View child = tagsChipGroup.getChildAt(i);
            if (child instanceof Chip && child != allTagsChip) {
                tagsChipGroup.removeViewAt(i);
            }
        }

        // 添加新的标签 Chip
        for (String tag : tags) {
            Chip chip = new Chip(requireContext());
            chip.setText(tag);
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setFocusable(true);
            chip.setChipIconVisible(false);

            // 设置点击事件
            chip.setOnClickListener(v -> {
                viewModel.filterByTag(tag);
            });

            tagsChipGroup.addView(chip);
        }
    }

    /**
     * 更新选中的标签 Chip 状态
     */
    private void updateSelectedTagChip(String selectedTag) {
        if (tagsChipGroup == null) return;

        // 如果没有选中标签，选中"全部"
        if (selectedTag == null || selectedTag.isEmpty()) {
            if (allTagsChip != null) {
                allTagsChip.setChecked(true);
            }
            // 取消其他标签的选中状态
            for (int i = 0; i < tagsChipGroup.getChildCount(); i++) {
                View child = tagsChipGroup.getChildAt(i);
                if (child instanceof Chip && child != allTagsChip) {
                    ((Chip) child).setChecked(false);
                }
            }
        } else {
            // 取消"全部"的选中状态
            if (allTagsChip != null) {
                allTagsChip.setChecked(false);
            }
            // 选中对应的标签
            for (int i = 0; i < tagsChipGroup.getChildCount(); i++) {
                View child = tagsChipGroup.getChildAt(i);
                if (child instanceof Chip && child != allTagsChip) {
                    Chip chip = (Chip) child;
                    if (chip.getText().toString().equals(selectedTag)) {
                        chip.setChecked(true);
                    } else {
                        chip.setChecked(false);
                    }
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // 每次返回时静默刷新数据（不显示加载动画）
        viewModel.refreshSilently();
    }
}