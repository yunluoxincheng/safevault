package com.ttt.safevault.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.ttt.safevault.SafeVaultApplication;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.ttt.safevault.R;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.network.TokenManager;
import com.ttt.safevault.service.ShareNotificationService;
import com.ttt.safevault.utils.SearchHistoryManager;
import com.ttt.safevault.viewmodel.PasswordListViewModel;

/**
 * 主Activity
 * 作为应用的主容器，承载各个Fragment，支持底部导航
 * 支持云端服务集成和WebSocket实时通知
 */
public class MainActivity extends AppCompatActivity {

    private NavController navController;
    private AppBarConfiguration appBarConfiguration;
    private PasswordListViewModel listViewModel;
    private BackendService backendService;
    private TokenManager tokenManager;
    private SearchHistoryManager searchHistoryManager;
    private BottomNavigationView bottomNavigationView;
    private FloatingActionButton fabAddPassword;

    // Search debounce handler
    private Handler searchDebounceHandler;
    private Runnable searchDebounceRunnable;
    private static final int SEARCH_DEBOUNCE_DELAY_MS = 150; // 150ms debounce delay

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 防止截图 - 根据 SecurityConfig 设置决定
        com.ttt.safevault.security.SecurityConfig securityConfig =
            new com.ttt.safevault.security.SecurityConfig(this);
        if (securityConfig.isScreenshotProtectionEnabled()) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        }

        // 获取BackendService实例
        backendService = com.ttt.safevault.ServiceLocator.getInstance().getBackendService();

        // 初始化TokenManager
        tokenManager = TokenManager.getInstance(this);

        // 初始化搜索 debounce handler
        searchDebounceHandler = new Handler(Looper.getMainLooper());

        // 初始化搜索历史管理器
        searchHistoryManager = SearchHistoryManager.getInstance(this);

        // 检查启动来源：
        // 1. savedInstanceState != null：Activity被系统重建，不需要检查锁定
        // 2. Intent有FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY：从最近任务恢复，需要检查锁定
        // 3. 否则：正常启动（登录后跳转），清除后台时间
        Intent intent = getIntent();
        boolean launchedFromHistory = (intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0;

        // 调试日志
        android.util.Log.d("MainActivity", "onCreate - savedInstanceState=" + savedInstanceState +
            ", launchedFromHistory=" + launchedFromHistory +
            ", action=" + intent.getAction());

        if (backendService != null) {
            long bgTime = backendService.getBackgroundTime();
            android.util.Log.d("MainActivity", "backgroundTime=" + bgTime);
        }

        if (savedInstanceState == null && launchedFromHistory) {
            // 从最近任务/后台恢复，检查是否需要锁定
            android.util.Log.d("MainActivity", "从后台恢复，检查是否需要锁定");
            if (shouldLockOnStart()) {
                android.util.Log.d("MainActivity", "需要锁定，跳转到登录页面");
                lockApp();
                return;
            }
        } else if (savedInstanceState == null && !launchedFromHistory && backendService != null) {
            // 正常启动（如登录后跳转），清除后台时间记录
            android.util.Log.d("MainActivity", "正常启动，清除后台时间");
            backendService.clearBackgroundTime();
        }

        initNavigation();
        initToolbar();
        initBottomNavigation();
        initFab();
        initViewModel();
        initCloudServices();

        // 处理从自动填充返回的意图
        handleAutofillIntent();
    }

    /**
     * 初始化云端服务
     * 如果用户已登录云端账号，启动WebSocket通知服务
     */
    private void initCloudServices() {
        if (tokenManager != null && tokenManager.isLoggedIn()) {
            startNotificationService();
        }
    }

    /**
     * 启动分享通知服务
     * 维护WebSocket连接以接收实时分享通知
     */
    private void startNotificationService() {
        try {
            Intent serviceIntent = new Intent(this, ShareNotificationService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Failed to start notification service", e);
        }
    }

    /**
     * 停止分享通知服务
     */
    private void stopNotificationService() {
        try {
            Intent serviceIntent = new Intent(this, ShareNotificationService.class);
            stopService(serviceIntent);
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Failed to stop notification service", e);
        }
    }

    private void initNavigation() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();

            // 设置顶级目的地（底部导航的四个选项卡）
            appBarConfiguration = new AppBarConfiguration.Builder(
                    R.id.nav_passwords,
                    R.id.nav_contacts,
                    R.id.nav_generator,
                    R.id.nav_settings
            ).build();
        }
    }

    private void initToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // 禁用自动配置，手动控制标题以避免闪烁
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }

        // 监听导航变化，动态设置标题和菜单
        if (navController != null) {
            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                // 设置标题
                int titleRes = getToolbarTitle(destination.getId());
                if (titleRes != 0) {
                    toolbar.setTitle(titleRes);
                }

                // 处理返回按钮显示
                boolean isTopLevelDestination = destination.getId() == R.id.nav_passwords
                        || destination.getId() == R.id.nav_contacts
                        || destination.getId() == R.id.nav_generator
                        || destination.getId() == R.id.nav_settings;

                if (getSupportActionBar() != null) {
                    getSupportActionBar().setDisplayHomeAsUpEnabled(!isTopLevelDestination);
                }
                toolbar.setNavigationOnClickListener(v -> {
                    if (!isTopLevelDestination) {
                        navController.navigateUp();
                    }
                });

                // 清除并重新创建菜单
                invalidateOptionsMenu();
            });
        }
    }

    private int getToolbarTitle(int destinationId) {
        if (destinationId == R.id.nav_passwords) {
            return R.string.nav_passwords;
        } else if (destinationId == R.id.nav_contacts) {
            return R.string.nav_contacts;
        } else if (destinationId == R.id.nav_generator) {
            return R.string.nav_generator;
        } else if (destinationId == R.id.nav_settings) {
            return R.string.nav_settings;
        } else if (destinationId == R.id.passwordDetailFragment) {
            return R.string.password_details;
        } else if (destinationId == R.id.editPasswordFragment) {
            return R.string.edit;
        } else if (destinationId == R.id.accountSecurityFragment) {
            return R.string.account_security;
        } else if (destinationId == R.id.appearanceSettingsFragment) {
            return R.string.appearance_settings;
        } else if (destinationId == R.id.aboutFragment) {
            return R.string.about;
        }
        return 0;
    }

    private void initBottomNavigation() {
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        if (bottomNavigationView != null && navController != null) {
            // 手动设置底部导航的点击事件，禁用动画
            bottomNavigationView.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                // 只在切换到不同的目标时才导航
                if (navController.getCurrentDestination() != null
                        && navController.getCurrentDestination().getId() != itemId) {
                    navController.navigate(itemId, null,
                            new NavOptions.Builder()
                                    .setEnterAnim(0)
                                    .setExitAnim(0)
                                    .setPopEnterAnim(0)
                                    .setPopExitAnim(0)
                                    .build());
                }
                return true;
            });

            // 监听导航变化，控制底部导航显示
            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                // 只在顶级目的地显示底部导航
                int destinationId = destination.getId();
                boolean isTopLevelDestination = destinationId == R.id.nav_passwords
                        || destinationId == R.id.nav_contacts
                        || destinationId == R.id.nav_generator
                        || destinationId == R.id.nav_settings;

                bottomNavigationView.setVisibility(isTopLevelDestination ? View.VISIBLE : View.GONE);

                // 同步底部导航的选中状态
                if (isTopLevelDestination) {
                    MenuItem item = bottomNavigationView.getMenu().findItem(destinationId);
                    if (item != null) {
                        item.setChecked(true);
                    }
                }
            });
        }
    }

    private void initFab() {
        fabAddPassword = findViewById(R.id.fab_add_password);
        if (fabAddPassword != null && navController != null) {
            fabAddPassword.setOnClickListener(v -> {
                // 导航到编辑密码页面（新建模式）
                navController.navigate(R.id.action_passwordListFragment_to_editPasswordFragment);
            });

            // 监听导航变化，只在密码库页面显示 FAB
            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                int destinationId = destination.getId();
                boolean shouldShowFab = destinationId == R.id.nav_passwords;

                if (shouldShowFab) {
                    fabAddPassword.show();
                } else {
                    fabAddPassword.hide();
                }
            });
        }
    }

    private void initViewModel() {
        // 通过ViewModelFactory获取ViewModel
        ViewModelProvider.Factory factory = new com.ttt.safevault.viewmodel.ViewModelFactory(getApplication());
        listViewModel = new ViewModelProvider(this, factory).get(PasswordListViewModel.class);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 根据当前页面决定显示哪些菜单项
        int currentDestinationId = 0;
        if (navController != null) {
            currentDestinationId = navController.getCurrentDestination().getId();
        }

        // 只在密码库页面显示搜索
        if (currentDestinationId == R.id.nav_passwords) {
            // 检查菜单是否已经存在，避免重复添加
            if (menu.findItem(R.id.action_search) == null) {
                getMenuInflater().inflate(R.menu.main_menu, menu);

                // 设置搜索菜单项
                MenuItem searchItem = menu.findItem(R.id.action_search);
                if (searchItem != null) {
                    androidx.appcompat.widget.SearchView searchView = (androidx.appcompat.widget.SearchView) searchItem.getActionView();
                    if (searchView != null) {
                        setupSearchView(searchView);
                    }
                }
            }
        } else {
            // 非密码库页面，清空菜单
            menu.clear();
        }

        return true;
    }

    private void setupSearchView(androidx.appcompat.widget.SearchView searchView) {
        searchView.setQueryHint("搜索密码");

        // 设置搜索建议（从搜索历史）
        searchView.setOnQueryTextFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && searchHistoryManager != null) {
                // 可以在这里显示搜索建议
                // SearchView 不直接支持下拉建议，需要自定义实现
            }
        });

        searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                // 取消待执行的 debounce 搜索
                if (searchDebounceRunnable != null) {
                    searchDebounceHandler.removeCallbacks(searchDebounceRunnable);
                }

                if (listViewModel != null) {
                    listViewModel.search(query);
                }

                // 添加到搜索历史
                if (searchHistoryManager != null && query != null && !query.trim().isEmpty()) {
                    searchHistoryManager.addSearchQuery(query.trim());
                }

                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // 取消之前的搜索任务
                if (searchDebounceRunnable != null) {
                    searchDebounceHandler.removeCallbacks(searchDebounceRunnable);
                }

                // 创建新的延迟搜索任务
                searchDebounceRunnable = () -> {
                    if (listViewModel != null) {
                        listViewModel.search(newText);
                    }
                };

                // 延迟执行搜索（debounce）
                searchDebounceHandler.postDelayed(searchDebounceRunnable, SEARCH_DEBOUNCE_DELAY_MS);

                return true;
            }
        });

        searchView.setOnCloseListener(() -> {
            try {
                // 取消待执行的 debounce 搜索
                if (searchDebounceRunnable != null) {
                    searchDebounceHandler.removeCallbacks(searchDebounceRunnable);
                }

                // 清除搜索
                if (listViewModel != null) {
                    listViewModel.clearSearch();
                }
            } catch (Exception e) {
                // 忽略异常，防止闪退
            }
            return true;
        });

        // 设置搜索历史（最近搜索）
        if (searchHistoryManager != null && !searchHistoryManager.isEmpty()) {
            List<String> recentSearches = searchHistoryManager.getSearchHistoryQueries();
            if (!recentSearches.isEmpty()) {
                searchView.setQuery(recentSearches.get(0), false);
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }

    @Override
    public void onBackPressed() {
        // 如果在搜索状态，退出搜索
        Boolean isSearching = listViewModel != null ? listViewModel.isSearching.getValue() : null;
        if (isSearching != null && isSearching) {
            // 取消待执行的 debounce 搜索
            if (searchDebounceRunnable != null) {
                searchDebounceHandler.removeCallbacks(searchDebounceRunnable);
            }

            if (listViewModel != null) {
                listViewModel.clearSearch();
            }
            return;
        }

        // 如果在非顶级页面，正常返回
        if (navController != null && navController.getCurrentDestination() != null) {
            int currentDestinationId = navController.getCurrentDestination().getId();
            boolean isTopLevelDestination = currentDestinationId == R.id.nav_passwords
                    || currentDestinationId == R.id.nav_contacts
                    || currentDestinationId == R.id.nav_generator
                    || currentDestinationId == R.id.nav_settings;

            if (!isTopLevelDestination) {
                super.onBackPressed();
                return;
            }
        }

        // 如果在顶级页面，退出应用
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        android.util.Log.d("MainActivity", "=== onResume ===");
        android.util.Log.d("MainActivity", "当前后台时间: " + (backendService != null ? backendService.getBackgroundTime() : "backendService=null"));

        // 应用从后台返回时，检查是否需要重新锁定
        checkAutoLock();
    }

    /**
     * 检查应用启动时是否需要锁定
     * 如果后台时间超过设定的超时时间，返回true
     */
    private boolean shouldLockOnStart() {
        if (backendService != null) {
            long backgroundTime = backendService.getBackgroundTime();
            long autoLockTimeoutMillis = new com.ttt.safevault.security.SecurityConfig(this)
                    .getAutoLockTimeoutMillisForMode();

            // 如果没有后台时间记录，不需要锁定（首次启动或刚登录成功）
            if (backgroundTime == 0) {
                return false;
            }

            // 从不锁定模式
            if (autoLockTimeoutMillis == Long.MAX_VALUE) {
                return false;
            }

            // 立即锁定模式：只要有后台时间记录就锁定（应用进入过后台）
            if (autoLockTimeoutMillis == 0) {
                return true;
            }

            // 其他模式：检查是否超时
            long backgroundMillis = System.currentTimeMillis() - backgroundTime;
            return backgroundMillis >= autoLockTimeoutMillis;
        }
        return false;
    }

    /**
     * 检查应用从后台返回时是否需要锁定
     * 只在应用真正进入后台（而不是Activity之间切换）时才检查
     *
     * 注意：onResume() 在 onActivityStarted() 之后调用，此时 isAppInForeground 已经被设为 true。
     * 因此这里直接检查后台时间，通过 backgroundTime 是否为 0 来判断是否需要锁定。
     */
    private void checkAutoLock() {
        android.util.Log.d("MainActivity", "=== checkAutoLock() 开始 ===");

        // 通过检查后台时间来判断是否需要锁定
        // 如果 backgroundTime == 0，说明应用没有进入过后台，不需要检查锁定

        if (backendService != null) {
            long backgroundTime = backendService.getBackgroundTime();
            long autoLockTimeoutMillis = new com.ttt.safevault.security.SecurityConfig(this)
                    .getAutoLockTimeoutMillisForMode();

            // 计算超时时间（秒）用于显示
            long timeoutSeconds = autoLockTimeoutMillis == Long.MAX_VALUE ? -1 : autoLockTimeoutMillis / 1000;

            android.util.Log.d("MainActivity", "后台时间戳: " + backgroundTime);
            android.util.Log.d("MainActivity", "当前时间戳: " + System.currentTimeMillis());
            android.util.Log.d("MainActivity", "超时设置: " + timeoutSeconds + " 秒 (" + autoLockTimeoutMillis + " 毫秒)");

            if (backgroundTime > 0) {
                long backgroundMillis = System.currentTimeMillis() - backgroundTime;
                android.util.Log.d("MainActivity", "后台时长: " + (backgroundMillis / 1000) + " 秒");

                if (autoLockTimeoutMillis == Long.MAX_VALUE) {
                    android.util.Log.d("MainActivity", "自动锁定模式: 从不锁定");
                } else if (backgroundMillis >= autoLockTimeoutMillis) {
                    // 超时，需要重新锁定
                    android.util.Log.d("MainActivity", "*** 自动锁定超时，执行锁定 ***");
                    lockApp();
                } else {
                    // 未超时，清除后台时间记录
                    android.util.Log.d("MainActivity", "未超时，清除后台时间");
                    backendService.clearBackgroundTime();
                }
            } else {
                android.util.Log.d("MainActivity", "没有后台时间记录（应用未进入过后台）");
            }
        } else {
            android.util.Log.e("MainActivity", "backendService 为 null，无法检查自动锁定");
        }

        android.util.Log.d("MainActivity", "=== checkAutoLock() 结束 ===");
    }

    private void lockApp() {
        android.util.Log.d("MainActivity", "=== lockApp() 被调用 ===");
        if (backendService != null) {
            android.util.Log.d("MainActivity", "调用 backendService.lock()");
            backendService.lock();
            android.util.Log.d("MainActivity", "backendService.lock() 完成");
        }

        // 跳转到登录页面
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    private void handleAutofillIntent() {
        Intent intent = getIntent();
        if (intent != null) {
            boolean isFromAutofill = intent.getBooleanExtra("from_autofill", false);
            String autofillDomain = intent.getStringExtra("autofill_domain");
            
            if (isFromAutofill && autofillDomain != null) {
                // 从自动填充返回，如果有域名参数，导航到密码列表并搜索
                if (navController != null) {
                    // 确保导航到密码列表页面
                    navController.navigate(R.id.nav_passwords);
                    
                    // 等待导航完成后再执行搜索
                    new Handler().postDelayed(() -> {
                        if (listViewModel != null) {
                            listViewModel.search(autofillDomain);
                        }
                    }, 300); // 延迟300毫秒确保页面已加载
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 清理 debounce handler
        if (searchDebounceHandler != null) {
            searchDebounceHandler.removeCallbacksAndMessages(null);
        }
    }
}