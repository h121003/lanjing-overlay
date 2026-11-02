# 路线图

当前版本：**v1.1**
下一版本：**v1.2**（规划中）

---

## v1.1 — 已完成（2026-11-01 发布）

目标：扩充动画素材，补齐联系方式入口，修掉一批稳定性问题。

- [x] 仓库开源（源码 + MIT 许可证 + 构建文档）
- [x] 动画从 12 段扩充到 **106 段**
- [x] 新增「关于 / 联系方式」页（QQ 群、Telegram、微信赞赏码、GitHub）
- [x] 修复 WebView 上下文导致的偶发崩溃
- [x] 修复服务销毁时的内存泄漏
- [x] 悬浮窗权限被撤销时不再崩溃
- [x] 修复横屏后画面拉伸
- [x] 切换动画时预加载，减少卡顿
- [x] 画面未就绪时点击不再丢失
- [x] 重新构建并发布 APK
- [x] 更新 README 的版本号与下载入口

> 动画素材的授权尚在与素材作者沟通中（见
> [素材来源](../README.md#素材来源)），因此仓库不分发素材，仅在安装包内使用。

## v1.2 — 已完成，待发布（2026-11-10）

安全加固版本。不改动 UI 与交互。

- [x] `targetSdk` 28 → 33
- [x] `allowBackup=false`，关闭 adb backup 通道
- [x] WebView 收紧：关闭 `contentAccess`、DOM Storage、跨源文件访问
- [x] 防 tapjacking：悬浮窗增加触摸遮挡检测
- [x] 声明 `foregroundServiceType=specialUse`（Android 14+ 必需）
- [x] 关闭调试日志输出

> 独立构建：`release/1.2` 分支，versionName 1.2 / versionCode 6

## v1.3 — 进行中

液态玻璃底部导航栏。

- [ ] 圆形玻璃气泡 + 真实折射
- [ ] 拖动时椭圆拉伸、松手回弹淡出
- [ ] 边缘彩色色散
- [ ] 主题：浅色 / 深色 / 跟随系统
- [ ] 自定义壁纸

## v1.4 — 规划中

- [ ] 稳定性与机型兼容
- [ ] 自定义动画导入

---

## 说明

- 日期是预估，实际以 [Releases](https://github.com/h121003/lanjing-overlay/releases) 为准
- 想看进度：本文件与 [CHANGELOG.md](CHANGELOG.md) 会同步更新
