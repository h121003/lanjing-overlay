# 路线图

当前版本：**v1.0-legacy**
下一版本：**v1.1**，预计 **2026-11-01** 发布

---

## v1.1 — 进行中

目标：新增一批宠物动画，并清理 v1.0 遗留问题。

- [x] 仓库开源（源码 + MIT 许可证 + 构建文档）
- [ ] 新增若干宠物动画
- [ ] 修复 v1.0 的 assets 重复打包问题（12 段变 24 段、体积翻倍）
- [ ] 重新构建并发布 APK
- [ ] 更新 README 的版本号与下载入口

> 动画素材的授权尚在与素材作者沟通中（见
> [素材来源](../README.md#素材来源)），确定后再决定 v1.1 是否随包分发素材。

## v1.2 — 进行中

### 安全加固

- [ ] `targetSdk` 28 → 33，补充 `POST_NOTIFICATIONS` 运行时申请
- [ ] WebView 收紧：关闭 `allowContentAccess` / `DomStorage` / 跨源文件访问
- [ ] `allowBackup="false"`，禁止 `adb backup` 导出应用数据
- [ ] `setFilterTouchesWhenObscured(true)`，防悬浮窗被覆盖时误触
- [ ] 发布版关闭调试日志
- [ ] `addView` 异常保护（权限被撤销时不崩溃）

### 稳定性修复

- [x] WebView 改用 `getApplicationContext()`，避免部分 ROM 因 Service 无窗口令牌而崩
- [x] `onDestroy` 调用 `webView.destroy()` 并 `stopForeground(true)`，修复内存泄漏
- [x] `addView` 加 try/catch，失败时优雅退出
- [x] canvas 监听 `resize` / `orientationchange`，修复横屏后画面拉伸

### 待排期

- [ ] 切换动画时预加载下一段，减少卡顿
- [ ] 页面未加载完时的点击兜底
- [ ] 缩放后同步 WebView 布局

---

## 说明

- 日期是预估，实际以 [Releases](https://github.com/h121003/lanjing-overlay/releases) 为准
- 想看进度：本文件与 [CHANGELOG.md](CHANGELOG.md) 会同步更新
