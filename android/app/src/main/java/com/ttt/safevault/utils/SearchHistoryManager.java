package com.ttt.safevault.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 搜索历史管理器
 * 管理用户的搜索历史记录，提供增删改查功能
 */
public class SearchHistoryManager {

    private static final String PREFS_NAME = "search_history";
    private static final String KEY_QUERY_PREFIX = "query_";
    private static final String KEY_TIMESTAMP_PREFIX = "timestamp_";
    private static final int MAX_HISTORY_SIZE = 20;
    private static final int MAX_SUGGESTION_COUNT = 5;

    private SharedPreferences preferences;
    private static SearchHistoryManager instance;

    private SearchHistoryManager(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 获取单例实例
     * @param context 上下文
     * @return SearchHistoryManager 实例
     */
    public static synchronized SearchHistoryManager getInstance(Context context) {
        if (instance == null) {
            instance = new SearchHistoryManager(context);
        }
        return instance;
    }

    /**
     * 添加搜索查询到历史记录
     * @param query 搜索查询字符串
     */
    public void addSearchQuery(String query) {
        if (TextUtils.isEmpty(query) || query.trim().isEmpty()) {
            return;
        }

        String trimmedQuery = query.trim();

        // 检查是否已存在，如果存在则更新时间戳
        if (hasQuery(trimmedQuery)) {
            removeQuery(trimmedQuery);
        }

        // 检查历史记录数量，如果超过最大值则删除最旧的
        List<SearchHistoryItem> history = getSearchHistory();
        if (history.size() >= MAX_HISTORY_SIZE) {
            removeOldestQuery();
        }

        // 添加新的搜索查询
        long timestamp = System.currentTimeMillis();
        preferences.edit()
                .putString(KEY_QUERY_PREFIX + trimmedQuery, trimmedQuery)
                .putLong(KEY_TIMESTAMP_PREFIX + trimmedQuery, timestamp)
                .apply();
    }

    /**
     * 从历史记录中删除指定查询
     * @param query 要删除的查询字符串
     */
    public void removeQuery(String query) {
        if (TextUtils.isEmpty(query)) {
            return;
        }

        preferences.edit()
                .remove(KEY_QUERY_PREFIX + query)
                .remove(KEY_TIMESTAMP_PREFIX + query)
                .apply();
    }

    /**
     * 删除最旧的查询
     */
    private void removeOldestQuery() {
        List<SearchHistoryItem> history = getSearchHistory();
        if (!history.isEmpty()) {
            SearchHistoryItem oldest = history.get(history.size() - 1);
            removeQuery(oldest.getQuery());
        }
    }

    /**
     * 检查查询是否存在于历史记录中
     * @param query 查询字符串
     * @return 是否存在
     */
    public boolean hasQuery(String query) {
        if (TextUtils.isEmpty(query)) {
            return false;
        }
        return preferences.contains(KEY_QUERY_PREFIX + query);
    }

    /**
     * 获取所有搜索历史（按时间倒序）
     * @return 搜索历史列表
     */
    public List<SearchHistoryItem> getSearchHistory() {
        List<SearchHistoryItem> history = new ArrayList<>();

        Map<String, ?> allEntries = preferences.getAll();
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(KEY_QUERY_PREFIX)) {
                String query = (String) entry.getValue();
                long timestamp = preferences.getLong(
                        KEY_TIMESTAMP_PREFIX + query,
                        System.currentTimeMillis()
                );
                history.add(new SearchHistoryItem(query, timestamp));
            }
        }

        // 按时间戳倒序排序
        Collections.sort(history, new Comparator<SearchHistoryItem>() {
            @Override
            public int compare(SearchHistoryItem item1, SearchHistoryItem item2) {
                return Long.compare(item2.getTimestamp(), item1.getTimestamp());
            }
        });

        return history;
    }

    /**
     * 获取搜索历史查询字符串列表（按时间倒序）
     * @return 查询字符串列表
     */
    public List<String> getSearchHistoryQueries() {
        List<SearchHistoryItem> history = getSearchHistory();
        List<String> queries = new ArrayList<>();

        for (SearchHistoryItem item : history) {
            queries.add(item.getQuery());
        }

        return queries;
    }

    /**
     * 根据输入生成搜索建议
     * @param input 用户输入
     * @return 搜索建议列表
     */
    public List<String> getSearchSuggestions(String input) {
        List<String> suggestions = new ArrayList<>();

        if (TextUtils.isEmpty(input)) {
            // 如果输入为空，返回最近的历史记录
            List<String> history = getSearchHistoryQueries();
            int count = Math.min(history.size(), MAX_SUGGESTION_COUNT);
            for (int i = 0; i < count; i++) {
                suggestions.add(history.get(i));
            }
            return suggestions;
        }

        String inputLower = input.toLowerCase().trim();
        List<SearchHistoryItem> history = getSearchHistory();

        for (SearchHistoryItem item : history) {
            if (suggestions.size() >= MAX_SUGGESTION_COUNT) {
                break;
            }

            String query = item.getQuery();
            if (query.toLowerCase().contains(inputLower)) {
                suggestions.add(query);
            }
        }

        return suggestions;
    }

    /**
     * 清除所有搜索历史
     */
    public void clearAllHistory() {
        preferences.edit().clear().apply();
    }

    /**
     * 获取搜索历史数量
     * @return 历史记录数量
     */
    public int getHistoryCount() {
        return getSearchHistory().size();
    }

    /**
     * 检查搜索历史是否为空
     * @return 是否为空
     */
    public boolean isEmpty() {
        return getHistoryCount() == 0;
    }

    /**
     * 搜索历史项数据类
     */
    public static class SearchHistoryItem {
        private String query;
        private long timestamp;

        public SearchHistoryItem(String query, long timestamp) {
            this.query = query;
            this.timestamp = timestamp;
        }

        public String getQuery() {
            return query;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setQuery(String query) {
            this.query = query;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            SearchHistoryItem that = (SearchHistoryItem) obj;
            return query != null ? query.equals(that.query) : that.query == null;
        }

        @Override
        public int hashCode() {
            return query != null ? query.hashCode() : 0;
        }

        @Override
        public String toString() {
            return "SearchHistoryItem{" +
                    "query='" + query + '\'' +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }

    /**
     * 搜索历史监听器接口
     */
    public interface SearchHistoryListener {
        /**
         * 当搜索历史发生变化时调用
         */
        void onSearchHistoryChanged();
    }

    private List<SearchHistoryListener> listeners = new ArrayList<>();

    /**
     * 注册搜索历史监听器
     * @param listener 监听器
     */
    public void registerListener(SearchHistoryListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * 取消注册搜索历史监听器
     * @param listener 监听器
     */
    public void unregisterListener(SearchHistoryListener listener) {
        listeners.remove(listener);
    }

    /**
     * 通知所有监听器搜索历史已更改
     */
    private void notifyHistoryChanged() {
        for (SearchHistoryListener listener : listeners) {
            listener.onSearchHistoryChanged();
        }
    }

    /**
     * 导出搜索历史为 JSON 字符串
     * @return JSON 字符串
     */
    public String exportToJson() {
        List<SearchHistoryItem> history = getSearchHistory();
        StringBuilder json = new StringBuilder();
        json.append("[");
        for (int i = 0; i < history.size(); i++) {
            SearchHistoryItem item = history.get(i);
            json.append("{");
            json.append("\"query\":\"").append(escapeJson(item.getQuery())).append("\",");
            json.append("\"timestamp\":").append(item.getTimestamp());
            json.append("}");
            if (i < history.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");
        return json.toString();
    }

    /**
     * 转义 JSON 特殊字符
     * @param value 原始字符串
     * @return 转义后的字符串
     */
    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 导入搜索历史（预留接口）
     * @param jsonData JSON 字符串
     */
    public void importFromJson(String jsonData) {
        // 预留导入功能，可后续实现
        // 使用 JSON 解析库（如 Gson 或 JSONObject）解析并导入
    }

    /**
     * 获取最热门的搜索查询（使用频率最高）
     * @param limit 返回数量限制
     * @return 最热门的搜索查询列表
     */
    public List<String> getTopQueries(int limit) {
        // 由于当前实现只存储时间戳，这里返回最近的历史记录
        // 如果需要实现频率统计，需要在存储中添加访问计数
        List<String> history = getSearchHistoryQueries();
        int count = Math.min(history.size(), limit);
        return history.subList(0, count);
    }

    /**
     * 压缩搜索历史，移除重复项并保留最新的
     */
    public void compressHistory() {
        List<SearchHistoryItem> history = getSearchHistory();
        clearAllHistory();

        for (SearchHistoryItem item : history) {
            preferences.edit()
                    .putString(KEY_QUERY_PREFIX + item.getQuery(), item.getQuery())
                    .putLong(KEY_TIMESTAMP_PREFIX + item.getQuery(), item.getTimestamp())
                    .apply();
        }
    }

    /**
     * 获取搜索历史占用的存储空间（字节）
     * @return 存储空间大小
     */
    public int getStorageSize() {
        Map<String, ?> allEntries = preferences.getAll();
        int size = 0;
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue().toString();
            size += key.length() * 2; // UTF-16
            size += value.length() * 2;
            size += 8; // Long (timestamp)
        }
        return size;
    }

    /**
     * 设置最大历史记录数量
     * @param maxSize 最大数量
     */
    public void setMaxHistorySize(int maxSize) {
        // 这个方法需要修改为使用动态配置
        // 当前使用静态常量 MAX_HISTORY_SIZE
        // 预留接口，可后续实现
    }

    /**
     * 获取最大历史记录数量
     * @return 最大数量
     */
    public int getMaxHistorySize() {
        return MAX_HISTORY_SIZE;
    }

    /**
     * 获取建议数量限制
     * @return 建议数量限制
     */
    public int getMaxSuggestionCount() {
        return MAX_SUGGESTION_COUNT;
    }
}
