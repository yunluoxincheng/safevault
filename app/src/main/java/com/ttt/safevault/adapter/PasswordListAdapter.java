package com.ttt.safevault.adapter;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.imageview.ShapeableImageView;
import com.ttt.safevault.R;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.utils.FaviconLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 密码列表的RecyclerView适配器
 * 支持现代化的卡片式布局、点击动画、快捷操作等功能
 */
public class PasswordListAdapter extends ListAdapter<PasswordItem, PasswordListAdapter.ViewHolder> {

    private OnItemClickListener listener;
    private boolean showAnimations = true;

    public PasswordListAdapter(OnItemClickListener listener) {
        super(DiffCallback);
        this.listener = listener;
    }

    public PasswordListAdapter(OnItemClickListener listener, boolean showAnimations) {
        super(DiffCallback);
        this.listener = listener;
        this.showAnimations = showAnimations;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_password, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PasswordItem item = getItem(position);
        holder.bind(item, listener, showAnimations);
    }

    /**
     * ViewHolder for password items
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final ShapeableImageView iconImage;
        private final TextView titleText;
        private final TextView usernameText;
        private final TextView timestampText;
        private final ImageButton moreButton;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            cardView = itemView.findViewById(R.id.card_view);
            iconImage = itemView.findViewById(R.id.icon_image);
            titleText = itemView.findViewById(R.id.title_text);
            usernameText = itemView.findViewById(R.id.username_text);
            timestampText = itemView.findViewById(R.id.timestamp_text);
            moreButton = itemView.findViewById(R.id.more_button);
        }

        public void bind(PasswordItem item, OnItemClickListener listener, boolean animate) {
            // 设置标题
            titleText.setText(item.getDisplayName());

            // 设置用户名
            if (item.getUsername() != null && !item.getUsername().isEmpty()) {
                usernameText.setText(item.getUsername());
                usernameText.setVisibility(View.VISIBLE);
            } else {
                usernameText.setVisibility(View.GONE);
            }

            // 设置图标 - 使用首字母或默认图标
            setupIcon(item);

            // 设置时间戳
            setupTimestamp(item);

            // 设置无障碍内容描述
            String contentDescription = buildContentDescription(item);
            cardView.setContentDescription(contentDescription);

            // 设置卡片点击事件
            cardView.setOnClickListener(v -> {
                if (animate) {
                    animateCardClick(cardView);
                }
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });

            // 设置长按事件
            cardView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onItemLongClick(item);
                    return true;
                }
                return false;
            });

            // 更多选项按钮
            if (moreButton != null) {
                moreButton.setOnClickListener(v -> {
                    if (animate) {
                        animateButtonClick(moreButton);
                    }
                    if (listener != null) {
                        listener.onItemMoreClick(item, moreButton);
                    }
                });
            }

            // 设置过渡动画名称
            ViewCompat.setTransitionName(cardView, "password_item_" + item.getId());
        }

        /**
         * 设置图标
         * 加载网站 favicon，失败时回退到首字母图标或默认图标
         */
        private void setupIcon(PasswordItem item) {
            if (iconImage == null) return;

            iconImage.setVisibility(View.VISIBLE);

            // 从 URL 或标题提取首字母作为回退
            char fallbackLetter = extractFallbackLetter(item);

            // 尝试加载网站图标
            if (item.getUrl() != null && !item.getUrl().isEmpty()) {
                FaviconLoader.loadIcon(itemView.getContext(), item.getUrl(), iconImage, fallbackLetter);
            } else {
                // 无 URL，直接显示首字母图标
                Drawable letterDrawable = FaviconLoader.createLetterDrawable(
                        itemView.getContext(), fallbackLetter);
                if (letterDrawable != null) {
                    iconImage.setImageDrawable(letterDrawable);
                } else {
                    iconImage.setImageResource(R.drawable.ic_password);
                }
            }
        }

        /**
         * 提取回退字母（优先使用首字母，否则使用标题首字母）
         */
        private char extractFallbackLetter(PasswordItem item) {
            // 优先使用用户名首字母
            if (item.getUsername() != null && !item.getUsername().isEmpty()) {
                return item.getUsername().charAt(0);
            }
            // 其次使用标题首字母
            if (item.getTitle() != null && !item.getTitle().isEmpty()) {
                return item.getTitle().charAt(0);
            }
            // 最后使用 URL 首字母
            if (item.getUrl() != null && !item.getUrl().isEmpty()) {
                return item.getUrl().charAt(0);
            }
            return 0;
        }

        /**
         * 设置时间戳
         */
        private void setupTimestamp(PasswordItem item) {
            if (timestampText == null) return;

            long lastModified = item.getUpdatedAt();
            if (lastModified > 0) {
                String timeString = formatRelativeTime(lastModified);
                timestampText.setText(timeString);
                timestampText.setVisibility(View.VISIBLE);
            } else {
                timestampText.setVisibility(View.GONE);
            }
        }

        /**
         * 格式化相对时间
         */
        private String formatRelativeTime(long timestamp) {
            long now = System.currentTimeMillis();
            long diff = now - timestamp;

            // 转换为分钟
            long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
            if (minutes < 1) {
                return "刚刚";
            } else if (minutes < 60) {
                return minutes + "分钟前";
            }

            // 转换为小时
            long hours = TimeUnit.MILLISECONDS.toHours(diff);
            if (hours < 24) {
                return hours + "小时前";
            }

            // 转换为天
            long days = TimeUnit.MILLISECONDS.toDays(diff);
            if (days < 7) {
                return days + "天前";
            }

            // 超过7天显示日期
            return DateUtils.formatDateTime(
                    itemView.getContext(),
                    timestamp,
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NUMERIC_DATE
            );
        }

        /**
         * 构建无障碍内容描述
         */
        private String buildContentDescription(PasswordItem item) {
            StringBuilder sb = new StringBuilder();
            sb.append("密码项\n");
            sb.append("标题: ").append(item.getDisplayName()).append("\n");

            if (item.getUsername() != null && !item.getUsername().isEmpty()) {
                sb.append("用户名: ").append(item.getUsername()).append("\n");
            }

            if (timestampText.getVisibility() == View.VISIBLE) {
                sb.append("最后修改: ").append(timestampText.getText());
            }

            return sb.toString();
        }

        private void animateCardClick(MaterialCardView card) {
            AnimatorSet animatorSet = new AnimatorSet();

            // 缩放动画
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(card, "scaleX", 1f, 0.98f, 1f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(card, "scaleY", 1f, 0.98f, 1f);

            // 背景颜色动画 - 使用更柔和的颜色变化
            int currentColor = MaterialColors.getColor(card, com.google.android.material.R.attr.colorSurfaceVariant);
            int pressedColor = MaterialColors.getColor(card, com.google.android.material.R.attr.colorPrimaryContainer);

            ObjectAnimator backgroundColor = ObjectAnimator.ofArgb(
                    card, "cardBackgroundColor", currentColor, pressedColor, currentColor);

            animatorSet.playTogether(scaleX, scaleY, backgroundColor);
            animatorSet.setDuration(150);
            animatorSet.start();
        }

        private void animateButtonClick(ImageButton button) {
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(button, "scaleX", 1f, 0.85f, 1f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(button, "scaleY", 1f, 0.85f, 1f);

            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(scaleX, scaleY);
            animatorSet.setDuration(100);
            animatorSet.start();
        }
    }

    /**
     * Item点击监听接口
     */
    public interface OnItemClickListener {
        void onItemClick(PasswordItem item);
        void onItemCopyClick(PasswordItem item);
        void onItemEditClick(PasswordItem item);
        void onItemDeleteClick(PasswordItem item);
        void onItemLongClick(PasswordItem item); // 新增长按事件
        void onItemMoreClick(PasswordItem item, View anchorView); // 新增更多选项事件
    }

    /**
     * DiffUtil回调，用于高效更新列表
     */
    private static final DiffUtil.ItemCallback<PasswordItem> DiffCallback = new DiffUtil.ItemCallback<PasswordItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull PasswordItem oldItem, @NonNull PasswordItem newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull PasswordItem oldItem, @NonNull PasswordItem newItem) {
            return oldItem.equals(newItem);
        }

        @Nullable
        @Override
        public Object getChangePayload(@NonNull PasswordItem oldItem, @NonNull PasswordItem newItem) {
            // 返回哪些字段发生了变化，用于局部更新
            Bundle diff = new Bundle();

            if (!oldItem.getTitle().equals(newItem.getTitle())) {
                diff.putString("title", newItem.getTitle());
            }
            if (!oldItem.getUsername().equals(newItem.getUsername())) {
                diff.putString("username", newItem.getUsername());
            }
            if (!oldItem.getUrl().equals(newItem.getUrl())) {
                diff.putString("url", newItem.getUrl());
            }

            return diff.isEmpty() ? null : diff;
        }
    };

    /**
     * 设置是否显示动画
     */
    public void setShowAnimations(boolean showAnimations) {
        this.showAnimations = showAnimations;
    }

    /**
     * 获取指定位置的密码项
     */
    public PasswordItem getItemAt(int position) {
        return getItem(position);
    }

    /**
     * 清空列表
     */
    public void clearList() {
        submitList(null);
    }

    /**
     * 更新单个项
     */
    public void updateItem(PasswordItem item) {
        List<PasswordItem> currentList = getCurrentList();
        for (int i = 0; i < currentList.size(); i++) {
            if (currentList.get(i).getId() == item.getId()) {
                currentList.set(i, item);
                submitList(new ArrayList<>(currentList));
                break;
            }
        }
    }
}