[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$SdkRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = [IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot '..\..')
)
$sdkRootFullPath = [IO.Path]::GetFullPath($SdkRoot)
$gradle = Join-Path $repositoryRoot 'gradlew.bat'
$module = Join-Path $repositoryRoot 'scripts\ReleaseApkTools.psm1'
$buildTools = Get-ChildItem `
    -LiteralPath (Join-Path $sdkRootFullPath 'build-tools') `
    -Directory |
    Sort-Object { [version]$_.Name } -Descending |
    Where-Object {
        Test-Path -LiteralPath (
            Join-Path $_.FullName 'aapt.exe'
        )
    } |
    Select-Object -First 1
if ($null -eq $buildTools) {
    throw 'Android SDK 中没有可用的 aapt.exe'
}
$aapt = Join-Path $buildTools.FullName 'aapt.exe'

$hadAndroidHome = Test-Path Env:ANDROID_HOME
$previousAndroidHome = $env:ANDROID_HOME
$env:ANDROID_HOME = $sdkRootFullPath
try {
    Push-Location $repositoryRoot
    try {
        & $gradle `
            assembleDebug `
            assembleRelease `
            '-Pkotlin.incremental=false' `
            --no-daemon `
            --stacktrace
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle 构建失败：$LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
} finally {
    if ($hadAndroidHome) {
        $env:ANDROID_HOME = $previousAndroidHome
    } else {
        Remove-Item Env:ANDROID_HOME -ErrorAction SilentlyContinue
    }
}

Import-Module $module -Force
$releaseApk = Join-Path `
    $repositoryRoot `
    'app\build\outputs\apk\release\app-release-unsigned.apk'
$release = Assert-Arm64CompressedArchive `
    -ApkPath $releaseApk `
    -MaximumBytes 70MB

$badging = & $aapt dump badging $releaseApk
if ($LASTEXITCODE -ne 0) {
    throw 'aapt 无法读取 Release APK'
}
$packageLine = $badging | Where-Object {
    $_ -like 'package:*'
} | Select-Object -First 1
if ($packageLine -notmatch "name='com\.local\.mediaviewer'") {
    throw "包名错误：$packageLine"
}
if ($packageLine -notmatch "versionCode='3'") {
    throw "versionCode 错误：$packageLine"
}
if ($packageLine -notmatch "versionName='1\.1\.0'") {
    throw "versionName 错误：$packageLine"
}
if ($badging -notcontains "sdkVersion:'29'") {
    throw 'minSdk 不是 29'
}
if ($badging -notcontains "targetSdkVersion:'36'") {
    throw 'targetSdk 不是 36'
}
if ($badging -notcontains "native-code: 'arm64-v8a'") {
    throw 'Release native-code 不是严格的 arm64-v8a'
}

$debugApk = Join-Path `
    $repositoryRoot `
    'app\build\outputs\apk\debug\app-debug.apk'
$debugAbis = @(
    Get-ApkArchiveInventory -ApkPath $debugApk |
        Where-Object { $null -ne $_.Abi } |
        Select-Object -ExpandProperty Abi -Unique
)
if ($debugAbis -notcontains 'x86_64') {
    throw (
        'Release ABI 过滤泄漏到 Debug，x86_64 模拟器将无法运行'
    )
}

Write-Host (
    "真实 Release 合约通过：$($release.SizeMiB) MiB，" +
    "ABI=$($release.Abi)"
)
