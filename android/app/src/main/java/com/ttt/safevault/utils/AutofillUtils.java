package com.ttt.safevault.utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * 自动填充工具类
 * 提供域名提取、URL规范化等功能
 */
public class AutofillUtils {
    
    /**
     * 从URL中提取域名
     * 
     * @param url 完整的URL，可能包含协议、路径等
     * @return 提取的域名，如 "example.com"
     */
    public static String extractDomainFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        
        // 处理 android:// 协议
        if (url.startsWith("android://")) {
            return url.substring("android://".length());
        }
        
        try {
            // 如果没有协议，添加默认协议以便解析
            String urlToParse = url;
            if (!url.contains("://")) {
                urlToParse = "https://" + url;
            }
            
            URI uri = new URI(urlToParse);
            String host = uri.getHost();
            
            if (host != null) {
                // 规范化：移除 www 前缀（可选）
                return normalizeHost(host);
            }
        } catch (URISyntaxException e) {
            // 解析失败，尝试简单的字符串处理
            return extractDomainSimple(url);
        }
        
        return extractDomainSimple(url);
    }
    
    /**
     * 规范化主机名
     * 可以移除 www 前缀，统一大小写等
     */
    private static String normalizeHost(String host) {
        if (host == null) {
            return null;
        }
        
        String normalized = host.toLowerCase(Locale.ROOT);
        
        // 可选：移除 www 前缀以便更好地匹配
        // 例如 www.example.com 和 example.com 应该被视为同一个域名
        // 但这里我们保留 www，因为有些网站的 www 和非 www 是不同的服务
        
        return normalized;
    }
    
    /**
     * 简单的域名提取（当URI解析失败时使用）
     */
    private static String extractDomainSimple(String url) {
        // 移除协议
        String domain = url;
        int protocolEnd = domain.indexOf("://");
        if (protocolEnd != -1) {
            domain = domain.substring(protocolEnd + 3);
        }
        
        // 移除路径
        int pathStart = domain.indexOf('/');
        if (pathStart != -1) {
            domain = domain.substring(0, pathStart);
        }
        
        // 移除端口
        int portStart = domain.indexOf(':');
        if (portStart != -1) {
            domain = domain.substring(0, portStart);
        }
        
        // 移除查询参数
        int queryStart = domain.indexOf('?');
        if (queryStart != -1) {
            domain = domain.substring(0, queryStart);
        }
        
        return normalizeHost(domain);
    }
    
    /**
     * 检查两个域名是否匹配
     * 
     * @param domain1 第一个域名
     * @param domain2 第二个域名
     * @return 是否匹配
     */
    public static boolean domainsMatch(String domain1, String domain2) {
        if (domain1 == null || domain2 == null) {
            return false;
        }
        
        String normalized1 = normalizeHost(domain1);
        String normalized2 = normalizeHost(domain2);
        
        if (normalized1 == null || normalized2 == null) {
            return false;
        }
        
        // 精确匹配
        if (normalized1.equals(normalized2)) {
            return true;
        }
        
        // 可选：带 www 和不带 www 的匹配
        String withoutWww1 = removeWww(normalized1);
        String withoutWww2 = removeWww(normalized2);
        
        return withoutWww1.equals(withoutWww2);
    }
    
    /**
     * 移除 www 前缀
     */
    private static String removeWww(String host) {
        if (host != null && host.startsWith("www.")) {
            return host.substring(4);
        }
        return host;
    }
    
    /**
     * 检查URL是否为应用包名格式
     */
    public static boolean isPackageNameUrl(String url) {
        return url != null && url.startsWith("android://");
    }
}
