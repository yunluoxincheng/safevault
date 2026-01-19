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

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            imageAvatar = itemView.findViewById(R.id.image_avatar);
            textDisplayName = itemView.findViewById(R.id.text_display_name);
            textUsername = itemView.findViewById(R.id.text_username);
            textNote = itemView.findViewById(R.id.text_note);
            textLastUsed = itemView.findViewById(R.id.text_last_used);
            menuButton = itemView.findViewById(R.id.btn_menu);
        }

        public void bind(Contact contact) {
            // 显示名称（如果有备注则显示备注，否则显示原显示名称）
            String displayText = contact.myNote != null && !contact.myNote.isEmpty()
                    ? contact.myNote
                    : (contact.displayName != null && !contact.displayName.isEmpty()
                        ? contact.displayName
                        : contact.username);
            textDisplayName.setText(displayText);

            // 用户名
            textUsername.setText(contact.username);

            // 备注显示
            if (contact.myNote != null && !contact.myNote.isEmpty()) {
                textNote.setVisibility(View.VISIBLE);
                textNote.setText("备注: " + contact.myNote);
            } else {
                textNote.setVisibility(View.GONE);
            }

            // 最后使用时间
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            textLastUsed.setText(sdf.format(new Date(contact.lastUsedAt)));

            // 头像（取首字母）
            String firstLetter = displayText.isEmpty() ? "?" : String.valueOf(displayText.charAt(0));
            imageAvatar.setImageDrawable(null); // 可以使用 TextDrawable 或其他库
            // 这里简化处理，可以后续添加首字母头像
            imageAvatar.setBackgroundResource(R.drawable.circle_avatar_background);
            // 可以用 Canvas 绘制文字

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
