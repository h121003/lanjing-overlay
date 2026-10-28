package com.deepseek.petoverlay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * 底部导航栏。
 *
 * 两种状态：
 *  - 静止：普通导航栏外观（纯色胶囊 + 图标文字），不做玻璃效果
 *  - 交互：按下 / 拖动 / 点击时切换成「液态玻璃」——玻璃底、透镜高光、
 *          透镜下的壁纸被放大折射、形变由速度驱动（快则压扁，慢则回弹）
 */
public class GlassBottomBar extends View {

    public interface OnTabSelected {
        void onTabSelected(int index);
    }

    private static final String[] LABELS = {"宠物", "外观", "设置", "关于"};
    private static final int ACCENT = 0xFF11A34C;

    private final float density;
    private final RectF barRect = new RectF();
    private final RectF lensRect = new RectF();
    private final Path iconPath = new Path();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int selected = 0;
    private int tintColor = 0xFF22262C;
    private float lensCenterX = -1f;
    private float deform = 0f;          // 0..1，越大越扁
    private boolean active = false;
    private boolean dark = true;
    private OnTabSelected listener;

    private Bitmap backdrop;            // 壁纸缩略图，用于透镜折射
    private int rootW = 1, rootH = 1;

    private boolean dragging = false;
    private float barOffsetX = 0f;   // 整条栏的轻微位移
    private float pressBoost = 0f;   // 按下时图标放大
    private int pressedIndex = -1;
    private ValueAnimator pressAnim;
    private float downX = 0f;
    private float dragStartCenter = 0f;
    private float lastX = 0f;
    private long lastT = 0L;
    private ValueAnimator anim;

    private final Runnable decay = new Runnable() {
        @Override public void run() {
            deform *= 0.86f;
            if (deform < 0.01f) {
                deform = 0f;
            } else {
                postInvalidateOnAnimation();
                postDelayed(this, 16);
            }
            invalidate();
        }
    };

    public GlassBottomBar(Context c) { this(c, null); }

    public GlassBottomBar(Context c, AttributeSet a) {
        super(c, a);
        density = getResources().getDisplayMetrics().density;
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1f));
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(dp(1.8f));
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(dp(11f));
    }

    public void setOnTabSelected(OnTabSelected l) { listener = l; }

    public int getSelected() { return selected; }

    public void setSelectedSilently(int i) {
        selected = Math.max(0, Math.min(LABELS.length - 1, i));
        lensCenterX = centerOf(selected);
        invalidate();
    }

    /** 壁纸缩略图 + 根布局尺寸，用于透镜下的折射 */
    public void setBackdrop(Bitmap bmp, int w, int h) {
        backdrop = bmp;
        rootW = Math.max(1, w);
        rootH = Math.max(1, h);
        invalidate();
    }

    public void setDark(boolean d) {
        dark = d;
        invalidate();
    }

    /** 采样自壁纸的颜色 */
    public void setTint(int color) {
        tintColor = color;
        invalidate();
    }

    private float dp(float v) { return v * density; }

    private float slotWidth() { return barRect.width() / LABELS.length; }

    private float centerOf(int index) { return barRect.left + slotWidth() * (index + 0.5f); }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float mx = dp(18f), my = dp(10f);
        barRect.set(mx, my, w - mx, h - my);
        if (lensCenterX < 0) lensCenterX = centerOf(selected);
    }

    // ---------------- 绘制 ----------------

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float r = barRect.height() / 2f;

        // 整条栏跟着透镜轻微横移（视差感）
        float center = (barRect.left + barRect.right) / 2f;
        float target = (lensCenterX - center) * 0.035f;
        float max = dp(7f);
        barOffsetX = Math.max(-max, Math.min(max, target));

        c.save();
        c.translate(barOffsetX, 0f);
        if (!active) {
            drawIdle(c, r);
        } else {
            drawActive(c, r);
        }
        drawItems(c);
        c.restore();
    }

    /** 静止态：普通导航栏 */
    private void drawIdle(Canvas c, float r) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(dark ? 0xEE151A20 : 0xEEF2F5F9);
        p.setShadowLayer(dp(8f), 0, dp(3f), 0x33000000);
        c.drawRoundRect(barRect, r, r, p);
        p.clearShadowLayer();

        // 选中格的实心底（不是玻璃）
        float slot = slotWidth();
        float w = slot - dp(12f);
        float h = barRect.height() - dp(14f);
        RectF sel = new RectF(lensCenterX - w / 2f, barRect.centerY() - h / 2f,
                lensCenterX + w / 2f, barRect.centerY() + h / 2f);
        p.setColor(dark ? 0x1FFFFFFF : 0x12000000);
        c.drawRoundRect(sel, h / 2f, h / 2f, p);

        stroke.setColor(dark ? 0x1AFFFFFF : 0x1A000000);
        c.drawRoundRect(barRect, r, r, stroke);
    }

    /** 交互态：液态玻璃 */
    private void drawActive(Canvas c, float r) {
        // 外阴影
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x00000000);
        p.setShadowLayer(dp(16f), 0, dp(7f), 0x77000000);
        c.drawRoundRect(barRect, r, r, p);
        p.clearShadowLayer();

        boolean hasBg = backdrop != null;
        // 玻璃本体：有壁纸时用壁纸色，没壁纸时用深色磨砂
        p.setColor(withAlpha(tintColor, hasBg ? 0x99 : 0xE6));
        c.drawRoundRect(barRect, r, r, p);

        // 白色渐变：没有壁纸时非常克制，避免变成一块灰片
        int a1 = hasBg ? 0x24 : 0x1F, a2 = hasBg ? 0x10 : 0x0D, a3 = hasBg ? 0x06 : 0x05;
        p.setShader(new LinearGradient(0, barRect.top, 0, barRect.bottom,
                new int[]{(a1 << 24) | 0xFFFFFF, (a2 << 24) | 0xFFFFFF, (a3 << 24) | 0xFFFFFF},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(barRect, r, r, p);
        p.setShader(null);

        // 顶部高光 + 底部内阴影
        c.save();
        Path clip = new Path();
        clip.addRoundRect(barRect, r, r, Path.Direction.CW);
        c.clipPath(clip);
        p.setShader(new LinearGradient(0, barRect.top, 0, barRect.top + dp(22f),
                0x99FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(barRect.left, barRect.top, barRect.right, barRect.top + dp(22f), p);
        p.setShader(new LinearGradient(0, barRect.bottom - dp(18f), 0, barRect.bottom,
                0x00000000, 0x4D000000, Shader.TileMode.CLAMP));
        c.drawRect(barRect.left, barRect.bottom - dp(18f), barRect.right, barRect.bottom, p);
        p.setShader(null);
        c.restore();

        // 边缘：上亮下暗
        stroke.setStrokeWidth(dp(1.2f));
        stroke.setShader(new LinearGradient(0, barRect.top, 0, barRect.bottom,
                0x80FFFFFF, 0x22FFFFFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(barRect, r, r, stroke);
        stroke.setShader(null);

        // 透镜
        float slot = slotWidth();
        // 透镜只包住「图标 + 文字」，不要整条高度
        float lensW = Math.min(slot - dp(14f), dp(76f));
        float lensH = dp(46f);
        float drawW = lensW * (1f + deform * 0.30f);
        float drawH = lensH * (1f - deform * 0.26f);
        float cx = lensCenterX;
        lensRect.set(cx - drawW / 2f, barRect.centerY() - drawH / 2f,
                cx + drawW / 2f, barRect.centerY() + drawH / 2f);
        float lr = drawH / 2f;

        // 透镜里的折射：把壁纸对应区域放大 1.18 倍画进透镜
        c.save();
        Path lensClip = new Path();
        lensClip.addRoundRect(lensRect, lr, lr, Path.Direction.CW);
        c.clipPath(lensClip);
        if (backdrop != null) {
            float scale = 1.26f;
            float bw = drawW / rootW * backdrop.getWidth() * scale;
            float bh = drawH / rootH * backdrop.getHeight() * scale;
            float bcx = (cx / rootW) * backdrop.getWidth();
            float bcy = (barRect.centerY() / rootH) * backdrop.getHeight();
            Rect src = new Rect((int) (bcx - bw / 2f), (int) (bcy - bh / 2f),
                    (int) (bcx + bw / 2f), (int) (bcy + bh / 2f));
            p.setAlpha(0xE6);
            c.drawBitmap(backdrop, src, lensRect, p);
            p.setAlpha(255);
        }
        // 透镜玻璃色：有壁纸偏亮，没壁纸偏深
        int g1 = backdrop != null ? 0x14FFFFFF : 0x24FFFFFF;
        int g2 = backdrop != null ? 0x06FFFFFF : 0x0AFFFFFF;
        p.setShader(new LinearGradient(0, lensRect.top, 0, lensRect.bottom,
                g1, g2, Shader.TileMode.CLAMP));
        c.drawRoundRect(lensRect, lr, lr, p);
        p.setShader(null);
        // 顶部弧形高光
        p.setShader(new LinearGradient(0, lensRect.top, 0, lensRect.top + drawH * 0.5f,
                0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(lensRect.left + dp(2f), lensRect.top + dp(2f),
                lensRect.right - dp(2f), lensRect.top + drawH * 0.5f), lr, lr, p);
        p.setShader(null);
        c.restore();

        stroke.setStrokeWidth(dp(1.1f));
        stroke.setColor(0x66FFFFFF);
        c.drawRoundRect(lensRect, lr, lr, stroke);
        stroke.setColor(0x33FFFFFF);
    }

    /** 图标 + 文字；透镜扫过谁，谁就形变（跟位置无关，跟距离有关） */
    private void drawItems(Canvas c) {
        float slot = slotWidth();
        for (int i = 0; i < LABELS.length; i++) {
            float ccx = barRect.left + slot * (i + 0.5f);
            boolean on = i == selected;
            int color = on ? ACCENT : (active || dark ? 0xEEFFFFFF : 0xCC111820);
            iconPaint.setColor(color);
            labelPaint.setColor(color);

            // 距离透镜越近，形变越强；离开就恢复原样
            float dist = Math.abs(ccx - lensCenterX);
            float falloff = Math.max(0f, 1f - dist / (slot * 0.85f));
            float amt = active ? deform * falloff : 0f;

            // 选中/按下时整体放大一点点
            float boost = (on ? 0.06f : 0f) + (i == pressedIndex ? pressBoost * 0.10f : 0f);
            float sx = 1f + boost + amt * 0.24f;
            float sy = 1f + boost - amt * 0.18f;

            if (Math.abs(sx - 1f) > 0.005f || Math.abs(sy - 1f) > 0.005f) {
                c.save();
                c.scale(sx, sy, ccx, barRect.centerY());
            }
            drawIcon(c, i, ccx, barRect.centerY() - dp(6f), dp(8.5f));
            c.drawText(LABELS[i], ccx, barRect.centerY() + dp(17f), labelPaint);
            if (Math.abs(sx - 1f) > 0.005f || Math.abs(sy - 1f) > 0.005f) c.restore();
        }
    }

    private void drawIcon(Canvas c, int index, float cx, float cy, float s) {
        iconPath.reset();
        switch (index) {
            case 0:
                iconPath.addCircle(cx, cy - s * 0.45f, s * 0.42f, Path.Direction.CW);
                iconPath.addCircle(cx - s * 0.85f, cy - s * 0.05f, s * 0.30f, Path.Direction.CW);
                iconPath.addCircle(cx + s * 0.85f, cy - s * 0.05f, s * 0.30f, Path.Direction.CW);
                iconPath.addCircle(cx - s * 0.35f, cy + s * 0.85f, s * 0.26f, Path.Direction.CW);
                iconPath.addCircle(cx + s * 0.35f, cy + s * 0.85f, s * 0.26f, Path.Direction.CW);
                break;
            case 1:
                iconPath.addCircle(cx, cy, s, Path.Direction.CW);
                iconPath.addCircle(cx, cy, s * 0.45f, Path.Direction.CW);
                break;
            case 2:
                iconPath.addCircle(cx, cy, s * 0.55f, Path.Direction.CW);
                iconPath.addCircle(cx, cy, s, Path.Direction.CW);
                for (int k = 0; k < 6; k++) {
                    double a = Math.PI * 2 * k / 6;
                    iconPath.moveTo(cx + (float) (Math.cos(a) * s), cy + (float) (Math.sin(a) * s));
                    iconPath.lineTo(cx + (float) (Math.cos(a) * s * 1.45f),
                            cy + (float) (Math.sin(a) * s * 1.45f));
                }
                break;
            default:
                iconPath.addCircle(cx, cy, s, Path.Direction.CW);
                iconPath.moveTo(cx, cy - s * 0.1f);
                iconPath.lineTo(cx, cy + s * 0.55f);
                break;
        }
        c.drawPath(iconPath, iconPaint);
        if (index == 3) c.drawCircle(cx, cy - s * 0.5f, dp(1.6f), iconPaint);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    // ---------------- 交互 ----------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (anim != null) anim.cancel();
                setActive(true);
                pressedIndex = (int) ((e.getX() - barRect.left) / slotWidth());
                pressedIndex = Math.max(0, Math.min(LABELS.length - 1, pressedIndex));
                animatePress(1f, 110);
                dragging = true;
                downX = e.getX();
                lastX = downX;
                lastT = System.currentTimeMillis();
                dragStartCenter = lensCenterX;
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (!dragging) return true;
                long now = System.currentTimeMillis();
                float dt = Math.max(1f, now - lastT);
                float v = Math.abs(e.getX() - lastX) / dt; // px per ms
                lastX = e.getX();
                lastT = now;
                // 速度驱动形变：快 → 压扁；慢 → 回落
                deform = Math.min(0.9f, deform * 0.55f + v * 0.55f);
                lastX = e.getX();

                float slot = slotWidth();
                float minC = barRect.left + slot / 2f;
                float maxC = barRect.right - slot / 2f;
                lensCenterX = Math.max(minC, Math.min(maxC, dragStartCenter + (e.getX() - downX)));
                removeCallbacks(decay);
                postDelayed(decay, 48);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (!dragging) return true;
                dragging = false;
                float moved = Math.abs(e.getX() - downX);
                boolean tap = e.getActionMasked() == MotionEvent.ACTION_UP
                        && moved < density * 8f;
                int target;
                if (tap) {
                    target = (int) ((e.getX() - barRect.left) / slotWidth());
                    target = Math.max(0, Math.min(LABELS.length - 1, target));
                } else {
                    target = nearestIndex(lensCenterX);
                }
                animateTo(target, true);
                animatePress(0f, 180);
                pressedIndex = -1;
                removeCallbacks(decay);
                postDelayed(decay, 120);
                postDelayed(new Runnable() {
                    @Override public void run() {
                        if (!dragging) setActive(false);
                    }
                }, 520);
                return true;
            }
        }
        return true;
    }

    private void animatePress(final float to, int duration) {
        if (pressAnim != null) pressAnim.cancel();
        pressAnim = ValueAnimator.ofFloat(pressBoost, to);
        pressAnim.setDuration(duration);
        pressAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator v) {
                pressBoost = (float) v.getAnimatedValue();
                invalidate();
            }
        });
        pressAnim.start();
    }

    private void setActive(boolean on) {
        if (active == on) return;
        active = on;
        invalidate();
    }

    private int nearestIndex(float cx) {
        int i = Math.round((cx - barRect.left) / slotWidth() - 0.5f);
        return Math.max(0, Math.min(LABELS.length - 1, i));
    }

    private void animateTo(final int index, final boolean notify) {
        final float from = lensCenterX;
        final float to = centerOf(index);
        selected = index;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(320);
        anim.setInterpolator(new DecelerateInterpolator(1.8f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator v) {
                float f = (float) v.getAnimatedValue();
                lensCenterX = from + (to - from) * f;
                invalidate();
            }
        });
        anim.start();
        if (notify && listener != null) listener.onTabSelected(index);
    }
}
