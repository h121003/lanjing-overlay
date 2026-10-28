package com.deepseek.petoverlay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * 底部液态玻璃导航栏。
 *
 * 视觉要素：
 *  - 浮起的胶囊条（左右留白、圆角 = 高度一半）
 *  - 半透明玻璃：渐变填充 + 顶部高光 + 描边 + 外阴影
 *  - 选中格是一枚「玻璃透镜」，切换时在格子之间滑动，途中横向拉伸（挤压-回弹）
 *
 * 说明：跨窗口（悬浮窗之外）的背景模糊 Android 不允许，所以这里用
 * 渐变 + 高光模拟玻璃，不采样背后的内容。
 */
public class GlassBottomBar extends View {

    public interface OnTabSelected {
        void onTabSelected(int index);
    }

    private static final String[] LABELS = {"宠物", "外观", "设置", "关于"};
    private static final int ACCENT = 0xFF11A34C; // 酷安绿

    private final Paint glassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lensPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lensStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specularPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF barRect = new RectF();
    private final RectF lensRect = new RectF();
    private final Path iconPath = new Path();

    private int selected = 0;
    private float lensCenterX = -1f;
    private float stretch = 0f;          // 0 = 正常, >0 = 横向拉伸
    private OnTabSelected listener;

    private final float density;

    public GlassBottomBar(Context c) { this(c, null); }

    public GlassBottomBar(Context c, AttributeSet a) {
        super(c, a);
        density = getResources().getDisplayMetrics().density;
        setLayerType(LAYER_TYPE_SOFTWARE, null); // 阴影需要软件层

        glassPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(1f));
        strokePaint.setColor(0x33FFFFFF);

        lensPaint.setStyle(Paint.Style.FILL);
        lensPaint.setColor(0x1FFFFFFF);
        lensStroke.setStyle(Paint.Style.STROKE);
        lensStroke.setStrokeWidth(dp(1f));
        lensStroke.setColor(0x55FFFFFF);

        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(dp(1.8f));
        iconPaint.setStrokeCap(Paint.Cap.ROUND);

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(dp(11f));

        specularPaint.setShader(new LinearGradient(0, 0, 0, dp(28f),
                0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    public void setOnTabSelected(OnTabSelected l) { listener = l; }

    public int getSelected() { return selected; }

    public void setSelectedSilently(int i) {
        selected = Math.max(0, Math.min(LABELS.length - 1, i));
        lensCenterX = centerOf(selected);
        invalidate();
    }

    private float dp(float v) { return v * density; }

    private float slotWidth() { return barRect.width() / LABELS.length; }

    private float centerOf(int index) { return barRect.left + slotWidth() * (index + 0.5f); }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float mx = dp(14f), my = dp(9f);
        barRect.set(mx, my, w - mx, h - my);
        if (lensCenterX < 0) lensCenterX = centerOf(selected);
        glassPaint.setShader(new LinearGradient(0, barRect.top, 0, barRect.bottom,
                0x2EFFFFFF, 0x14FFFFFF, Shader.TileMode.CLAMP));
        glassPaint.setShadowLayer(dp(10f), 0, dp(4f), 0x55000000);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float r = barRect.height() / 2f;

        // 玻璃主体
        c.drawRoundRect(barRect, r, r, glassPaint);
        // 顶部高光
        c.save();
        Path clip = new Path();
        clip.addRoundRect(barRect, r, r, Path.Direction.CW);
        c.clipPath(clip);
        c.drawRect(barRect.left, barRect.top, barRect.right, barRect.top + dp(28f), specularPaint);
        c.restore();
        c.drawRoundRect(barRect, r, r, strokePaint);

        // 透镜
        float slot = slotWidth();
        float lensW = slot - dp(10f);
        float lensH = barRect.height() - dp(10f);
        float cx = lensCenterX;
        float drawW = lensW * (1f + stretch * 0.35f);
        float drawH = lensH * (1f - stretch * 0.18f);
        lensRect.set(cx - drawW / 2f, barRect.centerY() - drawH / 2f,
                cx + drawW / 2f, barRect.centerY() + drawH / 2f);
        float lr = drawH / 2f;
        c.drawRoundRect(lensRect, lr, lr, lensPaint);
        c.drawRoundRect(lensRect, lr, lr, lensStroke);

        // 图标与文字
        for (int i = 0; i < LABELS.length; i++) {
            float ccx = barRect.left + slot * (i + 0.5f);
            boolean on = i == selected;
            int color = on ? ACCENT : 0xB0FFFFFF;
            iconPaint.setColor(color);
            labelPaint.setColor(color);
            drawIcon(c, i, ccx, barRect.centerY() - dp(7f), dp(9.5f));
            c.drawText(LABELS[i], ccx, barRect.centerY() + dp(19f), labelPaint);
        }
    }

    /** 用 Path 画简单线性图标，避免引入图片资源 */
    private void drawIcon(Canvas c, int index, float cx, float cy, float s) {
        iconPath.reset();
        switch (index) {
            case 0: // 宠物：肉垫
                iconPath.addCircle(cx, cy - s * 0.45f, s * 0.42f, Path.Direction.CW);
                iconPath.addCircle(cx - s * 0.85f, cy - s * 0.05f, s * 0.30f, Path.Direction.CW);
                iconPath.addCircle(cx + s * 0.85f, cy - s * 0.05f, s * 0.30f, Path.Direction.CW);
                iconPath.addCircle(cx - s * 0.35f, cy + s * 0.85f, s * 0.26f, Path.Direction.CW);
                iconPath.addCircle(cx + s * 0.35f, cy + s * 0.85f, s * 0.26f, Path.Direction.CW);
                break;
            case 1: // 外观：调色板
                iconPath.addCircle(cx, cy, s, Path.Direction.CW);
                break;
            case 2: // 设置：齿轮（圆 + 六根齿）
                iconPath.addCircle(cx, cy, s * 0.55f, Path.Direction.CW);
                iconPath.addCircle(cx, cy, s, Path.Direction.CW);
                for (int k = 0; k < 6; k++) {
                    double a = Math.PI * 2 * k / 6;
                    iconPath.moveTo(cx + (float) (Math.cos(a) * s), cy + (float) (Math.sin(a) * s));
                    iconPath.lineTo(cx + (float) (Math.cos(a) * s * 1.45f), cy + (float) (Math.sin(a) * s * 1.45f));
                }
                break;
            default: // 关于：i
                iconPath.addCircle(cx, cy, s, Path.Direction.CW);
                iconPath.moveTo(cx, cy - s * 0.1f);
                iconPath.lineTo(cx, cy + s * 0.55f);
                break;
        }
        c.drawPath(iconPath, iconPaint);
        if (index == 3) c.drawCircle(cx, cy - s * 0.5f, dp(1.6f), iconPaint);
    }

    private static final long SNAP_MS = 260;
    private boolean dragging = false;
    private float downX = 0f;
    private float dragStartCenter = 0f;
    private ValueAnimator anim;

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (anim != null) anim.cancel();
                dragging = true;
                downX = e.getX();
                dragStartCenter = lensCenterX;
                stretch = 0f;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging) return true;
                float dx = e.getX() - downX;
                float slot = slotWidth();
                float minC = barRect.left + slot / 2f;
                float maxC = barRect.right - slot / 2f;
                lensCenterX = Math.max(minC, Math.min(maxC, dragStartCenter + dx));
                // 拖动时按位移量拉伸，跟手
                stretch = Math.min(1f, Math.abs(dx) / (slot * 1.4f));
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!dragging) return true;
                dragging = false;
                int target = nearestIndex(lensCenterX);
                animateTo(target, true);
                return true;
        }
        return true;
    }

    private int nearestIndex(float cx) {
        float slot = slotWidth();
        int i = Math.round((cx - barRect.left) / slot - 0.5f);
        return Math.max(0, Math.min(LABELS.length - 1, i));
    }

    private void animateTo(final int index, boolean notify) {
        final float from = lensCenterX;
        final float to = centerOf(index);
        selected = index;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(SNAP_MS);
        anim.setInterpolator(new DecelerateInterpolator(1.8f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator v) {
                float f = (float) v.getAnimatedValue();
                lensCenterX = from + (to - from) * f;
                stretch = (float) Math.sin(Math.PI * f) * 0.6f;
                invalidate();
            }
        });
        anim.start();
        if (notify && listener != null) listener.onTabSelected(index);
    }
}
