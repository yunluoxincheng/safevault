package com.ttt.safevault.autofill.matcher;

import android.util.Log;

import com.ttt.safevault.autofill.model.AutofillRequest;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.PasswordItem;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 自动填充凭据匹配器
 * 负责根据域名或包名匹配合适的凭据
 */
public class AutofillMatcher {
    private static final String TAG = "AutofillMatcher";
    
    private final BackendService backendService;

    public AutofillMatcher(BackendService backendService) {
        this.backendService = backendService;
    }

    /**
     * 匹配凭据
     *
     * @param request AutofillRequest对象
     * @return 匹配的凭据列表
     */
    public List<PasswordItem> matchCredentials(AutofillRequest request) {
        logDebug("=== 开始匹配凭据 ===");
        logDebug("请求信息: isWeb=" + request.isWeb() +
                ", domain=" + request.getDomain() +
                ", packageName=" + request.getPackageName());

        if (backendService == null || !backendService.isUnlocked()) {
            logDebug("BackendService未解锁");
            return new ArrayList<>();
        }

        // 获取所有密码项
        List<PasswordItem> allItems = backendService.getAllItems();
        if (allItems == null || allItems.isEmpty()) {
            logDebug("没有可用的密码项");
            return new ArrayList<>();
        }

        logDebug("总密码项数量: " + allItems.size());

        List<PasswordItem> matchedItems = new ArrayList<>();

        // 优先使用域名匹配（Web页面）
        if (request.isWeb() && request.getDomain() != null) {
            matchedItems = matchByDomain(allItems, request.getDomain());
            logDebug("域名匹配结果: " + matchedItems.size() + " 项");
        }
        // 原生应用匹配（或者作为备用匹配）
        else if (request.getPackageName() != null) {
            matchedItems = matchByPackageName(allItems, request.getPackageName());
            logDebug("包名匹配结果: " + matchedItems.size() + " 项");
        }

        // 如果没有匹配到任何结果，尝试备用匹配
        if (matchedItems.isEmpty()) {
            logDebug("没有匹配到结果，尝试备用匹配");
            // 如果有域名，尝试用域名匹配
            if (request.getDomain() != null) {
                matchedItems = matchByDomain(allItems, request.getDomain());
                logDebug("备用域名匹配结果: " + matchedItems.size() + " 项");
            }
            // 如果有包名，尝试用包名匹配
            if (matchedItems.isEmpty() && request.getPackageName() != null) {
                matchedItems = matchByPackageName(allItems, request.getPackageName());
                logDebug("备用包名匹配结果: " + matchedItems.size() + " 项");
            }
        }

        return matchedItems;
    }

    /**
     * 根据域名匹配凭据
     */
    private List<PasswordItem> matchByDomain(List<PasswordItem> items, String targetDomain) {
        List<PasswordItem> matched = new ArrayList<>();
        String normalizedTarget = normalizeDomain(targetDomain);
        
        logDebug("目标域名: " + targetDomain + " -> 规范化: " + normalizedTarget);

        for (PasswordItem item : items) {
            String url = item.getUrl();
            if (url == null || url.isEmpty()) {
                continue;
            }

            String itemDomain = extractDomainFromUrl(url);
            if (itemDomain == null) {
                continue;
            }

            String normalizedItem = normalizeDomain(itemDomain);
            logDebug("比对 - 项URL: " + url + " -> 域名: " + normalizedItem);

            // 精确匹配
            if (normalizedTarget.equals(normalizedItem)) {
                matched.add(item);
                logDebug("精确匹配成功: " + item.getTitle());
                continue;
            }

            // 子域名匹配
            if (isSubdomainMatch(normalizedTarget, normalizedItem)) {
                matched.add(item);
                logDebug("子域名匹配成功: " + item.getTitle());
            }
        }

        return matched;
    }

    /**
     * 根据包名匹配凭据
     */
    private List<PasswordItem> matchByPackageName(List<PasswordItem> items, String packageName) {
        List<PasswordItem> matched = new ArrayList<>();
        
        logDebug("目标包名: " + packageName);

        for (PasswordItem item : items) {
            String url = item.getUrl();
            if (url == null || url.isEmpty()) {
                continue;
            }

            // 检查URL是否包含包名
            // 原生应用的URL格式可能是: android://com.example.app
            if (url.contains(packageName)) {
                matched.add(item);
                logDebug("包名匹配成功: " + item.getTitle() + " (" + url + ")");
            }
        }

        return matched;
    }

    /**
     * 从URL提取域名
     */
    private String extractDomainFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        try {
            // 确保URL有协议前缀
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }

            URI uri = new URI(url);
            String host = uri.getHost();
            
            if (host != null) {
                return host;
            }
        } catch (URISyntaxException e) {
            logDebug("URL解析失败: " + url + " - " + e.getMessage());
        }

        // 备用方案：手动解析
        try {
            String cleaned = url.replace("https://", "")
                               .replace("http://", "")
                               .replace("www.", "");
            
            int slashIndex = cleaned.indexOf('/');
            if (slashIndex > 0) {
                cleaned = cleaned.substring(0, slashIndex);
            }
            
            int colonIndex = cleaned.indexOf(':');
            if (colonIndex > 0) {
                cleaned = cleaned.substring(0, colonIndex);
            }
            
            return cleaned;
        } catch (Exception e) {
            logDebug("手动解析URL失败: " + url);
        }

        return null;
    }

    /**
     * 规范化域名（移除www前缀，转小写）
     */
    private String normalizeDomain(String domain) {
        if (domain == null) {
            return "";
        }

        String normalized = domain.toLowerCase(Locale.ROOT);
        
        // 移除www前缀
        if (normalized.startsWith("www.")) {
            normalized = normalized.substring(4);
        }

        return normalized;
    }

    /**
     * 判断是否为子域名匹配
     * 例如: login.example.com 应该匹配 example.com
     */
    private boolean isSubdomainMatch(String domain1, String domain2) {
        // 提取根域名
        String rootDomain1 = extractRootDomain(domain1);
        String rootDomain2 = extractRootDomain(domain2);

        if (rootDomain1 == null || rootDomain2 == null) {
            return false;
        }

        return rootDomain1.equals(rootDomain2);
    }

    /**
     * 提取根域名
     * 例如: login.example.com -> example.com
     *       sub.domain.example.com -> example.com
     */
    private String extractRootDomain(String domain) {
        if (domain == null || domain.isEmpty()) {
            return null;
        }

        String[] parts = domain.split("\\.");
        
        // 至少需要两部分（domain.tld）
        if (parts.length < 2) {
            return domain;
        }

        // 返回最后两部分
        return parts[parts.length - 2] + "." + parts[parts.length - 1];
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
            
            File logFile = new File(dir, "autofill_matcher.log");
            FileWriter writer = new FileWriter(logFile, true);
            writer.write(logMessage);
            writer.close();
        } catch (IOException e) {
            // 日志写入失败，使用系统日志记录
            android.util.Log.e(TAG, "Failed to write to log file", e);
        }
    }
}
