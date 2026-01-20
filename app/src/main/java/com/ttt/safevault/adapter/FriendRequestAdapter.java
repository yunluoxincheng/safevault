package com.ttt.safevault.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.ttt.safevault.R;
import com.ttt.safevault.data.FriendRequest;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 好友请求列表适配器
 */
public class FriendRequestAdapter extends ListAdapter<FriendRequest, FriendRequestAdapter.ViewHolder> {

    private OnActionListener actionListener;

    private static final DiffUtil.ItemCallback<FriendRequest> DIFF_CALLBACK = new DiffUtil.ItemCallback<FriendRequest>() {
        @Override
        public boolean areItemsTheSame(@NonNull FriendRequest oldItem, @NonNull FriendRequest newItem) {
            return oldItem.requestId != null && oldItem.requestId.equals(newItem.requestId);
        }

        @Override
        public boolean areContentsTheSame(@NonNull FriendRequest oldItem, @NonNull FriendRequest newItem) {
            return oldItem.status.equals(newItem.status) &&
                   oldItem.fromDisplayName.equals(newItem.fromDisplayName) &&
                   oldItem.message != null && oldItem.message.equals(newItem.message);
        }
    };

    public FriendRequestAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnActionListener(OnActionListener listener) {
        this.actionListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_friend_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FriendRequest request = getItem(position);
        holder.bind(request);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final ImageView imageAvatar;
        private final TextView textDisplayName;
        private final TextView textUsername;
        private final TextView textMessage;
        private final TextView textTime;
        private final MaterialButton btnAccept;
        private final MaterialButton btnReject;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            imageAvatar = itemView.findViewById(R.id.image_avatar);
            textDisplayName = itemView.findViewById(R.id.text_display_name);
            textUsername = itemView.findViewById(R.id.text_username);
            textMessage = itemView.findViewById(R.id.text_message);
            textTime = itemView.findViewById(R.id.text_time);
            btnAccept = itemView.findViewById(R.id.btn_accept);
            btnReject = itemView.findViewById(R.id.btn_reject);
        }

        public void bind(FriendRequest request) {
            // 显示名称
            String displayText = request.fromDisplayName != null && !request.fromDisplayName.isEmpty()
                    ? request.fromDisplayName
                    : request.fromUsername;
            textDisplayName.setText(displayText);

            // 用户名
            textUsername.setText("@" + request.fromUsername);

            // 请求消息
            if (request.message != null && !request.message.isEmpty()) {
                textMessage.setVisibility(View.VISIBLE);
                textMessage.setText(request.message);
            } else {
                textMessage.setVisibility(View.GONE);
            }

            // 请求时间
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            textTime.setText(sdf.format(new Date(request.createdAt)));

            // 头像背景
            imageAvatar.setBackgroundResource(R.drawable.circle_avatar_background);
            imageAvatar.setImageDrawable(null);

            // 状态处理
            boolean isPending = "PENDING".equals(request.status);
            btnAccept.setVisibility(isPending ? View.VISIBLE : View.GONE);
            btnReject.setVisibility(isPending ? View.VISIBLE : View.GONE);

            if (!isPending) {
                // 已处理的请求显示状态
                textMessage.setVisibility(View.VISIBLE);
                String statusText = "ACCEPTED".equals(request.status) ? "已同意" : "已拒绝";
                textMessage.setText(statusText);
                int statusColor = "ACCEPTED".equals(request.status) ? 0xFF4CAF50 : 0xFFF44336;
                textMessage.setTextColor(statusColor);
            }

            // 同意按钮
            btnAccept.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onAccept(request);
                }
            });

            // 拒绝按钮
            btnReject.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onReject(request);
                }
            });
        }
    }

    /**
     * 操作回调接口
     */
    public interface OnActionListener {
        void onAccept(FriendRequest request);
        void onReject(FriendRequest request);
    }
}
