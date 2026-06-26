$ErrorActionPreference = 'Stop'

$Root = Resolve-Path (Join-Path $PSScriptRoot '..')
$MainPath = Join-Path $Root 'app/src/main/java/com/systemobservatory/mobile/MainActivity.java'
if (-not (Test-Path -LiteralPath $MainPath)) {
    throw "Missing MainActivity.java"
}

$main = Get-Content -LiteralPath $MainPath -Raw

function Require-Text {
    param([string]$Pattern, [string]$Message)
    if ($main -notmatch $Pattern) {
        throw $Message
    }
}

function Forbid-Text {
    param([string]$Pattern, [string]$Message)
    if ($main -match $Pattern) {
        throw $Message
    }
}

Require-Text 'settingsRelayInput' 'Settings page must keep an inline relay URL input.'
Require-Text 'settingsKeyInput' 'Settings page must keep an inline device key input.'
Require-Text 'InputMethodManager' 'Settings inputs must explicitly request the soft keyboard.'
Require-Text 'showSoftInput' 'Settings inputs must show the keyboard when tapped.'
Require-Text 'requestFocus' 'Settings inputs must request focus before opening the keyboard.'
Require-Text 'saveConnectionFromSettings' 'Settings page must save connection data inline.'
Require-Text 'showToast' 'Settings save should show a lightweight confirmation instead of a dialog.'
Require-Text 'Windows 父项目内置服务器' 'Android settings copy must mention the built-in Windows server architecture.'
Forbid-Text 'openConnectionDialog\s*\(\)' 'Connection configuration must not use the crash-prone dialog path.'
Forbid-Text 'new AlertDialog\.Builder\(this\)\s*\n\s*\.setTitle\("连接设置"' 'Connection settings must not create an AlertDialog.'

Write-Host 'Android connection settings contract passed.'
