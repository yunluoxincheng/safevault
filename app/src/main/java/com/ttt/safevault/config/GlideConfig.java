package com.ttt.safevault.config;

import android.content.Context;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.cache.DiskLruCacheFactory;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;

/**
 * Glide 配置模块
 * 配置内存缓存、磁盘缓存和图片加载策略
 */
@com.bumptech.glide.annotation.GlideModule
public class GlideConfig extends AppGlideModule {

    private static final int DISK_CACHE_SIZE = 50 * 1024 * 1024; // 50MB
    private static final int MEMORY_CACHE_SIZE_BYTES = 20 * 1024 * 1024; // 20MB

    @Override
    public void applyOptions(Context context, GlideBuilder builder) {
        // 配置磁盘缓存
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, DISK_CACHE_SIZE));

        // 配置内存缓存
        builder.setMemoryCache(new com.bumptech.glide.load.engine.cache.LruResourceCache(MEMORY_CACHE_SIZE_BYTES));

        // 设置默认的图片加载选项
        RequestOptions requestOptions = new RequestOptions()
                .format(DecodeFormat.PREFER_RGB_565); // 使用 RGB_565 减少内存占用

        builder.setDefaultRequestOptions(requestOptions);
    }

    @Override
    public boolean isManifestParsingEnabled() {
        // 禁用清单解析，提高性能
        return false;
    }
}
