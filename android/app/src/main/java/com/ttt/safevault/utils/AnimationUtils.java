package com.ttt.safevault.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.transition.Transition;
import android.transition.TransitionSet;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.OvershootInterpolator;
import android.view.animation.TranslateAnimation;

import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.ttt.safevault.R;

/**
 * 动画工具类
 * 提供统一的页面过渡动画、列表动画和按钮反馈动画
 */
public class AnimationUtils {

    private static final int DEFAULT_DURATION = 300;
    private static final int SHORT_DURATION = 150;
    private static final int LONG_DURATION = 400;

    /**
     * 页面淡入动画
     */
    public static void fadeIn(View view, int duration) {
        view.setVisibility(View.VISIBLE);
        view.setAlpha(0f);
        view.animate()
                .alpha(1f)
                .setDuration(duration)
                .setInterpolator(new FastOutSlowInInterpolator())
                .start();
    }

    /**
     * 页面淡入动画（使用默认时长）
     */
    public static void fadeIn(View view) {
        fadeIn(view, DEFAULT_DURATION);
    }

    /**
     * 页面淡出动画
     */
    public static void fadeOut(final View view, int duration, final Runnable endAction) {
        view.animate()
                .alpha(0f)
                .setDuration(duration)
                .setInterpolator(new FastOutSlowInInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.setVisibility(View.GONE);
                        if (endAction != null) {
                            endAction.run();
                        }
                    }
                })
                .start();
    }

    /**
     * 页面淡出动画（使用默认时长）
     */
    public static void fadeOut(View view) {
        fadeOut(view, DEFAULT_DURATION, null);
    }

    /**
     * 从左侧滑入动画
     */
    public static void slideInLeft(View view, int duration) {
        view.setVisibility(View.VISIBLE);
        view.setTranslationX(-view.getWidth());
        view.animate()
                .translationX(0f)
                .setDuration(duration)
                .setInterpolator(new FastOutSlowInInterpolator())
                .start();
    }

    /**
     * 从左侧滑入动画（使用默认时长）
     */
    public static void slideInLeft(View view) {
        slideInLeft(view, DEFAULT_DURATION);
    }

    /**
     * 从右侧滑入动画
     */
    public static void slideInRight(View view, int duration) {
        view.setVisibility(View.VISIBLE);
        view.setTranslationX(view.getWidth());
        view.animate()
                .translationX(0f)
                .setDuration(duration)
                .setInterpolator(new FastOutSlowInInterpolator())
                .start();
    }

    /**
     * 从右侧滑入动画（使用默认时长）
     */
    public static void slideInRight(View view) {
        slideInRight(view, DEFAULT_DURATION);
    }

    /**
     * 从下方滑入动画
     */
    public static void slideInUp(View view, int duration) {
        view.setVisibility(View.VISIBLE);
        view.setTranslationY(view.getHeight());
        view.animate()
                .translationY(0f)
                .setDuration(duration)
                .setInterpolator(new FastOutSlowInInterpolator())
                .start();
    }

    /**
     * 从下方滑入动画（使用默认时长）
     */
    public static void slideInUp(View view) {
        slideInUp(view, DEFAULT_DURATION);
    }

    /**
     * 列表项逐个进入动画
     * @param views 要执行动画的视图数组
     * @param staggerDelay 每个视图之间的延迟
     */
    public static void staggeredEnter(View[] views, int staggerDelay) {
        for (int i = 0; i < views.length; i++) {
            final View view = views[i];
            view.setAlpha(0f);
            view.setTranslationY(20 * (i + 1));
            view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(DEFAULT_DURATION)
                    .setStartDelay(i * staggerDelay)
                    .setInterpolator(new FastOutSlowInInterpolator())
                    .start();
        }
    }

    /**
     * 列表项逐个进入动画（使用默认延迟）
     */
    public static void staggeredEnter(View[] views) {
        staggeredEnter(views, 50);
    }

    /**
     * 按钮点击缩放反馈动画
     */
    public static void buttonPressFeedback(View view) {
        view.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(SHORT_DURATION)
                .setInterpolator(new FastOutSlowInInterpolator())
                .withEndAction(() -> {
                    view.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(SHORT_DURATION)
                            .setInterpolator(new FastOutSlowInInterpolator())
                            .start();
                })
                .start();
    }

    /**
     * 按钮点击缩放反馈动画（带弹性效果）
     */
    public static void buttonPressFeedbackBounce(View view) {
        view.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(SHORT_DURATION)
                .setInterpolator(new FastOutSlowInInterpolator())
                .withEndAction(() -> {
                    view.animate()
                            .scaleX(1.05f)
                            .scaleY(1.05f)
                            .setDuration(SHORT_DURATION)
                            .setInterpolator(new OvershootInterpolator())
                            .withEndAction(() -> {
                                view.animate()
                                        .scaleX(1f)
                                        .scaleY(1f)
                                        .setDuration(SHORT_DURATION)
                                        .setInterpolator(new FastOutSlowInInterpolator())
                                        .start();
                            })
                            .start();
                })
                .start();
    }

    /**
     * 圆形揭示动画（用于密码显示/隐藏等）
     * @param view 要显示的视图
     * @param centerX 圆心 X 坐标
     * @param centerY 圆心 Y 坐标
     */
    public static void circularReveal(View view, int centerX, int centerY) {
        int width = view.getWidth();
        int height = view.getHeight();
        float finalRadius = (float) Math.hypot(width, height);

        Animator anim = ViewAnimationUtils.createCircularReveal(
                view, centerX, centerY, 0f, finalRadius);
        view.setVisibility(View.VISIBLE);
        anim.setDuration(DEFAULT_DURATION);
        anim.setInterpolator(new FastOutSlowInInterpolator());
        anim.start();
    }

    /**
     * 反向圆形揭示动画（用于隐藏）
     * @param view 要隐藏的视图
     * @param centerX 圆心 X 坐标
     * @param centerY 圆心 Y 坐标
     * @param endAction 动画结束后的操作
     */
    public static void circularHide(final View view, int centerX, int centerY, final Runnable endAction) {
        int width = view.getWidth();
        int height = view.getHeight();
        float initialRadius = (float) Math.hypot(width, height);

        Animator anim = ViewAnimationUtils.createCircularReveal(
                view, centerX, centerY, initialRadius, 0f);
        anim.setDuration(DEFAULT_DURATION);
        anim.setInterpolator(new FastOutSlowInInterpolator());
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.setVisibility(View.INVISIBLE);
                if (endAction != null) {
                    endAction.run();
                }
            }
        });
        anim.start();
    }

    /**
     * 旋转动画（用于图标状态切换）
     * @param view 要旋转的视图
     * @param fromRotation 起始角度
     * @param toRotation 目标角度
     */
    public static void rotate(View view, float fromRotation, float toRotation) {
        view.animate()
                .rotation(fromRotation)
                .setDuration(0)
                .start();

        view.animate()
                .rotation(toRotation)
                .setDuration(DEFAULT_DURATION)
                .setInterpolator(new FastOutSlowInInterpolator())
                .start();
    }

    /**
     * 抖动动画（用于错误提示）
     */
    public static void shake(View view) {
        view.animate()
                .translationX(20)
                .setDuration(50)
                .setInterpolator(new FastOutSlowInInterpolator())
                .withEndAction(() -> {
                    view.animate()
                            .translationX(-20)
                            .setDuration(50)
                            .setInterpolator(new FastOutSlowInInterpolator())
                            .withEndAction(() -> {
                                view.animate()
                                        .translationX(10)
                                        .setDuration(50)
                                        .setInterpolator(new FastOutSlowInInterpolator())
                                        .withEndAction(() -> {
                                            view.animate()
                                                    .translationX(-10)
                                                    .setDuration(50)
                                                    .setInterpolator(new FastOutSlowInInterpolator())
                                                    .withEndAction(() -> {
                                                        view.animate()
                                                                .translationX(0)
                                                                .setDuration(50)
                                                                .setInterpolator(new FastOutSlowInInterpolator())
                                                                .start();
                                                    })
                                                    .start();
                                        })
                                        .start();
                            })
                            .start();
                })
                .start();
    }

    /**
     * 脉冲动画（用于重要提示）
     */
    public static void pulse(View view, int repeatCount) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.1f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.1f, 1f);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(scaleX, scaleY);
        animatorSet.setDuration(600);
        // 使用 ObjectAnimator 的重复
        scaleX.setRepeatCount(repeatCount);
        scaleY.setRepeatCount(repeatCount);
        animatorSet.start();
    }

    /**
     * 创建共享元素过渡动画配置
     * @return Transition 对象
     */
    public static Transition createSharedElementTransition() {
        android.transition.ChangeBounds changeBounds = new android.transition.ChangeBounds();
        changeBounds.setDuration(DEFAULT_DURATION);
        changeBounds.setInterpolator(new FastOutSlowInInterpolator());
        return changeBounds;
    }

    /**
     * 从 XML 加载动画
     * @param context 上下文
     * @param animRes 动画资源 ID
     * @return Animation 对象
     */
    public static Animation loadXmlAnimation(Context context, int animRes) {
        return android.view.animation.AnimationUtils.loadAnimation(context, animRes);
    }
}
