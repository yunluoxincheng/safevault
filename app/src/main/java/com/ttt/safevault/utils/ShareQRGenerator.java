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

import java.util.HashMap;
import java.util.Map;

/**
 * QR码生成工具
 * 专门用于密码分享功能的QR码生成
 */
public class ShareQRGenerator {
    private static final String TAG = "ShareQRGenerator";

    private static final int DEFAULT_SIZE = 512;
    private static final int MARGIN_SIZE = 1;

    /**
     * 生成QR码
     *
     * @param content QR码内容
     * @param size 图片大小（像素）
     * @return QR码图片
     */
    @Nullable
    public static Bitmap generateQRCode(@NonNull String content, int size) {
        try {
            QRCodeWriter writer = new QRCodeWriter();

            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, MARGIN_SIZE);
            hints.put(EncodeHintType.ERROR_CORRECTION, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H);

            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints);

            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            return bitmap;

        } catch (WriterException e) {
            Log.e(TAG, "Failed to generate QR code", e);
            return null;
        }
    }

    /**
     * 生成默认大小的QR码
     */
    @Nullable
    public static Bitmap generateQRCode(@NonNull String content) {
        return generateQRCode(content, DEFAULT_SIZE);
    }

    /**
     * 检查内容大小是否适合QR码
     *
     * @param content 内容字符串
     * @return true表示可以生成QR码
     */
    public static boolean isContentSizeValid(@NonNull String content) {
        // QR码最大容量（版本40，H纠错级别）
        final int MAX_BYTES = 2953;
        return content.getBytes().length <= MAX_BYTES;
    }
}
