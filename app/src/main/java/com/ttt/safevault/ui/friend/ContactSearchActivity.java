package com.ttt.safevault.ui.friend;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.ttt.safevault.R;
import com.ttt.safevault.adapter.UserSearchResultAdapter;
import com.ttt.safevault.dto.request.SendFriendRequestRequest;
import com.ttt.safevault.dto.response.FriendRequestResponse;
import com.ttt.safevault.dto.response.UserSearchResult;
import com.ttt.safevault.network.RetrofitClient;
import com.ttt.safevault.network.api.FriendServiceApi;
import com.ttt.safevault.service.manager.ContactManager;
import com.ttt.safevault.data.Contact;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 云端用户搜索界面
 * 提供用户搜索功能，搜索结果可以添加为好友
 */
public class ContactSearchActivity extends AppCompatActivity {

    private static final String TAG = "ContactSearchActivity";
    private static final int SEARCH_DEBOUNCE_DELAY_MS = 300;

    private MaterialToolbar toolbar;
    private TextInputEditText editSearch;
    private RecyclerView recyclerView;
    private View emptyView;
    private View progressView;

    private UserSearchResultAdapter adapter;
    private FriendServiceApi friendServiceApi;
    private ContactManager contactManager;
    private CompositeDisposable disposables = new CompositeDisposable();

    // 好友过滤：存储已添加好友的 cloudUserId
    private Set<String> friendCloudUserIds = new HashSet<>();

    private Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_search);

        friendServiceApi = RetrofitClient.getInstance(this).getFriendServiceApi();
        contactManager = new ContactManager(this);

        initViews();
        setupSearchListener();

        // 加载好友过滤器
        loadFriendFilter();
    }

    /**
     * 加载已添加好友的 cloudUserId，用于过滤搜索结果
     */
    private void loadFriendFilter() {
        new Thread(() -> {
            List<Contact> contacts = contactManager.getAllContacts();
            friendCloudUserIds.clear();
            for (Contact contact : contacts) {
                if (contact.cloudUserId != null && !contact.cloudUserId.isEmpty()) {
                    friendCloudUserIds.add(contact.cloudUserId);
                }
            }
        }).start();
    }

    private void initViews() {
        // 工具栏
        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // 搜索框
        editSearch = findViewById(R.id.edit_search);

        // 结果列表
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new UserSearchResultAdapter(this::onAddFriendClick);
        recyclerView.setAdapter(adapter);

        // 空状态视图
        emptyView = findViewById(R.id.empty_view);

        // 进度指示器
        progressView = findViewById(R.id.progress_view);

        // 初始显示空状态（提示用户搜索）
        showEmptyState(true);
    }

    /**
     * 设置搜索监听器，带防抖处理
     */
    private void setupSearchListener() {
        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 取消之前的搜索任务
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }

                // 创建新的搜索任务
                String query = s.toString().trim();
                if (query.isEmpty()) {
                    // 清空搜索时显示初始空状态
                    showEmptyState(true);
                    adapter.submitList(new ArrayList<>());
                    return;
                }

                // 延迟执行搜索
                searchRunnable = () -> performSearch(query);
                searchHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_DELAY_MS);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    /**
     * 执行搜索
     */
    private void performSearch(String query) {
        if (query.length() < 2) {
            // 搜索关键词太短
            Toast.makeText(this, "请输入至少2个字符", Toast.LENGTH_SHORT).show();
            return;
        }

        showProgress(true);

        disposables.add(
            friendServiceApi.searchUsers(query)
                .subscribeOn(Schedulers.io())
                .map(results -> {
                    // 过滤掉已添加的好友
                    List<UserSearchResult> filtered = new ArrayList<>();
                    for (UserSearchResult result : results) {
                        if (!friendCloudUserIds.contains(result.getUserId())) {
                            filtered.add(result);
                        }
                    }
                    return filtered;
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    results -> {
                        showProgress(false);
                        if (results.isEmpty()) {
                            showEmptyState(true);
                            adapter.submitList(new ArrayList<>());
                        } else {
                            showEmptyState(false);
                            adapter.submitList(results);
                        }
                    },
                    error -> {
                        showProgress(false);
                        showEmptyState(true);
                        String errorMsg = error.getMessage() != null ? error.getMessage() : "搜索失败";
                        Toast.makeText(this, "搜索失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                    }
                )
        );
    }

    /**
     * 点击添加好友按钮
     */
    private void onAddFriendClick(UserSearchResult userResult) {
        // 显示发送好友请求对话框
        SendFriendRequestDialog dialog = SendFriendRequestDialog.newInstance(userResult);
        dialog.setOnSendRequestListener((toUserId, message) -> sendFriendRequest(toUserId, message, userResult));
        dialog.show(getSupportFragmentManager(), SendFriendRequestDialog.class.getSimpleName());
    }

    /**
     * 发送好友请求
     */
    private void sendFriendRequest(String toUserId, String message, UserSearchResult userResult) {
        showProgressDialog("正在发送好友请求...");

        SendFriendRequestRequest request = new SendFriendRequestRequest(toUserId, message);

        disposables.add(
            friendServiceApi.sendFriendRequest(request)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    response -> {
                        hideProgressDialog();
                        String displayText = userResult.getDisplayName() != null && !userResult.getDisplayName().isEmpty()
                                ? userResult.getDisplayName()
                                : userResult.getUsername();
                        Toast.makeText(this, "已向 " + displayText + " 发送好友请求", Toast.LENGTH_SHORT).show();

                        // 可选：发送成功后自动关闭界面
                        // finish();
                    },
                    error -> {
                        hideProgressDialog();
                        String errorMsg = error.getMessage() != null ? error.getMessage() : "发送失败";
                        Toast.makeText(this, "发送好友请求失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                    }
                )
        );
    }

    /**
     * 显示/隐藏进度指示器
     */
    private void showProgress(boolean show) {
        progressView.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    /**
     * 显示/隐藏空状态视图
     */
    private void showEmptyState(boolean show) {
        emptyView.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            recyclerView.setVisibility(View.GONE);
        }
    }

    /**
     * 显示进度对话框
     */
    private void showProgressDialog(String message) {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setMessage(message);
            progressDialog.setCancelable(false);
        } else {
            progressDialog.setMessage(message);
        }
        progressDialog.show();
    }

    /**
     * 隐藏进度对话框
     */
    private void hideProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 取消待执行的搜索任务
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
        // 清理RxJava订阅
        disposables.clear();
        // 隐藏进度对话框
        hideProgressDialog();
    }
}
