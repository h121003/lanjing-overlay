package com.deepseek.petoverlay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * 底部导航栏 + 液态玻璃气泡。
 *
 * 交互时从选中项升起一个圆形玻璃气泡（直径 ≈ 栏高 × 1.35，允许超出栏体），
 * 气泡内对「任务栏自身的截屏」做折射扭曲（Android 13+ 用 AGSL 片元着色器，
 * 低版本降级为放大采样），移动时横向拉成椭圆，松手回圆并淡出。
 */
public class GlassBottomBar extends View {

    public interface OnTabSelected {
        void onTabSelected(int index);
    }

    private static final String[] LABELS = {"宠物", "外观", "设置", "关于"};
    private static final int ACCENT = 0xFF11A34C;

    /** 气泡内壁的折射：中心不动，靠近边缘处把背景往中心「吸」 */
    private static final String LENS_AGSL =
            "uniform shader content;\n" +
            "uniform float2 center;\n" +
            "uniform float radius;\n" +
            "uniform float strength;\n" +
            "half4 main(float2 p) {\n" +
            "  float2 d = p - center;\n" +
            "  float dist = length(d);\n" +
            "  if (dist < 0.001 || dist > radius) { return content.eval(p); }\n" +
            "  float t = 1.0 - dist / radius;\n" +
            "  float2 dir = d / dist;\n" +
            "  float2 offset = dir * (t * t) * strength;\n" +
            "  return content.eval(p - offset);\n" +
            "}\n";

    private final float density;
    private final RectF barRect = new RectF();
    private final RectF bubbleRect = new RectF();
    private final Path iconPath = new Path();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int selected = 0;
    private int tintColor = 0xFF22262C;
    private boolean dark = true;
    private boolean active = false;

    private float lensCenterX = -1f;
    private float deform = 0f;      // 0..1，速度驱动
    private float bubbleAlpha = 0f; // 0..1，淡入淡出
    private float pressBoost = 0f;

    private Bitmap backdrop;        // 壁纸缩略图
    private int rootW = 1, rootH = 1;

    private Bitmap selfTexture;     // 任务栏自身快照，供折射采样
    private Object runtimeShader;   // android.graphics.RuntimeShader（反射持有）
    private boolean shaderBroken = false;

    private boolean dragging = false;
    private int pressedIndex = -1;
    private float downX = 0f, dragStartCenter = 0f, lastX = 0f;
    private long lastT = 0L;
    private ValueAnimator anim, alphaAnim, pressAnim;

    private final Runnable decay = new Runnable() {
        @Override public void run() {
            deform *= 0.87f;
            if (deform < 0.01f) deform = 0f; else postDelayed(this, 16);
            invalidate();
        }
    };

    public GlassBottomBar(Context c) { this(c, null); }

    public GlassBottomBar(Context c, AttributeSet a) {
        super(c, a);
        density = getResources().getDisplayMetrics().density;
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(dp(1.8f));
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(dp(11f));
    }

    public void setOnTabSelected(OnTabSelected l) { listener = l; }
    private OnTabSelected listener;

    public int getSelected() { return selected; }

    public void setSelectedSilently(int i) {
        selected = Math.max(0, Math.min(LABELS.length - 1, i));
        lensCenterX = centerOf(selected);
        invalidate();
    }

    public void setDark(boolean d) { dark = d; invalidate(); }

    public void setTint(int color) { tintColor = color; invalidate(); }

    public void setBackdrop(Bitmap bmp, int w, int h) {
        backdrop = bmp;
        rootW = Math.max(1, w);
        rootH = Math.max(1, h);
        invalidate();
    }

    private float dp(float v) { return v * density; }
    private float slotWidth() { return barRect.width() / LABELS.length; }
    private float centerOf(int i) { return barRect.left + slotWidth() * (i + 0.5f); }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        barRect.set(dp(18f), dp(10f), w - dp(18f), h - dp(10f));
        if (lensCenterX < 0) lensCenterX = centerOf(selected);
    }

    // ---------------- 绘制 ----------------

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float r = barRect.height() / 2f;

        // 整条栏微动（幅度很小）
        float center = (barRect.left + barRect.right) / 2f;
        float shift = (lensCenterX - center) * 0.02f;
        float max = dp(4f);
        shift = Math.max(-max, Math.min(max, shift));

        c.save();
        c.translate(shift, 0f);

        if (!active || bubbleAlpha < 0.02f) {
            drawBarIdle(c, r);
        } else {
            drawBarActive(c, r);
            drawBubble(c);
        }
        drawItems(c);
        c.restore();
    }

    private void drawBarIdle(Canvas c, float r) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(dark ? 0xEE151A20 : 0xEEF2F5F9);
        paint.setShadowLayer(dp(8f), 0, dp(3f), 0x33000000);
        c.drawRoundRect(barRect, r, r, paint);
        paint.clearShadowLayer();

        float slot = slotWidth();
        float w = slot - dp(12f), h = barRect.height() - dp(14f);
        RectF sel = new RectF(lensCenterX - w / 2f, barRect.centerY() - h / 2f,
                lensCenterX + w / 2f, barRect.centerY() + h / 2f);
        paint.setColor(dark ? 0x1FFFFFFF : 0x12000000);
        c.drawRoundRect(sel, h / 2f, h / 2f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1f));
        paint.setColor(dark ? 0x1AFFFFFF : 0x1A000000);
        c.drawRoundRect(barRect, r, r, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawBarActive(Canvas c, float r) {
        int base = dark ? 0xCC101418 : 0xCCE9EEF4;
        paint.setShader(null);
        paint.setColor(base);
        c.drawRoundRect(barRect, r, r, paint);

        paint.setShader(new LinearGradient(0, barRect.top, 0, barRect.bottom,
                new int[]{0x22FFFFFF, 0x0CFFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(barRect, r, r, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1f));
        paint.setColor(dark ? 0x33FFFFFF : 0x22000000);
        c.drawRoundRect(barRect, r, r, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    /** 圆形玻璃气泡：折射 + 亮边 + 椭圆形变 */
    private void drawBubble(Canvas c) {
        float baseSize = barRect.height() * 1.35f;
        float sx = 1f + deform * 0.30f;
        float sy = 1f - deform * 0.22f;
        float rx = baseSize / 2f * sx;
        float ry = baseSize / 2f * sy;
        float cx = lensCenterX;
        float cy = barRect.centerY();

        bubbleRect.set(cx - rx, cy - ry, cx + rx, cy + ry);

        int alpha = (int) (255 * bubbleAlpha);

        // 1) 折射内容
        if (!drawRefraction(c, cx, cy, rx, ry, alpha)) {
            drawMagnifiedBackdrop(c, cx, cy, rx, ry, alpha);
        }

        // 2) 玻璃色 + 顶部高光
        paint.setShader(new LinearGradient(0, bubbleRect.top, 0, bubbleRect.bottom,
                (int) (0x33 * bubbleAlpha / 1f) << 24 | 0xFFFFFF,
                (int) (0x0A * bubbleAlpha / 1f) << 24 | 0xFFFFFF,
                Shader.TileMode.CLAMP));
        c.drawOval(bubbleRect, paint);
        paint.setShader(null);

        // 3) 亮边
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.6f));
        paint.setShader(new LinearGradient(0, bubbleRect.top, 0, bubbleRect.bottom,
                (int) (0xBB * bubbleAlpha) << 24 | 0xFFFFFF,
                (int) (0x33 * bubbleAlpha) << 24 | 0xFFFFFF,
                Shader.TileMode.CLAMP));
        c.drawOval(bubbleRect, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
    }

    /** 用 AGSL 做真实折射（API 33+），失败返回 false 走降级 */
    private boolean drawRefraction(Canvas c, float cx, float cy, float rx, float ry, int alpha) {
        if (Build.VERSION.SDK_INT < 33 || shaderBroken) return false;
        if (selfTexture == null || selfTexture.isRecycled()) return false;
        try {
            Shader shader = buildAgsShader(cx, cy, Math.min(rx, ry));
            if (shader == null) return false;
            paint.setShader(shader);
            paint.setAlpha(alpha);
            c.drawOval(bubbleRect, paint);
            paint.setAlpha(255);
            paint.setShader(null);
            return true;
        } catch (Throwable t) {
            shaderBroken = true;
            paint.setShader(null);
            return false;
        }
    }

    @SuppressWarnings({"NewApi", "unchecked"})
    private Shader buildAgsShader(float cx, float cy, float radius) throws Exception {
        Class<?> cls = Class.forName("android.graphics.RuntimeShader");
        Object rs = cls.getConstructor(String.class).newInstance(LENS_AGSL);
        Class<?> shaderCls = Class.forName("android.graphics.Shader");
        java.lang.reflect.Method setInput = cls.getMethod("setInputShader",
                String.class, shaderCls);
        BitmapShader bs = new BitmapShader(selfTexture, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        setInput.invoke(rs, "content", bs);
        cls.getMethod("setFloatUniform", String.class, float[].class)
                .invoke(rs, "center", new float[]{cx, cy});
        cls.getMethod("setFloatUniform", String.class, float.class)
                .invoke(rs, "radius", radius);
        cls.getMethod("setFloatUniform", String.class, float.class)
                .invoke(rs, "strength", radius * 0.42f);
        runtimeShader = rs;
        return (Shader) rs;
    }

    /** 降级：把壁纸/快照放大 1.34 倍画进气泡 */
    private void drawMagnifiedBackdrop(Canvas c, float cx, float cy, float rx, float ry, int alpha) {
        Bitmap src = selfTexture != null ? selfTexture : backdrop;
        if (src == null) return;
        float k = 1.34f;
        Rect dst = new Rect((int) (cx - rx), (int) (cy - ry), (int) (cx + rx), (int) (cy + ry));
        Rect out = new Rect();
        int w = (int) (rx * 2 / k), h = (int) (ry * 2 / k);
        out.set((int) cx - w / 2, (int) cy - h / 2, (int) cx + w / 2, (int) cy + h / 2);
        paint.setShader(null);
        paint.setAlpha(alpha);
        c.drawBitmap(src, Region(out), dst, paint);
        paint.setAlpha(255);
    }

    /** 抓一份任务栏快照，作为折射的采样源 */
    private void captureSelf() {
        try {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bmp);
            drawBarIdle(cv, barRect.height() / 2f);
            drawItems(cv);
            selfTexture = bmp;
        } catch (Throwable ignored) {
        }
    }

    private void drawItems(Canvas c) {
        float slot = slotWidth();
        for (int i = 0; i < LABELS.length; i++) {
            float ccx = barRect.left + slot * (i + 0.5f);
            boolean on = i == selected;
            int color = on ? ACCENT : (dark ? 0xEEFFFFFF : 0xCC111820);
            iconPaint.setColor(color);
            labelPaint.setColor(color);

            float boost = (on ? 0.06f : 0f) + (i == pressedIndex ? pressBoost * 0.10f : 0f);
            float isx = 1f + boost;
            float isy = 1f + boost;
            c.save();
            c.scale(isx, isy, ccx, barRect.centerY() - dp(6f));
            drawIcon(c, i, ccx, barRect.centerY() - dp(6f), dp(8.5f));
            c.restore();

            c.save();
            c.scale(1f + boost * 0.5f, 1f + boost * 0.5f, ccx, barRect.centerY() + dp(17f));
            c.drawText(LABELS[i], ccx, barRect.centerY() + dp(17f), labelPaint);
            c.restore();
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

    // ---------------- 交互 ----------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (anim != null) anim.cancel();
                pressedIndex = (int) ((e.getX() - barRect.left) / slotWidth());
                pressedIndex = Math.max(0, Math.min(LABELS.length - 1, pressedIndex));
                dragging = true;
                downX = e.getX();
                lastX = downX;
                lastT = System.currentTimeMillis();
                dragStartCenter = lensCenterX;
                captureSelf();
                showBubble();
                animatePress(1f, 110);
                try { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); } catch (Throwable ignored) {}
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!dragging) return true;
                long now = System.currentTimeMillis();
                float dt = Math.max(1f, now - lastT);
                float v = Math.abs(e.getX() - lastX) / dt;
                lastX = e.getX();
                lastT = now;
                deform = Math.min(0.9f, deform * 0.5f + v * 0.7f);

                float slot = slotWidth();
                float minC = barRect.left + slot / 2f;
                float maxC = barRect.right - slot / 2f;
                lensCenterX = Math.max(minC, Math.min(maxC, dragStartCenter + (e.getX() - downX)));
                removeCallbacks(decay);
                postDelayed(decay, 40);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (!dragging) return true;
                dragging = false;
                float moved = Math.abs(e.getX() - downX);
                boolean tap = e.getActionMasked() == MotionEvent.ACTION_UP && moved < density * 8f;
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
                postDelayed(decay, 80);
                hideBubble(520);
                return true;
            }
        }
        return true;
    }

    private void showBubble() {
        if (alphaAnim != null) alphaAnim.cancel();
        alphaAnim = ValueAnimator.ofFloat(bubbleAlpha, 1f);
        alphaAnim.setDuration(160);
        alphaAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator v) {
                bubbleAlpha = (float) v.getAnimatedValue();
                active = bubbleAlpha > 0.01f;
                invalidate();
            }
        });
        alphaAnim.start();
    }

    private void hideBubble(long delay) {
        postDelayed(new Runnable() {
            @Override public void run() {
                if (alphaAnim != null) alphaAnim.cancel();
                alphaAnim = ValueAnimator.ofFloat(bubbleAlpha, 0f);
                alphaAnim.setDuration(220);
                alphaAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override public void onAnimationUpdate(ValueAnimator v) {
                        bubbleAlpha = (float) v.getAnimatedValue();
                        active = bubbleAlpha > 0.01f;
                        invalidate();
                    }
                });
                alphaAnim.start();
            }
        }, delay);
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
        anim.setDuration(300);
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

    /** 简易矩形区域（避免引入 android.graphics.Region 名冲突） */
    private static class Region extends android.graphics.Rect {
        Region(Rect r) { super(r); }
    }
}
