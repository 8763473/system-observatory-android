# 系统观测台 Android

这是给 Windows 端"系统观测台"配套的 Android 远程查看端。**无需独立中转服务**——Windows 父项目内置 HTTP+WebSocket 服务器，Android 直接连接。外网访问时配合 MSLFrp 暴露端口即可。

## 架构

```
Windows 父项目（内置服务器 :8787）
    ↑ WebSocket 实时推送
    │
MSLFrp 内网穿透（仅网络工具，非项目程序）
    │
    ↓
Android App（WebSocket + HTTP 回退）
```

只有两个程序：Windows exe 和 Android App。不再需要 Node.js 中转服务。

## 目录

```text
Android/
  app/                 Android 原生 App
    app/build.gradle   依赖 OkHttp（WebSocket 支持）
    app/src/.../RelayClient.java          HTTP 回退客户端
    app/src/.../RelayWebSocketClient.java WebSocket 实时推送客户端
    app/src/.../SnapshotDto.java          数据模型
    app/src/.../MainActivity.java         主界面 + 数据同步
  samples/             SystemSnapshot 示例
  tools/               契约检查脚本
../Windows/            Windows 父项目源码（含内置服务器）
```

## Android App

用 Android Studio 打开 `Android` 文件夹即可。App 首屏是"安静总览"：

- 连接地址、设备状态、最后更新
- 健康分
- CPU、内存、显卡、磁盘、网络
- Token 查询入口
- 连接设置入口

Token 查询和连接设置已经从主界面拆成独立界面。Token 查询页显示今日、累计、速度、模型、最近一轮、连续记录，并使用和父项目一致思路的 53 周热力图，支持每日和每周两种显示。

数据同步策略：

- 优先使用 WebSocket 实时推送（`GET /api/snapshot/stream`），父项目采集到新快照后 Android 立即收到，延迟低于 100ms
- WebSocket 断线时自动重连，重连期间回退到 HTTP 轮询（每 5 秒一次）保证数据不中断
- 未配置地址时每 30 秒检查一次
- 后台不主动连接，切到前台后自动恢复

## 部署步骤

### 1. Windows 端

1. 双击根目录 `系统观测台.exe` 启动
2. 在设置中开启"允许 Android 连接"
3. 用 **MSLFrp** 把本机 `http://127.0.0.1:8787` 暴露到公网。MSLFrp 隧道的本地地址填 `127.0.0.1`，本地端口填 `8787`
4. 复制 MSLFrp 显示的公网连接地址
5. 在 Windows 端设置的"配置地址"中粘贴公网地址并保存

设备密钥自动生成，保存在 exe 同目录的 `embedded-server-key.txt` 中。设置面板会显示这串密钥，Android 端填入相同的密钥。

如果 MSLFrp 给的是 `域名:端口`，公网地址要写成 `http://域名:端口`。如果给的是 HTTPS 域名，就直接写完整 `https://...`。

### 2. Android 端

1. 安装 Android App
2. 打开连接设置，填入：
   - 公网地址：Windows 端配置的公网地址
   - 设备密钥：Windows 端设置面板显示的密钥
3. 保存后自动连接

Token 查询数据由 Windows 端内置服务器随快照一起推送，包含 `tokenjiankong` 字段，来源是电脑端现有的 OpenClaw Token 监控和 `token_usage_daily.json` 统计。

## 父项目构建

只有修改父项目本身时才需要重新生成根目录 `系统观测台.exe`。父项目源码位于 `Windows/` 子目录，范围包括 `Windows/HwMonitor.csproj`、`Windows/Program.cs`、`Windows/Hardware/`、`Windows/Models/`、`Windows/Settings/`、`Windows/UI/`、`Windows/Utilities/`。只修改 Android 或文档时，不需要运行父项目构建脚本。

如果想在父项目源码变动后自动构建，在项目根目录双击并保持窗口打开：

```bat
watch-parent-build.cmd
```

需要单次手动发布父项目时，双击：

```bat
build-parent-exe.cmd
```

它会重新生成根目录 `系统观测台.exe`，替换旧程序并清理旧构建产物。

## API 契约

Windows 父项目内置服务器提供以下端点，所有请求需携带设备密钥头：

```http
X-Device-Key: your-device-key
```

Android 实时推送（WebSocket，优先使用）：

```http
GET /api/snapshot/stream
```

连接成功后立即推送一次当前最新快照，之后每次父项目采集时自动推送。消息格式：

```json
{
  "type": "snapshot",
  "appId": "system-observatory",
  "deviceId": "desktop-main",
  "receivedAt": "2026-06-26T12:00:00.000Z",
  "snapshot": { ... }
}
```

Android HTTP 回退读取（WebSocket 不可用时）：

```http
GET /api/snapshot/latest
```

返回：

```json
{
  "receivedAt": "2026-06-26T12:00:00.000Z",
  "snapshot": {
    "diannaomingcheng": "DESKTOP-OBSERVE",
    "tokenjiankong": {
      "jinritokens": 2561108,
      "leijitokens": 31951524,
      "moxingmingcheng": "gpt-5"
    }
  }
}
```

其他端点：

```http
GET /health              无需密钥，健康检查
GET /api/status          服务器状态
GET /api/gateway         网关地址查询
POST /api/gateway        设置公网地址
```

## 带宽

WebSocket 实时推送模式下，数据仅在变化时传输：

- 父项目每 3 秒采集一次：约 3-15 MB/天
- WebSocket 长连接心跳：约 0.5-1 MB/天
- HTTP 回退轮询（仅 WebSocket 不可用时）：约 3-15 MB/天

所以内网穿透重点看稳定性和延迟，带宽 1 Mbps 已经很宽裕。

## 验证

在项目根目录运行：

```powershell
.\Android\tools\validate-android-project.ps1
```

如果本机装有 Android SDK/Android Studio，也可以在 `Android` 目录执行：

```powershell
.\gradlew.bat assembleDebug
```

本目录已包含 Gradle Wrapper；也可以用 Android Studio 打开 `Android` 文件夹运行。
