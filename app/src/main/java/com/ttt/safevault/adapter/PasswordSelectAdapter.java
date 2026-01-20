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
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;
import com.ttt.safevault.R;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.utils.FaviconLoader;

import java.util.function.Consumer;

/**
 * 密码选择列表的RecyclerView适配器
 * 用于密码选择对话框中显示密码列表并提供分享按钮
 */
public class PasswordSelectAdapter extends ListAdapter<PasswordItem, PasswordSelectAdapter.ViewHolder> {

    private final Consumer<PasswordItem> onShareClick;

    private static final DiffUtil.ItemCallback<PasswordItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<PasswordItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull PasswordItem oldItem, @NonNull PasswordItem newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull PasswordItem oldItem, @NonNull PasswordItem newItem) {
            return oldItem.getTitle().equals(newItem.getTitle()) &&
                   oldItem.getUsername().equals(newItem.getUsername());
        }
    };

    /**
     * 构造函数
     * @param onShareClick 分享按钮点击回调
     */
    public PasswordSelectAdapter(Consumer<PasswordItem> onShareClick) {
        super(DIFF_CALLBACK);
        this.onShareClick = onShareClick;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_password_select, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PasswordItem item = getItem(position);
        holder.bind(item);
    }

    /**
     * ViewHolder for password select items
     */
    class ViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final ShapeableImageView iconImage;
        private final TextView titleText;
        private final TextView usernameText;
        private final MaterialButton shareButton;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_view);
            iconImage = itemView.findViewById(R.id.icon_image);
            titleText = itemView.findViewById(R.id.title_text);
            usernameText = itemView.findViewById(R.id.username_text);
            shareButton = itemView.findViewById(R.id.btn_share);
        }

        public void bind(PasswordItem item) {
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

            // 设置卡片点击事件（点击整个卡片也可以触发分享）
            cardView.setOnClickListener(v -> {
                if (onShareClick != null) {
                    onShareClick.accept(item);
                }
            });

            // 设置分享按钮点击事件
            if (shareButton != null) {
                shareButton.setOnClickListener(v -> {
                    if (onShareClick != null) {
                        onShareClick.accept(item);
                    }
                });
            }

            // 设置无障碍内容描述
            String contentDescription = buildContentDescription(item);
            cardView.setContentDescription(contentDescription);
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
                android.graphics.drawable.Drawable letterDrawable = FaviconLoader.createLetterDrawable(
                        itemView.getContext(), fallbackLetter);
                if (letterDrawable != null) {
                    iconImage.setImageDrawable(letterDrawable);
                } else {
                    iconImage.setImageResource(R.drawable.ic_password);
                }
            }
        }

        /**
         * 提取回退字母（优先使用用户名首字母，否则使用标题首字母）
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
         * 构建无障碍内容描述
         */
        private String buildContentDescription(PasswordItem item) {
            StringBuilder sb = new StringBuilder();
            sb.append("密码项\n");
            sb.append("标题: ").append(item.getDisplayName()).append("\n");

            if (item.getUsername() != null && !item.getUsername().isEmpty()) {
                sb.append("用户名: ").append(item.getUsername()).append("\n");
            }

            sb.append("点击分享此密码");

            return sb.toString();
        }
    }
}
