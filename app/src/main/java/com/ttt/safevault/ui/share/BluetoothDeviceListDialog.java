package com.ttt.safevault.ui.share;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.ttt.safevault.R;
import com.ttt.safevault.utils.BluetoothTransferManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 蓝牙设备选择对话框
 * 显示已配对设备并可扫描新设备
 * 区分显示已配对和新发现的设备
 */
public class BluetoothDeviceListDialog extends DialogFragment {

    public static final String TAG = "BluetoothDeviceListDialog";
    private static final String ARG_PASSWORD_ID = "password_id";
    private static final int REQUEST_ENABLE_BT = 100;

    public interface OnDeviceSelectedListener {
        void onDeviceSelected(BluetoothDevice device);
    }

    /**
     * 设备项，包含设备和配对状态
     */
    private static class DeviceItem {
        final BluetoothDevice device;
        final boolean isPaired;

        DeviceItem(BluetoothDevice device, boolean isPaired) {
            this.device = device;
            this.isPaired = isPaired;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            DeviceItem deviceItem = (DeviceItem) obj;
            return device != null && device.equals(deviceItem.device);
        }

        @Override
        public int hashCode() {
            return device != null ? device.hashCode() : 0;
        }
    }

    private int passwordId;
    private OnDeviceSelectedListener listener;
    private BluetoothTransferManager bluetoothManager;
    private DeviceAdapter adapter;
    private List<DeviceItem> devices = new ArrayList<>();
    private ProgressBar progressBar;
    private TextView textStatus;
    private boolean isScanning = false;

    private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    DeviceItem item = new DeviceItem(device, false);
                    if (!devices.contains(item)) {
                        devices.add(item);
                        adapter.notifyDataSetChanged();
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                isScanning = false;
                updateScanningState();
            }
        }
    };

    public static BluetoothDeviceListDialog newInstance(int passwordId) {
        BluetoothDeviceListDialog dialog = new BluetoothDeviceListDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_PASSWORD_ID, passwordId);
        dialog.setArguments(args);
        return dialog;
    }

    public void setOnDeviceSelectedListener(OnDeviceSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            passwordId = getArguments().getInt(ARG_PASSWORD_ID, -1);
        }
        bluetoothManager = new BluetoothTransferManager(requireContext());

        // 注册广播接收器
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        requireContext().registerReceiver(discoveryReceiver, filter);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_bluetooth_devices, container, false);
        initViews(view);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        android.app.Dialog dialog = getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = WindowManager.LayoutParams.MATCH_PARENT;
                window.setAttributes(params);
            }
        }
    }

    private void initViews(View view) {
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        progressBar = view.findViewById(R.id.progressBar);
        textStatus = view.findViewById(R.id.textStatus);
        Button btnScan = view.findViewById(R.id.btnScan);
        Button btnCancel = view.findViewById(R.id.btnCancel);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new DeviceAdapter(devices, this::onDeviceClick);
        recyclerView.setAdapter(adapter);

        btnScan.setOnClickListener(v -> toggleScanning());
        btnCancel.setOnClickListener(v -> dismiss());

        loadPairedDevices();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        requireContext().unregisterReceiver(discoveryReceiver);
        if (isScanning) {
            stopDiscovery();
        }
    }

    private void loadPairedDevices() {
        Set<BluetoothDevice> pairedDevices = bluetoothManager.getPairedDevices();
        devices.clear();
        if (pairedDevices != null && !pairedDevices.isEmpty()) {
            for (BluetoothDevice device : pairedDevices) {
                devices.add(new DeviceItem(device, true));
            }
            textStatus.setText("已配对设备");
        } else {
            textStatus.setText("没有已配对的设备，请点击扫描");
        }
        adapter.notifyDataSetChanged();
    }

    private void toggleScanning() {
        if (isScanning) {
            stopDiscovery();
        } else {
            startDiscovery();
        }
    }

    private void startDiscovery() {
        if (!checkBluetoothPermissions()) {
            return;
        }

        // 清空列表并添加已配对设备
        loadPairedDevices();

        // 开始扫描
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null && adapter.isDiscovering()) {
            adapter.cancelDiscovery();
        }

        if (adapter != null && adapter.startDiscovery()) {
            isScanning = true;
            updateScanningState();
        } else {
            new MaterialAlertDialogBuilder(requireContext())
                .setTitle("扫描失败")
                .setMessage("无法启动设备扫描")
                .setPositiveButton("确定", null)
                .show();
        }
    }

    private void stopDiscovery() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null && adapter.isDiscovering()) {
            adapter.cancelDiscovery();
        }
        isScanning = false;
        updateScanningState();
    }

    private void updateScanningState() {
        if (isScanning) {
            progressBar.setVisibility(View.VISIBLE);
            textStatus.setText("正在扫描设备...");
        } else {
            progressBar.setVisibility(View.GONE);
            if (devices.isEmpty()) {
                textStatus.setText("未找到设备");
            } else {
                textStatus.setText("找到 " + devices.size() + " 个设备");
            }
        }
    }

    private boolean checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (requireContext().checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                }, 101);
                return false;
            }
        }
        return true;
    }

    private void onDeviceClick(BluetoothDevice device) {
        if (listener != null) {
            listener.onDeviceSelected(device);
        }
        dismiss();
    }

    static class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {
        private final List<DeviceItem> devices;
        private final OnDeviceClickListener listener;

        interface OnDeviceClickListener {
            void onDeviceClick(BluetoothDevice device);
        }

        DeviceAdapter(List<DeviceItem> devices, OnDeviceClickListener listener) {
            this.devices = devices;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bluetooth_device, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DeviceItem item = devices.get(position);
            holder.bind(item);
        }

        @Override
        public int getItemCount() {
            return devices.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView textDeviceName;
            private final TextView textDeviceAddress;
            private final TextView textStatus;
            private final MaterialCardView cardDevice;

            ViewHolder(View itemView) {
                super(itemView);
                textDeviceName = itemView.findViewById(R.id.textDeviceName);
                textDeviceAddress = itemView.findViewById(R.id.textDeviceAddress);
                textStatus = itemView.findViewById(R.id.textStatus);
                cardDevice = itemView.findViewById(R.id.cardDevice);
            }

            void bind(DeviceItem item) {
                BluetoothDevice device = item.device;
                String name = device.getName();
                if (name == null || name.isEmpty()) {
                    name = "未知设备";
                }
                textDeviceName.setText(name);
                textDeviceAddress.setText(device.getAddress());

                // 显示配对状态
                if (item.isPaired) {
                    textStatus.setText("已配对");
                    textStatus.setTextColor(itemView.getContext().getColor(R.color.success_green));
                } else {
                    textStatus.setText("新设备");
                    textStatus.setTextColor(itemView.getContext().getColor(R.color.primary_blue));
                }

                cardDevice.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onDeviceClick(device);
                    }
                });
            }
        }
    }
}
