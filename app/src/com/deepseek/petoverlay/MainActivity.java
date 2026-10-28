package com.deepseek.petoverlay;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String QQ_GROUP = "1129465403";
    private static final String TELEGRAM = "https://t.me/sbh121003";
    private static final String GITHUB = "https://github.com/h121003/lanjing-overlay";

    private final LinearLayout[] pages = new LinearLayout[4];
    private FrameLayout content;
    private GlassBottomBar bar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F1114);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        pages[0] = buildPetPage();
        pages[1] = buildLookPage();
        pages[2] = buildSettingsPage();
        pages[3] = buildAboutPage();
        for (LinearLayout p : pages) {
            content.addView(p, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }
        show(0);

        bar = new GlassBottomBar(this);
        bar.setOnTabSelected(new GlassBottomBar.OnTabSelected() {
            @Override public void onTabSelected(int index) { show(index); }
        });
        root.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(84)));

        setContentView(root);
        applyInsets(root);
    }

    /** Android 15 强制 edge-to-edge：给根布局补系统栏内边距 */
    private void applyInsets(final View root) {
        if (Build.VERSION.SDK_INT < 30) return;
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets insets) {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            }
        });
    }

    private void show(int index) {
        for (int i = 0; i < pages.length; i++) {
            pages[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
        }
        if (bar != null && bar.getSelected() != index) bar.setSelectedSilently(index);
    }

    // ---------------- 宠物页 ----------------

    private LinearLayout buildPetPage() {
        LinearLayout ll = page();

        TextView title = new TextView(this);
        title.setText("蓝鱼悬浮窗");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFFFFFFFF);
        ll.addView(title);
        ll.addView(hint("① 授予悬浮窗权限\n② 启动悬浮宠物\n③ 回到桌面，可拖动、点击切换动作"));

        Button perm = primary("授予悬浮窗权限");
        perm.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        ll.addView(perm);

        Button start = primary("启动悬浮宠物");
        start.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!Settings.canDrawOverlays(MainActivity.this)) {
                    toast("还没有悬浮窗权限，请先授予");
                    return;
                }
                requestNotificationPermissionIfNeeded();
                startService(new Intent(MainActivity.this, OverlayService.class));
                toast("已启动，回桌面看看");
            }
        });
        ll.addView(start);

        Button stop = primary("停止");
        stop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                stopService(new Intent(MainActivity.this, OverlayService.class));
                toast("已停止");
            }
        });
        ll.addView(stop);

        return ll;
    }

    // ---------------- 外观页 ----------------

    private LinearLayout buildLookPage() {
        LinearLayout ll = page();
        ll.addView(sectionTitle("外观"));
        ll.addView(hint("调整悬浮窗大小。位置直接在桌面上拖动宠物即可。"));

        Button bigger = primary("调大一点");
        bigger.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!Settings.canDrawOverlays(MainActivity.this)) { toast("请先授予悬浮窗权限"); return; }
                OverlayService.resize(80);
            }
        });
        ll.addView(bigger);

        Button smaller = primary("调小一点");
        smaller.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!Settings.canDrawOverlays(MainActivity.this)) { toast("请先授予悬浮窗权限"); return; }
                OverlayService.resize(-80);
            }
        });
        ll.addView(smaller);

        return ll;
    }

    // ---------------- 设置页 ----------------

    private LinearLayout buildSettingsPage() {
        LinearLayout ll = page();
        ll.addView(sectionTitle("设置"));
        ll.addView(hint("悬浮窗权限：" + (Settings.canDrawOverlays(this) ? "已授予" : "未授予")));
        ll.addView(hint("通知权限：" + (hasNotificationPermission() ? "已授予" : "未授予（不影响悬浮窗）")));

        Button notify = primary("申请通知权限");
        notify.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { requestNotificationPermissionIfNeeded(); }
        });
        ll.addView(notify);

        Button stop = primary("停止悬浮宠物");
        stop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                stopService(new Intent(MainActivity.this, OverlayService.class));
                toast("已停止");
            }
        });
        ll.addView(stop);

        return ll;
    }

    // ---------------- 关于页 ----------------

    private LinearLayout buildAboutPage() {
        LinearLayout ll = page();
        ll.addView(sectionTitle("关于"));
        ll.addView(hint("蓝鲸悬浮窗 · 版本 " + versionName()
                + "\n常驻桌面的 WebView 悬浮窗，循环播放 WebM 透明动画\n开源协议：MIT"));

        ll.addView(caption("QQ 交流群 · " + QQ_GROUP));
        Button qq = primary("打开 QQ 群");
        qq.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openQqGroup(); }
        });
        ll.addView(qq);
        Button qqCopy = primary("复制群号");
        qqCopy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { copy(QQ_GROUP); toast("群号已复制"); }
        });
        ll.addView(qqCopy);

        ll.addView(caption("Telegram · @SBH121003"));
        ll.addView(qr(R.drawable.tg_qr));
        Button tg = primary("打开 Telegram");
        tg.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openUrl(TELEGRAM); }
        });
        ll.addView(tg);

        ll.addView(caption("支持开发（微信赞赏码）"));
        ll.addView(qr(R.drawable.wx_qr));

        ll.addView(caption("GitHub 仓库"));
        Button gh = primary("查看源码与反馈问题");
        gh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openUrl(GITHUB); }
        });
        ll.addView(gh);

        return ll;
    }

    // ---------------- 小工具 ----------------

    private LinearLayout page() {
        ScrollView sc = new ScrollView(this);
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(22), dp(26), dp(22), dp(16));
        sc.addView(inner);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(sc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        return wrap;
    }

    private TextView sectionTitle(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(24);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(0xFFFFFFFF);
        v.setPadding(0, 0, 0, dp(6));
        return v;
    }

    private TextView hint(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(14);
        v.setTextColor(0xFFB9C4CF);
        v.setLineSpacing(dp(4), 1f);
        v.setPadding(0, dp(4), 0, dp(10));
        return v;
    }

    private TextView caption(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(13);
        v.setTextColor(0xFF8FA0B0);
        v.setPadding(0, dp(16), 0, dp(6));
        return v;
    }

    private Button primary(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setBackgroundColor(0xFF1B2027);
        b.setTextColor(0xFFE8EEF4);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        lp.bottomMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private ImageView qr(int res) {
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(BitmapFactory.decodeResource(getResources(), res));
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(200));
        lp.topMargin = dp(6);
        lp.bottomMargin = dp(6);
        iv.setLayoutParams(lp);
        return iv;
    }

    private void openQqGroup() {
        String scheme = "mqqapi://card/show_pslcard?src_type=internal&version=1&uin="
                + QQ_GROUP + "&card_type=group&source=qrcode";
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(scheme));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            copy(QQ_GROUP);
            toast("未找到 QQ，群号已复制");
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast("没有可打开该链接的应用");
        }
    }

    private void copy(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("text", text));
        } catch (Exception ignored) {
        }
    }

    private void toast(String t) {
        Toast.makeText(this, t, Toast.LENGTH_SHORT).show();
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return true;
        return checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (hasNotificationPermission()) return;
        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1001);
    }

    private String versionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName == null ? "1.3" : info.versionName;
        } catch (Exception e) {
            return "1.3";
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
