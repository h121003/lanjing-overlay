# 版本记录

## 未发布 — 安全加固（源码）

- Manifest：`allowBackup=false`、`usesCleartextTraffic=false`、Activity 显式 `exported`、Service 显式 `exported="false"`
- `targetSdk` 28 → 33，补充 `POST_NOTIFICATIONS` 运行时申请
- WebView：关闭 `allowContentAccess` / `DomStorage` / 表单保存，显式关闭跨源文件访问，开启 `setFilterTouchesWhenObscured`
- 发布版关闭调试日志（`DEBUG=false`）
- `addView` 增加异常保护，权限被撤销时不再崩溃


## v1.0-legacy — 2026-10-21

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
