package com.deepseek.petoverlay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * 底部导航栏 + 液态玻璃气泡。
 *
 * 交互时升起一个圆形玻璃气泡（直径 ≈ 栏高 × 1.35，允许上下超出栏体），
 * 气泡里把「任务栏自身」放大 1.35 倍重绘一遍 → 放大镜式折射观感，
 * 比采样纹理干净，也不依赖 AGSL。移动时横向拉成椭圆，松手回圆并淡出。
 */
public class GlassBottomBar extends View {

    public interface OnTabSelected {
        void onTabSelected(int index);
    }

    private static final String[] LABELS = {"宠物", "外观", "设置", "关于"};
    private static final int ACCENT = 0xFF11A34C;
    private static final float BAR_H_DP = 56f;
    private static final float MARGIN_X_DP = 18f;
    private static final float MARGIN_BOTTOM_DP = 10f;
    private static final float MAGNIFY = 1.30f;

    /** 折射 + 色散：中心放大，边缘把 RGB 三个通道往不同方向偏 */
    private static final String AGSL =
            "uniform shader content;\n" +
            "uniform float2 center;\n" +
            "uniform float radius;\n" +
            "uniform float mag;\n" +
            "uniform float disp;\n" +
            "half4 main(float2 p) {\n" +
            "  float2 d = p - center;\n" +
            "  float dist = length(d);\n" +
            "  float2 dir = dist > 0.001 ? d / dist : float2(0.0, 0.0);\n" +
            "  float r = clamp(dist / radius, 0.0, 1.0);\n" +
            "  // 平面（X-Y）径向形变：中间收、边缘外扩，线条向圆周弯\n" +
            "  float samp = r * (0.52 + 0.48 * r * r);\n" +
            "  float2 q = center + dir * (samp * radius);\n" +
            "  float e = disp * smoothstep(radius * 0.80, radius, dist);\n" +
            "  half4 cr = content.eval(q - dir * e);\n" +
            "  half4 cg = content.eval(q);\n" +
            "  half4 cb = content.eval(q + dir * e);\n" +
            "  half4 col = half4(cr.r, cg.g, cb.b, half(1.0));\n" +
            "  return col;\n" +
            "}\n";

    private final float density;
    private final RectF barRect = new RectF();
    private final RectF bubbleRect = new RectF();
    private final Path iconPath = new Path();
    private final Path bubblePath = new Path();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int selected = 0;
    private boolean dark = true;
    private float lensCenterX = -1f;
    private float deform = 0f;
    private float bubbleAlpha = 0f;
    private float pressBoost = 0f;

    private boolean dragging = false;
    private int pressedIndex = -1;
    private float downX = 0f, dragStartCenter = 0f, lastX = 0f;
    private long lastT = 0L;
    private ValueAnimator anim, alphaAnim, pressAnim;
    private android.graphics.Bitmap snapshot;   // 任务栏快照（折射采样源）
    private Object runtimeShader;               // RuntimeShader（API 33+）
    private boolean shaderBroken = false;
    private OnTabSelected listener;

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
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(dp(1.8f));
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(dp(11f));
    }

    public void setOnTabSelected(OnTabSelected l) { listener = l; }
    public int getSelected() { return selected; }
    public void setDark(boolean d) { dark = d; invalidate(); }
    public void setTint(int color) { invalidate(); }

    public void setSelectedSilently(int i) {
        selected = Math.max(0, Math.min(LABELS.length - 1, i));
        lensCenterX = centerOf(selected);
        invalidate();
    }

    private float dp(float v) { return v * density; }
    private float slotWidth() { return barRect.width() / LABELS.length; }
    private float centerOf(int i) { return barRect.left + slotWidth() * (i + 0.5f); }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float barH = dp(BAR_H_DP);
        barRect.set(dp(MARGIN_X_DP), h - dp(MARGIN_BOTTOM_DP) - barH,
                w - dp(MARGIN_X_DP), h - dp(MARGIN_BOTTOM_DP));
        if (lensCenterX < 0) lensCenterX = centerOf(selected);
    }

    // ---------------- 绘制 ----------------

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float r = barRect.height() / 2f;

        float center = (barRect.left + barRect.right) / 2f;
        float shift = (lensCenterX - center) * 0.02f;
        float max = dp(4f);
        shift = Math.max(-max, Math.min(max, shift));

        c.save();
        c.translate(shift, 0f);
        drawBar(c, r);
        drawItems(c);
        c.restore();

        // 气泡按未位移坐标绘制，保证与快照纹理 1:1 对齐
        if (bubbleAlpha > 0.01f) drawBubble(c);
    }

    private void drawBar(Canvas c, float r) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(dark ? 0xEE151A20 : 0xEEF2F5F9);
        paint.setShadowLayer(dp(8f), 0, dp(3f), 0x33000000);
        c.drawRoundRect(barRect, r, r, paint);
        paint.clearShadowLayer();

        if (bubbleAlpha < 0.01f) {
            float slot = slotWidth();
            float w = slot - dp(12f), h = barRect.height() - dp(14f);
            RectF sel = new RectF(lensCenterX - w / 2f, barRect.centerY() - h / 2f,
                    lensCenterX + w / 2f, barRect.centerY() + h / 2f);
            paint.setColor(dark ? 0x1FFFFFFF : 0x12000000);
            c.drawRoundRect(sel, h / 2f, h / 2f, paint);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1f));
        paint.setColor(dark ? 0x1AFFFFFF : 0x1A000000);
        c.drawRoundRect(barRect, r, r, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    /** 圆形玻璃气泡：AGSL 分通道采样做折射+色散；不可用时回退到放大重绘 */
    private void drawBubble(Canvas c) {
        float base = barRect.height() * 1.35f;
        float sx = 1f + deform * 0.34f;
        float sy = 1f - deform * 0.24f;
        float rx = base / 2f * sx;
        float ry = base / 2f * sy;
        float cx = lensCenterX;
        float cy = barRect.centerY();
        bubbleRect.set(cx - rx, cy - ry, cx + rx, cy + ry);

        bubblePath.reset();
        bubblePath.addOval(bubbleRect, Path.Direction.CW);

        if (!drawDispersion(c, cx, cy, Math.min(rx, ry))) {
            drawMagnifiedFallback(c, cx, cy);
        }

        // 玻璃色
        paint.setShader(new LinearGradient(0, bubbleRect.top, 0, bubbleRect.bottom,
                ((int) (0x22 * bubbleAlpha) << 24) | 0xFFFFFF,
                ((int) (0x06 * bubbleAlpha) << 24) | 0xFFFFFF,
                Shader.TileMode.CLAMP));
        c.drawPath(bubblePath, paint);

        // 亮边
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.6f));
        paint.setShader(new LinearGradient(0, bubbleRect.top, 0, bubbleRect.bottom,
                ((int) (0xB0 * bubbleAlpha) << 24) | 0xFFFFFF,
                ((int) (0x2A * bubbleAlpha) << 24) | 0xFFFFFF,
                Shader.TileMode.CLAMP));
        c.drawPath(bubblePath, paint);

        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
    }

    /** 快照一次，作为折射纹理 */
    private void captureSnapshot() {
        try {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                    w, h, android.graphics.Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bmp);
            drawBar(cv, barRect.height() / 2f);
            drawItems(cv);
            snapshot = bmp;
        } catch (Throwable t) {
            snapshot = null;
        }
    }

    /** AGSL：按 R/G/B 三个通道分别偏移采样，形成真实色散 */
    private boolean drawDispersion(Canvas c, float cx, float cy, float radius) {
        if (android.os.Build.VERSION.SDK_INT < 33 || shaderBroken) return false;
        if (snapshot == null || snapshot.isRecycled()) return false;
        try {
            Shader sh = buildShader(cx, cy, radius);
            if (sh == null) return false;
            c.save();
            c.clipPath(bubblePath);
            paint.setShader(sh);
            paint.setAlpha((int) (255 * bubbleAlpha));
            c.drawRect(bubbleRect, paint);
            paint.setAlpha(255);
            paint.setShader(null);
            c.restore();
            return true;
        } catch (Throwable t) {
            shaderBroken = true;
            paint.setShader(null);
            return false;
        }
    }

    @SuppressWarnings("NewApi")
    private Shader buildShader(float cx, float cy, float radius) throws Exception {
        Class<?> rsCls = Class.forName("android.graphics.RuntimeShader");
        Object rs = rsCls.getConstructor(String.class).newInstance(AGSL);
        Class<?> shaderCls = Class.forName("android.graphics.Shader");
        android.graphics.BitmapShader bs = new android.graphics.BitmapShader(
                snapshot, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        rsCls.getMethod("setInputShader", String.class, shaderCls).invoke(rs, "content", bs);
        rsCls.getMethod("setFloatUniform", String.class, float[].class)
                .invoke(rs, "center", new float[]{cx, cy});
        rsCls.getMethod("setFloatUniform", String.class, float.class)
                .invoke(rs, "radius", radius);
        rsCls.getMethod("setFloatUniform", String.class, float.class)
                .invoke(rs, "mag", MAGNIFY);
        rsCls.getMethod("setFloatUniform", String.class, float.class)
                .invoke(rs, "disp", dp(0.8f));
        runtimeShader = rs;
        return (Shader) rs;
    }

    /** 降级：放大重绘（保证不崩） */
    private void drawMagnifiedFallback(Canvas c, float cx, float cy) {
        c.save();
        c.clipPath(bubblePath);
        c.scale(MAGNIFY, MAGNIFY, cx, cy);
        drawBar(c, barRect.height() / 2f);
        drawItems(c);
        c.restore();
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
            c.save();
            c.scale(1f + boost, 1f + boost, ccx, barRect.centerY() - dp(6f));
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
                captureSnapshot();
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

    /** 调试用：立刻显示气泡 ms 毫秒（自动截图验证效果） */
    public void previewBubble(long ms) {
        captureSnapshot();
        bubbleAlpha = 1f;
        invalidate();
        hideBubble(ms);
    }

    private void showBubble() {
        if (alphaAnim != null) alphaAnim.cancel();
        alphaAnim = ValueAnimator.ofFloat(bubbleAlpha, 1f);
        alphaAnim.setDuration(160);
        alphaAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator v) {
                bubbleAlpha = (float) v.getAnimatedValue();
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
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                lensCenterX = to;
                invalidate();
            }
        });
        anim.start();
        if (notify && listener != null) listener.onTabSelected(index);
    }
}
