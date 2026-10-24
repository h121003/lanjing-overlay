# 版本记录

## v1.1 — 2026-10-24

106 段动画版本（首个公开发布的完整动画包）。

| 项 | 值 |
|---|---|
| 包名 | `com.deepseek.petoverlay` |
| versionName | 1.1 |
| versionCode | 1 |
| 动画 | 106 段 |
| 大小 | 54,416,041 字节（51.9 MiB） |
| SHA-256 | `d4e756a3890ea0de86eb6dc18ad293383d919ed84f6dfbc74b2e864dc2af39ae` |

**签名**：自签名（非 Google Play），证书主体 `CN=Lanjing Overlay, O=h121003`，
2048 位 RSA，有效期至 2051-10-24，
证书 SHA-256 指纹 `EC:A5:BB:8B:90:0A:ED:6F:65:30:6B:77:EB:48:27:B0:E7:21:FE:6C:63:55:2B:75:AA:C0:AC:5A:45:84:D9:92`。

> 与 v1.0 签名不同，从 v1.0 升级到 v1.1 需要先卸载旧版。

## v1.2 — 进行中

见 [ROADMAP](ROADMAP.md)。包含安全加固与稳定性修复，尚未发布。

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
