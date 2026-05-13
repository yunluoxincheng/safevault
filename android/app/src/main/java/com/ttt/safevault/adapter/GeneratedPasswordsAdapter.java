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
import com.ttt.safevault.databinding.ItemGeneratedPasswordBinding;
import com.ttt.safevault.model.GeneratedPassword;

import java.text.DateFormat;
import java.util.Date;
import java.util.function.Consumer;

/**
 * 生成密码历史记录的 RecyclerView Adapter
 */
public class GeneratedPasswordsAdapter extends ListAdapter<GeneratedPassword, GeneratedPasswordsAdapter.ViewHolder> {

    private Consumer<String> onPasswordClick;
    private Consumer<Integer> onDeleteClick;

    public GeneratedPasswordsAdapter(Consumer<String> onPasswordClick, Consumer<Integer> onDeleteClick) {
        super(new DiffCallback());
        this.onPasswordClick = onPasswordClick;
        this.onDeleteClick = onDeleteClick;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemGeneratedPasswordBinding binding = ItemGeneratedPasswordBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GeneratedPassword item = getItem(position);
        holder.bind(item, position);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemGeneratedPasswordBinding binding;

        ViewHolder(ItemGeneratedPasswordBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(GeneratedPassword item, int position) {
            binding.passwordText.setText(item.getPassword());
            binding.configText.setText(item.getConfigDescription());

            // 格式化时间
            DateFormat dateFormat = DateFormat.getDateTimeInstance(
                    DateFormat.SHORT, DateFormat.SHORT);
            String timeStr = dateFormat.format(new Date(item.getTimestamp()));
            binding.timeText.setText(timeStr);

            // 点击整个卡片复制密码
            binding.getRoot().setOnClickListener(v -> {
                if (onPasswordClick != null) {
                    onPasswordClick.accept(item.getPassword());
                }
            });

            // 复制按钮点击事件
            binding.copyHistoryButton.setOnClickListener(v -> {
                if (onPasswordClick != null) {
                    onPasswordClick.accept(item.getPassword());
                }
            });
        }
    }

    private static class DiffCallback extends DiffUtil.ItemCallback<GeneratedPassword> {
        @Override
        public boolean areItemsTheSame(@NonNull GeneratedPassword oldItem, @NonNull GeneratedPassword newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull GeneratedPassword oldItem, @NonNull GeneratedPassword newItem) {
            return oldItem.getPassword().equals(newItem.getPassword())
                    && oldItem.getTimestamp() == newItem.getTimestamp();
        }
    }
}
