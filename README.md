# 蓝鲸悬浮窗 · Lanjing Overlay

> 一块常驻桌面的 WebView 悬浮窗，循环播放 WebM 透明动画，可拖动、可缩放。

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-3ddc84.svg)](#安装)
[![Version](https://img.shields.io/badge/version-1.0-orange.svg)](docs/CHANGELOG.md)
[![Package](https://img.shields.io/badge/package-com.deepseek.petoverlay-6f42c1.svg)](#安装)

---

## 目录

- [特性](#特性)
- [安装](#安装)
- [源码](#源码)
- [构建](#构建)
- [权限](#权限)
- [已知问题](#已知问题)
- [版本记录](#版本记录)
- [致谢](#致谢)
- [许可证](#许可证)
- [免责声明](#免责声明)

---

## 特性

| | |
|---|---|
| **透明悬浮窗** | 覆盖在桌面和其他应用之上，背景完全透明 |
| **动画播放** | WebView 内循环播放 VP9-alpha 的 `.webm` 动画 |
| **拖动** | 单指拖动到屏幕任意位置 |
| **缩放** | 双指缩放调整大小（180px 起，默认 460px） |
| **保活** | 前台服务 + 常驻通知，避免被系统回收 |
| **零网络** | 不申请 `INTERNET`，不采集任何数据 |

## 安装

| 项 | 值 |
|---|---|
| 安装包 | [`lanjing-overlay-v1.0-legacy.apk`](https://github.com/h121003/lanjing-overlay/releases/download/v1.0-legacy/lanjing-overlay-v1.0-legacy.apk) |
| 大小 | 11,688,146 字节（11.1 MiB） |
| 包名 | `com.deepseek.petoverlay` |
| 版本 | 1.0 |
| SHA-256 | `55fb33388ad7bf8d38cc5f6a1294da6a0c5f0720fea803ee22fa10f84b5d7967` |

**系统要求**：Android 7.0（API 24）及以上。

装好后必须在系统里授予「**显示在其他应用上层**」权限，否则悬浮窗不会出现：

```text
设置 → 应用 → 蓝鲸悬浮窗 → 权限 → 显示在其他应用上层
```

> 仓库内也保留了同一份文件：[`apk/lanjing-overlay-v1.0-legacy.apk`](apk/lanjing-overlay-v1.0-legacy.apk)

## 源码

```text
app/
├── AndroidManifest.xml              权限声明、Activity / Service 注册
├── src/com/deepseek/petoverlay/
│   ├── MainActivity.java            入口：申请悬浮窗权限、启动 / 停止服务
│   └── OverlayService.java          前台服务：WindowManager + WebView 悬浮窗
├── res/drawable/ic_launcher.png     图标
└── assets/
    ├── index.html                   渲染层：按序循环播放 webm
    └── webm/                        动画素材（106 个，未随仓库发布）

docs/
├── BUILD.md                         构建说明
└── CHANGELOG.md                     版本记录
```

**技术要点**

- `OverlayService` 通过 `WindowManager.addView()` 挂载浮层
  - Android 8.0+ 使用 `TYPE_APPLICATION_OVERLAY`
  - 7.x 回退到 `TYPE_PHONE`
  - 标志位：`FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL`
- 浮层内容是一个 `WebView`，加载 `file:///android_asset/index.html`
- 动画由前端 `<video>` 轮播，素材为 VP9-alpha 透明视频
- 拖动走 `OnTouchListener` 更新 `LayoutParams.x/y`；缩放改动 `LayoutParams.width/height`

## 构建

环境与步骤见 **[docs/BUILD.md](docs/BUILD.md)**。要点：

1. 把 106 个 `.webm` 放进 `app/assets/webm/`（52 MB，未进仓库）
2. 用 Android Studio 打开 `app/`，或 Gradle 直接构建
3. 用你自己的 keystore 签名

## 权限

| 权限 | 用途 |
|---|---|
| `SYSTEM_ALERT_WINDOW` | 显示悬浮窗（必须手动授予） |
| `FOREGROUND_SERVICE` | 前台服务保活 |
| `POST_NOTIFICATIONS` | 前台服务的常驻通知（Android 13+ 需授权） |

**没有网络权限**，不采集、不上传任何数据。

## 已知问题

**v1.0-legacy 存在资源重复打包**：同一批动画在包里存了两份 ——

```text
assets/webm/anim1.webm          ← 实际使用的
assets/assets/webm/anim1.webm   ← 多余的副本
```

导致 12 个动画变成 24 个，安装包从 5.9 MB 涨到 11.7 MB。动画文件逐字节相同，**功能上没有差异**，纯粹是构建脚本的 assets 目录层级写错了。

这也是该版本只作历史留存、不推荐日常使用的原因。

## 版本记录

| 版本 | 日期 | 说明 |
|---|---|---|
| v1.0-legacy | 2026-10-21 | 首个公开留存的构建，存在 assets 重复打包问题 |

完整记录见 **[docs/CHANGELOG.md](docs/CHANGELOG.md)**。

## 致谢

- 上游项目：[PC2005-cloud/dsh-pet](https://github.com/PC2005-cloud/dsh-pet) —— 桌面宠物插件的原作者，宠物形态与动画素材源自这里，按其 MIT 许可证使用。
- 本项目维护：[h121003](https://github.com/h121003)

## 许可证

[MIT](LICENSE) © 2026 h121003

可自由使用、修改、分发与商用，保留版权声明与许可声明即可。

## 免责声明

本项目为个人作品，与腾讯、DeepSeek 均无隶属关系。包名中的 `deepseek` 仅为历史命名，不代表官方。
