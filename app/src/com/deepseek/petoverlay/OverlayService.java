package com.deepseek.petoverlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.widget.Toast;
import android.webkit.WebView;

public class OverlayService extends Service {
    /** 发布版关闭日志：避免把窗口坐标与点击时长写进 logcat。 */
    private static final boolean DEBUG = false;

    private static OverlayService instance;

    private WindowManager wm;
    private WebView webView;
    private WindowManager.LayoutParams params;
    private int initialX, initialY;
    private float initialTouchX, initialTouchY;
    private long downTime;
    private int size = 460;

    public static void resize(int delta) {
        if (instance != null && instance.webView != null) {
            instance.size = Math.max(180, instance.size + delta);
            instance.params.width = instance.size;
            instance.params.height = instance.size;
            instance.wm.updateViewLayout(instance.webView, instance.params);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        createChannel();
        Notification n = buildNotification();
        startForeground(1, n);
        addOverlay();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    "pet", "蓝鱼悬浮窗", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("悬浮宠物运行中");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, "pet");
        } else {
            b = new Notification.Builder(this);
        }
        return b.setContentTitle("蓝鱼悬浮窗")
                .setContentText("宠物正在桌面上")
                .setSmallIcon(android.R.drawable.ic_menu_gallery)
                .setOngoing(true)
                .build();
    }

    private void addOverlay() {
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                size, size, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 200;
        params.y = 700;

        webView = new WebView(this);
        webView.setBackgroundColor(Color.TRANSPARENT);
        // hardware layer removed: it blocked DOM repaint for the bubble

        WebSettings s = webView.getSettings();
        // 只加载本地 file:///android_asset，收紧其余能力：
        // 无 INTERNET 权限、无 addJavascriptInterface，远程内容进不来
        s.setJavaScriptEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);                    // 必需：加载 android_asset
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setDomStorageEnabled(false);
        s.setSaveFormData(false);
        // 防止被其他悬浮窗盖住时偷走点击（tapjacking）
        webView.setFilterTouchesWhenObscured(true);

        webView.loadUrl("file:///android_asset/index.html");

        webView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        if (DEBUG) Log.d("PET", "DOWN raw=" + event.getRawX() + "," + event.getRawY());
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        downTime = System.currentTimeMillis();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        wm.updateViewLayout(webView, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        long dur = System.currentTimeMillis() - downTime;
                        float dx = Math.abs(event.getRawX() - initialTouchX);
                        float dy = Math.abs(event.getRawY() - initialTouchY);
                        if (DEBUG) Log.d("PET", "UP dur=" + dur + " dx=" + dx + " dy=" + dy);
                        if (dur < 300 && dx < 20 && dy < 20) {
                            if (DEBUG) Log.d("PET", "TAP -> nextAnim");
                            webView.evaluateJavascript("nextAnim()", new android.webkit.ValueCallback<String>() {
                                public void onReceiveValue(String value) {
                                    if (DEBUG) Log.d("PET", "js=" + value);
                                }
                            });
                        }
                        return true;
                }
                return false;
            }
        });

        try {
            wm.addView(webView, params);
        } catch (Throwable t) {
            // 权限被撤销 / 窗口服务异常时优雅退出，不让服务崩掉
            Log.w("PET", "addView failed, stop self", t);
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (webView != null && wm != null) {
            try {
                wm.removeView(webView);
            } catch (Exception ignored) {
            }
        }
        instance = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
