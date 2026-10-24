package com.deepseek.petoverlay;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 关于页：版本信息 + 联系方式 + 支持开发。
 * 纯代码构建界面，不依赖 layout XML；二维码图片放在 res/drawable。
 */
public class AboutActivity extends Activity {

    private static final String QQ_GROUP = "1129465403";
    private static final String TELEGRAM = "https://t.me/sbh121003";
    private static final String GITHUB = "https://github.com/h121003/lanjing-overlay";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, dp(16), pad, pad);

        TextView title = new TextView(this);
        title.setText("关于");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, matchWrap());

        root.addView(section("蓝鲸悬浮窗 · Lanjing Overlay",
                "版本 " + versionName() + "\n"
                        + "常驻桌面的 WebView 悬浮窗，循环播放 WebM 透明动画\n"
                        + "开源协议：MIT"));

        root.addView(section("QQ 交流群",
                "群号 " + QQ_GROUP + "\n长按群号可复制，或直接扫码加入"));
        root.addView(qrImage(R.drawable.qq_qr));

        root.addView(section("Telegram",
                "@SBH121003\n扫码或点击下方按钮打开"));
        root.addView(qrImage(R.drawable.tg_qr));
        root.addView(actionButton("打开 Telegram", TELEGRAM));

        root.addView(section("支持开发",
                "如果这个项目帮到你，可以扫码请作者喝杯咖啡 ☕"));
        root.addView(qrImage(R.drawable.wx_qr));

        root.addView(section("GitHub 仓库",
                GITHUB));
        root.addView(actionButton("查看源码与反馈问题", GITHUB));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private String versionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName == null ? "1.1" : info.versionName;
        } catch (Exception e) {
            return "1.1";
        }
    }

    /** 一段标题 + 说明文字 */
    private LinearLayout section(String heading, String body) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(18), 0, dp(6));

        TextView h = new TextView(this);
        h.setText(heading);
        h.setTextSize(16);
        h.setTypeface(h.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(h);

        TextView b = new TextView(this);
        b.setText(body);
        b.setTextSize(13);
        b.setLineSpacing(dp(4), 1f);
        b.setTextIsSelectable(true);
        box.addView(b);

        return box;
    }

    /** 二维码图片，宽度随屏幕自适应 */
    private ImageView qrImage(int resId) {
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(BitmapFactory.decodeResource(getResources(), resId));
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(220));
        lp.topMargin = dp(8);
        iv.setLayoutParams(lp);
        return iv;
    }

    private Button actionButton(String text, final String url) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) {
                }
            }
        });
        return b;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
