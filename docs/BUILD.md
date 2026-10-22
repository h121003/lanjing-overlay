# 构建说明

## 环境

| 项 | 要求 |
|---|---|
| Android SDK | 随 `app/AndroidManifest.xml` 的 `minSdk` |
| JDK | 17+ |
| 构建工具 | Gradle 8+，或直接用 Android Studio 打开 `app/` |

## 目录

```
app/
├── AndroidManifest.xml
├── src/com/deepseek/petoverlay/
│   ├── MainActivity.java     入口：申请悬浮窗权限、启动/停止服务
│   └── OverlayService.java   前台服务：常驻 WebView 悬浮窗，可拖动、可缩放
├── res/drawable/ic_launcher.png
└── assets/
    ├── index.html            渲染层：循环播放 webm 动画
    └── webm/                 动画素材（未随仓库发布，见下）
```

## 动画素材

`app/assets/webm/` 下的 106 个 `.webm` 共 52 MB，**未进 Git 仓库**（见 `.gitignore`）。
构建前把它们放进 `app/assets/webm/` 即可；素材为 VP9-alpha 透明动画，`assets/index.html` 按顺序加载。

## 签名

工程没有附带签名配置。自行构建时用你自己的 keystore：

```bash
keytool -genkey -v -keystore my-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias pet
```

打包后在系统里授予「显示在其他应用上层」权限才能看到悬浮窗。
