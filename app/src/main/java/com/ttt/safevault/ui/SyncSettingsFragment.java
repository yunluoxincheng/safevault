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
import com.ttt.safevault.sync.SyncScheduler;
import com.ttt.safevault.sync.SyncStateManager;
import com.ttt.safevault.sync.SyncStatus;
import com.ttt.safevault.sync.SyncState;
import com.ttt.safevault.sync.VaultSyncManager;
import com.ttt.safevault.viewmodel.ViewModelFactory;

/**
 * 云端同步设置 Fragment
 */
public class SyncSettingsFragment extends BaseFragment {

    private FragmentSyncSettingsBinding binding;
    private SyncStateManager syncStateManager;
    private VaultSyncManager vaultSyncManager;
    private SyncScheduler syncScheduler;
    private ViewModelFactory viewModelFactory;

    // 用于跟踪是否正在处理冲突
    private boolean isHandlingConflict = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSyncSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 初始化管理器
        syncStateManager = SyncStateManager.getInstance();
        vaultSyncManager = VaultSyncManager.getInstance(requireContext());
        syncScheduler = SyncScheduler.getInstance(requireContext());
        viewModelFactory = new ViewModelFactory(requireActivity().getApplication());

        setupSyncControls();
        observeSyncState();
        observeSyncConfig();
    }

    /**
     * 设置同步控件和点击监听器
     */
    private void setupSyncControls() {
        // 同步开关切换
        binding.switchSyncEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SyncConfig config = syncStateManager.getCurrentConfig();
            if (config != null) {
                config.setSyncEnabled(isChecked);
                syncStateManager.updateConfig(config);

                // 根据启用状态调度或取消同步
                if (isChecked) {
                    syncScheduler.scheduleSync();
                    Toast.makeText(requireContext(), "自动同步已启用", Toast.LENGTH_SHORT).show();
                } else {
                    syncScheduler.cancelScheduledSync();
                    Toast.makeText(requireContext(), "自动同步已禁用", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // WiFi 限制开关切换
        binding.switchWifiOnly.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SyncConfig config = syncStateManager.getCurrentConfig();
            if (config != null) {
                config.setWifiOnly(isChecked);
                syncStateManager.updateConfig(config);

                // 重新调度同步以应用新的 WiFi 限制
                syncScheduler.rescheduleSync();

                String message = isChecked ? "已启用仅 WiFi 同步" : "已允许使用移动数据同步";
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });

        // 同步间隔选择
        binding.cardSyncInterval.setOnClickListener(v -> showIntervalDialog());

        // 手动同步按钮
        binding.btnManualSync.setOnClickListener(v -> performManualSync());
    }

    /**
     * 显示同步间隔选择对话框
     */
    private void showIntervalDialog() {
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

                    // 重新调度同步以应用新的间隔
                    syncScheduler.rescheduleSync();

                    String message = "同步间隔已设置为 " + intervals[which];
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                }
                dialog.dismiss();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * 更新间隔文本显示
     */
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

    /**
     * 执行手动同步
     */
    private void performManualSync() {
        // 检查同步是否启用
        SyncConfig config = syncStateManager.getCurrentConfig();
        if (config != null && !config.isSyncEnabled()) {
            Toast.makeText(requireContext(), "请先启用同步功能", Toast.LENGTH_SHORT).show();
            return;
        }

        // 重置冲突处理标志
        isHandlingConflict = false;

        Toast.makeText(requireContext(), "开始同步...", Toast.LENGTH_SHORT).show();

        // 调用同步管理器执行同步
        vaultSyncManager.syncNow(new VaultSyncManager.SyncCallback() {
            @Override
            public void onSyncSuccess(long newVersion) {
                // 成功会在 observeSyncState 中处理
                Toast.makeText(requireContext(), "同步成功，版本: " + newVersion, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSyncConflict(long cloudVersion, long localVersion) {
                android.util.Log.d("SyncSettingsFragment", "Sync conflict callback received - cloud: " + cloudVersion + ", local: " + localVersion + ", isHandlingConflict: " + isHandlingConflict);
                // 显示冲突对话框
                if (!isHandlingConflict) {
                    isHandlingConflict = true;
                    showConflictDialog(cloudVersion, localVersion);
                } else {
                    android.util.Log.w("SyncSettingsFragment", "Conflict dialog skipped because isHandlingConflict is already true");
                }
            }

            @Override
            public void onSyncFailure(String errorMessage) {
                // 失败会在 observeSyncState 中处理
                Toast.makeText(requireContext(), "同步失败: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * 显示冲突解决对话框
     */
    private void showConflictDialog(long cloudVersion, long localVersion) {
        android.util.Log.d("SyncSettingsFragment", "Attempting to show conflict dialog...");
        // 检查 Fragment 是否仍然附加到 Activity
        if (!isAdded() || getContext() == null) {
            android.util.Log.e("SyncSettingsFragment", "Cannot show dialog - Fragment is not added or context is null");
            isHandlingConflict = false;
            return;
        }

        try {
            new MaterialAlertDialogBuilder(requireContext())
                .setTitle("数据冲突")
                .setMessage("检测到数据冲突：\n云端版本: " + cloudVersion + "\n本地版本: " + localVersion + "\n\n请选择如何解决：")
                .setPositiveButton("使用云端数据", (dialog, which) -> {
                    android.util.Log.d("SyncSettingsFragment", "User chose USE_CLOUD");
                    resolveConflict(VaultSyncManager.SyncStrategy.USE_CLOUD);
                })
                .setNegativeButton("使用本地数据", (dialog, which) -> {
                    android.util.Log.d("SyncSettingsFragment", "User chose USE_LOCAL");
                    resolveConflict(VaultSyncManager.SyncStrategy.USE_LOCAL);
                })
                .setNeutralButton("取消", (dialog, which) -> {
                    android.util.Log.d("SyncSettingsFragment", "User chose CANCEL");
                    resolveConflict(VaultSyncManager.SyncStrategy.CANCEL);
                })
                .setOnDismissListener(dialog -> {
                    android.util.Log.d("SyncSettingsFragment", "Conflict dialog dismissed");
                    isHandlingConflict = false;
                })
                .show();
            android.util.Log.d("SyncSettingsFragment", "Conflict dialog shown successfully");
        } catch (Exception e) {
            android.util.Log.e("SyncSettingsFragment", "Failed to show conflict dialog", e);
            isHandlingConflict = false;
        }
    }

    /**
     * 解决同步冲突
     */
    private void resolveConflict(VaultSyncManager.SyncStrategy strategy) {
        Toast.makeText(requireContext(), "正在解决冲突...", Toast.LENGTH_SHORT).show();

        vaultSyncManager.resolveConflict(strategy, new VaultSyncManager.SyncCallback() {
            @Override
            public void onSyncSuccess(long newVersion) {
                String message = "冲突已解决，版本: " + newVersion;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSyncConflict(long cloudVersion, long localVersion) {
                // 不应该再次发生冲突
                Toast.makeText(requireContext(), "冲突解决失败", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSyncFailure(String errorMessage) {
                Toast.makeText(requireContext(), "冲突解决失败: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void observeSyncState() {
        syncStateManager.getSyncState().observe(getViewLifecycleOwner(), this::updateSyncStateUI);
    }

    private void observeSyncConfig() {
        syncStateManager.getSyncConfig().observe(getViewLifecycleOwner(), this::updateSyncConfigUI);
    }

    /**
     * 更新同步状态 UI
     */
    private void updateSyncStateUI(SyncState state) {
        if (state == null) return;

        SyncStatus status = state.getStatus();
        if (status == null) return;

        // 根据状态更新 UI
        switch (status) {
            case SYNCING:
                // 同步中：显示进度，禁用控件
                binding.tvSyncStatus.setText("正在同步...");
                binding.tvSyncStatus.setTextColor(getResources().getColor(R.color.blue_500, null));
                binding.syncProgressBar.setVisibility(View.VISIBLE);
                binding.syncStatusIndicator.setVisibility(View.VISIBLE);
                binding.btnManualSync.setEnabled(false);
                binding.switchSyncEnabled.setEnabled(false);
                binding.cardSyncInterval.setEnabled(false);
                binding.switchWifiOnly.setEnabled(false);
                break;

            case SUCCESS:
                // 同步成功：显示成功消息，更新最后同步时间
                binding.tvSyncStatus.setText("同步成功");
                binding.tvSyncStatus.setTextColor(getResources().getColor(R.color.green_500, null));
                binding.syncProgressBar.setVisibility(View.GONE);
                binding.syncStatusIndicator.setVisibility(View.GONE);
                binding.btnManualSync.setEnabled(true);
                binding.switchSyncEnabled.setEnabled(true);
                binding.cardSyncInterval.setEnabled(true);
                binding.switchWifiOnly.setEnabled(true);

                if (state.getLastSyncTime() != null) {
                    binding.tvLastSyncTime.setText(formatSyncTime(state.getLastSyncTime()));
                }
                break;

            case FAILED:
                // 同步失败：显示错误消息
                binding.tvSyncStatus.setText("同步失败");
                binding.tvSyncStatus.setTextColor(getResources().getColor(R.color.red_500, null));
                binding.syncProgressBar.setVisibility(View.GONE);
                binding.syncStatusIndicator.setVisibility(View.GONE);
                binding.btnManualSync.setEnabled(true);
                binding.switchSyncEnabled.setEnabled(true);
                binding.cardSyncInterval.setEnabled(true);
                binding.switchWifiOnly.setEnabled(true);

                if (state.getErrorMessage() != null) {
                    Toast.makeText(requireContext(), state.getErrorMessage(), Toast.LENGTH_LONG).show();
                }
                break;

            case CONFLICT:
                // 发生冲突：显示冲突状态
                binding.tvSyncStatus.setText("数据冲突");
                binding.tvSyncStatus.setTextColor(getResources().getColor(R.color.orange_500, null));
                binding.syncProgressBar.setVisibility(View.GONE);
                binding.syncStatusIndicator.setVisibility(View.GONE);
                binding.btnManualSync.setEnabled(true);
                binding.switchSyncEnabled.setEnabled(true);
                binding.cardSyncInterval.setEnabled(true);
                binding.switchWifiOnly.setEnabled(true);
                break;

            case OFFLINE:
                // 离线状态：显示离线指示器
                binding.tvSyncStatus.setText("离线状态");
                binding.tvSyncStatus.setTextColor(getResources().getColor(R.color.gray_500, null));
                binding.syncProgressBar.setVisibility(View.GONE);
                binding.syncStatusIndicator.setVisibility(View.GONE);
                binding.btnManualSync.setEnabled(true);
                binding.switchSyncEnabled.setEnabled(true);
                binding.cardSyncInterval.setEnabled(true);
                binding.switchWifiOnly.setEnabled(true);
                break;

            case IDLE:
            default:
                // 空闲状态：恢复正常显示
                binding.tvSyncStatus.setText("就绪");
                binding.tvSyncStatus.setTextColor(getResources().getColor(R.color.gray_500, null));
                binding.syncProgressBar.setVisibility(View.GONE);
                binding.syncStatusIndicator.setVisibility(View.GONE);
                binding.btnManualSync.setEnabled(true);
                binding.switchSyncEnabled.setEnabled(true);
                binding.cardSyncInterval.setEnabled(true);
                binding.switchWifiOnly.setEnabled(true);
                break;
        }

        // 更新版本号显示
        if (state.getClientVersion() != null) {
            binding.tvClientVersion.setText(String.valueOf(state.getClientVersion()));
        } else {
            binding.tvClientVersion.setText("-");
        }

        if (state.getServerVersion() != null) {
            binding.tvServerVersion.setText(String.valueOf(state.getServerVersion()));
        } else {
            binding.tvServerVersion.setText("-");
        }

        // 更新最后同步时间（如果不是同步状态）
        if (status != SyncStatus.SYNCING && state.getLastSyncTime() != null) {
            binding.tvLastSyncTime.setText(formatSyncTime(state.getLastSyncTime()));
        } else if (status != SyncStatus.SYNCING) {
            binding.tvLastSyncTime.setText("从未同步");
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
