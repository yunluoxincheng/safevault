package com.ttt.safevault.utils;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

/**
 * 无障碍工具类
 * 提供内容描述设置、屏幕阅读器兼容性检查和触摸目标尺寸验证
 */
public class AccessibilityUtils {

    private static final int MIN_TOUCH_TARGET_DP = 48;

    /**
     * 检查是否启用了屏幕阅读器（TalkBack）
     * @param context 上下文
     * @return 是否启用了屏幕阅读器
     */
    public static boolean isScreenReaderEnabled(Context context) {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am != null) {
            return am.isEnabled() && am.isTouchExplorationEnabled();
        }
        return false;
    }

    /**
     * 检查是否启用了任何无障碍服务
     * @param context 上下文
     * @return 是否启用了无障碍服务
     */
    public static boolean isAccessibilityEnabled(Context context) {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        return am != null && am.isEnabled();
    }

    /**
     * 为视图设置内容描述（支持字符串资源）
     * @param view 要设置的视图
     * @param contentDescRes 内容描述字符串资源 ID
     */
    public static void setContentDescription(View view, @StringRes int contentDescRes) {
        if (view == null) return;
        view.setContentDescription(view.getContext().getString(contentDescRes));
    }

    /**
     * 为视图设置内容描述（支持字符串）
     * @param view 要设置的视图
     * @param contentDesc 内容描述字符串
     */
    public static void setContentDescription(View view, String contentDesc) {
        if (view == null || contentDesc == null) return;
        view.setContentDescription(contentDesc);
    }

    /**
     * 为 ImageView 设置内容和描述
     * @param imageView ImageView
     * @param contentDescRes 内容描述字符串资源 ID
     */
    public static void setImageContentDescription(ImageView imageView, @StringRes int contentDescRes) {
        if (imageView == null) return;
        setContentDescription(imageView, contentDescRes);
    }

    /**
     * 为 ImageView 设置内容和描述（带图标类型）
     * @param imageView ImageView
     * @param iconType 图标类型（如"密码"、"用户名"等）
     */
    public static void setImageContentDescription(ImageView imageView, String iconType) {
        if (imageView == null) return;
        String description = imageView.getContext().getString(
                imageView.getContext().getResources().getIdentifier(
                        "icon_" + iconType.toLowerCase(),
                        "string",
                        imageView.getContext().getPackageName()
                )
        );
        if (description != null && !description.isEmpty()) {
            imageView.setContentDescription(description);
        }
    }

    /**
     * 为 TextView 设置无障碍标签（用于输入框）
     * @param textView TextView
     * @param labelRes 标签字符串资源 ID
     */
    public static void setAccessibilityLabel(TextView textView, @StringRes int labelRes) {
        if (textView == null) return;
        textView.setContentDescription(textView.getContext().getString(labelRes));
        textView.setLabelFor(textView.getId());
    }

    /**
     * 为 EditText 设置无障碍标签和提示
     * @param editText EditText
     * @param labelRes 标签字符串资源 ID
     * @param hintRes 提示字符串资源 ID
     */
    public static void setEditTextAccessibility(EditText editText,
                                               @StringRes int labelRes,
                                               @StringRes int hintRes) {
        if (editText == null) return;
        editText.setContentDescription(editText.getContext().getString(labelRes));
        editText.setHint(editText.getContext().getString(hintRes));
    }

    /**
     * 验证视图的触摸目标尺寸是否符合最小要求（48dp）
     * @param view 要验证的视图
     * @return 是否符合最小触摸目标尺寸要求
     */
    public static boolean isValidTouchTargetSize(View view) {
        if (view == null) return false;

        int width = view.getWidth();
        int height = view.getHeight();

        if (width <= 0 || height <= 0) {
            // 视图尚未测量，使用布局参数
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (params != null) {
                width = params.width;
                height = params.height;
            }
        }

        float density = view.getContext().getResources().getDisplayMetrics().density;
        int minSizePx = (int) (MIN_TOUCH_TARGET_DP * density);

        return width >= minSizePx && height >= minSizePx;
    }

    /**
     * 获取视图的触摸目标尺寸信息
     * @param view 视图
     * @return 尺寸信息字符串
     */
    public static String getTouchTargetSizeInfo(View view) {
        if (view == null) return "View is null";

        int width = view.getWidth();
        int height = view.getHeight();
        float density = view.getContext().getResources().getDisplayMetrics().density;

        int widthDp = (int) (width / density);
        int heightDp = (int) (height / density);
        int minSizePx = (int) (MIN_TOUCH_TARGET_DP * density);

        boolean isValid = width >= minSizePx && height >= minSizePx;

        return String.format("Size: %dx%ddp (Min: %ddp) - %s",
                widthDp, heightDp, MIN_TOUCH_TARGET_DP,
                isValid ? "Valid" : "Invalid");
    }

    /**
     * 为视图添加无障碍焦点管理
     * @param view 视图
     * @param focusable 是否可获得焦点
     */
    public static void setAccessibilityFocusable(View view, boolean focusable) {
        if (view == null) return;
        view.setFocusable(focusable);
        view.setClickable(focusable);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.setScreenReaderFocusable(focusable);
        }
    }

    /**
     * 为视图添加无障碍点击动作
     * @param view 视图
     * @param actionDescription 动作描述
     */
    public static void setAccessibilityClickAction(View view, String actionDescription) {
        if (view == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction action =
                    new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(
                            android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,
                            actionDescription
                    );
            // 注意：这需要在视图附加到窗口后才能使用
        }
    }

    /**
     * 发送无障碍事件通知
     * @param view 视图
     * @param messageType 消息类型
     * @param message 消息内容
     */
    public static void announceForAccessibility(View view, String message) {
        if (view == null || message == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            view.announceForAccessibility(message);
        } else {
            // 对于旧版本，发送 TYPE_VIEW_FOCUSED 事件
            view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        }
    }

    /**
     * 为视图设置无障碍委托（用于自定义视图）
     * @param view 视图
     * @param delegate 委托对象
     */
    public static void setAccessibilityDelegate(View view, View.AccessibilityDelegate delegate) {
        if (view == null) return;
        view.setAccessibilityDelegate(delegate);
    }

    /**
     * 为重要的内容变更设置无障碍通知
     * @param view 视图
     * @param previousText 之前的文本
     * @param currentText 当前的文本
     */
    public static void announceTextChange(View view, String previousText, String currentText) {
        if (view == null || currentText == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if (!currentText.equals(previousText)) {
                view.announceForAccessibility(currentText);
            }
        }
    }

    /**
     * 为按钮设置无障碍支持
     * @param button 按钮视图
     * @param textRes 按钮文本资源
     * @param contentDescRes 内容描述资源（可选）
     */
    public static void setupButtonAccessibility(android.widget.Button button,
                                               @StringRes int textRes,
                                               @StringRes int contentDescRes) {
        if (button == null) return;

        button.setText(textRes);
        if (contentDescRes != 0) {
            setContentDescription(button, contentDescRes);
        }
    }

    /**
     * 为图标按钮设置无障碍支持
     * @param iconButton 图标按钮
     * @param contentDescRes 内容描述资源
     */
    public static void setupIconButtonAccessibility(View iconButton, @StringRes int contentDescRes) {
        if (iconButton == null) return;

        setContentDescription(iconButton, contentDescRes);
        setAccessibilityFocusable(iconButton, true);
    }

    /**
     * 为列表项设置无障碍支持
     * @param itemView 列表项视图
     * @param title 标题
     * @param description 描述（可选）
     */
    public static void setupListItemAccessibility(View itemView, String title, String description) {
        if (itemView == null) return;

        StringBuilder contentDesc = new StringBuilder(title);
        if (description != null && !description.isEmpty()) {
            contentDesc.append(", ").append(description);
        }

        setContentDescription(itemView, contentDesc.toString());

        // 设置为可点击以确保无障碍用户可以激活
        itemView.setClickable(true);
        itemView.setFocusable(true);
    }

    /**
     * 检查视图是否在无障碍树中重要
     * @param view 视图
     * @return 是否重要
     */
    public static boolean isImportantForAccessibility(View view) {
        if (view == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            return view.isImportantForAccessibility();
        }
        return true;
    }

    /**
     * 设置视图在无障碍树中的重要性
     * @param view 视图
     * @param important 是否重要
     */
    public static void setImportantForAccessibility(View view, boolean important) {
        if (view == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            int mode = important ?
                    View.IMPORTANT_FOR_ACCESSIBILITY_YES :
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO;
            view.setImportantForAccessibility(mode);
        }
    }

    /**
     * 为密码输入框设置特殊无障碍处理
     * @param editText 密码输入框
     * @param labelRes 标签资源
     */
    public static void setupPasswordInputAccessibility(EditText editText, @StringRes int labelRes) {
        if (editText == null) return;

        Context context = editText.getContext();
        String label = context.getString(labelRes);
        editText.setContentDescription(label);

        // 密码字段不应该被朗读，但要告知用户这是密码字段
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            // 设置输入类型为密码
            editText.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
    }

    /**
     * 获取本地化的触摸目标尺寸
     * @param context 上下文
     * @return 最小触摸目标尺寸（像素）
     */
    public static int getMinTouchTargetSizePx(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (MIN_TOUCH_TARGET_DP * density);
    }
}
