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
import com.google.android.material.chip.Chip;
import com.ttt.safevault.R;
import com.ttt.safevault.data.ShareRecord;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.model.BackendService;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 分享时间线适配器
 * 显示与特定联系人的分享记录（发送和接收）
 */
public class ShareTimelineAdapter extends ListAdapter<ShareRecord, ShareTimelineAdapter.ViewHolder> {

    private final Consumer<ShareRecord> onViewClick;
    private final Consumer<ShareRecord> onItemLongClick;
    private final BackendService backendService;

    private static final DiffUtil.ItemCallback<ShareRecord> DIFF_CALLBACK = new DiffUtil.ItemCallback<ShareRecord>() {
        @Override
        public boolean areItemsTheSame(@NonNull ShareRecord oldItem, @NonNull ShareRecord newItem) {
            return oldItem.shareId != null && oldItem.shareId.equals(newItem.shareId);
        }

        @Override
        public boolean areContentsTheSame(@NonNull ShareRecord oldItem, @NonNull ShareRecord newItem) {
            return oldItem.status.equals(newItem.status) &&
                   oldItem.accessedAt == newItem.accessedAt;
        }
    };

    /**
     * 分享项类型
     */
    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;

    public ShareTimelineAdapter(Consumer<ShareRecord> onViewClick,
                                Consumer<ShareRecord> onItemLongClick,
                                BackendService backendService) {
        super(DIFF_CALLBACK);
        this.onViewClick = onViewClick;
        this.onItemLongClick = onItemLongClick;
        this.backendService = backendService;
    }

    @Override
    public int getItemViewType(int position) {
        ShareRecord record = getItem(position);
        if ("sent".equals(record.type)) {
            return VIEW_TYPE_SENT;
        } else {
            return VIEW_TYPE_RECEIVED;
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutRes = (viewType == VIEW_TYPE_SENT)
                ? R.layout.item_share_sent
                : R.layout.item_share_received;
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ShareRecord record = getItem(position);
        holder.bind(record);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView textPasswordTitle;
        private final TextView textUsername;
        private final TextView textTime;
        private final Chip chipStatus;
        private final MaterialButton btnView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textPasswordTitle = itemView.findViewById(R.id.text_password_title);
            textUsername = itemView.findViewById(R.id.text_username);
            textTime = itemView.findViewById(R.id.text_time);
            chipStatus = itemView.findViewById(R.id.chip_status);
            btnView = itemView.findViewById(R.id.btn_view);
        }

        public void bind(ShareRecord record) {
            // 加载密码信息
            if (backendService != null) {
                new Thread(() -> {
                    PasswordItem password = backendService.getPasswordById(record.passwordId);
                    if (password != null) {
                        String title = password.getTitle();
                        String username = password.getUsername();

                        // 更新UI
                        if (itemView.getContext() != null && itemView.isAttachedToWindow()) {
                            itemView.post(() -> {
                                if (textPasswordTitle != null) {
                                    textPasswordTitle.setText(title != null ? title : "未命名密码");
                                }
                                if (textUsername != null) {
                                    textUsername.setText(username != null ? username : "");
                                }
                            });
                        }
                    }
                }).start();
            }

            // 设置时间
            if (textTime != null) {
                textTime.setText(formatTime(record.createdAt));
            }

            // 设置状态
            if (chipStatus != null) {
                chipStatus.setText(formatStatus(record.status));
                chipStatus.setChipBackgroundColorResource(getStatusColor(record.status));
            }

            // 查看按钮点击事件
            if (btnView != null && onViewClick != null) {
                btnView.setOnClickListener(v -> onViewClick.accept(record));
            }

            // 长按事件
            if (onItemLongClick != null) {
                itemView.setOnLongClickListener(v -> {
                    onItemLongClick.accept(record);
                    return true;
                });
            }
        }

        /**
         * 格式化时间显示
         */
        private String formatTime(long timestamp) {
            long now = System.currentTimeMillis();
            long diff = now - timestamp;

            // 一天内显示"今天 HH:mm"
            // 一天前显示"昨天 HH:mm"
            // 更早显示"MM-dd HH:mm"
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(timestamp);

            Calendar today = Calendar.getInstance();
            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DAY_OF_MONTH, -1);

            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            SimpleDateFormat dateTimeFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

            if (isSameDay(calendar, today)) {
                return "今天 " + timeFormat.format(new Date(timestamp));
            } else if (isSameDay(calendar, yesterday)) {
                return "昨天 " + timeFormat.format(new Date(timestamp));
            } else {
                return dateTimeFormat.format(new Date(timestamp));
            }
        }

        /**
         * 判断两个日期是否是同一天
         */
        private boolean isSameDay(Calendar cal1, Calendar cal2) {
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                   cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
        }

        /**
         * 格式化状态文本
         */
        private String formatStatus(String status) {
            if (status == null) return "";

            switch (status) {
                case "active":
                    return "活跃";
                case "expired":
                    return "已过期";
                case "revoked":
                    return "已撤销";
                case "accepted":
                    return "已接受";
                case "pending":
                    return "待接受";
                default:
                    return status;
            }
        }

        /**
         * 获取状态颜色
         */
        private int getStatusColor(String status) {
            if (status == null) return R.color.md_theme_light_outline;

            switch (status) {
                case "active":
                    return R.color.success_green;
                case "expired":
                    return R.color.strength_medium;
                case "revoked":
                    return R.color.error_red;
                case "accepted":
                    return R.color.primary_blue;
                case "pending":
                    return R.color.strength_medium;
                default:
                    return R.color.md_theme_light_outline;
            }
        }
    }
}
