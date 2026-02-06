package com.ttt.safevault.autofill.builder;

import android.content.Context;
import android.content.IntentSender;
import android.os.Build;
import android.service.autofill.Dataset;
import android.service.autofill.FillResponse;
import android.service.autofill.SaveInfo;
import android.util.Log;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;

import com.ttt.safevault.R;
import com.ttt.safevault.autofill.model.AutofillRequest;
import com.ttt.safevault.model.PasswordItem;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * FillResponse构建器
 * 负责创建自动填充响应，包括Dataset和SaveInfo
 */
public class FillResponseBuilder {
    private static final String TAG = "FillResponseBuilder";
    
    private final Context context;

    public FillResponseBuilder(Context context) {
        this.context = context;
    }

    /**
     * 构建FillResponse
     *
     * @param request  AutofillRequest对象
     * @param credentials 匹配的凭据列表
     * @param authIntentSender 认证IntentSender（当应用未解锁时）
     * @param isLocked 应用是否已锁定
     * @return FillResponse对象
     */
    public FillResponse buildResponse(AutofillRequest request,
                                      List<PasswordItem> credentials,
                                      IntentSender authIntentSender,
                                      boolean isLocked) {
        logDebug("=== 开始构建 FillResponse ===");
        logDebug("凭据数量: " + (credentials != null ? credentials.size() : 0));
        logDebug("应用是否锁定: " + isLocked);

        if (request == null) {
            logDebug("AutofillRequest为null");
            return null;
        }

        FillResponse.Builder responseBuilder = new FillResponse.Builder();

        // 1. 如果需要认证（应用未解锁），只显示密码库选项
        if (authIntentSender != null && isLocked) {
            logDebug("应用已锁定，仅显示密码库选项");
            logDebug("不会添加任何凭据Dataset，只添加'密码库已锁定'选项");

            // 添加"转到我的密码库"选项
            Dataset vaultDataset = createVaultDataset(request, authIntentSender, isLocked);
            if (vaultDataset != null) {
                responseBuilder.addDataset(vaultDataset);
                logDebug("添加密码库选项Dataset");
            }

            // 添加SaveInfo
            SaveInfo saveInfo = createSaveInfo(request);
            if (saveInfo != null) {
                responseBuilder.setSaveInfo(saveInfo);
            }

            return responseBuilder.build();
        }

        // 2. 为每个凭据创建Dataset（在密码库选项之前添加）
        if (credentials != null && !credentials.isEmpty()) {
            for (PasswordItem credential : credentials) {
                Dataset dataset = createDataset(request, credential);
                if (dataset != null) {
                    responseBuilder.addDataset(dataset);
                    logDebug("添加Dataset: " + credential.getTitle());
                }
            }
        }

        // 3. 最后添加"转到我的密码库"选项（放在所有选项的最下面）
        Dataset vaultDataset = createVaultDataset(request, authIntentSender, isLocked);
        if (vaultDataset != null) {
            responseBuilder.addDataset(vaultDataset);
            logDebug("添加密码库选项Dataset（在最后）");
        }

        // 添加SaveInfo以支持保存新凭据
        SaveInfo saveInfo = createSaveInfo(request);
        if (saveInfo != null) {
            responseBuilder.setSaveInfo(saveInfo);
        }

        return responseBuilder.build();
    }

    /**
     * 创建"转到我的密码库"选项的Dataset
     */
    private Dataset createVaultDataset(AutofillRequest request, IntentSender authIntentSender, boolean isLocked) {
        RemoteViews presentation = createVaultPresentation(isLocked);
        Dataset.Builder datasetBuilder = new Dataset.Builder(presentation);

        boolean hasAnyField = false;

        // 为用户名字段设置空值（仅用于触发跳转）
        for (AutofillId usernameId : request.getUsernameIds()) {
            datasetBuilder.setValue(usernameId, null, presentation);
            hasAnyField = true;
        }

        // 为密码字段设置空值（仅用于触发跳转）
        for (AutofillId passwordId : request.getPasswordIds()) {
            datasetBuilder.setValue(passwordId, null, presentation);
            hasAnyField = true;
        }

        // 确保至少有一个字段被设置
        if (!hasAnyField) {
            logDebug("没有字段可设置");
            return null;
        }

        // 设置认证Intent（用于跳转到密码选择页面）
        if (authIntentSender != null) {
            logDebug("设置认证Intent: isLocked=" + isLocked);
            datasetBuilder.setAuthentication(authIntentSender);
        }

        return datasetBuilder.build();
    }

    /**
     * 创建"转到我的密码库"选项的Presentation视图
     */
    private RemoteViews createVaultPresentation(boolean isLocked) {
        RemoteViews presentation = new RemoteViews(
                context.getPackageName(),
                R.layout.autofill_auth_item
        );

        // 设置标题为SafeVault
        presentation.setTextViewText(
                R.id.autofill_auth_title,
                context.getString(R.string.app_name)
        );

        // 设置状态文字（锁定时显示"密码库已锁定"，未锁定时显示"转到我的密码库"）
        String statusText = isLocked
                ? context.getString(R.string.autofill_vault_locked)
                : context.getString(R.string.autofill_open_vault);
        presentation.setTextViewText(R.id.autofill_auth_text, statusText);

        return presentation;
    }

    /**
     * 构建FillResponse（旧方法，保留以兼容）
     * @deprecated 使用 buildResponse(request, credentials, authIntentSender, isLocked) 替代
     */
    @Deprecated
    public FillResponse buildResponse(AutofillRequest request,
                                      List<PasswordItem> credentials,
                                      IntentSender authIntentSender) {
        return buildResponse(request, credentials, authIntentSender, authIntentSender != null);
    }

    /**
     * 创建Dataset
     */
    private Dataset createDataset(AutofillRequest request, PasswordItem credential) {
        // 创建presentation（显示给用户的视图）
        RemoteViews presentation = createPresentation(credential);
        
        Dataset.Builder datasetBuilder = new Dataset.Builder(presentation);
        
        boolean hasAnyField = false;

        // 设置用户名字段的值
        for (AutofillId usernameId : request.getUsernameIds()) {
            AutofillValue usernameValue = AutofillValue.forText(credential.getUsername());
            datasetBuilder.setValue(usernameId, usernameValue, presentation);
            hasAnyField = true;
            logDebug("设置用户名字段: " + usernameId);
        }

        // 设置密码字段的值
        for (AutofillId passwordId : request.getPasswordIds()) {
            AutofillValue passwordValue = AutofillValue.forText(credential.getPassword());
            datasetBuilder.setValue(passwordId, passwordValue, presentation);
            hasAnyField = true;
            logDebug("设置密码字段: " + passwordId);
        }
        
        // 确保至少有一个字段被设置
        if (!hasAnyField) {
            logDebug("没有字段可设置");
            return null;
        }

        return datasetBuilder.build();
    }

    /**
     * 创建Presentation视图（显示凭据信息）
     */
    private RemoteViews createPresentation(PasswordItem credential) {
        RemoteViews presentation = new RemoteViews(
                context.getPackageName(), 
                R.layout.autofill_dataset_item
        );

        // 设置标题
        String title = credential.getTitle();
        if (title == null || title.isEmpty()) {
            title = credential.getUsername();
        }
        presentation.setTextViewText(R.id.autofill_dataset_title, title);

        // 设置用户名（部分隐藏）
        String username = credential.getUsername();
        String maskedUsername = maskString(username);
        presentation.setTextViewText(R.id.autofill_dataset_username, maskedUsername);

        return presentation;
    }

    /**
     * 创建认证提示的RemoteViews
     */
    private RemoteViews createAuthPresentation() {
        RemoteViews presentation = new RemoteViews(
                context.getPackageName(),
                R.layout.autofill_auth_item
        );
        
        presentation.setTextViewText(
                R.id.autofill_auth_text,
                context.getString(R.string.autofill_unlock_safevault)
        );
        
        return presentation;
    }

    /**
     * 创建SaveInfo
     */
    private SaveInfo createSaveInfo(AutofillRequest request) {
        List<AutofillId> usernameIds = request.getUsernameIds();
        List<AutofillId> passwordIds = request.getPasswordIds();

        // 至少需要一个密码字段
        if (passwordIds.isEmpty()) {
            logDebug("没有密码字段，无法创建SaveInfo");
            return null;
        }

        SaveInfo.Builder saveInfoBuilder = new SaveInfo.Builder(
                SaveInfo.SAVE_DATA_TYPE_PASSWORD,
                passwordIds.toArray(new AutofillId[0])
        );

        // 添加可选的用户名字段
        if (!usernameIds.isEmpty()) {
            saveInfoBuilder.setOptionalIds(usernameIds.toArray(new AutofillId[0]));
        }

        return saveInfoBuilder.build();
    }

    /**
     * 部分隐藏字符串（用于显示）
     */
    private String maskString(String str) {
        if (str == null || str.isEmpty()) {
            return "";
        }

        if (str.length() <= 4) {
            return str;
        }

        // 显示前2个和后2个字符，中间用*替代
        int visibleChars = 2;
        String start = str.substring(0, visibleChars);
        String end = str.substring(str.length() - visibleChars);
        int maskedLength = str.length() - (visibleChars * 2);
        
        StringBuilder masked = new StringBuilder(start);
        for (int i = 0; i < maskedLength; i++) {
            masked.append("*");
        }
        masked.append(end);

        return masked.toString();
    }

    /**
     * 调试日志输出到文件
     */
    private void logDebug(String message) {
        Log.d(TAG, message);
        
        // 同时输出到文件（用于手机端调试）
        try {
            String logDir = "/storage/emulated/0/Android/data/com.ttt.safevault/files/autofill_logs/";
            File dir = new File(logDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                    .format(new Date());
            String logMessage = timestamp + " [" + TAG + "] " + message + "\n";
            
            File logFile = new File(dir, "autofill_builder.log");
            FileWriter writer = new FileWriter(logFile, true);
            writer.write(logMessage);
            writer.close();
        } catch (IOException e) {
            // 日志写入失败，使用系统日志记录
            android.util.Log.e(TAG, "Failed to write to log file", e);
        }
    }
}
