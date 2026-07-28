[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$SdkRoot,

    [Parameter(Mandatory)]
    [string]$AvdName,

    [Parameter(Mandatory)]
    [string]$RealServerBaseUrl
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = [IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot '..')
)
$sdkRootFullPath = [IO.Path]::GetFullPath($SdkRoot)
$adb = Join-Path $sdkRootFullPath 'platform-tools\adb.exe'
$emulator = Join-Path $sdkRootFullPath 'emulator\emulator.exe'
$gradle = Join-Path $repositoryRoot 'gradlew.bat'

foreach ($required in @($adb, $emulator, $gradle)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "缺少必需文件：$required"
    }
}

function Invoke-Checked {
    param(
        [Parameter(Mandatory)]
        [string]$FilePath,

        [Parameter(Mandatory)]
        [string[]]$Arguments
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw (
            "命令失败，退出码 $LASTEXITCODE：" +
            "$FilePath $($Arguments -join ' ')"
        )
    }
}

function Find-AvdSerial {
    param(
        [Parameter(Mandatory)]
        [string]$ExpectedAvdName
    )

    $deviceLines = & $adb devices
    foreach ($line in $deviceLines) {
        if ($line -notmatch '^(emulator-\d+)\s+device$') {
            continue
        }
        $candidateSerial = $Matches[1]
        $reportedName = (
            & $adb `
                -s $candidateSerial `
                emu avd name 2>$null |
                Select-Object -First 1
        )
        if (
            $null -ne $reportedName -and
            $reportedName.Trim() -eq $ExpectedAvdName
        ) {
            return $candidateSerial
        }
    }
    return $null
}

$hadAndroidHome = Test-Path Env:ANDROID_HOME
$previousAndroidHome = $env:ANDROID_HOME
$hadAndroidSerial = Test-Path Env:ANDROID_SERIAL
$previousAndroidSerial = $env:ANDROID_SERIAL
$env:ANDROID_HOME = $sdkRootFullPath

Push-Location $repositoryRoot
try {
    $gitStatusBeforeVerification = & git status --porcelain
    if ($LASTEXITCODE -ne 0) {
        throw '无法读取当前 Git 工作树状态'
    }
    if (
        -not [string]::IsNullOrWhiteSpace(
            (
                $gitStatusBeforeVerification -join
                    [Environment]::NewLine
            )
        )
    ) {
        throw '验收前工作树必须干净，请先提交测试、脚本和文档'
    }

    $serial = Find-AvdSerial -ExpectedAvdName $AvdName
    if ($null -eq $serial) {
        Start-Process `
            -FilePath $emulator `
            -ArgumentList @(
                '-avd', $AvdName,
                '-no-snapshot-save',
                '-no-boot-anim'
            ) `
            -WindowStyle Hidden | Out-Null
    }

    $bootDeadline = [DateTime]::UtcNow.AddMinutes(4)
    do {
        $serial = Find-AvdSerial -ExpectedAvdName $AvdName
        $bootCompleted = ''
        if ($null -ne $serial) {
            $bootCompleted = (
                & $adb `
                    -s $serial `
                    shell getprop sys.boot_completed `
                    2>$null
            ).Trim()
        }
        if ($bootCompleted -eq '1') {
            break
        }
        Start-Sleep -Seconds 2
    } while (
        $bootCompleted -ne '1' -and
        [DateTime]::UtcNow -lt $bootDeadline
    )
    if ($bootCompleted -ne '1' -or $null -eq $serial) {
        throw "模拟器 $AvdName 在 4 分钟内未完成启动"
    }
    $env:ANDROID_SERIAL = $serial

    Invoke-Checked $gradle @(
        'testDebugUnitTest',
        'lintDebug',
        'assembleDebug',
        '--stacktrace'
    )
    Invoke-Checked $gradle @(
        'connectedDebugAndroidTest',
        '--stacktrace'
    )
    Invoke-Checked $gradle @(
        'connectedDebugAndroidTest',
        (
            '-Pandroid.testInstrumentationRunnerArguments.class=' +
            'com.local.mediaviewer.RealServerSmokeTest'
        ),
        (
            '-Pandroid.testInstrumentationRunnerArguments.' +
            "realServerBaseUrl=$RealServerBaseUrl"
        ),
        '--stacktrace'
    )

    $apk = Join-Path `
        $repositoryRoot `
        'app\build\outputs\apk\debug\app-debug.apk'
    Invoke-Checked $adb @(
        '-s',
        $serial,
        'install',
        '-r',
        $apk
    )
    Invoke-Checked $adb @(
        '-s',
        $serial,
        'shell',
        'am',
        'force-stop',
        'com.local.mediaviewer'
    )
    Invoke-Checked $adb @(
        '-s',
        $serial,
        'shell',
        'am',
        'start',
        '-W',
        '-n',
        'com.local.mediaviewer/.MainActivity'
    )
    $pidValue = (
        & $adb `
            -s $serial `
            shell pidof com.local.mediaviewer
    ).Trim()
    if ([string]::IsNullOrWhiteSpace($pidValue)) {
        throw (
            'APK 已安装，但 com.local.mediaviewer ' +
            '未保持运行'
        )
    }

    foreach ($rootPath in @('/middle/', '/pik/')) {
        $response = Invoke-WebRequest `
            -Uri (
                $RealServerBaseUrl.TrimEnd('/') +
                $rootPath
            ) `
            -Headers @{ Accept = 'application/json' } `
            -TimeoutSec 15
        if ($response.StatusCode -ne 200) {
            throw (
                "$rootPath 返回 HTTP " +
                $response.StatusCode
            )
        }
        $content = [string]$response.Content
        try {
            $null = ConvertFrom-Json -InputObject $content
        } catch {
            throw "$rootPath 未返回合法 JSON"
        }
    }

    $delivery = & (
        Join-Path $PSScriptRoot 'Write-ApkChecksum.ps1'
    )
    $apiLevel = (
        & $adb `
            -s $serial `
            shell getprop ro.build.version.sdk
    ).Trim()
    $abi = (
        & $adb `
            -s $serial `
            shell getprop ro.product.cpu.abi
    ).Trim()
    if ($apiLevel -ne '36') {
        throw (
            "验收设备 API 必须为 36，实际为 $apiLevel"
        )
    }
    if ($abi -ne 'x86_64') {
        throw (
            "验收设备 ABI 必须为 x86_64，实际为 $abi"
        )
    }
    $revision = (& git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw '无法读取当前 Git 修订号'
    }
    $completedAt = [DateTimeOffset]::Now.ToString(
        'yyyy-MM-dd HH:mm:ss zzz'
    )
    $verificationDirectory = Join-Path `
        $repositoryRoot `
        'docs\verification'
    $verificationPath = Join-Path `
        $verificationDirectory `
        '2026-07-28-android-mediaviewer.md'
    New-Item `
        -ItemType Directory `
        -Path $verificationDirectory `
        -Force | Out-Null
    $record = @"
# mediaviewer Android 验收记录

- 完成时间：$completedAt
- Git 修订：$revision
- AVD：$AvdName
- Android API：$apiLevel
- ABI：$abi
- 真实服务器：$RealServerBaseUrl
- 应用进程 PID：$pidValue
- APK：dist/mediaviewer-debug.apk
- SHA-256：$($delivery.Sha256)

## 自动门禁

- JVM 单元测试：通过
- Robolectric API 29：通过
- Android Lint：0 error
- Debug APK 构建：通过
- Compose 全导航：通过
- PNG/WAV/MP4 自生成夹具：通过
- HTTP Range 206：通过
- LibVLC 视频、音频与 seek：通过
- 横屏旋转：通过
- API 36 x86_64 安装与启动：通过

## 真实服务器

- /middle/：HTTP 200，Caddy JSON 可解析，应用内解析通过
- /pik/：HTTP 200，Caddy JSON 可解析，应用内解析通过

验收过程未读取媒体正文，未在日志或本记录中写入真实目录条目名称。
"@
    $utf8NoBom = [Text.UTF8Encoding]::new($false)
    [IO.File]::WriteAllText(
        $verificationPath,
        $record,
        $utf8NoBom
    )

    Write-Host "验收通过：$verificationPath"
    Write-Host "APK：$($delivery.Apk)"
    Write-Host "SHA-256：$($delivery.Sha256)"
} finally {
    if ($hadAndroidSerial) {
        $env:ANDROID_SERIAL = $previousAndroidSerial
    } else {
        Remove-Item `
            Env:ANDROID_SERIAL `
            -ErrorAction SilentlyContinue
    }
    if ($hadAndroidHome) {
        $env:ANDROID_HOME = $previousAndroidHome
    } else {
        Remove-Item `
            Env:ANDROID_HOME `
            -ErrorAction SilentlyContinue
    }
    Pop-Location
}
