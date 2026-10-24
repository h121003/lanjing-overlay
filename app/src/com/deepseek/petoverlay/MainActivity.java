package com.deepseek.petoverlay;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;

    /** Android 13+ 显示前台服务通知需要该权限；拒绝也不影响悬浮窗本身。 */
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED) return;
        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1001);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(48, 96, 48, 48);
        ll.setGravity(Gravity.CENTER_HORIZONTAL);

        status = new TextView(this);
        status.setTextSize(16);
        status.setText("蓝鱼悬浮窗\n\n① 先点「授予悬浮窗权限」\n② 再点「启动悬浮宠物」\n③ 回桌面即可看到，可拖动、点击切换动作");
        ll.addView(status);

        Button perm = new Button(this);
        perm.setText("1. 授予悬浮窗权限");
        perm.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }
        });
        ll.addView(perm);

        Button start = new Button(this);
        start.setText("2. 启动悬浮宠物");
        start.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!Settings.canDrawOverlays(MainActivity.this)) {
                    status.setText("还没有悬浮窗权限，请先点第 1 步授予");
                    return;
                }
                requestNotificationPermissionIfNeeded();
                Intent i = new Intent(MainActivity.this, OverlayService.class);
                startService(i);
                status.setText("已启动！回到桌面看看，可以拖动它");
            }
        });
        ll.addView(start);

        Button stop = new Button(this);
        stop.setText("3. 停止");
        stop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                stopService(new Intent(MainActivity.this, OverlayService.class));
                status.setText("已停止");
            }
        });
        ll.addView(stop);

        Button small = new Button(this);
        small.setText("调小一点");
        small.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                OverlayService.resize(-80);
            }
        });
        ll.addView(small);

        Button big = new Button(this);
        big.setText("调大一点");
        big.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                OverlayService.resize(80);
            }
        });
        ll.addView(big);

        Button about = new Button(this);
        about.setText("关于 / 联系方式");
        about.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, AboutActivity.class));
            }
        });
        ll.addView(about);

        setContentView(ll);
    }
}
