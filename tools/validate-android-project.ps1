$ErrorActionPreference = 'Stop'

$Root = Resolve-Path (Join-Path $PSScriptRoot '..')
$ProjectRoot = Resolve-Path (Join-Path $Root '..')

function Read-RequiredFile {
    param(
        [string]$Base,
        [string]$RelativePath
    )

    $path = Join-Path $Base $RelativePath
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Missing required file: $RelativePath"
    }
    return Get-Content -LiteralPath $path -Raw
}

function Require-Text {
    param(
        [string]$Name,
        [string]$Content,
        [string]$Pattern
    )

    if ($Content -notmatch $Pattern) {
        throw "$Name does not match required pattern: $Pattern"
    }
}

function Forbid-ProjectPath {
    param([string]$RelativePath)
    $path = Join-Path $ProjectRoot $RelativePath
    if (Test-Path -LiteralPath $path) {
        throw "Obsolete project path should not exist: $RelativePath"
    }
}

$settings = Read-RequiredFile $Root 'settings.gradle'
$appGradle = Read-RequiredFile $Root 'app/build.gradle'
$manifest = Read-RequiredFile $Root 'app/src/main/AndroidManifest.xml'
$main = Read-RequiredFile $Root 'app/src/main/java/com/systemobservatory/mobile/MainActivity.java'
$snapshot = Read-RequiredFile $Root 'app/src/main/java/com/systemobservatory/mobile/SnapshotDto.java'
$relayClient = Read-RequiredFile $Root 'app/src/main/java/com/systemobservatory/mobile/RelayClient.java'
$wsClient = Read-RequiredFile $Root 'app/src/main/java/com/systemobservatory/mobile/RelayWebSocketClient.java'
$readme = Read-RequiredFile $Root 'README.md'
$embeddedServer = Read-RequiredFile $ProjectRoot 'Windows/Hardware/EmbeddedServer.cs'

Require-Text 'settings.gradle' $settings 'SystemObservatoryAndroid'
Require-Text 'app/build.gradle' $appGradle 'com\.android\.application'
Require-Text 'app/build.gradle' $appGradle 'okhttp:4\.12\.0'
Require-Text 'AndroidManifest.xml' $manifest 'android\.permission\.INTERNET'
Require-Text 'AndroidManifest.xml' $manifest 'MainActivity'
Require-Text 'AndroidManifest.xml' $manifest 'windowSoftInputMode'

foreach ($field in @(
    'diannaomingcheng',
    'zhimingcheng',
    'chuliqi3',
    'neicun',
    'cipan2',
    'xianqia7',
    'wangluo',
    'tokenjiankong'
)) {
    Require-Text 'SnapshotDto.java' $snapshot $field
}

Require-Text 'RelayClient.java' $relayClient '/api/snapshot/latest'
Require-Text 'RelayClient.java' $relayClient 'X-Device-Key'
Require-Text 'RelayClient.java' $relayClient 'HttpURLConnection'
Require-Text 'RelayWebSocketClient.java' $wsClient '/api/snapshot/stream'
Require-Text 'RelayWebSocketClient.java' $wsClient 'WebSocketListener'
Require-Text 'RelayWebSocketClient.java' $wsClient 'scheduleReconnect'

Require-Text 'MainActivity.java' $main 'buildSettingsView'
Require-Text 'MainActivity.java' $main 'settingsRelayInput'
Require-Text 'MainActivity.java' $main 'settingsKeyInput'
Require-Text 'MainActivity.java' $main 'InputMethodManager'
Require-Text 'MainActivity.java' $main 'showSoftInput'
Require-Text 'MainActivity.java' $main 'saveConnectionFromSettings'
Require-Text 'MainActivity.java' $main 'connectWebSocket'
Require-Text 'MainActivity.java' $main 'HTTP_FALLBACK_INTERVAL_MS'
Require-Text 'MainActivity.java' $main 'TokenHeatmapView'
Require-Text 'MainActivity.java' $main 'bindTokenUi'

Require-Text 'EmbeddedServer.cs' $embeddedServer 'HttpListener'
Require-Text 'EmbeddedServer.cs' $embeddedServer 'AcceptWebSocketAsync'
Require-Text 'EmbeddedServer.cs' $embeddedServer '/api/snapshot/latest'
Require-Text 'EmbeddedServer.cs' $embeddedServer '/api/snapshot/stream'
Require-Text 'EmbeddedServer.cs' $embeddedServer 'EnsureDeviceKey'

Require-Text 'README.md' $readme '无需独立中转服务'
Require-Text 'README.md' $readme 'Windows 父项目（内置服务器 :8787）'
Require-Text 'README.md' $readme 'WebSocket 实时推送'
Require-Text 'README.md' $readme 'MSLFrp'
Require-Text 'README.md' $readme 'GET /api/snapshot/latest'
Require-Text 'README.md' $readme 'GET /api/snapshot/stream'

Forbid-ProjectPath 'Relay/server.js'
Forbid-ProjectPath 'Relay/start-relay.cmd'

Write-Host 'Android project contract checks passed.'
