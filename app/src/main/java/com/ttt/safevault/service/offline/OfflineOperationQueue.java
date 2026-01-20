package com.ttt.safevault.service.offline;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 离线操作队列
 * 用于在网络不可用时暂存操作，网络恢复后自动执行
 */
public class OfflineOperationQueue {
    private static final String TAG = "OfflineOperationQueue";
    private static final String PREFS_NAME = "offline_queue";
    private static final String KEY_QUEUE = "operations";

    private final Context context;
    private final SharedPreferences prefs;
    private final ExecutorService executor;
    private final Gson gson;
    private final List<OfflineOperation> queue;

    public OfflineOperationQueue(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.executor = Executors.newSingleThreadExecutor();
        this.gson = new Gson();
        this.queue = loadQueue();
    }

    /**
     * 添加操作到队列
     */
    public void addOperation(OfflineOperation operation) {
        operation.setTimestamp(System.currentTimeMillis());
        queue.add(operation);
        saveQueue();
        Log.i(TAG, "Operation queued: " + operation.getType());

        // 尝试执行
        processQueue();
    }

    /**
     * 处理队列中的操作
     */
    public void processQueue() {
        if (queue.isEmpty()) {
            return;
        }

        executor.execute(() -> {
            List<OfflineOperation> completed = new ArrayList<>();
            List<OfflineOperation> failed = new ArrayList<>();

            for (OfflineOperation op : queue) {
                if (NetworkUtil.isNetworkAvailable(context)) {
                    try {
                        boolean success = op.execute();
                        if (success) {
                            completed.add(op);
                            Log.i(TAG, "Operation completed: " + op.getType());
                        } else {
                            if (op.canRetry()) {
                                op.incrementRetry();
                                failed.add(op);
                                Log.w(TAG, "Operation failed, retrying: " + op.getType() + " (" + op.getRetryCount() + "/" + OfflineOperation.MAX_RETRY + ")");
                            } else {
                                completed.add(op); // 达到最大重试次数，从队列移除
                                Log.e(TAG, "Operation failed after max retries: " + op.getType());
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Execute operation failed: " + op.getType(), e);
                        if (op.canRetry()) {
                            op.incrementRetry();
                            failed.add(op);
                        } else {
                            completed.add(op); // 达到最大重试次数，从队列移除
                        }
                    }
                } else {
                    failed.add(op);
                }
            }

            // 更新队列
            queue.clear();
            queue.addAll(failed);
            saveQueue();

            Log.i(TAG, "Queue processed: completed=" + completed.size() +
                       ", failed=" + failed.size() +
                       ", remaining=" + queue.size());
        });
    }

    @SuppressWarnings("unchecked")
    private List<OfflineOperation> loadQueue() {
        String json = prefs.getString(KEY_QUEUE, "[]");
        Type type = new TypeToken<List<OfflineOperationData>>(){}.getType();
        List<OfflineOperationData> dataList = gson.fromJson(json, type);

        List<OfflineOperation> result = new ArrayList<>();
        for (OfflineOperationData data : dataList) {
            OfflineOperation op = OperationFactory.createOperation(data);
            if (op != null) {
                result.add(op);
            }
        }
        return result;
    }

    private void saveQueue() {
        List<OfflineOperationData> dataList = new ArrayList<>();
        for (OfflineOperation op : queue) {
            dataList.add(OperationFactory.toData(op));
        }
        String json = gson.toJson(dataList);
        prefs.edit().putString(KEY_QUEUE, json).apply();
    }

    /**
     * 清空队列
     */
    public void clear() {
        queue.clear();
        saveQueue();
    }

    /**
     * 获取队列大小
     */
    public int size() {
        return queue.size();
    }

    /**
     * 获取队列中的所有操作
     */
    public List<OfflineOperation> getQueue() {
        return new ArrayList<>(queue);
    }
}
