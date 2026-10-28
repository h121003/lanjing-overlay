package com.deepseek.petoverlay;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
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
    private static final int REQ_WALLPAPER = 2001;
    private static final String PREFS = "lanjing";
    private static final String KEY_WALLPAPER = "wallpaper_uri";

    private static final String KEY_THEME = "theme"; // system / light / dark

    private boolean dark;
    private int cBg, cText, cSub, cCard, cCardText;

    private final View[] pages = new View[4];
    private FrameLayout content;
    private ImageView wallpaper;
    private GlassBottomBar bar;
    private FrameLayout rootView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        dark = resolveDark();
        if (dark) {
            cBg = 0xFF0F1114; cText = 0xFFFFFFFF; cSub = 0xFFD3DCE6;
            cCard = 0xCC1B2027; cCardText = 0xFFE8EEF4;
        } else {
            cBg = 0xFFF4F6F9; cText = 0xFF11161C; cSub = 0xFF4A5560;
            cCard = 0xFFE3E9F0; cCardText = 0xFF131A21;
        }

        FrameLayout root = new FrameLayout(this);
        rootView = root;

        wallpaper = new ImageView(this);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallpaper.setBackgroundColor(cBg);
        root.addView(wallpaper, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        applyWallpaper();

        // 壁纸上的暗色蒙版，保证文字可读
        View scrim = new View(this);
        scrim.setBackgroundColor(dark ? 0xB3000000 : 0x66FFFFFF);
        root.addView(scrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        content = new FrameLayout(this);
        column.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        pages[0] = buildPetPage();
        pages[1] = buildLookPage();
        pages[2] = buildSettingsPage();
        pages[3] = buildAboutPage();
        for (View p : pages) {
            content.addView(p, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }
        show(0);

        bar = new GlassBottomBar(this);
        bar.setDark(dark);
        bar.setTint(dark ? 0xFF22262C : 0xFFE7ECF2);
        bar.setOnTabSelected(new GlassBottomBar.OnTabSelected() {
            @Override public void onTabSelected(int index) { show(index); }
        });
        column.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)));

        setContentView(root);
        applyInsets(root);
    }

    private void applyInsets(final View root) {
        if (Build.VERSION.SDK_INT < 30) return;
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets insets) {
                android.graphics.Insets b = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                v.setPadding(b.left, b.top, b.right, b.bottom);
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

    private View buildPetPage() {
        LinearLayout ll = pageInner();
        ll.addView(sectionTitle("蓝鱼悬浮窗"));
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
        return wrap(ll);
    }

    // ---------------- 外观页 ----------------

    private View buildLookPage() {
        LinearLayout ll = pageInner();
        ll.addView(sectionTitle("外观"));
        ll.addView(hint("换一张自己的背景图，或调整悬浮窗大小。"));

        Button pick = primary("选择壁纸");
        pick.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                try {
                    startActivityForResult(i, REQ_WALLPAPER);
                } catch (Exception e) {
                    toast("没有可用的图片选择器");
                }
            }
        });
        ll.addView(pick);

        Button clear = primary("清除壁纸");
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs().edit().remove(KEY_WALLPAPER).apply();
                wallpaper.setImageDrawable(null);
                wallpaper.setBackgroundColor(cBg);
                toast("已恢复默认背景");
            }
        });
        ll.addView(clear);

        ll.addView(hint("悬浮窗大小（需要先启动悬浮宠物）"));
        Button bigger = primary("调大一点");
        bigger.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { OverlayService.resize(80); }
        });
        ll.addView(bigger);

        Button smaller = primary("调小一点");
        smaller.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { OverlayService.resize(-80); }
        });
        ll.addView(smaller);

        Button reset = primary("恢复默认大小");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!OverlayService.isRunning()) { toast("悬浮宠物还没启动"); return; }
                OverlayService.resetSize();
                toast("已恢复默认大小");
            }
        });
        ll.addView(reset);
        return wrap(ll);
    }

    // ---------------- 设置页 ----------------

    private View buildSettingsPage() {
        LinearLayout ll = pageInner();
        ll.addView(sectionTitle("设置"));
        ll.addView(caption("主题"));
        String cur = prefs().getString(KEY_THEME, "system");
        ll.addView(themeButton("跟随系统", "system", cur));
        ll.addView(themeButton("浅色（白色）", "light", cur));
        ll.addView(themeButton("深色（黑色）", "dark", cur));
        ll.addView(gap(dp(6)));
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
        return wrap(ll);
    }

    // ---------------- 关于页 ----------------

    private View buildAboutPage() {
        LinearLayout ll = pageInner();
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
        return wrap(ll);
    }

    // ---------------- 壁纸 ----------------

    private void applyWallpaper() {
        String s = prefs().getString(KEY_WALLPAPER, null);
        if (s == null) return;
        try {
            wallpaper.setImageURI(Uri.parse(s));
            sampleTint(Uri.parse(s));
        } catch (Exception ignored) {
        }
    }

    /** 从壁纸底部采样平均色，喂给玻璃做「透背景」的色调 */
    private void sampleTint(final Uri uri) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inSampleSize = 16;
                    android.graphics.Bitmap bmp = getContentResolver()
                            .openInputStream(uri) == null ? null : null;
                    java.io.InputStream in = getContentResolver().openInputStream(uri);
                    bmp = BitmapFactory.decodeStream(in, null, o);
                    if (in != null) in.close();
                    if (bmp == null) return;
                    int w = bmp.getWidth(), h = bmp.getHeight();
                    long r = 0, g = 0, bl = 0; int n = 0;
                    for (int y = (int) (h * 0.78f); y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            int p = bmp.getPixel(x, y);
                            r += (p >> 16) & 0xFF; g += (p >> 8) & 0xFF; bl += p & 0xFF; n++;
                        }
                    }
                    if (n == 0) return;
                    final int color = 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (bl / n);
                    final android.graphics.Bitmap thumb = bmp;
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (bar == null) return;
                            bar.setTint(color);
                            bar.setBackdrop(thumb, rootView.getWidth(), rootView.getHeight());
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_WALLPAPER || resultCode != RESULT_OK || data == null) return;
        final Uri uri = data.getData();
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        prefs().edit().putString(KEY_WALLPAPER, uri.toString()).apply();
        try {
            wallpaper.setImageURI(uri);
            sampleTint(uri);
        } catch (Exception e) {
            toast("这张图读不出来，换一张试试");
        }
    }

    private boolean resolveDark() {
        String t = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_THEME, "system");
        if ("dark".equals(t)) return true;
        if ("light".equals(t)) return false;
        int mode = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private Button themeButton(String label, final String value, String current) {
        Button b = primary(value.equals(current) ? "✓ " + label : label);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs().edit().putString(KEY_THEME, value).apply();
                recreate();
            }
        });
        return b;
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    // ---------------- 小工具 ----------------

    private LinearLayout pageInner() {
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(22), dp(26), dp(22), dp(24));
        return inner;
    }

    private View wrap(LinearLayout inner) {
        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.addView(inner);
        return sc;
    }

    private View gap(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }

    private TextView sectionTitle(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(24);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(cText);
        v.setPadding(0, 0, 0, dp(6));
        return v;
    }

    private TextView hint(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(14);
        v.setTextColor(cSub);
        v.setLineSpacing(dp(4), 1f);
        v.setPadding(0, dp(4), 0, dp(12));
        return v;
    }

    private TextView caption(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(13);
        v.setTextColor(cSub);
        v.setPadding(0, dp(16), 0, dp(6));
        return v;
    }

    private Button primary(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setBackgroundColor(cCard);
        b.setTextColor(cCardText);
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
