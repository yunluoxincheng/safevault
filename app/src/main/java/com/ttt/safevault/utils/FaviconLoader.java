package com.ttt.safevault.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.android.material.color.MaterialColors;
import com.ttt.safevault.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 网站图标加载工具类
 * 支持多层回退机制加载网站 favicon
 */
public class FaviconLoader {

    private static final int ICON_SIZE_DP = 48;

    /**
     * 加载网站图标到 ImageView
     *
     * @param context       上下文
     * @param url           网站 URL
     * @param imageView     目标 ImageView
     * @param fallbackLetter 回退时显示的首字母
     */
    public static void loadIcon(Context context, String url, ImageView imageView, char fallbackLetter) {
        if (url == null || url.isEmpty()) {
            // 无 URL，显示默认图标
            loadDefaultIcon(context, imageView, fallbackLetter);
            return;
        }

        String domain = extractDomain(url);
        if (domain == null || domain.isEmpty()) {
            // 无法提取域名，显示默认图标
            loadDefaultIcon(context, imageView, fallbackLetter);
            return;
        }

        List<String> faviconUrls = buildFaviconUrls(domain);

        // 尝试按优先级加载图标
        loadIconWithFallback(context, faviconUrls, 0, imageView, fallbackLetter);
    }

    /**
     * 递归尝试加载多个 URL，失败则尝试下一个
     */
    private static void loadIconWithFallback(Context context, List<String> urls, int index,
                                             ImageView imageView, char fallbackLetter) {
        if (index >= urls.size()) {
            // 所有 URL 都失败，显示默认图标
            loadDefaultIcon(context, imageView, fallbackLetter);
            return;
        }

        Glide.with(context)
                .load(urls.get(index))
                .override(ICON_SIZE_DP * 2, ICON_SIZE_DP * 2) // 2x for high DPI
                .diskCacheStrategy(DiskCacheStrategy.ALL) // 缓存所有版本
                .transform(new CircleCrop())
                .error(Glide.with(context)
                        .load(index + 1 < urls.size() ? urls.get(index + 1) : null)
                        .override(ICON_SIZE_DP * 2, ICON_SIZE_DP * 2)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .transform(new CircleCrop())
                        .error(createLetterDrawable(context, fallbackLetter))
                )
                .into(imageView);
    }

    /**
     * 提取域名
     * 例如: https://www.github.com/user -> github.com
     */
    private static String extractDomain(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        // 移除协议前缀
        String domain = url.replaceFirst("^https?://", "");
        domain = domain.replaceFirst("^www\\.", "");

        // 移除路径部分
        int pathIndex = domain.indexOf('/');
        if (pathIndex > 0) {
            domain = domain.substring(0, pathIndex);
        }

        // 移除端口号
        int portIndex = domain.indexOf(':');
        if (portIndex > 0) {
            domain = domain.substring(0, portIndex);
        }

        // 移除查询参数
        int queryIndex = domain.indexOf('?');
        if (queryIndex > 0) {
            domain = domain.substring(0, queryIndex);
        }

        return domain.isEmpty() ? null : domain;
    }

    /**
     * 构建 favicon URL 列表（按优先级）
     */
    private static List<String> buildFaviconUrls(String domain) {
        List<String> urls = new ArrayList<>();

        // 1. 标准 favicon.ico
        urls.add("https://" + domain + "/favicon.ico");

        // 2. Apple touch icon
        urls.add("https://" + domain + "/apple-touch-icon.png");

        // 3. Google Favicon Service 作为最后回退
        urls.add("https://www.google.com/s2/favicons?domain=" + domain + "&sz=64");

        return urls;
    }

    /**
     * 加载默认图标（首字母图标或密码图标）
     */
    private static void loadDefaultIcon(Context context, ImageView imageView, char letter) {
        Drawable letterDrawable = createLetterDrawable(context, letter);
        if (letterDrawable != null) {
            imageView.setImageDrawable(letterDrawable);
        } else {
            imageView.setImageResource(R.drawable.ic_password);
        }
    }

    /**
     * 创建首字母图标
     */
    public static Drawable createLetterDrawable(Context context, char letter) {
        if (letter == 0 || Character.isWhitespace(letter)) {
            return null;
        }

        final String displayLetter = String.valueOf(Character.toUpperCase(letter)).trim();

        return new android.graphics.drawable.Drawable() {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF bounds = new RectF();

            {
                // 设置背景颜色
                int backgroundColor = MaterialColors.getColor(
                        context,
                        com.google.android.material.R.attr.colorPrimaryContainer,
                        0xFF6750A4
                );

                // 设置文字颜色
                int textColor = MaterialColors.getColor(
                        context,
                        com.google.android.material.R.attr.colorOnPrimaryContainer,
                        0xFFFFFFFF
                );

                paint.setColor(textColor);
                paint.setTextAlign(Paint.Align.CENTER);
                // 根据屏幕密度计算文字大小
                float density = context.getResources().getDisplayMetrics().density;
                paint.setTextSize(24 * density);
            }

            @Override
            public void draw(Canvas canvas) {
                // 绘制背景
                int backgroundColor = MaterialColors.getColor(
                        context,
                        com.google.android.material.R.attr.colorPrimaryContainer,
                        0xFF6750A4
                );
                paint.setColor(backgroundColor);
                canvas.drawOval(bounds, paint);

                // 绘制文字
                int textColor = MaterialColors.getColor(
                        context,
                        com.google.android.material.R.attr.colorOnPrimaryContainer,
                        0xFFFFFFFF
                );
                paint.setColor(textColor);

                float x = bounds.centerX();
                float y = bounds.centerY() - ((paint.descent() + paint.ascent()) / 2);
                canvas.drawText(displayLetter, x, y, paint);
            }

            @Override
            public void setBounds(int left, int top, int right, int bottom) {
                super.setBounds(left, top, right, bottom);
                bounds.set(left, top, right, bottom);
            }

            @Override
            public void setAlpha(int alpha) {
                paint.setAlpha(alpha);
            }

            @Override
            public void setColorFilter(android.graphics.ColorFilter colorFilter) {
                paint.setColorFilter(colorFilter);
            }

            @Override
            public int getOpacity() {
                return android.graphics.PixelFormat.TRANSLUCENT;
            }

            @Override
            public int getIntrinsicWidth() {
                float density = context.getResources().getDisplayMetrics().density;
                return (int) (ICON_SIZE_DP * density);
            }

            @Override
            public int getIntrinsicHeight() {
                float density = context.getResources().getDisplayMetrics().density;
                return (int) (ICON_SIZE_DP * density);
            }
        };
    }

    /**
     * 从标题中提取首字母
     */
    public static char extractFirstLetter(String title) {
        if (title == null || title.isEmpty()) {
            return 0;
        }
        return title.charAt(0);
    }

    /**
     * 清除图标缓存
     */
    public static void clearCache(Context context) {
        Glide.get(context).clearMemory();
        new Thread(() -> Glide.get(context).clearDiskCache()).start();
    }
}
