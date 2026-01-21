package com.ttt.safevault.ui.share;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ttt.safevault.R;
import com.ttt.safevault.adapter.ShareHistoryAdapter;
import com.ttt.safevault.dto.response.ReceivedShareResponse;
import com.ttt.safevault.model.PasswordShare;
import com.ttt.safevault.network.TokenManager;
import com.ttt.safevault.viewmodel.ShareHistoryViewModel;
import com.ttt.safevault.viewmodel.ViewModelFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 分享列表Fragment
 * 用于显示我的分享或接收的分享
 * 仅支持云端联系人分享
 */
public class ShareListFragment extends Fragment {

    private static final String ARG_IS_MY_SHARES = "is_my_shares";

    private ShareHistoryViewModel viewModel;
    private ShareHistoryAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private View emptyView;
    private boolean isMyShares;
    private TokenManager tokenManager;

    public static ShareListFragment newInstance(boolean isMyShares) {
        ShareListFragment fragment = new ShareListFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_IS_MY_SHARES, isMyShares);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            isMyShares = getArguments().getBoolean(ARG_IS_MY_SHARES, true);
        }
        tokenManager = TokenManager.getInstance(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_share_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        initViewModel();
        setupRecyclerView();
        setupObservers();

        // 静默加载数据（首次进入不显示加载动画）
        loadDataSilently();
    }

    private void initViews(View view) {
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        emptyView = view.findViewById(R.id.empty_view);

        // 下拉刷新
        swipeRefresh.setOnRefreshListener(this::loadData);
    }

    private void initViewModel() {
        ViewModelFactory factory = new ViewModelFactory(
            requireActivity().getApplication(),
            com.ttt.safevault.ServiceLocator.getInstance().getBackendService()
        );
        viewModel = new ViewModelProvider(this, factory).get(ShareHistoryViewModel.class);
    }

    private void setupRecyclerView() {
        adapter = new ShareHistoryAdapter(isMyShares);
        adapter.setOnShareActionListener(new ShareHistoryAdapter.OnShareActionListener() {
            @Override
            public void onViewShare(PasswordShare share) {
                viewShareDetails(share);
            }

            @Override
            public void onRevokeShare(PasswordShare share) {
                confirmRevokeShare(share);
            }
        });

        RecyclerView recyclerView = requireView().findViewById(R.id.recycler_view);
        recyclerView.setAdapter(adapter);
    }

    private void setupObservers() {
        // 观察云端分享列表
        if (isMyShares) {
            viewModel.cloudMyShares.observe(getViewLifecycleOwner(), shares -> {
                adapter.setCloudShareList(shares);
                updateEmptyView(shares == null || shares.isEmpty());
            });
        } else {
            viewModel.cloudReceivedShares.observe(getViewLifecycleOwner(), shares -> {
                adapter.setCloudShareList(shares);
                updateEmptyView(shares == null || shares.isEmpty());
            });
        }

        // 观察加载状态
        viewModel.isLoading.observe(getViewLifecycleOwner(), isLoading -> {
            swipeRefresh.setRefreshing(isLoading != null && isLoading);
        });

        // 观察错误信息
        viewModel.errorMessage.observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
                viewModel.clearError();
            }
        });

        // 观察撤销成功
        viewModel.revokeSuccess.observe(getViewLifecycleOwner(), success -> {
            if (success) {
                Toast.makeText(requireContext(), "分享已撤销", Toast.LENGTH_SHORT).show();
                loadDataSilently();
            }
        });

        // 观察操作成功
        viewModel.operationSuccess.observe(getViewLifecycleOwner(), success -> {
            if (success) {
                loadDataSilently();
            }
        });
    }

    private void loadData() {
        // 仅加载云端联系人分享
        if (isMyShares) {
            viewModel.loadCloudMyShares();
        } else {
            viewModel.loadCloudReceivedShares();
        }
    }

    private void updateEmptyView(boolean isEmpty) {
        emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    private void viewShareDetails(PasswordShare share) {
        // 跳转到接收分享页面查看详情
        Intent intent = new Intent(requireContext(), ReceiveShareActivity.class);
        intent.putExtra("SHARE_ID", share.getShareId());
        startActivity(intent);
    }

    private void confirmRevokeShare(PasswordShare share) {
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("撤销分享")
            .setMessage("确定要撤销这个分享吗？对方将无法再访问此密码。")
            .setNegativeButton("取消", null)
            .setPositiveButton("撤销", (dialog, which) -> {
                viewModel.revokeCloudShare(share.getShareId());
            })
            .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 静默刷新数据（不显示加载动画）
        loadDataSilently();
    }

    private void loadDataSilently() {
        // 仅加载云端联系人分享
        if (isMyShares) {
            viewModel.loadCloudMySharesSilently();
        } else {
            viewModel.loadCloudReceivedSharesSilently();
        }
    }
}
