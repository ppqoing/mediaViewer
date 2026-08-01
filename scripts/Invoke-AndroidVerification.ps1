[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$SdkRoot,

    [switch]$RunDeviceTests,

    [string]$AvdName,

    [switch]$RunRealServerTest,

    [string]$RealServerBaseUrl
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = [IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot '..')
)
$sdkRootFullPath = [IO.Path]::GetFullPath($SdkRoot)
$gradle = Join-Path $repositoryRoot 'gradlew.bat'
$module = Join-Path $PSScriptRoot 'ReleaseApkTools.psm1'

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

function Assert-ManifestPlaybackContract {
    $manifestPath = Join-Path `
        $repositoryRoot `
        'app\src\main\AndroidManifest.xml'
    [xml]$manifest = Get-Content `
        -LiteralPath $manifestPath `
        -Raw
    $androidNamespace = (
        'http://schemas.android.com/apk/res/android'
    )
    $permissions = @(
        $manifest.manifest.'uses-permission' |
            ForEach-Object {
                $_.GetAttribute(
                    'name',
                    $androidNamespace
                )
            }
    )
    foreach ($requiredPermission in @(
        'android.permission.FOREGROUND_SERVICE',
        (
            'android.permission.' +
            'FOREGROUND_SERVICE_MEDIA_PLAYBACK'
        )
    )) {
        if ($permissions -notcontains $requiredPermission) {
            throw "Manifest 缺少权限：$requiredPermission"
        }
    }

    $service = @(
        $manifest.manifest.application.service |
            Where-Object {
                $_.GetAttribute(
                    'name',
                    $androidNamespace
                ) -eq '.service.PlaybackService'
            }
    ) | Select-Object -First 1
    if ($null -eq $service) {
        throw 'Manifest 未注册 PlaybackService'
    }
    if (
        $service.GetAttribute(
            'foregroundServiceType',
            $androidNamespace
        ) -ne 'mediaPlayback'
    ) {
        throw (
            'PlaybackService foregroundServiceType ' +
            '必须为 mediaPlayback'
        )
    }
}

function Assert-Media3Contract {
    $catalog = Get-Content `
        -LiteralPath (
            Join-Path $repositoryRoot 'gradle\libs.versions.toml'
        ) `
        -Raw
    if ($catalog -notmatch '(?m)^media3\s*=\s*"1\.10\.1"\s*$') {
        throw 'Media3 版本必须为 1.10.1'
    }
    $appBuild = Get-Content `
        -LiteralPath (
            Join-Path $repositoryRoot 'app\build.gradle.kts'
        ) `
        -Raw
    foreach ($dependency in @(
        'implementation(libs.androidx.media3.common)',
        'implementation(libs.androidx.media3.session)'
    )) {
        if (-not $appBuild.Contains($dependency)) {
            throw "app 缺少 Media3 依赖：$dependency"
        }
    }
}

function Assert-ApkAbiContract {
    Import-Module $module -Force
    $debugApk = Join-Path `
        $repositoryRoot `
        'app\build\outputs\apk\debug\app-debug.apk'
    $releaseApk = Join-Path `
        $repositoryRoot `
        'app\build\outputs\apk\release\app-release-unsigned.apk'
    $debugEntries = @(
        Get-ApkArchiveInventory -ApkPath $debugApk |
            Where-Object { $null -ne $_.Abi }
    )
    $debugAbis = @(
        $debugEntries.Abi | Sort-Object -Unique
    )
    foreach ($requiredAbi in @('arm64-v8a', 'x86_64')) {
        if ($debugAbis -notcontains $requiredAbi) {
            throw "Debug APK 缺少 LibVLC ABI：$requiredAbi"
        }
    }

    $releaseEntries = @(
        Get-ApkArchiveInventory -ApkPath $releaseApk |
            Where-Object { $null -ne $_.Abi }
    )
    $releaseAbis = @(
        $releaseEntries.Abi | Sort-Object -Unique
    )
    if (
        $releaseAbis.Count -ne 1 -or
        $releaseAbis[0] -ne 'arm64-v8a'
    ) {
        throw (
            'Release APK 只允许 arm64-v8a，实际为：' +
            ($releaseAbis -join ', ')
        )
    }
}

function Find-AvdSerial {
    param(
        [Parameter(Mandatory)]
        [string]$Adb,

        [Parameter(Mandatory)]
        [string]$ExpectedAvdName
    )

    foreach ($line in (& $Adb devices)) {
        if ($line -notmatch '^(emulator-\d+)\s+device$') {
            continue
        }
        $candidateSerial = $Matches[1]
        $reportedName = (
            & $Adb `
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

function Get-ReadyDevice {
    param(
        [Parameter(Mandatory)]
        [string]$Adb,

        [Parameter(Mandatory)]
        [string]$Emulator,

        [Parameter(Mandatory)]
        [string]$ExpectedAvdName
    )

    $serial = Find-AvdSerial `
        -Adb $Adb `
        -ExpectedAvdName $ExpectedAvdName
    if ($null -eq $serial) {
        Start-Process `
            -FilePath $Emulator `
            -ArgumentList @(
                '-avd', $ExpectedAvdName,
                '-no-snapshot-save',
                '-no-boot-anim'
            ) `
            -WindowStyle Hidden | Out-Null
    }

    $deadline = [DateTime]::UtcNow.AddMinutes(4)
    do {
        $serial = Find-AvdSerial `
            -Adb $Adb `
            -ExpectedAvdName $ExpectedAvdName
        $bootCompleted = ''
        if ($null -ne $serial) {
            $bootCompleted = (
                & $Adb `
                    -s $serial `
                    shell getprop sys.boot_completed `
                    2>$null
            ).Trim()
        }
        if ($bootCompleted -eq '1') {
            return $serial
        }
        Start-Sleep -Seconds 2
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "模拟器 $ExpectedAvdName 在 4 分钟内未完成启动"
}

foreach ($required in @($gradle, $module)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "缺少必需文件：$required"
    }
}
if ($RunDeviceTests -and [string]::IsNullOrWhiteSpace($AvdName)) {
    throw '-RunDeviceTests 需要 -AvdName'
}
if ($RunRealServerTest -and -not $RunDeviceTests) {
    throw '-RunRealServerTest 需要同时启用 -RunDeviceTests'
}
if (
    $RunRealServerTest -and
    [string]::IsNullOrWhiteSpace($RealServerBaseUrl)
) {
    throw '-RunRealServerTest 需要 -RealServerBaseUrl'
}

$hadAndroidHome = Test-Path Env:ANDROID_HOME
$previousAndroidHome = $env:ANDROID_HOME
$hadAndroidSerial = Test-Path Env:ANDROID_SERIAL
$previousAndroidSerial = $env:ANDROID_SERIAL
$env:ANDROID_HOME = $sdkRootFullPath

Push-Location $repositoryRoot
try {
    Assert-ManifestPlaybackContract
    Assert-Media3Contract
    Invoke-Checked $gradle @(
        'testDebugUnitTest',
        'lintDebug',
        'assembleDebug',
        'assembleRelease',
        'compileDebugAndroidTestKotlin',
        '-Pkotlin.incremental=false',
        '--no-daemon',
        '--stacktrace'
    )
    Assert-ApkAbiContract
    Write-Host (
        '本地自动门禁通过：JVM、Lint、Debug/Release、' +
        'androidTest 编译、Manifest、Media3、APK ABI'
    )

    if ($RunDeviceTests) {
        $adb = Join-Path `
            $sdkRootFullPath `
            'platform-tools\adb.exe'
        $emulator = Join-Path `
            $sdkRootFullPath `
            'emulator\emulator.exe'
        foreach ($required in @($adb, $emulator)) {
            if (-not (
                Test-Path -LiteralPath $required -PathType Leaf
            )) {
                throw "缺少设备验证工具：$required"
            }
        }
        $serial = Get-ReadyDevice `
            -Adb $adb `
            -Emulator $emulator `
            -ExpectedAvdName $AvdName
        $env:ANDROID_SERIAL = $serial
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
        if ($apiLevel -ne '36' -or $abi -ne 'x86_64') {
            throw (
                '后台定向测试需要 API 36 x86_64，实际为 ' +
                "API $apiLevel $abi"
            )
        }
        Invoke-Checked $gradle @(
            'connectedDebugAndroidTest',
            (
                '-Pandroid.testInstrumentationRunnerArguments.class=' +
                'com.local.mediaviewer.BackgroundPlaybackTest,' +
                'com.local.mediaviewer.MediaSessionControlsTest,' +
                'com.local.mediaviewer.LibVlcVideoOutputTest'
            ),
            '-Pkotlin.incremental=false',
            '--no-daemon',
            '--stacktrace'
        )
        Write-Host 'API 36 后台播放定向设备测试通过'
    } else {
        Write-Host (
            '设备测试：NOT RUN（使用 -RunDeviceTests 明确启用）'
        )
    }

    if ($RunRealServerTest) {
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
            '-Pkotlin.incremental=false',
            '--no-daemon',
            '--stacktrace'
        )
        Write-Host '真实服务器 Smoke：通过'
    } else {
        Write-Host (
            '真实服务器测试：NOT RUN' +
            '（使用 -RunDeviceTests -RunRealServerTest 明确启用）'
        )
    }

    Write-Host '人工设备检查项（脚本不自动判定）：'
    Write-Host '1. 通知和锁屏的标题、进度、播放/暂停、上一项、下一项'
    Write-Host '2. 视频退到后台仅继续声音，返回后画面连续'
    Write-Host '3. 正在播放时划掉最近任务仍继续；暂停且无控制器时可停止'
    Write-Host '4. 通知停止释放资源但保留播放队列'
    Write-Host '5. 有线耳机拔出永久暂停，重新插入不会自动播放'
    Write-Host '6. 蓝牙/耳机按键可控制 play、pause、previous、next'
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
