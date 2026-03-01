package com.ttt.safevault.autofill;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.service.autofill.AutofillService;
import android.service.autofill.FillCallback;
import android.service.autofill.FillRequest;
import android.service.autofill.FillResponse;
import android.service.autofill.SaveCallback;
import android.service.autofill.SaveRequest;
import android.util.Log;
import android.view.autofill.AutofillId;

import com.ttt.safevault.ServiceLocator;
import com.ttt.safevault.autofill.builder.FillResponseBuilder;
import com.ttt.safevault.autofill.matcher.AutofillMatcher;
import com.ttt.safevault.autofill.model.AutofillField;
import com.ttt.safevault.autofill.model.AutofillParsedData;
import com.ttt.safevault.autofill.model.AutofillRequest;
import com.ttt.safevault.autofill.parser.AutofillParser;
import com.ttt.safevault.autofill.security.SecurityConfig;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.ui.LoginActivity;
import com.ttt.safevault.ui.autofill.AutofillSaveActivity;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SafeVault自动填充服务
 * 处理系统的自动填充请求和保存请求
 */
public class SafeVaultAutofillService extends AutofillService {
    private static final String TAG = "SafeVaultAutofillService";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private BackendService backendService;
    private SecurityConfig securityConfig;

    @Override
    public void onCreate() {
        super.onCreate();
        logDebug("=== SafeVaultAutofillService onCreate ===");

        // 初始化BackendService
        backendService = ServiceLocator.getInstance().getBackendService();

        // 初始化安全配置
        securityConfig = new SecurityConfig();
    }

    @Override
    public void onConnected() {
        super.onConnected();
        logDebug("=== AutofillService 已连接 ===");
    }

    @Override
    public void onDisconnected() {
        super.onDisconnected();
        logDebug("=== AutofillService 已断开 ===");
    }

    @Override
    public void onFillRequest(FillRequest request, CancellationSignal cancellationSignal,
                             FillCallback callback) {
        logDebug("=== 收到 FillRequest ===");

        // 异步处理请求
        executor.execute(() -> {
            try {
                // 检查是否被取消
                if (cancellationSignal.isCanceled()) {
                    logDebug("请求已取消");
                    return;
                }

                // 解析请求
                AutofillParsedData parsedData = AutofillParser.parseFillRequest(request);
                if (parsedData == null) {
                    logDebug("解析失败");
                    callback.onFailure("解析请求失败");
                    return;
                }

                // 安全检查：排除自己的应用
                String packageName = parsedData.getPackageName();
                if (getPackageName().equals(packageName)) {
                    logDebug("忽略自己的应用，不提供自动填充");
                    callback.onSuccess(null);
                    return;
                }

                // 安全检查：检查是否在阻止列表中
                if (securityConfig.isBlocked(packageName)) {
                    logDebug("应用在阻止列表中: " + packageName);
                    callback.onSuccess(null);
                    return;
                }

                // 构建AutofillRequest
                AutofillRequest autofillRequest = buildAutofillRequest(parsedData);
                if (autofillRequest == null) {
                    logDebug("构建AutofillRequest失败");
                    callback.onFailure("无法识别填充字段");
                    return;
                }

                // 自动填充服务需要主动检查后台超时并锁定
                // 因为此时 MainActivity 不会启动，onResume() 不会执行
                checkBackgroundTimeoutAndLock();

                // 检查应用是否已解锁
                boolean isUnlockedValue = (backendService != null && backendService.isUnlocked());
                boolean isLocked = !isUnlockedValue;
                logDebug("=== 应用锁定状态检查 ===");
                logDebug("backendService=" + (backendService != null ? "存在" : "null"));
                logDebug("isUnlocked()=" + isUnlockedValue);
                logDebug("isLocked=" + isLocked);
                logDebug("检查完成");
                IntentSender authIntentSender = null;

                // 无论锁定与否，都使用AutofillCredentialSelectorActivity作为认证Intent
                // 锁定状态下，该Activity会先要求用户验证身份，验证成功后显示凭据列表
                // 用户选择凭据后，直接返回Dataset给系统进行自动填充
                Intent selectorIntent = new Intent(this, com.ttt.safevault.ui.autofill.AutofillCredentialSelectorActivity.class);
                selectorIntent.putExtra("domain", parsedData.getDomain());
                selectorIntent.putExtra("packageName", parsedData.getPackageName());
                selectorIntent.putExtra("title", parsedData.getTitle());
                selectorIntent.putExtra("isWeb", parsedData.isWeb());
                // 传递AutofillId信息
                selectorIntent.putParcelableArrayListExtra("username_ids", new ArrayList<>(autofillRequest.getUsernameIds()));
                selectorIntent.putParcelableArrayListExtra("password_ids", new ArrayList<>(autofillRequest.getPasswordIds()));
                // 如果锁定，设置需要认证标志
                if (isLocked) {
                    selectorIntent.putExtra("needs_auth", true);
                    logDebug("应用未解锁，设置needs_auth=true");
                } else {
                    logDebug("应用已解锁，直接显示凭据选择器");
                }

                PendingIntent pendingIntent = PendingIntent.getActivity(
                        this,
                        0,
                        selectorIntent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                );

                authIntentSender = pendingIntent.getIntentSender();

                // 匹配凭据（如果已解锁）
                List<PasswordItem> credentials = null;
                if (!isLocked) {
                    AutofillMatcher matcher = new AutofillMatcher(backendService);
                    credentials = matcher.matchCredentials(autofillRequest);
                }

                // 构建响应（使用新的buildResponse方法）
                FillResponseBuilder builder = new FillResponseBuilder(this);
                FillResponse response = builder.buildResponse(autofillRequest, credentials, authIntentSender, isLocked);

                if (response != null) {
                    logDebug("FillResponse构建成功");
                    callback.onSuccess(response);
                } else {
                    logDebug("没有可用的凭据");
                    callback.onSuccess(null);
                }

            } catch (Exception e) {
                logDebug("处理FillRequest异常: " + e.getMessage());
                Log.e(TAG, "处理FillRequest异常", e);
                callback.onFailure("处理请求失败: " + e.getMessage());
            }
        });

        // 监听取消信号
        cancellationSignal.setOnCancelListener(() -> {
            logDebug("FillRequest被取消");
        });
    }

    @Override
    public void onSaveRequest(SaveRequest request, SaveCallback callback) {
        logDebug("=== 收到 SaveRequest ===");

        try {
            // 解析SaveRequest
            AutofillParsedData parsedData = AutofillParser.parseSaveRequest(request);
            if (parsedData == null) {
                logDebug("解析SaveRequest失败");
                callback.onFailure("解析保存请求失败");
                return;
            }
            
            // 安全检查：排除自己的应用
            String packageName = parsedData.getPackageName();
            if (getPackageName().equals(packageName)) {
                logDebug("忽略自己的应用，不触发保存");
                callback.onFailure("忽略自己的应用");
                return;
            }

            // 提取用户名和密码值（使用新的提取方法）
            String username = extractFieldValueByType(parsedData, AutofillField.FieldType.USERNAME);
            if (username == null || username.isEmpty()) {
                // 尝试邮箱和手机号
                username = extractFieldValueByType(parsedData, AutofillField.FieldType.EMAIL);
                if (username == null || username.isEmpty()) {
                    username = extractFieldValueByType(parsedData, AutofillField.FieldType.PHONE);
                }
            }
            
            String password = extractFieldValueByType(parsedData, AutofillField.FieldType.PASSWORD);

            if (password == null || password.isEmpty()) {
                logDebug("密码为空，无法保存");
                callback.onFailure("密码不能为空");
                return;
            }
            
            logDebug("提取到用户名: " + (username != null ? username : "(空)"));
            logDebug("提取到密码长度: " + password.length());

            // 获取应用名称（如果是原生应用）
            String appName = null;
            if (parsedData.getPackageName() != null && !parsedData.isWeb()) {
                appName = getApplicationName(parsedData.getPackageName());
                logDebug("提取到应用名称: " + appName);
            }
            
            // 获取标题（优先使用提取的标题，否则使用域名/应用名）
            String title = parsedData.getTitle();
            if (title == null || title.isEmpty()) {
                if (parsedData.isWeb() && parsedData.getDomain() != null) {
                    title = parsedData.getDomain();
                } else if (appName != null) {
                    title = appName;
                } else if (parsedData.getPackageName() != null) {
                    title = parsedData.getPackageName();
                }
            }

            // 创建Intent启动认证Activity（会检查锁定状态，如果锁定则先登录）
            Intent saveIntent = new Intent(this, com.ttt.safevault.ui.autofill.AutofillSaveAuthActivity.class);
            saveIntent.putExtra("username", username);
            saveIntent.putExtra("password", password);
            saveIntent.putExtra("domain", parsedData.getDomain());
            saveIntent.putExtra("packageName", parsedData.getPackageName());
            saveIntent.putExtra("title", title);
            saveIntent.putExtra("isWeb", parsedData.isWeb());
            saveIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // 创建PendingIntent
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    saveIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );

            IntentSender intentSender = pendingIntent.getIntentSender();
            
            // 返回IntentSender给系统
            callback.onSuccess(intentSender);
            
            logDebug("SaveRequest处理成功，已启动保存界面");

        } catch (Exception e) {
            logDebug("处理SaveRequest异常: " + e.getMessage());
            Log.e(TAG, "处理SaveRequest异常", e);
            callback.onFailure("处理保存请求失败");
        }
    }

    /**
     * 构建AutofillRequest
     */
    private AutofillRequest buildAutofillRequest(AutofillParsedData parsedData) {
        AutofillRequest.Builder builder = new AutofillRequest.Builder();

        // 添加用户名字段
        for (AutofillField field : parsedData.getUsernameFields()) {
            builder.addUsernameId(field.getAutofillId());
        }

        // 添加密码字段
        for (AutofillField field : parsedData.getPasswordFields()) {
            builder.addPasswordId(field.getAutofillId());
        }

        // 设置元数据
        builder.setDomain(parsedData.getDomain())
               .setPackageName(parsedData.getPackageName())
               .setApplicationName(parsedData.getApplicationName())
               .setIsWeb(parsedData.isWeb());

        AutofillRequest request = builder.build();
        
        // 检查是否有有效字段（用户名或密码字段任一即可）
        if (request.getUsernameIds().isEmpty() && request.getPasswordIds().isEmpty()) {
            logDebug("没有找到用户名或密码字段");
            return null;
        }
        
        logDebug("找到字段: 用户名=" + request.getUsernameIds().size() + 
                ", 密码=" + request.getPasswordIds().size());

        return request;
    }

    /**
     * 从解析数据中按类型提取字段值（使用新的 getValue() 方法）
     */
    private String extractFieldValueByType(AutofillParsedData parsedData, 
                                          AutofillField.FieldType fieldType) {
        for (AutofillField field : parsedData.getFields()) {
            if (field.getFieldType() == fieldType) {
                // 使用新的 getValue() 方法获取实际值
                String value = field.getValue();
                if (value != null && !value.isEmpty()) {
                    return value;
                }
                // 回退到 hint（这是为了兼容性）
                return field.getHint();
            }
        }
        return null;
    }

    /**
     * 获取应用名称
     */
    private String getApplicationName(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            CharSequence appLabel = pm.getApplicationLabel(appInfo);
            if (appLabel != null) {
                return appLabel.toString();
            }
        } catch (PackageManager.NameNotFoundException e) {
            logDebug("无法获取应用名称: " + e.getMessage());
        }
        // 回退到使用包名
        return packageName;
    }

    /**
     * 从解析数据中提取字段值（旧方法，保留以保证兼容性）
     * @deprecated 使用 extractFieldValueByType 替代
     */
    @Deprecated
    private String extractFieldValue(AutofillParsedData parsedData, 
                                     AutofillField.FieldType fieldType) {
        for (AutofillField field : parsedData.getFields()) {
            if (field.getFieldType() == fieldType) {
                // 注意：这里只能获取hint，实际值需要从 SaveRequest 中获取
                // 实际实现中需要使用 AssistStructure.ViewNode.getText()
                return field.getHint();
            }
        }
        return null;
    }

    /**
     * 检查后台超时并自动锁定
     * 当应用进入后台超过设定时间后，自动锁定并清除会话密钥
     *
     * 注意：此方法在 AutofillService 中调用，因为当用户在其他 App 触发自动填充时，
     * MainActivity 不会启动，onResume() 不会执行，所以需要在此处主动检查。
     */
    private void checkBackgroundTimeoutAndLock() {
        logDebug("=== checkBackgroundTimeoutAndLock() 开始 ===");

        if (backendService == null) {
            logDebug("backendService 为 null，无法检查超时");
            return;
        }

        try {
            // 获取后台时间戳
            long backgroundTime = backendService.getBackgroundTime();
            logDebug("后台时间戳: " + backgroundTime);

            if (backgroundTime == 0) {
                // 应用没有进入过后台
                logDebug("应用未进入过后台，跳过检查");
                return;
            }

            // 使用 SessionGuard 统一检查是否需要锁定
            com.ttt.safevault.security.SessionGuard sessionGuard =
                    com.ttt.safevault.security.SessionGuard.getInstance();
            sessionGuard.setSecurityConfig(this);

            if (sessionGuard.shouldLockBySessionTimeout(backgroundTime)) {
                logDebug("*** 后台超时，执行会话锁定 ***");
                backendService.lock(); // 锁定并清除会话密钥
                logDebug("锁定完成");
            } else {
                logDebug("未超时，无需锁定");
            }
        } catch (Exception e) {
            logDebug("检查后台超时失败: " + e.getMessage());
            Log.e(TAG, "检查后台超时失败", e);
        }

        logDebug("=== checkBackgroundTimeoutAndLock() 结束 ===");
    }

    /**
     * 调试日志输出到文件
     */
    private void logDebug(String message) {
        Log.d(TAG, message);

        // 同时输出到文件（用于手机端调试）
        FileWriter writer = null;
        try {
            String logDir = "/storage/emulated/0/Android/data/com.ttt.safevault/files/autofill_logs/";
            File dir = new File(logDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                    .format(new Date());
            String logMessage = timestamp + " [" + TAG + "] " + message + "\n";

            File logFile = new File(dir, "autofill_service.log");
            writer = new FileWriter(logFile, true);
            writer.write(logMessage);
        } catch (IOException e) {
            // 忽略日志写入错误
        } finally {
            // 确保FileWriter正确关闭，防止资源泄漏
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    // 忽略关闭错误
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // 清除敏感引用，防止内存泄漏
        if (backendService != null) {
            backendService = null;
        }
        if (securityConfig != null) {
            securityConfig = null;
        }

        // 正确关闭ExecutorService，使用shutdownNow中断正在执行的任务
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }

        logDebug("=== SafeVaultAutofillService onDestroy ===");
    }
}
