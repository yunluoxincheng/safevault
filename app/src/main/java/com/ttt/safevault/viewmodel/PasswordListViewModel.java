package com.ttt.safevault.viewmodel;

import android.app.Application;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.PasswordItem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 密码列表页面的ViewModel
 * 负责管理和展示密码条目列表
 */
public class PasswordListViewModel extends AndroidViewModel {

    private final BackendService backendService;
    private final ExecutorService executor;

    // LiveData用于UI状态管理
    private final MutableLiveData<List<PasswordItem>> _passwordItems = new MutableLiveData<>();
    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> _searchQuery = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> _isSearching = new MutableLiveData<>(false);
    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> _isEmpty = new MutableLiveData<>(false);
    private final MutableLiveData<String> _selectedTag = new MutableLiveData<>(null);  // 当前选中的标签
    private final MutableLiveData<List<String>> _allTags = new MutableLiveData<>(new ArrayList<>());  // 所有可用标签

    public LiveData<List<PasswordItem>> passwordItems = _passwordItems;
    public LiveData<Boolean> isLoading = _isLoading;
    public LiveData<String> searchQuery = _searchQuery;
    public LiveData<Boolean> isSearching = _isSearching;
    public LiveData<String> errorMessage = _errorMessage;
    public LiveData<Boolean> isEmpty = _isEmpty;
    public LiveData<String> selectedTag = _selectedTag;
    public LiveData<List<String>> allTags = _allTags;

    private List<PasswordItem> allItems; // 保存原始数据

    public PasswordListViewModel(@NonNull Application application, BackendService backendService) {
        super(application);
        this.backendService = backendService;
        this.executor = Executors.newSingleThreadExecutor();
        loadPasswordItems();
    }

    /**
     * 获取所有密码的LiveData（供密码选择对话框使用）
     * @return 所有密码的LiveData
     */
    public LiveData<List<PasswordItem>> getAllPasswords() {
        // 如果数据未加载，先加载
        if (allItems == null) {
            loadPasswordItems();
        }
        // 返回原始数据（未过滤的）
        MutableLiveData<List<PasswordItem>> result = new MutableLiveData<>();
        result.setValue(allItems != null ? new ArrayList<>(allItems) : new ArrayList<>());
        return result;
    }

    /**
     * 加载所有密码条目
     */
    public void loadPasswordItems() {
        _isLoading.setValue(true);
        _errorMessage.setValue(null);

        executor.execute(() -> {
            try {
                List<PasswordItem> items = backendService.getAllItems();
                // 创建新的 ArrayList 以确保 ListAdapter 能检测到变化
                allItems = new ArrayList<>(items);

                // 提取所有标签
                List<String> tags = extractAllTags(items);
                _allTags.postValue(tags);

                // 应用当前过滤（搜索和标签）
                items = applyFilters(items);

                _passwordItems.postValue(items);
                _isEmpty.postValue(items.isEmpty());
            } catch (Exception e) {
                _errorMessage.postValue("加载失败: " + e.getMessage());
            } finally {
                _isLoading.postValue(false);
            }
        });
    }

    /**
     * 搜索密码条目
     */
    public void search(String query) {
        _searchQuery.setValue(query);

        if (query == null || query.trim().isEmpty()) {
            clearSearch();
            return;
        }

        _isSearching.setValue(true);

        executor.execute(() -> {
            try {
                List<PasswordItem> filteredItems;

                // 如果有原始数据，在前端过滤（更快）
                if (allItems != null) {
                    filteredItems = applyFilters(allItems);
                } else {
                    filteredItems = new ArrayList<>();
                }

                // 创建新的 ArrayList 以确保 ListAdapter 能检测到变化
                _passwordItems.postValue(new ArrayList<>(filteredItems));
                _isEmpty.postValue(filteredItems.isEmpty());
            } catch (Exception e) {
                _errorMessage.postValue("搜索失败: " + e.getMessage());
            }
        });
    }

    /**
     * 按标签筛选
     */
    public void filterByTag(String tag) {
        _selectedTag.setValue(tag);

        executor.execute(() -> {
            try {
                List<PasswordItem> filteredItems;
                if (allItems != null) {
                    filteredItems = applyFilters(allItems);
                } else {
                    filteredItems = new ArrayList<>();
                }

                _passwordItems.postValue(new ArrayList<>(filteredItems));
                _isEmpty.postValue(filteredItems.isEmpty());
            } catch (Exception e) {
                _errorMessage.postValue("标签筛选失败: " + e.getMessage());
            }
        });
    }

    /**
     * 清除标签筛选
     */
    public void clearTagFilter() {
        filterByTag(null);
    }

    /**
     * 清除搜索，显示所有条目
     */
    public void clearSearch() {
        _searchQuery.setValue("");
        _isSearching.setValue(false);

        executor.execute(() -> {
            if (allItems != null) {
                // 应用标签过滤（如果有选中的标签）
                List<PasswordItem> filteredItems = applyFilters(allItems);
                _passwordItems.postValue(new ArrayList<>(filteredItems));
                _isEmpty.postValue(filteredItems.isEmpty());
            } else {
                loadPasswordItems();
            }
        });
    }

    /**
     * 删除密码条目
     */
    public void deletePasswordItem(int itemId) {
        executor.execute(() -> {
            try {
                boolean success = backendService.deleteItem(itemId);
                if (success) {
                    // 从列表中移除 - 创建新列表以确保 ListAdapter 能检测到变化
                    List<PasswordItem> currentItems = _passwordItems.getValue();
                    if (currentItems != null) {
                        // 创建新的 ArrayList，而不是修改原列表
                        List<PasswordItem> updatedItems = new ArrayList<>(currentItems);
                        updatedItems.removeIf(item -> item.getId() == itemId);
                        _passwordItems.postValue(updatedItems);

                        // 更新原始数据 - 同样创建新列表
                        if (allItems != null) {
                            allItems = new ArrayList<>(allItems);
                            allItems.removeIf(item -> item.getId() == itemId);
                        }
                    }
                } else {
                    _errorMessage.postValue("删除失败");
                }
            } catch (Exception e) {
                _errorMessage.postValue("删除失败: " + e.getMessage());
            }
        });
    }

    /**
     * 获取单个密码条目
     */
    public void getPasswordItem(int itemId, PasswordItemCallback callback) {
        executor.execute(() -> {
            try {
                PasswordItem item = backendService.decryptItem(itemId);
                if (callback != null) {
                    callback.onResult(item, null);
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onResult(null, e);
                }
            }
        });
    }

    /**
     * 复制密码到剪贴板
     */
    public void copyPassword(int itemId) {
        executor.execute(() -> {
            try {
                PasswordItem item = backendService.decryptItem(itemId);
                if (item != null && item.getPassword() != null) {
                    // TODO: 使用剪贴板管理器复制密码
                    // clipboardManager.copy(item.getPassword());
                }
            } catch (Exception e) {
                _errorMessage.postValue("复制失败: " + e.getMessage());
            }
        });
    }

    /**
     * 刷新数据（显示加载动画）
     */
    public void refresh() {
        loadPasswordItems();
    }

    /**
     * 静默刷新数据（不显示加载动画，用于从其他页面返回时）
     */
    public void refreshSilently() {
        _errorMessage.setValue(null);

        executor.execute(() -> {
            try {
                List<PasswordItem> items = backendService.getAllItems();
                // 创建新的 ArrayList 以确保 ListAdapter 能检测到变化
                allItems = new ArrayList<>(items);

                // 提取所有标签
                List<String> tags = extractAllTags(items);
                _allTags.postValue(tags);

                // 应用当前过滤（搜索和标签）
                items = applyFilters(items);

                _passwordItems.postValue(items);
                _isEmpty.postValue(items.isEmpty());
            } catch (Exception e) {
                _errorMessage.postValue("刷新失败: " + e.getMessage());
            }
        });
    }

    /**
     * 清除错误信息
     */
    public void clearError() {
        _errorMessage.setValue(null);
    }

    /**
     * 应用所有过滤器（搜索和标签）
     */
    private List<PasswordItem> applyFilters(List<PasswordItem> items) {
        String query = _searchQuery.getValue();
        String tag = _selectedTag.getValue();

        return items.stream()
                .filter(item -> {
                    // 首先检查标签过滤
                    if (tag != null && !tag.isEmpty() && !item.hasTag(tag)) {
                        return false;
                    }
                    // 然后检查搜索过滤
                    if (query != null && !query.trim().isEmpty() && !matchesQuery(item, query)) {
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    /**
     * 检查条目是否匹配搜索词
     */
    private boolean matchesQuery(PasswordItem item, String query) {
        String lowerQuery = query.toLowerCase();

        return (item.getTitle() != null && item.getTitle().toLowerCase().contains(lowerQuery)) ||
               (item.getUsername() != null && item.getUsername().toLowerCase().contains(lowerQuery)) ||
               (item.getUrl() != null && item.getUrl().toLowerCase().contains(lowerQuery)) ||
               (item.getNotes() != null && item.getNotes().toLowerCase().contains(lowerQuery)) ||
               (item.getTags().stream().anyMatch(t -> t.toLowerCase().contains(lowerQuery)));  // 也搜索标签
    }

    /**
     * 从所有密码条目中提取唯一标签
     */
    private List<String> extractAllTags(List<PasswordItem> items) {
        return items.stream()
                .flatMap(item -> item.getTags().stream())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 回调接口
     */
    public interface PasswordItemCallback {
        void onResult(PasswordItem item, Exception error);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}