# 版本记录

## v1.1 — 2026-11-01

动画扩充 + 联系方式页 + 稳定性修复。

**新增**
- 动画从 12 段扩充到 **106 段**
- 「关于 / 联系方式」页面：QQ 交流群（一键跳转 / 复制群号）、Telegram、微信赞赏码、GitHub 仓库
- 底部新增「关于/联系方式」入口

**修复**
- WebView 改用 `getApplicationContext()`，避免部分 ROM 因 Service 无窗口令牌而崩溃
- `onDestroy` 增加 `webView.destroy()` 与 `stopForeground(true)`，修复内存泄漏
- `addView` 加异常保护，悬浮窗权限被撤销时优雅退出而非崩溃
- 监听 `resize` / `orientationchange`，修复横屏后画面拉伸
- 切换动画时预加载下一段，减少卡顿
- 页面未就绪时的点击先排队，修复「点了没反应」

| 项 | 值 |
|---|---|
| 包名 | `com.deepseek.petoverlay` |
| versionName | 1.1 |
| versionCode | 5 |
| 动画 | 106 段 |
| 大小 | 55,201,605 字节 |
| SHA-256 | `e6cb7e2c684254dddbfad6ed1bd684919e3f49f4199a629d7170286d7c21ec87` |

**签名**：自签名（非 Google Play），证书主体 `CN=Lanjing Overlay, O=h121003`，
2048 位 RSA，有效期至 2051-10-24，v1 + v2 双签。
证书 SHA-256 指纹 `EC:A5:BB:8B:90:0A:ED:6F:65:30:6B:77:EB:48:27:B0:E7:21:FE:6C:63:55:2B:75:AA:C0:AC:5A:45:84:D9:92`。

> 与 v1.0 签名不同，从 v1.0 升级到 v1.1 需要先卸载旧版。

## v1.0（旧构建标为 v1.0-legacy）— 2026-10-21

> 12 段动画版本。v1.1 为 106 段动画版本，见上。

首个公开留存的构建。

**已知问题**

- 资源重复打包：`assets/webm/` 与 `assets/assets/webm/` 各存一份，12 个动画变成 24 个，
  安装包体积从 5.9 MB 涨到 11.7 MB。动画文件逐字节相同，功能无差异。

**产物**

| 项 | 值 |
|---|---|
| 文件 | `lanjing-overlay-v1.0-legacy.apk` |
| 大小 | 11,688,146 字节 |
| SHA-256 | `55fb33388ad7bf8d38cc5f6a1294da6a0c5f0720fea803ee22fa10f84b5d7967` |
