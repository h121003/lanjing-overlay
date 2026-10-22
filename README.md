# 蓝鲸悬浮窗 · Lanjing Overlay

桌面悬浮宠物：一块常驻的 WebView 悬浮窗，循环播放 WebM 动画，可拖动、可缩放。

> 本仓库包含 **v1.0 历史构建（legacy）安装包** 与 **Android 源码**，源码按 MIT 许可证开源。
> 动画素材（52 MB / 106 个 webm）不随仓库发布，构建前需自行放入 `app/assets/webm/`，见 [docs/BUILD.md](docs/BUILD.md)。

## 安装

| 项 | 值 |
|---|---|
| 安装包 | [`apk/lanjing-overlay-v1.0-legacy.apk`](apk/lanjing-overlay-v1.0-legacy.apk) |
| 大小 | 11,688,146 字节（11.1 MiB） |
| 包名 | `com.deepseek.petoverlay` |
| 版本 | 1.0 |
| 系统要求 | Android 7.0+（API 24） |
| SHA-256 | `55fb33388ad7bf8d38cc5f6a1294da6a0c5f0720fea803ee22fa10f84b5d7967` |

装好后需要在系统里授予「**显示在其他应用上层**」权限，否则悬浮窗不会出现。

## 源码

```
app/
├── AndroidManifest.xml
├── src/com/deepseek/petoverlay/
│   ├── MainActivity.java     入口：申请悬浮窗权限、启动/停止服务
│   └── OverlayService.java   前台服务：常驻 WebView 悬浮窗，可拖动、可缩放
├── res/drawable/ic_launcher.png
└── assets/index.html         渲染层：循环播放 webm 动画
docs/BUILD.md                 构建说明（环境、素材、签名）
```

技术要点：`OverlayService` 用 `WindowManager` 添加 `TYPE_APPLICATION_OVERLAY`（Android 8.0+，
低版本回退 `TYPE_PHONE`）窗口，内部放一个 `WebView` 加载 `assets/index.html`，
动画由前端按序播放 `webm` 透明视频；窗口支持拖动与双指缩放，服务为前台服务保活。

## 这一版已知的问题

**资源重复打包**：同一批动画在包里存了两份 ——

```
assets/webm/anim1.webm          ← 实际使用的
assets/assets/webm/anim1.webm   ← 多余的副本
```

12 个动画因此变成 24 个，安装包体积从 5.9 MB 涨到 11.7 MB。动画文件本身逐字节相同，**功能上与后续构建没有任何差异**，纯粹是构建脚本的 assets 目录层级写错了。

这也是本版本只作为历史留存、不推荐日常使用的原因。

## 权限说明

| 权限 | 用途 |
|---|---|
| `SYSTEM_ALERT_WINDOW` | 显示悬浮窗（必须手动授予） |
| `FOREGROUND_SERVICE` | 前台服务保活，避免宠物被系统回收 |
| `POST_NOTIFICATIONS` | 前台服务的常驻通知（Android 13+ 需授权） |

没有网络权限，不采集任何数据。

## 版本记录

| 版本 | 日期 | 说明 |
|---|---|---|
| v1.0-legacy | 2026-10-21 | 首个公开留存的构建，存在 assets 重复打包问题 |

## 许可

[MIT](LICENSE) © 2026 h121003 —— 可自由使用、修改、分发与商用，保留版权与许可声明即可。

## 免责声明

本项目为个人作品，与腾讯、DeepSeek 均无隶属关系。包名中的 `deepseek` 仅为历史命名，不代表官方。
