package com.ttt.safevault.ui.autofill;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ttt.safevault.R;
import com.ttt.safevault.model.PasswordItem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 自动填充凭据选择Adapter
 */
public class AutofillCredentialAdapter extends RecyclerView.Adapter<AutofillCredentialAdapter.CredentialViewHolder> {

    private List<PasswordItem> credentials;
    private Consumer<PasswordItem> onCredentialSelected;

    public AutofillCredentialAdapter(List<PasswordItem> credentials, Consumer<PasswordItem> onCredentialSelected) {
        this.credentials = credentials != null ? credentials : new ArrayList<>();
        this.onCredentialSelected = onCredentialSelected;
    }

    @NonNull
    @Override
    public CredentialViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_autofill_credential, parent, false);
        return new CredentialViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CredentialViewHolder holder, int position) {
        PasswordItem item = credentials.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return credentials.size();
    }

    class CredentialViewHolder extends RecyclerView.ViewHolder {
        private ImageView iconView;
        private TextView titleView;
        private TextView usernameView;
        private View containerView;

        CredentialViewHolder(@NonNull View itemView) {
            super(itemView);
            iconView = itemView.findViewById(R.id.credential_icon);
            titleView = itemView.findViewById(R.id.credential_title);
            usernameView = itemView.findViewById(R.id.credential_username);
            containerView = itemView.findViewById(R.id.credential_container);
        }

        void bind(PasswordItem item) {
            // 设置标题
            String title = item.getTitle();
            if (title == null || title.isEmpty()) {
                title = item.getUsername();
            }
            titleView.setText(title);

            // 设置用户名
            usernameView.setText(item.getUsername());

            // 设置默认图标（密码图标）
            iconView.setImageResource(R.drawable.ic_password);

            // 点击事件
            containerView.setOnClickListener(v -> {
                if (onCredentialSelected != null) {
                    onCredentialSelected.accept(item);
                }
            });
        }
    }
}
