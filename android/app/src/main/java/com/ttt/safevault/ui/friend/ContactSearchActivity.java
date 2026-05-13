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
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.ttt.safevault.R;
import com.ttt.safevault.adapter.UserSearchResultAdapter;
import com.ttt.safevault.dto.response.UserSearchResult;
import com.ttt.safevault.viewmodel.ContactSearchViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Cloud user search page.
 */
public class ContactSearchActivity extends AppCompatActivity {

    private static final int SEARCH_DEBOUNCE_DELAY_MS = 300;

    private MaterialToolbar toolbar;
    private TextInputEditText editSearch;
    private RecyclerView recyclerView;
    private View emptyView;
    private View progressView;

    private UserSearchResultAdapter adapter;
    private ContactSearchViewModel viewModel;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_search);

        viewModel = new ViewModelProvider(this).get(ContactSearchViewModel.class);
        initViews();
        setupObservers();
        setupSearchListener();
        viewModel.loadFriendFilter();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        editSearch = findViewById(R.id.edit_search);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new UserSearchResultAdapter(this::onAddFriendClick);
        recyclerView.setAdapter(adapter);

        emptyView = findViewById(R.id.empty_view);
        progressView = findViewById(R.id.progress_view);
        showEmptyState(true);
    }

    private void setupObservers() {
        viewModel.getSearchResults().observe(this, results -> renderResults(results == null ? new ArrayList<>() : results));

        viewModel.getSearching().observe(this, searching -> showProgress(Boolean.TRUE.equals(searching)));

        viewModel.getErrorMessage().observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                viewModel.clearTransientMessages();
            }
        });

        viewModel.getRequestSending().observe(this, sending -> {
            if (Boolean.TRUE.equals(sending)) {
                showProgressDialog("正在发送好友请求...");
            } else {
                hideProgressDialog();
            }
        });

        viewModel.getRequestMessage().observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                viewModel.clearTransientMessages();
            }
        });
    }

    private void setupSearchListener() {
        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }

                String query = s.toString().trim();
                if (query.isEmpty()) {
                    viewModel.clearSearchResults();
                    renderResults(new ArrayList<>());
                    return;
                }

                searchRunnable = () -> viewModel.searchUsers(query);
                searchHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_DELAY_MS);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void onAddFriendClick(UserSearchResult userResult) {
        SendFriendRequestDialog dialog = SendFriendRequestDialog.newInstance(userResult);
        dialog.setOnSendRequestListener((toUserId, message) -> {
            String displayText = userResult.getDisplayName() != null && !userResult.getDisplayName().isEmpty()
                ? userResult.getDisplayName()
                : userResult.getUsername();
            viewModel.sendFriendRequest(toUserId, message, displayText);
        });
        dialog.show(getSupportFragmentManager(), SendFriendRequestDialog.class.getSimpleName());
    }

    private void renderResults(List<UserSearchResult> results) {
        adapter.submitList(results);
        if (results.isEmpty()) {
            showEmptyState(true);
        } else {
            showEmptyState(false);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showProgress(boolean show) {
        progressView.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void showEmptyState(boolean show) {
        emptyView.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            recyclerView.setVisibility(View.GONE);
        }
    }

    private void showProgressDialog(String message) {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setCancelable(false);
        }
        progressDialog.setMessage(message);
        progressDialog.show();
    }

    private void hideProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
        hideProgressDialog();
    }
}

