package com.ttt.safevault.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ttt.safevault.R;
import com.ttt.safevault.databinding.FragmentSyncSettingsBinding;
import com.ttt.safevault.sync.SyncConfig;
import com.ttt.safevault.sync.SyncStateManager;
import com.ttt.safevault.sync.SyncStatus;
import com.ttt.safevault.sync.SyncState;
import com.ttt.safevault.viewmodel.ViewModelFactory;

/**
 * 云端同步设置 Fragment
 */
public class SyncSettingsFragment extends BaseFragment {

    private FragmentSyncSettingsBinding binding;
    private SyncStateManager syncStateManager;
    private ViewModelFactory viewModelFactory;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSyncSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        syncStateManager = SyncStateManager.getInstance();
        viewModelFactory = new ViewModelFactory(requireActivity().getApplication());

        setupClickListeners();
        observeSyncState();
        observeSyncConfig();
    }

    private void setupClickListeners() {
        // 同步开关
        binding.switchSyncEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SyncConfig config = syncStateManager.getCurrentConfig();
            if (config != null) {
                config.setSyncEnabled(isChecked);
                syncStateManager.updateConfig(config);
            }
        });

        // WiFi 限制开关
        binding.switchWifiOnly.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SyncConfig config = syncStateManager.getCurrentConfig();
            if (config != null) {
                config.setWifiOnly(isChecked);
                syncStateManager.updateConfig(config);
            }
        });

        // 同步间隔选择
        binding.cardSyncInterval.setOnClickListener(v -> showSyncIntervalDialog());

        // 手动同步
        binding.btnManualSync.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "开始同步...", Toast.LENGTH_SHORT).show();
            // TODO: 调用同步管理器执行同步
        });
    }

    private void showSyncIntervalDialog() {
        SyncConfig config = syncStateManager.getCurrentConfig();
        int currentInterval = config != null ? config.getSyncIntervalMinutes() : SyncConfig.INTERVAL_30_MINUTES;

        String[] intervals = {"30 分钟", "1 小时", "2 小时", "4 小时", "仅手动同步"};
        int[] values = {
            SyncConfig.INTERVAL_30_MINUTES,
            SyncConfig.INTERVAL_1_HOUR,
            SyncConfig.INTERVAL_2_HOURS,
            SyncConfig.INTERVAL_4_HOURS,
            SyncConfig.INTERVAL_MANUAL_ONLY
        };

        int checkedIndex = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == currentInterval) {
                checkedIndex = i;
                break;
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("选择同步间隔")
            .setSingleChoiceItems(intervals, checkedIndex, (dialog, which) -> {
                if (config != null) {
                    config.setSyncIntervalMinutes(values[which]);
                    syncStateManager.updateConfig(config);
                    updateIntervalText(values[which]);
                }
                dialog.dismiss();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void updateIntervalText(int intervalMinutes) {
        String text;
        if (intervalMinutes == SyncConfig.INTERVAL_MANUAL_ONLY) {
            text = "仅手动同步";
        } else if (intervalMinutes < 60) {
            text = intervalMinutes + " 分钟";
        } else {
            text = (intervalMinutes / 60) + " 小时";
        }
        binding.tvSyncIntervalValue.setText(text);
    }

    private void observeSyncState() {
        syncStateManager.getSyncState().observe(getViewLifecycleOwner(), this::updateSyncStateUI);
    }

    private void observeSyncConfig() {
        syncStateManager.getSyncConfig().observe(getViewLifecycleOwner(), this::updateSyncConfigUI);
    }

    private void updateSyncStateUI(SyncState state) {
        if (state == null) return;

        // 更新状态信息
        if (state.getLastSyncTime() != null) {
            binding.tvLastSyncTime.setText(formatSyncTime(state.getLastSyncTime()));
        } else {
            binding.tvLastSyncTime.setText("从未同步");
        }

        if (state.getClientVersion() != null) {
            binding.tvClientVersion.setText(String.valueOf(state.getClientVersion()));
        }

        if (state.getServerVersion() != null) {
            binding.tvServerVersion.setText(String.valueOf(state.getServerVersion()));
        }
    }

    private void updateSyncConfigUI(SyncConfig config) {
        if (config == null) return;

        binding.switchSyncEnabled.setChecked(config.isSyncEnabled());
        binding.switchWifiOnly.setChecked(config.isWifiOnly());
        updateIntervalText(config.getSyncIntervalMinutes());
    }

    private String formatSyncTime(String timeString) {
        // 简单格式化时间
        try {
            long time = Long.parseLong(timeString);
            long now = System.currentTimeMillis();
            long diff = now - time;

            if (diff < 60000) {
                return "刚刚";
            } else if (diff < 3600000) {
                return (diff / 60000) + " 分钟前";
            } else if (diff < 86400000) {
                return (diff / 3600000) + " 小时前";
            } else {
                return (diff / 86400000) + " 天前";
            }
        } catch (Exception e) {
            return timeString;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
