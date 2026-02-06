package com.ttt.safevault.network;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 认证拦截器
 * 自动添加Authorization头，处理401错误
 * 改进并发刷新机制，使用请求队列和原子状态管理
 */
public class AuthInterceptor implements Interceptor {
    private static final String TAG = "AuthInterceptor";
    private static final long REFRESH_TIMEOUT_SECONDS = 10;

    // Token过期广播Action
    public static final String ACTION_TOKEN_EXPIRED = "com.ttt.safevault.action.TOKEN_EXPIRED";

    // 刷新状态枚举
    private enum RefreshState {
        IDLE,       // 空闲，没有正在进行的刷新
        PENDING,    // 刷新进行中
        SUCCESS,    // 刷新成功
        FAILED      // 刷新失败
    }

    private final TokenManager tokenManager;
    private final Context context;
    private final AtomicReference<RefreshState> refreshState;
    private final Queue<Request> pendingRequests; // 等待刷新完成的请求队列
    private final Object queueLock = new Object(); // 队列锁

    public AuthInterceptor(TokenManager tokenManager, Context context) {
        this.tokenManager = tokenManager;
        this.context = context.getApplicationContext();
        this.refreshState = new AtomicReference<>(RefreshState.IDLE);
        this.pendingRequests = new LinkedList<>();
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();

        // 如果是刷新Token的请求,直接放行
        if (original.url().encodedPath().contains("/auth/refresh")) {
            return chain.proceed(original);
        }

        // 添加Authorization头
        String token = tokenManager.getAccessToken();
        Request.Builder requestBuilder = original.newBuilder();

        Log.d(TAG, "Intercepting: " + original.method() + " " + original.url().encodedPath());

        if (token != null) {
            requestBuilder.header("Authorization", "Bearer " + token);
            Log.d(TAG, "Authorization header added");
        } else {
            Log.e(TAG, "No token available! Request will be sent without authorization.");
        }

        Request request = requestBuilder.build();
        Response response = chain.proceed(request);

        // 处理401错误 - Token过期
        if (response.code() == 401 && token != null) {
            Log.d(TAG, "Token expired, attempting to refresh");
            response.close();

            // 检查是否已经有刷新在进行
            RefreshState currentState = refreshState.get();
            if (currentState == RefreshState.PENDING) {
                Log.d(TAG, "Refresh already in progress, queuing request");
                // 将当前请求加入队列
                synchronized (queueLock) {
                    pendingRequests.add(original);
                }
                // 等待刷新完成
                waitForRefreshCompletion();
                // 使用新token重试
                String newToken = tokenManager.getAccessToken();
                if (newToken != null) {
                    Request newRequest = original.newBuilder()
                        .header("Authorization", "Bearer " + newToken)
                        .build();
                    return chain.proceed(newRequest);
                } else {
                    // 刷新失败
                    return response;
                }
            }

            // 尝试刷新Token (同步方式)
            RefreshResult refreshResult = refreshTokenSync();
            if (refreshResult.success) {
                // 使用新Token重试当前请求
                Request newRequest = original.newBuilder()
                    .header("Authorization", "Bearer " + refreshResult.newToken)
                    .build();
                Response retryResponse = chain.proceed(newRequest);

                // 处理队列中的其他请求
                processPendingRequests(chain, refreshResult.newToken);

                return retryResponse;
            } else {
                // 刷新失败，清除token并发送广播
                Log.e(TAG, "Token refresh failed, clearing tokens and sending broadcast");
                tokenManager.clearTokens();

                // 清空队列中的请求
                synchronized (queueLock) {
                    pendingRequests.clear();
                }

                // 发送token过期广播
                Intent intent = new Intent(ACTION_TOKEN_EXPIRED);
                intent.setPackage(context.getPackageName());
                context.sendBroadcast(intent);
                Log.d(TAG, "Token expired broadcast sent");
            }
        }

        return response;
    }

    /**
     * 刷新结果类
     */
    private static class RefreshResult {
        boolean success;
        String newToken;

        RefreshResult(boolean success, String newToken) {
            this.success = success;
            this.newToken = newToken;
        }
    }

    /**
     * 处理队列中等待的请求
     */
    private void processPendingRequests(Chain chain, String newToken) {
        synchronized (queueLock) {
            Log.d(TAG, "Processing " + pendingRequests.size() + " pending requests");
            while (!pendingRequests.isEmpty()) {
                Request pendingRequest = pendingRequests.poll();
                // 这里我们只是从队列中移除请求，不实际执行
                // 因为那些请求会在各自的线程中等待完成后重新尝试
            }
        }
    }

    /**
     * 等待刷新完成
     */
    private void waitForRefreshCompletion() {
        try {
            int waitCount = 0;
            int maxWait = (int) (REFRESH_TIMEOUT_SECONDS * 10); // 100ms间隔
            while (refreshState.get() == RefreshState.PENDING && waitCount < maxWait) {
                Thread.sleep(100);
                waitCount++;
            }
            Log.d(TAG, "Wait completed after " + (waitCount * 100) + "ms, state: " + refreshState.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "Wait for refresh interrupted", e);
        }
    }

    /**
     * 同步刷新Token
     * 改进版：使用原子状态管理，避免并发刷新
     */
    private RefreshResult refreshTokenSync() {
        // 尝试设置状态为 PENDING
        if (!refreshState.compareAndSet(RefreshState.IDLE, RefreshState.PENDING)) {
            // 已有其他线程在刷新，等待完成
            Log.d(TAG, "Refresh already in progress by another thread, waiting...");
            waitForRefreshCompletion();

            // 检查刷新结果
            RefreshState state = refreshState.get();
            String newToken = tokenManager.getAccessToken();
            if (state == RefreshState.SUCCESS && newToken != null) {
                Log.d(TAG, "Refresh completed by another thread, reusing token");
                return new RefreshResult(true, newToken);
            } else {
                Log.w(TAG, "Refresh failed in another thread");
                return new RefreshResult(false, null);
            }
        }

        // 成功设置为 PENDING 状态，开始刷新
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> newTokenRef = new AtomicReference<>(null);

        try {
            Log.d(TAG, "Starting synchronous token refresh");
            tokenManager.refreshToken()
                .subscribe(new Observer<com.ttt.safevault.dto.response.AuthResponse>() {
                    @Override
                    public void onSubscribe(@io.reactivex.rxjava3.annotations.NonNull Disposable d) {
                        // Ignore
                    }

                    @Override
                    public void onNext(@io.reactivex.rxjava3.annotations.NonNull com.ttt.safevault.dto.response.AuthResponse authResponse) {
                        String newToken = authResponse.getAccessToken();
                        newTokenRef.set(newToken);
                        Log.d(TAG, "Token refreshed successfully");
                    }

                    @Override
                    public void onError(@io.reactivex.rxjava3.annotations.NonNull Throwable e) {
                        Log.e(TAG, "Token refresh failed", e);
                        newTokenRef.set(null);

                        // 发送token过期广播
                        Intent intent = new Intent(ACTION_TOKEN_EXPIRED);
                        intent.setPackage(context.getPackageName());
                        context.sendBroadcast(intent);
                        Log.d(TAG, "Token expired broadcast sent from refresh error");

                        refreshState.set(RefreshState.FAILED);
                        latch.countDown();
                    }

                    @Override
                    public void onComplete() {
                        if (newTokenRef.get() != null) {
                            refreshState.set(RefreshState.SUCCESS);
                        } else {
                            refreshState.set(RefreshState.FAILED);
                        }
                        latch.countDown();
                    }
                });

            // 等待刷新完成，最多等待指定秒数
            boolean success = latch.await(REFRESH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!success) {
                Log.e(TAG, "Token refresh timeout after " + REFRESH_TIMEOUT_SECONDS + " seconds");
                refreshState.set(RefreshState.FAILED);
                return new RefreshResult(false, null);
            }

            String newToken = newTokenRef.get();
            if (newToken != null) {
                Log.d(TAG, "Token refresh completed successfully");
                return new RefreshResult(true, newToken);
            } else {
                Log.e(TAG, "Token refresh failed: no token returned");
                return new RefreshResult(false, null);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "Token refresh interrupted", e);
            refreshState.set(RefreshState.FAILED);
            return new RefreshResult(false, null);
        } finally {
            // 刷新完成后重置状态（延迟重置，让等待的线程有时间读取状态）
            if (refreshState.get() == RefreshState.SUCCESS) {
                // 成功时延迟重置，让等待的线程能读取到 SUCCESS 状态
                new Thread(() -> {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {}
                    refreshState.set(RefreshState.IDLE);
                }).start();
            } else {
                // 失败时立即重置
                refreshState.set(RefreshState.IDLE);
            }
        }
    }
}
