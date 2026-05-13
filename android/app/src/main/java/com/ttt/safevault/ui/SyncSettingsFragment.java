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
import com.ttt.safevault.sync.SyncState;
import com.ttt.safevault.sync.SyncStatus;
import com.ttt.safevault.viewmodel.SyncSettingsViewModel;
import com.ttt.safevault.viewmodel.ViewModelFactory;

public class SyncSettingsFragment extends BaseFragment {

    private FragmentSyncSettingsBinding binding;
    private SyncSettingsViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSyncSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ViewModelProvider.Factory factory = new ViewModelFactory(requireActivity().getApplication());
        viewModel = new ViewModelProvider(this, factory).get(SyncSettingsViewModel.class);

        setupSyncControls();
        observeSyncState();
        observeSyncConfig();
        observeMessages();
        observeConflicts();
    }

    private void setupSyncControls() {
        binding.switchSyncEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            viewModel.updateSyncEnabled(isChecked);
        });

        binding.switchWifiOnly.setOnCheckedChangeListener((buttonView, isChecked) -> {
            viewModel.updateWifiOnly(isChecked);
        });

        binding.cardSyncInterval.setOnClickListener(v -> showIntervalDialog());
        binding.btnManualSync.setOnClickListener(v -> viewModel.performManualSync());
    }

    private void showIntervalDialog() {
        SyncConfig config = viewModel.getCurrentConfig();
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
                viewModel.updateInterval(values[which]);
                updateIntervalText(values[which]);
                Toast.makeText(requireContext(), "同步间隔已设置为 " + intervals[which], Toast.LENGTH_SHORT).show();
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
        viewModel.syncState.observe(getViewLifecycleOwner(), this::updateSyncStateUI);
    }

    private void observeSyncConfig() {
        viewModel.syncConfig.observe(getViewLifecycleOwner(), this::updateSyncConfigUI);
    }

    private void observeMessages() {
        viewModel.message.observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                viewModel.clearMessage();
            }
        });
    }

    private void observeConflicts() {
        viewModel.conflict.observe(getViewLifecycleOwner(), conflict -> {
            if (conflict != null) {
                showConflictDialog(conflict.cloudVersion, conflict.localVersion);
                viewModel.clearConflict();
            }
        });
    }

    private void showConflictDialog(long cloudVersion, long localVersion) {
        if (!isAdded() || getContext() == null) return;

        try {
            new MaterialAlertDialogBuilder(requireContext())
                .setTitle("数据冲突")
                .setMessage("检测到数据冲突：\n云端版本: " + cloudVersion + "\n本地版本: " + localVersion + "\n\n请选择如何解决：")
                .setPositiveButton("使用云端数据", (dialog, which) -> {
                    viewModel.resolveWithCloud();
                })
                .setNegativeButton("使用本地数据", (dialog, which) -> {
                    viewModel.resolveWithLocal();
                })
                .setNeutralButton("取消", (dialog, which) -> {
                    viewModel.resolveCancel();
                })
                .show();
        } catch (Exception e) {
            android.util.Log.e("SyncSettingsFragment", "Failed to show conflict dialog", e);
        }
    }

    private void updateSyncStateUI(SyncState state) {
        if (state == null) return;

        SyncStatus status = state.getStatus();
        if (status == null) return;

        switch (status) {
            case SYNCING:
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
