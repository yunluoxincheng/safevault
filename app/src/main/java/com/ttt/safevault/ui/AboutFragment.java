package com.ttt.safevault.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ttt.safevault.BuildConfig;
import com.ttt.safevault.R;
import com.ttt.safevault.databinding.FragmentAboutBinding;

/**
 * 关于页面 Fragment
 * 显示应用信息和相关链接
 */
public class AboutFragment extends BaseFragment {

    private FragmentAboutBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAboutBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupClickListeners();
        loadVersionInfo();
    }

    private void loadVersionInfo() {
        String versionText = "版本 " + BuildConfig.VERSION_NAME;
        binding.tvVersion.setText(versionText);
    }

    private void setupClickListeners() {
        // 开源许可证
        binding.cardLicenses.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "开源许可证页面待实现", Toast.LENGTH_SHORT).show();
        });

        // 隐私政策
        binding.cardPrivacy.setOnClickListener(v -> {
            openUrl("https://safevault.example/privacy");
        });

        // 用户协议
        binding.cardTerms.setOnClickListener(v -> {
            openUrl("https://safevault.example/terms");
        });
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "无法打开链接", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
