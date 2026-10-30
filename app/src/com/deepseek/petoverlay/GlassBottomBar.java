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
    private static final float MARGIN_BOTTOM_DP = 22f;
    private static final float MAGNIFY = 1.30f;

    /** 折射 + 色散：中心放大，边缘把 RGB 三个通道往不同方向偏 */
    private static final String AGSL =
            "uniform shader content;\n" +
            "uniform float2 center;\n" +
            "uniform float radius;\n" +
            "uniform float disp;\n" +
            "half4 main(float2 p) {\n" +
            "  float2 d = p - center;\n" +
            "  float dist = length(d);\n" +
            "  float2 dir = dist > 0.001 ? d / dist : float2(0.0, 0.0);\n" +
            "  float2 tang = float2(-dir.y, dir.x);\n" +
            "  float r = clamp(dist / radius, 0.0, 1.0);\n" +
            "  float b = smoothstep(0.45, 1.0, r);\n" +
            "  // 只有在左右两侧才做弯曲与模糊，上下保持清晰\n" +
            "  float side = smoothstep(0.45, 1.0, abs(dir.x));\n" +
            "  float edge = b * side;\n" +
            "  float mag = 1.18 + 0.40 * b;\n" +
            "  float2 q = center + d / mag;\n" +
            "  q += tang * (edge * edge * radius * 0.14);\n" +
            "  half4 c = content.eval(q);\n" +
            "  // 左右两侧的伪模糊\n" +
            "  float k = edge * 4.0;\n" +
            "  half4 s = content.eval(q + float2(k, 0.0)) + content.eval(q - float2(k, 0.0))\n" +
            "         + content.eval(q + float2(0.0, k)) + content.eval(q - float2(0.0, k));\n" +
            "  c = mix(c, (c + s) * 0.2, edge);\n" +
            "  float e = disp * edge;\n" +
            "  half4 cb = content.eval(q + dir * e);\n" +
            "  return half4(c.r, c.g, cb.b, half(1.0));\n" +
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
    private Runnable pendingHide;
    private android.graphics.Bitmap snapshot;   // 任务栏快照（折射采样源）
    private Object runtimeShader;               // 复用 RuntimeShader（API 33+）
    private android.graphics.BitmapShader shaderInput;
    private android.graphics.Bitmap shaderBoundTo;
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
        // 动画进行中不要强行对齐，否则滑动动画会被立刻"钉"到终点（看上去像没动）
        if (anim == null || !anim.isRunning()) {
            lensCenterX = centerOf(selected);
        }
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
        if (bubbleAlpha > 0.01f) drawBubble(c);
        c.restore();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (snapshot != null && !snapshot.isRecycled()) snapshot.recycle();
        snapshot = null;
        shaderBoundTo = null;
        shaderInput = null;
        runtimeShader = null;
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
        paint.setStrokeWidth(dp(1.2f));
        paint.setColor(dark ? 0x3DFFFFFF : 0x30000000);
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

        // 显式补一条「被放大的栏体边线」：按与着色器一致的映射逐点算出来，
        // 这样线会像内容一样在圆边处向上/向下弯
        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(dp(1.6f));
        edge.setColor(dark ? 0x66FFFFFF : 0x55000000);
        c.save();
        c.clipPath(bubblePath);
        float away = deform * dp(7f);   // 压扁时把线往外推，避免压到文字
        drawEdgeLine(c, cx, cy, Math.min(rx, ry), barRect.top + dp(5f) - away, true,
                dark ? 0x66FFFFFF : 0x55000000);
        drawEdgeLine(c, cx, cy, Math.min(rx, ry), barRect.bottom - dp(5f) + away, false,
                dark ? 0x55FFFFFF : 0x44000000);
        c.restore();

        // 玻璃色：统一一层很淡的白，不做上下渐变（避免像球）
        paint.setShader(null);
        paint.setColor(((int) (0x0A * bubbleAlpha) << 24) | 0xFFFFFF);
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

    /** 把栏体的某一条水平边，按着色器同样的映射画成弯线 */
    private void drawEdgeLine(Canvas c, float cx, float cy, float radius,
                              float edgeY, boolean top, int color) {
        float shiftY = cy - barRect.centerY();      // 跟随气泡上下
        float half = radius;
        int steps = 60;
        Path sharp = new Path();
        Path soft = new Path();
        boolean sharpStarted = false, softStarted = false;
        for (int i = 0; i <= steps; i++) {
            float x = barRect.left + (barRect.right - barRect.left) * i / (float) steps;
            float dx = x - cx;
            float t = Math.min(1f, Math.abs(dx) / half);
            float bow = smoothstep(0.45f, 1f, t);
            bow = bow * bow * radius * 0.28f * (top ? -1f : 1f);
            float y = edgeY + shiftY + bow;
            if (t < 0.72f) {
                if (!sharpStarted) { sharp.moveTo(x, y); sharpStarted = true; }
                else sharp.lineTo(x, y);
            }
            if (t > 0.55f) {
                if (!softStarted) { soft.moveTo(x, y); softStarted = true; }
                else soft.lineTo(x, y);
            }
        }
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.6f));
        paint.setColor(color);
        if (sharpStarted) c.drawPath(sharp, paint);
        // 弯的那一段更宽更淡 = 模糊
        paint.setStrokeWidth(dp(4.5f));
        paint.setColor((int) (0x30 * bubbleAlpha) << 24 | 0xFFFFFF);
        if (softStarted) c.drawPath(soft, paint);
    }

    private static float smoothstep(float a, float b, float x) {
        float t = Math.max(0f, Math.min(1f, (x - a) / (b - a)));
        return t * t * (3f - 2f * t);
    }

    /** 快照一次，作为折射纹理 */
    private void captureSnapshot() {
        try {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            if (snapshot == null || snapshot.isRecycled()
                    || snapshot.getWidth() != w || snapshot.getHeight() != h) {
                if (snapshot != null && !snapshot.isRecycled()) snapshot.recycle();
                snapshot = null;
                shaderBoundTo = null;
                snapshot = android.graphics.Bitmap.createBitmap(
                        w, h, android.graphics.Bitmap.Config.ARGB_8888);
            }
            Canvas cv = new Canvas(snapshot);
            // 整张快照先铺底色：气泡顶部会采样到栏体上方，铺底色避免出现空洞
            Paint bg = new Paint();
            bg.setColor(dark ? 0xFF151A20 : 0xFFF2F5F9);
            cv.drawRect(0, 0, w, h, bg);
            Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(dp(1.2f));
            line.setColor(dark ? 0x3DFFFFFF : 0x30000000);
            cv.drawRoundRect(barRect, barRect.height() / 2f, barRect.height() / 2f, line);
            drawItems(cv);
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
        Class<?> shaderCls = Class.forName("android.graphics.Shader");
        if (runtimeShader == null) {
            runtimeShader = rsCls.getConstructor(String.class).newInstance(AGSL);
        }
        Object rs = runtimeShader;
        // 纹理只在快照换了才重新绑定，避免每帧重建 BitmapShader
        if (shaderBoundTo != snapshot) {
            shaderInput = new android.graphics.BitmapShader(
                    snapshot, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            rsCls.getMethod("setInputShader", String.class, shaderCls)
                    .invoke(rs, "content", shaderInput);
            shaderBoundTo = snapshot;
        }
        rsCls.getMethod("setFloatUniform", String.class, float[].class)
                .invoke(rs, "center", new float[]{cx, cy});
        rsCls.getMethod("setFloatUniform", String.class, float.class)
                .invoke(rs, "radius", radius);
        rsCls.getMethod("setFloatUniform", String.class, float.class)
                .invoke(rs, "disp", dp(0.45f));
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
                // 取消上一次松手后安排的淡出，否则长按拖动时气泡会被它掐掉
                if (pendingHide != null) {
                    removeCallbacks(pendingHide);
                    pendingHide = null;
                }
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
                // 基础形变 0.22：慢速拖动也有压扁，快速再叠加
                deform = Math.min(0.9f, Math.max(0.22f, deform * 0.5f + v * 0.7f));

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
                hideBubble(900);
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
        if (pendingHide != null) removeCallbacks(pendingHide);
        pendingHide = new Runnable() {
            @Override public void run() {
                pendingHide = null;
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
        };
        postDelayed(pendingHide, delay);
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
