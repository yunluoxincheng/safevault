package com.ttt.safevault.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.ttt.safevault.R;
import com.ttt.safevault.dto.response.UserSearchResult;

import java.util.function.Consumer;

/**
 * 用户搜索结果适配器
 * 显示搜索到的用户列表，每个项目包含用户信息和"添加好友"按钮
 */
public class UserSearchResultAdapter extends ListAdapter<UserSearchResult, UserSearchResultAdapter.ViewHolder> {

    private final Consumer<UserSearchResult> onAddFriendClick;

    private static final DiffUtil.ItemCallback<UserSearchResult> DIFF_CALLBACK = new DiffUtil.ItemCallback<UserSearchResult>() {
        @Override
        public boolean areItemsTheSame(@NonNull UserSearchResult oldItem, @NonNull UserSearchResult newItem) {
            return oldItem.getUserId() != null && oldItem.getUserId().equals(newItem.getUserId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull UserSearchResult oldItem, @NonNull UserSearchResult newItem) {
            return oldItem.getUserId().equals(newItem.getUserId()) &&
                   oldItem.getUsername().equals(newItem.getUsername()) &&
                   oldItem.getEmail().equals(newItem.getEmail()) &&
                   (oldItem.getDisplayName() == null ? newItem.getDisplayName() == null : oldItem.getDisplayName().equals(newItem.getDisplayName()));
        }
    };

    public UserSearchResultAdapter(Consumer<UserSearchResult> onAddFriendClick) {
        super(DIFF_CALLBACK);
        this.onAddFriendClick = onAddFriendClick;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user_search_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UserSearchResult result = getItem(position);
        holder.bind(result);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final ImageView imageAvatar;
        private final TextView textDisplayName;
        private final TextView textUsername;
        private final TextView textEmail;
        private final Button btnAddFriend;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            imageAvatar = itemView.findViewById(R.id.image_avatar);
            textDisplayName = itemView.findViewById(R.id.text_display_name);
            textUsername = itemView.findViewById(R.id.text_username);
            textEmail = itemView.findViewById(R.id.text_email);
            btnAddFriend = itemView.findViewById(R.id.btn_add_friend);
        }

        public void bind(UserSearchResult result) {
            // 显示名称（优先使用displayName，否则使用username）
            String displayText = result.getDisplayName() != null && !result.getDisplayName().isEmpty()
                    ? result.getDisplayName()
                    : result.getUsername();
            textDisplayName.setText(displayText);

            // 用户名
            textUsername.setText("@" + result.getUsername());

            // 邮箱
            textEmail.setText(result.getEmail());

            // 头像（取首字母）
            String firstLetter = displayText.isEmpty() ? "?" : String.valueOf(displayText.charAt(0));
            imageAvatar.setImageDrawable(null);
            imageAvatar.setBackgroundResource(R.drawable.circle_avatar_background);

            // 添加好友按钮点击事件
            btnAddFriend.setOnClickListener(v -> {
                if (onAddFriendClick != null) {
                    onAddFriendClick.accept(result);
                }
            });
        }
    }
}
