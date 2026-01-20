package com.ttt.safevault.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.ttt.safevault.R;
import com.ttt.safevault.data.ShareRecord;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 分享记录适配器
 * 使用ShareRecord实体显示分享历史
 */
public class ShareRecordAdapter extends ListAdapter<ShareRecord, ShareRecordAdapter.ViewHolder> {

    private final Consumer<ShareRecord> onClickListener;
    private final Consumer<ShareRecord> onLongClickListener;
    private boolean isMyShares = true; // true表示我的分享，false表示接收的分享

    private static final DiffUtil.ItemCallback<ShareRecord> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ShareRecord>() {
                @Override
                public boolean areItemsTheSame(@NonNull ShareRecord oldItem, @NonNull ShareRecord newItem) {
                    return oldItem.shareId.equals(newItem.shareId);
                }

                @Override
                public boolean areContentsTheSame(@NonNull ShareRecord oldItem, @NonNull ShareRecord newItem) {
                    return oldItem.status.equals(newItem.status) &&
                            oldItem.accessedAt == newItem.accessedAt;
                }
            };

    public ShareRecordAdapter(Consumer<ShareRecord> onClickListener,
                             Consumer<ShareRecord> onLongClickListener) {
        super(DIFF_CALLBACK);
        this.onClickListener = onClickListener;
        this.onLongClickListener = onLongClickListener;
    }

    public void setIsMyShares(boolean isMyShares) {
        this.isMyShares = isMyShares;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_share_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ShareRecord record = getItem(position);
        holder.bind(record, isMyShares);

        holder.itemView.setOnClickListener(v -> {
            if (onClickListener != null) {
                onClickListener.accept(record);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (onLongClickListener != null) {
                onLongClickListener.accept(record);
            }
            return true;
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView textTitle;
        private final TextView textStatus;
        private final TextView textUser;
        private final TextView textPermission;
        private final TextView textTime;
        private final TextView textExpire;
        private final MaterialButton btnView;
        private final MaterialButton btnRevoke;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textTitle = itemView.findViewById(R.id.text_title);
            textStatus = itemView.findViewById(R.id.text_status);
            textUser = itemView.findViewById(R.id.text_user);
            textPermission = itemView.findViewById(R.id.text_permission);
            textTime = itemView.findViewById(R.id.text_time);
            textExpire = itemView.findViewById(R.id.text_expire);
            btnView = itemView.findViewById(R.id.btn_view);
            btnRevoke = itemView.findViewById(R.id.btn_revoke);
        }

        public void bind(ShareRecord record, boolean isMyShares) {
            // 标题（显示密码ID）
            textTitle.setText("密码ID: " + record.passwordId);

            // 状态
            updateStatus(record.status);

            // 用户信息
            if (isMyShares) {
                if (record.contactId != null && !record.contactId.isEmpty()) {
                    textUser.setText("分享给联系人: " + record.contactId);
                } else if (record.remoteUserId != null && !record.remoteUserId.isEmpty()) {
                    textUser.setText("分享给用户: " + record.remoteUserId);
                } else {
                    textUser.setText("分享创建");
                }
            } else {
                if (record.contactId != null && !record.contactId.isEmpty()) {
                    textUser.setText("来自联系人: " + record.contactId);
                } else if (record.remoteUserId != null && !record.remoteUserId.isEmpty()) {
                    textUser.setText("来自用户: " + record.remoteUserId);
                } else {
                    textUser.setText("收到分享");
                }
            }

            // 权限信息
            textPermission.setText("查看分享详情了解权限");

            // 创建时间
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            textTime.setText(sdf.format(new Date(record.createdAt)));

            // 过期时间
            updateExpireTime(record.expireAt);

            // 只有活跃状态的分享才能撤销
            btnRevoke.setEnabled("active".equals(record.status));

            // 接收的分享不显示撤销按钮
            if (!isMyShares) {
                btnRevoke.setVisibility(View.GONE);
            } else {
                btnRevoke.setVisibility(View.VISIBLE);
            }
        }

        private void updateStatus(String status) {
            switch (status) {
                case "active":
                    textStatus.setText("活跃");
                    textStatus.setBackgroundResource(R.drawable.bg_status_active);
                    textStatus.setTextColor(0xFF2E7D32); // 绿色
                    break;
                case "expired":
                    textStatus.setText("已过期");
                    textStatus.setBackgroundResource(android.R.color.transparent);
                    textStatus.setTextColor(0xFF757575); // 灰色
                    break;
                case "revoked":
                    textStatus.setText("已撤销");
                    textStatus.setBackgroundResource(android.R.color.transparent);
                    textStatus.setTextColor(0xFFD32F2F); // 红色
                    break;
                case "accepted":
                    textStatus.setText("已接收");
                    textStatus.setBackgroundResource(android.R.color.transparent);
                    textStatus.setTextColor(0xFF1976D2); // 蓝色
                    break;
                case "pending":
                    textStatus.setText("等待中");
                    textStatus.setBackgroundResource(android.R.color.transparent);
                    textStatus.setTextColor(0xFFFF6F00); // 橙色
                    break;
                default:
                    textStatus.setText(status);
                    textStatus.setBackgroundResource(android.R.color.transparent);
                    textStatus.setTextColor(0xFF757575);
            }
        }

        private void updateExpireTime(long expireTime) {
            if (expireTime <= 0) {
                textExpire.setText("永久有效");
                textExpire.setTextColor(0xFF757575); // 灰色
                return;
            }

            long now = System.currentTimeMillis();
            long remaining = expireTime - now;

            if (remaining <= 0) {
                textExpire.setText("已过期");
                textExpire.setTextColor(0xFFD32F2F); // 红色
            } else {
                long hours = remaining / (1000 * 60 * 60);
                long days = hours / 24;

                String expireText;
                if (days > 0) {
                    expireText = days + "天后过期";
                } else if (hours > 0) {
                    expireText = hours + "小时后过期";
                } else {
                    long minutes = remaining / (1000 * 60);
                    expireText = minutes + "分钟后过期";
                }

                textExpire.setText(expireText);
                textExpire.setTextColor(0xFFFF6F00); // 橙色
            }
        }
    }
}
