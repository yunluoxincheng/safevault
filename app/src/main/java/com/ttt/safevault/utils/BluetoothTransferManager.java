package com.ttt.safevault.utils;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

/**
 * 蓝牙传输管理器
 * 负责通过蓝牙发送和接收密码分享数据
 */
public class BluetoothTransferManager {
    
    private static final String TAG = "BluetoothTransfer";
    
    // 服务名称
    private static final String SERVICE_NAME = "SafeVault_PasswordShare";
    
    // UUID - 必须在发送端和接收端保持一致
    private static final UUID SERVICE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    
    // 最大传输数据大小（1MB）
    private static final int MAX_DATA_SIZE = 1024 * 1024;
    
    private final Context context;
    private BluetoothAdapter bluetoothAdapter;
    private AcceptThread acceptThread;
    private ConnectThread connectThread;
    private TransferThread transferThread;
    private TransferCallback callback;

    /**
     * 传输回调接口
     */
    public interface TransferCallback {
        void onTransferStarted();
        void onTransferProgress(int progress);
        void onTransferSuccess(String data);
        void onTransferFailed(String error);
    }

    public BluetoothTransferManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    /**
     * 检查蓝牙是否可用
     */
    public boolean isBluetoothAvailable() {
        return bluetoothAdapter != null;
    }

    /**
     * 检查蓝牙是否已启用
     */
    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    /**
     * 检查蓝牙权限
     */
    public boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ 需要新的蓝牙权限
            return ActivityCompat.checkSelfPermission(context, 
                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, 
                Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 11 及以下使用旧的蓝牙权限
            return ActivityCompat.checkSelfPermission(context, 
                Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, 
                Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED;
        }
    }

    /**
     * 获取已配对的设备列表
     */
    @Nullable
    public Set<BluetoothDevice> getPairedDevices() {
        if (!isBluetoothAvailable() || !isBluetoothEnabled()) {
            Log.w(TAG, "Bluetooth not available or not enabled");
            return null;
        }

        if (!hasBluetoothPermissions()) {
            Log.w(TAG, "Missing bluetooth permissions");
            return null;
        }

        try {
            if (ActivityCompat.checkSelfPermission(context, 
                Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return null;
            }
            return bluetoothAdapter.getBondedDevices();
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception getting paired devices", e);
            return null;
        }
    }

    /**
     * 设置传输回调
     */
    public void setTransferCallback(@Nullable TransferCallback callback) {
        this.callback = callback;
    }

    /**
     * 开始监听连接（接收端）
     */
    public void startListening() {
        if (!isBluetoothAvailable()) {
            notifyError("蓝牙不可用");
            return;
        }

        if (!hasBluetoothPermissions()) {
            notifyError("缺少蓝牙权限");
            return;
        }

        Log.d(TAG, "Starting to listen for connections");
        
        // 停止现有的监听线程
        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }

        acceptThread = new AcceptThread();
        acceptThread.start();
    }

    /**
     * 停止监听
     */
    public void stopListening() {
        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }
    }

    /**
     * 连接到设备并发送数据（发送端）
     */
    public void sendData(@NonNull BluetoothDevice device, @NonNull String data) {
        if (!isBluetoothAvailable()) {
            notifyError("蓝牙不可用");
            return;
        }

        if (!hasBluetoothPermissions()) {
            notifyError("缺少蓝牙权限");
            return;
        }

        if (data.length() > MAX_DATA_SIZE) {
            notifyError("数据过大，无法通过蓝牙传输");
            return;
        }

        Log.d(TAG, "Connecting to device: " + device.getName());
        
        // 停止现有的连接线程
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        connectThread = new ConnectThread(device, data);
        connectThread.start();
    }

    /**
     * 关闭所有连接
     */
    public void close() {
        stopListening();
        
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        
        if (transferThread != null) {
            transferThread.cancel();
            transferThread = null;
        }
    }

    // ========== 内部线程类 ==========

    /**
     * 接受连接的线程
     */
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket serverSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                if (ActivityCompat.checkSelfPermission(context, 
                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                        SERVICE_NAME, SERVICE_UUID);
                } else {
                    Log.e(TAG, "Missing BLUETOOTH_CONNECT permission");
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to create server socket", e);
            }
            serverSocket = tmp;
        }

        @Override
        public void run() {
            BluetoothSocket socket = null;
            
            while (true) {
                try {
                    Log.d(TAG, "Waiting for connection...");
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Accept failed", e);
                    break;
                }

                if (socket != null) {
                    Log.d(TAG, "Connection accepted");
                    connected(socket);
                    try {
                        serverSocket.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to close server socket", e);
                    }
                    break;
                }
            }
        }

        public void cancel() {
            try {
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to close server socket", e);
            }
        }
    }

    /**
     * 发起连接的线程
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket socket;
        private final BluetoothDevice device;
        private final String dataToSend;

        public ConnectThread(BluetoothDevice device, String data) {
            this.device = device;
            this.dataToSend = data;
            BluetoothSocket tmp = null;

            try {
                if (ActivityCompat.checkSelfPermission(context, 
                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    tmp = device.createRfcommSocketToServiceRecord(SERVICE_UUID);
                } else {
                    Log.e(TAG, "Missing BLUETOOTH_CONNECT permission");
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to create socket", e);
            }
            socket = tmp;
        }

        @Override
        public void run() {
            if (socket == null) {
                notifyError("无法创建蓝牙连接");
                return;
            }

            try {
                // 取消发现设备，以提高连接速度
                if (ActivityCompat.checkSelfPermission(context, 
                    Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothAdapter.cancelDiscovery();
                }

                Log.d(TAG, "Connecting...");
                if (ActivityCompat.checkSelfPermission(context, 
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    notifyError("缺少蓝牙权限");
                    return;
                }
                socket.connect();
                Log.d(TAG, "Connected");
                
                // 连接成功，发送数据
                sendDataThroughSocket(socket, dataToSend);
                
            } catch (IOException e) {
                Log.e(TAG, "Connection failed", e);
                notifyError("连接失败：" + e.getMessage());
                try {
                    socket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "Failed to close socket", e2);
                }
            }
        }

        public void cancel() {
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to close socket", e);
            }
        }
    }

    /**
     * 数据传输线程
     * 支持接收数据时的进度回调
     */
    private class TransferThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        // 进度跟踪
        private long totalBytesRead = 0;
        private long lastProgressUpdate = 0;
        private static final long PROGRESS_THROTTLE_MS = 100; // 每100ms更新一次进度
        private static final int MIN_PROGRESS_PERCENT = 10; // 最小进度百分比（避免小文件跳动）

        public TransferThread(BluetoothSocket socket) {
            this.socket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Failed to get streams", e);
            }

            inputStream = tmpIn;
            outputStream = tmpOut;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            StringBuilder dataBuilder = new StringBuilder();

            try {
                notifyStarted();

                int bytes;
                // 估算最大数据大小用于进度计算（实际大小未知）
                long estimatedMaxSize = MAX_DATA_SIZE;

                while ((bytes = inputStream.read(buffer)) != -1) {
                    String chunk = new String(buffer, 0, bytes);
                    dataBuilder.append(chunk);

                    // 更新进度
                    totalBytesRead += bytes;
                    notifyProgressThrottled(estimatedMaxSize);
                }

                String receivedData = dataBuilder.toString();
                Log.d(TAG, "Data received: " + receivedData.length() + " bytes");

                // 确保进度达到100%
                notifyProgress(100);
                notifySuccess(receivedData);

            } catch (IOException e) {
                Log.e(TAG, "Failed to read data", e);
                notifyError("接收数据失败");
            }
        }

        /**
         * 节流的进度更新（避免过于频繁）
         */
        private void notifyProgressThrottled(long estimatedMaxSize) {
            long now = System.currentTimeMillis();

            // 计算进度百分比
            int progress = (int) ((totalBytesRead * 100) / estimatedMaxSize);

            // 限制在合理范围内
            progress = Math.max(MIN_PROGRESS_PERCENT, Math.min(95, progress));

            // 节流：只有过了足够时间或进度显著变化才更新
            if (now - lastProgressUpdate >= PROGRESS_THROTTLE_MS || progress >= 95) {
                notifyProgress(progress);
                lastProgressUpdate = now;
            }
        }

        public void write(byte[] bytes) {
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                Log.e(TAG, "Failed to write data", e);
                notifyError("发送数据失败");
            }
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close socket", e);
            }
        }
    }

    // ========== 辅助方法 ==========

    private void connected(BluetoothSocket socket) {
        Log.d(TAG, "Starting transfer thread");
        
        if (transferThread != null) {
            transferThread.cancel();
        }
        
        transferThread = new TransferThread(socket);
        transferThread.start();
    }

    private void sendDataThroughSocket(BluetoothSocket socket, String data) {
        try {
            notifyStarted();

            OutputStream outputStream = socket.getOutputStream();
            byte[] bytes = data.getBytes("UTF-8");
            int totalBytes = bytes.length;

            // 分块发送以支持进度显示
            int chunkSize = 1024; // 每块1KB
            int bytesSent = 0;
            long lastProgressUpdate = 0;

            while (bytesSent < totalBytes) {
                int currentChunkSize = Math.min(chunkSize, totalBytes - bytesSent);
                outputStream.write(bytes, bytesSent, currentChunkSize);
                bytesSent += currentChunkSize;

                // 计算并通知进度
                int progress = (int) ((bytesSent * 100) / totalBytes);
                long now = System.currentTimeMillis();

                // 节流：每100ms或完成时更新
                if (now - lastProgressUpdate >= PROGRESS_THROTTLE_MS || bytesSent >= totalBytes) {
                    notifyProgress(progress);
                    lastProgressUpdate = now;
                }
            }

            outputStream.flush();

            Log.d(TAG, "Data sent: " + totalBytes + " bytes");
            notifyProgress(100); // 确保显示100%
            notifySuccess(null);

            socket.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to send data", e);
            notifyError("发送数据失败：" + e.getMessage());
        }
    }

    private static final long PROGRESS_THROTTLE_MS = 100; // 发送进度节流时间

    private void notifyStarted() {
        if (callback != null) {
            callback.onTransferStarted();
        }
    }

    private void notifyProgress(int progress) {
        if (callback != null) {
            callback.onTransferProgress(progress);
        }
    }

    private void notifySuccess(String data) {
        if (callback != null) {
            callback.onTransferSuccess(data);
        }
    }

    private void notifyError(String error) {
        if (callback != null) {
            callback.onTransferFailed(error);
        }
    }
}
