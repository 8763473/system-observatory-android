# 系统观测台 Android 端设计

## 方案

选用方案 1：安静总览。

手机端的第一目标不是复刻电脑端所有复杂面板，而是在离开电脑后快速回答两件事：电脑现在是否正常，以及 OpenClaw Token 使用量是否正常。页面采用三页移动控制台结构：主界面只保留总览和入口，Token 查询页单独展示用量与热力图，连接设置页单独承载公网地址、设备密钥和 MSLFrp 说明。

## 数据链路

```text
被监控电脑
  -> POST /api/snapshot 或 /api/apps/{appId}/devices/{deviceId}/snapshot
中转电脑上的 Relay
  -> 内网穿透 HTTPS 地址
Android App
  -> GET /api/snapshot/latest
```

被监控电脑主动上传，不需要暴露端口。中转电脑运行根目录 `Relay/` 服务，并通过 Cloudflare Tunnel、frp、Tailscale Funnel、花生壳或同类工具对 Android 暴露公网入口。Relay 启动时会在控制台输出登录密钥，自带 WebUI 登录页和控制台，用于查看服务状态、网关出口、设备列表、最新快照和接入示例。第三方穿透软件可以通过 `PUBLIC_BASE_URL` 或 `POST /api/gateway` 写入公网地址。

Token 查询不在 Android 本地采集。父项目上传 `SystemSnapshot` 时附带 `tokenjiankong` 字段，来源是电脑端 OpenClaw Token 监控和 `token_usage_daily.json`。Relay 保留 `GET /api/token/latest` 和 `GET /api/apps/{appId}/devices/{deviceId}/token/latest`，便于其他客户端只查询 Token 数据。

## 交互

- 主界面顶部显示公网地址、设备状态、最后更新。
- 健康分根据 CPU、内存、显卡温度、磁盘剩余空间等指标粗略计算。
- Token 查询页显示今日 Token、累计 Token、输出速度、最近模型、最近一轮和连续记录。
- Token 查询页使用父项目同款思路的 53 周热力图，支持每日和每周聚合两种显示。
- 指标行包含 CPU、内存、显卡、磁盘、网络。
- 设置界面单独显示公网地址、设备密钥和 MSLFrp 接入说明。
- App 在前台默认 3 秒刷新一次；未配置或连接失败时降为 30 秒，降低内网穿透流量。

## 数据契约

Android 直接读取当前 Windows 程序的 `SystemSnapshot` JSON 字段，例如：

- `diannaomingcheng`
- `chuliqi3.shiyonglvbaifenbi`
- `neicun.shiyonglvbaifenbi2`
- `xianqia7[].shiyonglvbaifenbi4`
- `cipan2[].shiyonglvbaifenbi3`
- `wangluo[].xiazaisudu2`

中转服务也接受包装响应，并额外提供面向后续扩展的通用 app/device 端点：

```json
{
  "receivedAt": "2026-06-12T10:00:00.000Z",
  "snapshot": {}
}
```

```http
POST /api/apps/system-observatory/devices/desktop/snapshot
GET /api/apps/system-observatory/devices/desktop/latest
GET /api/token/latest
GET /api/apps/system-observatory/devices/desktop/token/latest
GET /api/gateway
POST /api/gateway
```

## 安全

- 中转服务要求 `X-Device-Key`。
- 内网穿透建议使用 HTTPS。
- 不传屏幕截图、窗口标题、文件列表等隐私数据，只传硬件状态数值。
