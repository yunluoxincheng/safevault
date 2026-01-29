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

import com.google.android.material.card.MaterialCardView;
import com.ttt.safevault.R;
import com.ttt.safevault.data.Contact;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 联系人列表适配器
 */
public class ContactAdapter extends ListAdapter<Contact, ContactAdapter.ViewHolder> {

    private final Consumer<Contact> onContactClick;
    private final Consumer<Contact> onContactLongClick;

    private static final DiffUtil.ItemCallback<Contact> DIFF_CALLBACK = new DiffUtil.ItemCallback<Contact>() {
        @Override
        public boolean areItemsTheSame(@NonNull Contact oldItem, @NonNull Contact newItem) {
            return oldItem.contactId != null && oldItem.contactId.equals(newItem.contactId);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Contact oldItem, @NonNull Contact newItem) {
            return oldItem.displayName.equals(newItem.displayName) &&
                   oldItem.myNote != null && oldItem.myNote.equals(newItem.myNote) &&
                   oldItem.lastUsedAt == newItem.lastUsedAt;
        }
    };

    public ContactAdapter(Consumer<Contact> onContactClick, Consumer<Contact> onContactLongClick) {
        super(DIFF_CALLBACK);
        this.onContactClick = onContactClick;
        this.onContactLongClick = onContactLongClick;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_contact, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Contact contact = getItem(position);
        holder.bind(contact);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final ImageView imageAvatar;
        private final TextView textDisplayName;
        private final TextView textUsername;
        private final TextView textNote;
        private final TextView textLastUsed;
        private final View menuButton;
        private final View onlineStatusView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            imageAvatar = itemView.findViewById(R.id.image_avatar);
            textDisplayName = itemView.findViewById(R.id.text_display_name);
            textUsername = itemView.findViewById(R.id.text_username);
            textNote = itemView.findViewById(R.id.text_note);
            textLastUsed = itemView.findViewById(R.id.text_last_used);
            menuButton = itemView.findViewById(R.id.btn_menu);
            onlineStatusView = itemView.findViewById(R.id.view_online_status);
        }

        public void bind(Contact contact) {
            // 第一行：显示名称（使用 displayName，如果为空则使用 username）
            String displayText = (contact.displayName != null && !contact.displayName.isEmpty())
                    ? contact.displayName
                    : contact.username;
            textDisplayName.setText(displayText);

            // 第二行：完整邮箱地址（如果有的话）
            if (contact.email != null && !contact.email.isEmpty()) {
                textUsername.setText(contact.email);
                textUsername.setVisibility(View.VISIBLE);
            } else {
                textUsername.setVisibility(View.GONE);
            }

            // 第三行：备注（仅当有备注时显示）
            if (contact.myNote != null && !contact.myNote.isEmpty()) {
                textNote.setText("备注: " + contact.myNote);
                textNote.setVisibility(View.VISIBLE);
            } else {
                textNote.setVisibility(View.GONE);
            }

            // 第四行：最后使用时间（仅在确实有使用记录时显示）
            // 使用条件：lastUsedAt > 0 且 lastUsedAt != addedAt
            if (contact.lastUsedAt > 0 && contact.lastUsedAt != contact.addedAt) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                textLastUsed.setText("最后使用: " + sdf.format(new Date(contact.lastUsedAt)));
                textLastUsed.setVisibility(View.VISIBLE);
            } else {
                textLastUsed.setVisibility(View.GONE);
            }

            // 在线状态指示器（绿色圆点）
            // 🟢 在线时显示，⚫ 离线时不显示
            if (contact.isOnline() && onlineStatusView != null) {
                onlineStatusView.setVisibility(View.VISIBLE);
            } else {
                onlineStatusView.setVisibility(View.GONE);
            }

            // 头像背景
            imageAvatar.setImageDrawable(null);
            imageAvatar.setBackgroundResource(R.drawable.circle_avatar_background);

            // 点击事件
            cardView.setOnClickListener(v -> {
                if (onContactClick != null) {
                    onContactClick.accept(contact);
                }
            });

            // 长按事件（显示菜单）
            cardView.setOnLongClickListener(v -> {
                if (onContactLongClick != null) {
                    onContactLongClick.accept(contact);
                    return true;
                }
                return false;
            });

            // 菜单按钮点击
            menuButton.setOnClickListener(v -> {
                if (onContactLongClick != null) {
                    onContactLongClick.accept(contact);
                }
            });
        }
    }
}
