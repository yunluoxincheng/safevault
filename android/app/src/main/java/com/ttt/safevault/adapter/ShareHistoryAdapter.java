package com.ttt.safevault.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.ttt.safevault.R;
import com.ttt.safevault.dto.response.ReceivedShareResponse;
import com.ttt.safevault.model.PasswordShare;
import com.ttt.safevault.model.SharePermission;
import com.ttt.safevault.model.ShareStatus;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 分享历史适配器
 * 支持离线分享和云端分享
 */
public class ShareHistoryAdapter extends RecyclerView.Adapter<ShareHistoryAdapter.ViewHolder> {

    private List<PasswordShare> shareList = new ArrayList<>();
    private List<ReceivedShareResponse> cloudShareList = new ArrayList<>();
    private boolean isCloudMode = false;
    private OnShareActionListener listener;
    private boolean isMyShares; // true表示我的分享，false表示接收的分享

    public interface OnShareActionListener {
        void onViewShare(PasswordShare share);
        void onRevokeShare(PasswordShare share);
    }

    public ShareHistoryAdapter(boolean isMyShares) {
        this.isMyShares = isMyShares;
    }

    public void setOnShareActionListener(OnShareActionListener listener) {
        this.listener = listener;
    }

    public void setShareList(List<PasswordShare> shares) {
        this.shareList = shares != null ? shares : new ArrayList<>();
        this.isCloudMode = false;
        notifyDataSetChanged();
    }

    public void setCloudShareList(List<ReceivedShareResponse> shares) {
        this.cloudShareList = shares != null ? shares : new ArrayList<>();
        this.isCloudMode = true;
        notifyDataSetChanged();
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
        if (isCloudMode) {
            ReceivedShareResponse cloudShare = cloudShareList.get(position);
            holder.bindCloudShare(cloudShare);
        } else {
            PasswordShare share = shareList.get(position);
            holder.bind(share);
        }
    }

    @Override
    public int getItemCount() {
        return isCloudMode ? cloudShareList.size() : shareList.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
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

        public void bind(PasswordShare share) {
            // 标题（这里简化显示分享ID，实际应该显示密码标题）
            textTitle.setText("密码ID: " + share.getPasswordId());

            // 状态
            updateStatus(share.getStatus());

            // 用户信息
            if (isMyShares) {
                textUser.setText("分享给: " + share.getToUserId());
            } else {
                textUser.setText("来自: " + share.getFromUserId());
            }

            // 权限信息
            updatePermission(share.getPermission());

            // 创建时间
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            textTime.setText(sdf.format(new Date(share.getCreatedAt())));

            // 过期时间
            updateExpireTime(share.getExpireTime());

            // 按钮点击事件
            btnView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onViewShare(share);
                }
            });

            btnRevoke.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRevokeShare(share);
                }
            });

            // 只有活跃状态的分享才能撤销
            btnRevoke.setEnabled(share.getStatus() == ShareStatus.ACTIVE);
            
            // 接收的分享不显示撤销按钮
            if (!isMyShares) {
                btnRevoke.setVisibility(View.GONE);
            }
        }

        public void bindCloudShare(ReceivedShareResponse cloudShare) {
            // 标题
            textTitle.setText(cloudShare.getTitle() != null ? cloudShare.getTitle() : "密码");

            // 状态
            String status = cloudShare.getStatus();
            if ("ACTIVE".equals(status)) {
                textStatus.setText("活跃");
                textStatus.setBackgroundResource(R.drawable.bg_status_active);
                textStatus.setTextColor(0xFF2E7D32);
            } else if ("EXPIRED".equals(status)) {
                textStatus.setText("已过期");
                textStatus.setBackgroundResource(android.R.color.transparent);
                textStatus.setTextColor(0xFF757575);
            } else if ("REVOKED".equals(status)) {
                textStatus.setText("已撤销");
                textStatus.setBackgroundResource(android.R.color.transparent);
                textStatus.setTextColor(0xFFD32F2F);
            }

            // 用户信息
            if (isMyShares) {
                textUser.setText("分享给: " + (cloudShare.getToUserDisplayName() != null ? 
                    cloudShare.getToUserDisplayName() : cloudShare.getToUserId()));
            } else {
                textUser.setText("来自: " + (cloudShare.getFromUserDisplayName() != null ? 
                    cloudShare.getFromUserDisplayName() : cloudShare.getFromUserId()));
            }

            // 权限信息
            updatePermission(cloudShare.getPermission());

            // 创建时间
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            if (cloudShare.getCreatedAt() > 0) {
                textTime.setText(sdf.format(new Date(cloudShare.getCreatedAt())));
            }

            // 过期时间
            if (cloudShare.getExpireTime() != null) {
                updateExpireTime(cloudShare.getExpireTime());
            }

            // 按钮点击事件 - 使用ShareId作为PasswordShare的ID
            btnView.setOnClickListener(v -> {
                if (listener != null) {
                    PasswordShare tempShare = new PasswordShare();
                    tempShare.setShareId(cloudShare.getShareId());
                    listener.onViewShare(tempShare);
                }
            });

            btnRevoke.setOnClickListener(v -> {
                if (listener != null) {
                    PasswordShare tempShare = new PasswordShare();
                    tempShare.setShareId(cloudShare.getShareId());
                    listener.onRevokeShare(tempShare);
                }
            });

            // 只有活跃状态的分享才能撤销
            btnRevoke.setEnabled("ACTIVE".equals(status));
            
            // 接收的分享不显示撤销按钮
            if (!isMyShares) {
                btnRevoke.setVisibility(View.GONE);
            }
        }

        private void updateStatus(ShareStatus status) {
            switch (status) {
                case ACTIVE:
                    textStatus.setText("活跃");
                    textStatus.setBackgroundResource(R.drawable.bg_status_active);
                    textStatus.setTextColor(0xFF2E7D32); // 绿色
                    break;
                case EXPIRED:
                    textStatus.setText("已过期");
                    textStatus.setBackgroundResource(android.R.color.transparent);
                    textStatus.setTextColor(0xFF757575); // 灰色
                    break;
                case REVOKED:
                    textStatus.setText("已撤销");
                    textStatus.setBackgroundResource(android.R.color.transparent);
                    textStatus.setTextColor(0xFFD32F2F); // 红色
                    break;
                case ACCEPTED:
                    textStatus.setText("已接收");
                    textStatus.setBackgroundResource(android.R.color.transparent);
                    textStatus.setTextColor(0xFF1976D2); // 蓝色
                    break;
                case PENDING:
                    textStatus.setText("等待中");
                    textStatus.setBackgroundResource(android.R.color.transparent);
                    textStatus.setTextColor(0xFFFF6F00); // 橙色
                    break;
            }
        }

        private void updatePermission(SharePermission permission) {
            if (permission == null) {
                textPermission.setText("无权限信息");
                return;
            }

            StringBuilder permText = new StringBuilder();
            if (permission.isCanView()) {
                permText.append("可查看");
            }
            if (permission.isCanSave()) {
                if (permText.length() > 0) permText.append("、");
                permText.append("可保存");
            }
            if (permission.isRevocable()) {
                if (permText.length() > 0) permText.append("、");
                permText.append("可撤销");
            }

            textPermission.setText(permText.toString());
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
