package com.ttt.safevault.utils;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.HashMap;
import java.util.Map;

/**
 * 二维码工具类
 * 提供二维码生成和扫描功能
 */
public class QRCodeUtils {
    
    private static final String TAG = "QRCodeUtils";
    
    // 默认二维码尺寸
    private static final int DEFAULT_QR_SIZE = 512;
    
    // 默认前景色（黑色）
    private static final int DEFAULT_FOREGROUND_COLOR = Color.BLACK;
    
    // 默认背景色（白色）
    private static final int DEFAULT_BACKGROUND_COLOR = Color.WHITE;

    /**
     * 生成二维码（使用默认尺寸和颜色）
     *
     * @param content 二维码内容
     * @return 二维码位图，失败返回null
     */
    @Nullable
    public static Bitmap generateQRCode(@NonNull String content) {
        return generateQRCode(content, DEFAULT_QR_SIZE, DEFAULT_QR_SIZE);
    }

    /**
     * 生成QR码图片（兼容旧API）
     *
     * @param content QR码内容
     * @param size 图片大小（像素）
     * @return QR码图片
     * @throws WriterException 如果生成失败
     */
    @NonNull
    public static Bitmap encodeQRCode(@NonNull String content, int size) throws WriterException {
        if (content == null || content.isEmpty()) {
            throw new WriterException("Content cannot be null or empty");
        }

        if (size <= 0) {
            throw new WriterException("Size must be positive: " + size);
        }

        QRCodeWriter writer = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);

        BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints);

        int width = bitMatrix.getWidth();
        int height = bitMatrix.getHeight();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }

        Log.d(TAG, "QR code encoded successfully: " + size + "x" + size);
        return bitmap;
    }

    /**
     * 生成二维码（指定尺寸，使用默认颜色）
     *
     * @param content 二维码内容
     * @param width   宽度（像素）
     * @param height  高度（像素）
     * @return 二维码位图，失败返回null
     */
    @Nullable
    public static Bitmap generateQRCode(@NonNull String content, int width, int height) {
        return generateQRCode(content, width, height, 
            DEFAULT_FOREGROUND_COLOR, DEFAULT_BACKGROUND_COLOR);
    }

    /**
     * 生成二维码（完整参数）
     *
     * @param content         二维码内容
     * @param width           宽度（像素）
     * @param height          高度（像素）
     * @param foregroundColor 前景色
     * @param backgroundColor 背景色
     * @return 二维码位图，失败返回null
     */
    @Nullable
    public static Bitmap generateQRCode(@NonNull String content, int width, int height,
                                       int foregroundColor, int backgroundColor) {
        if (content == null || content.isEmpty()) {
            Log.e(TAG, "Content is empty");
            return null;
        }

        if (width <= 0 || height <= 0) {
            Log.e(TAG, "Invalid size: " + width + "x" + height);
            return null;
        }

        try {
            // 配置二维码参数
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.MARGIN, 1); // 边距

            // 生成二维码矩阵
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height, hints);

            // 创建位图
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            
            // 填充像素
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? foregroundColor : backgroundColor);
                }
            }

            Log.d(TAG, "QR code generated successfully: " + width + "x" + height);
            return bitmap;
            
        } catch (WriterException e) {
            Log.e(TAG, "Failed to generate QR code", e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error generating QR code", e);
            return null;
        }
    }

    /**
     * 生成用户分享二维码
     * 使用更大的尺寸和更高的纠错级别
     *
     * @param userQRData 用户二维码数据（包含用户ID和公钥）
     * @return 二维码位图
     */
    @Nullable
    public static Bitmap generateUserQRCode(@NonNull String userQRData) {
        return generateQRCode(userQRData, 800, 800);
    }

    /**
     * 生成密码分享二维码
     * 使用适中的尺寸，适合在手机屏幕上显示和扫描
     *
     * @param shareData 分享数据（加密的密码分享信息）
     * @return 二维码位图
     */
    @Nullable
    public static Bitmap generatePasswordShareQRCode(@NonNull String shareData) {
        return generateQRCode(shareData, 600, 600);
    }

    /**
     * 检查内容是否适合生成二维码
     * 二维码有容量限制，过长的内容可能无法生成
     *
     * @param content 要检查的内容
     * @return true表示内容长度合适
     */
    public static boolean isContentSizeValid(@NonNull String content) {
        // 使用UTF-8编码，高纠错级别(H)，最大容量约为1273字符
        // 为了保险起见，限制在1000字符以内
        return content != null && content.length() <= 1000;
    }

    /**
     * 估算二维码大小
     * 根据内容长度推荐合适的二维码尺寸
     *
     * @param content 二维码内容
     * @return 推荐的尺寸（像素）
     */
    public static int getRecommendedSize(@NonNull String content) {
        if (content == null) {
            return DEFAULT_QR_SIZE;
        }
        
        int length = content.length();
        if (length < 100) {
            return 400;
        } else if (length < 300) {
            return 512;
        } else if (length < 600) {
            return 700;
        } else {
            return 800;
        }
    }

    /**
     * 验证二维码内容格式
     * 检查是否为有效的分享数据或用户数据
     *
     * @param content 二维码内容
     * @return true表示格式有效
     */
    public static boolean isValidQRContent(@Nullable String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        
        // 检查是否为有效的JSON格式或特定前缀
        // 这里可以根据实际数据格式进行更严格的验证
        return content.startsWith("{") || 
               content.startsWith("user_") || 
               content.startsWith("share_");
    }

    /**
     * 从二维码内容中提取数据类型
     *
     * @param content 二维码内容
     * @return 数据类型：user, share, 或 unknown
     */
    @NonNull
    public static String getQRContentType(@Nullable String content) {
        if (content == null || content.isEmpty()) {
            return "unknown";
        }
        
        if (content.startsWith("user_") || content.contains("\"userId\"")) {
            return "user";
        } else if (content.startsWith("share_") || content.contains("\"shareId\"")) {
            return "share";
        } else if (content.startsWith("{")) {
            // JSON格式，需要进一步解析
            return "json";
        } else {
            return "unknown";
        }
    }
}
