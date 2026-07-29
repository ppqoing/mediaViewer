[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$SdkRoot,

    [string]$KeystorePath = (
        Join-Path $env:USERPROFILE '.android\debug.keystore'
    ),

    [string]$KeyAlias = 'androiddebugkey',

    [string]$PasswordEnvironmentVariable = (
        'MEDIAVIEWER_KEYSTORE_PASSWORD'
    )
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$expectedApplicationId = 'com.local.mediaviewer'
$expectedVersionName = '1.0.1'
$expectedVersionCode = 2
$expectedAbi = 'arm64-v8a'
$maximumBytes = 70MB
$artifactName = (
    'mediaviewer-v1.0.1-arm64-v8a-release.apk'
)

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

function Invoke-SigningChecked {
    param(
        [Parameter(Mandatory)]
        [string]$FilePath,

        [Parameter(Mandatory)]
        [string[]]$Arguments
    )

    $null = @(& $FilePath @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "APK 签名命令失败，退出码 $LASTEXITCODE"
    }
}

function Find-BuildTools {
    param(
        [Parameter(Mandatory)]
        [string]$ResolvedSdkRoot
    )

    $candidate = Get-ChildItem `
        -LiteralPath (Join-Path $ResolvedSdkRoot 'build-tools') `
        -Directory |
        Sort-Object { [version]$_.Name } -Descending |
        Where-Object {
            $directory = $_
            $missing = @(
                @(
                    'aapt.exe',
                    'zipalign.exe',
                    'apksigner.bat'
                ) | Where-Object {
                    -not (Test-Path -LiteralPath (
                        Join-Path $directory.FullName $_
                    ))
                }
            )
            $missing.Count -eq 0
        } |
        Select-Object -First 1
    if ($null -eq $candidate) {
        throw 'Android SDK 中缺少完整 Build Tools'
    }
    $candidate.FullName
}

$repositoryRoot = [IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot '..')
)
$distDirectory = [IO.Path]::GetFullPath(
    (Join-Path $repositoryRoot 'dist')
)
$finalApk = [IO.Path]::GetFullPath(
    (Join-Path $distDirectory $artifactName)
)
$checksumPath = [IO.Path]::GetFullPath(
    "$finalApk.sha256"
)
$distPrefix = $distDirectory.TrimEnd(
    [IO.Path]::DirectorySeparatorChar
) + [IO.Path]::DirectorySeparatorChar
if (
    -not $finalApk.StartsWith(
        $distPrefix,
        [StringComparison]::OrdinalIgnoreCase
    ) -or
    -not $checksumPath.StartsWith(
        $distPrefix,
        [StringComparison]::OrdinalIgnoreCase
    ) -or
    [IO.Path]::GetFileName($finalApk) -ne $artifactName -or
    [IO.Path]::GetFileName($checksumPath) -ne "$artifactName.sha256"
) {
    throw '个人 Release 交付路径校验失败'
}
New-Item `
    -ItemType Directory `
    -Path $distDirectory `
    -Force | Out-Null
Remove-Item `
    -LiteralPath @($finalApk, $checksumPath) `
    -Force `
    -ErrorAction SilentlyContinue
$sdkRootFullPath = [IO.Path]::GetFullPath($SdkRoot)
$keystoreFullPath = [IO.Path]::GetFullPath($KeystorePath)
$gradle = Join-Path $repositoryRoot 'gradlew.bat'
$buildTools = Find-BuildTools -ResolvedSdkRoot $sdkRootFullPath
$aapt = Join-Path $buildTools 'aapt.exe'
$zipalign = Join-Path $buildTools 'zipalign.exe'
$apksigner = Join-Path $buildTools 'apksigner.bat'
$module = Join-Path $PSScriptRoot 'ReleaseApkTools.psm1'

if (-not (Test-Path -LiteralPath $keystoreFullPath -PathType Leaf)) {
    throw '缺少必需签名文件'
}

foreach ($required in @(
    $gradle,
    $aapt,
    $zipalign,
    $apksigner,
    $module
)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "缺少必需文件：$required"
    }
}

Push-Location $repositoryRoot
try {
    $status = & git status --porcelain
    if ($LASTEXITCODE -ne 0) {
        throw '无法读取 Git 工作树状态'
    }
    if (-not [string]::IsNullOrWhiteSpace(
        ($status -join [Environment]::NewLine)
    )) {
        throw '个人 Release 必须从干净工作树构建'
    }
} finally {
    Pop-Location
}

$hadAndroidHome = Test-Path Env:ANDROID_HOME
$previousAndroidHome = $env:ANDROID_HOME
$env:ANDROID_HOME = $sdkRootFullPath
try {
    Push-Location $repositoryRoot
    try {
        Invoke-Checked $gradle @(
            'clean',
            'testDebugUnitTest',
            'lintRelease',
            'assembleRelease',
            '--no-daemon',
            '--stacktrace'
        )
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
$unsignedApk = Join-Path `
    $repositoryRoot `
    'app\build\outputs\apk\release\app-release-unsigned.apk'
$stagingDirectory = Join-Path `
    $repositoryRoot `
    'app\build\personal-release'
New-Item `
    -ItemType Directory `
    -Path $stagingDirectory `
    -Force | Out-Null
$alignedApk = Join-Path $stagingDirectory 'aligned.apk'
$signedStagingApk = Join-Path $stagingDirectory $artifactName

$null = Assert-Arm64CompressedArchive `
    -ApkPath $unsignedApk `
    -MaximumBytes $maximumBytes
Invoke-Checked $zipalign @(
    '-P', '16',
    '-f',
    '-v',
    '4',
    $unsignedApk,
    $alignedApk
)

$password = [Environment]::GetEnvironmentVariable(
    $PasswordEnvironmentVariable
)
$defaultDebugKeystore = [IO.Path]::GetFullPath(
    (Join-Path $env:USERPROFILE '.android\debug.keystore')
)
if ([string]::IsNullOrWhiteSpace($password)) {
    if (-not $keystoreFullPath.Equals(
        $defaultDebugKeystore,
        [StringComparison]::OrdinalIgnoreCase
    )) {
        throw (
            "自定义签名文件必须通过环境变量 " +
            "$PasswordEnvironmentVariable 提供密码"
        )
    }
    $password = 'android'
}
$apksignerPasswordVariable = (
    'MEDIAVIEWER_APKSIGNER_PASSWORD'
)
$hadApkSignerPassword = Test-Path (
    "Env:$apksignerPasswordVariable"
)
$previousApkSignerPassword = [Environment]::GetEnvironmentVariable(
    $apksignerPasswordVariable
)
try {
    Set-Item `
        -Path "Env:$apksignerPasswordVariable" `
        -Value $password
    Invoke-SigningChecked $apksigner @(
        'sign',
        '--ks', $keystoreFullPath,
        '--ks-key-alias', $KeyAlias,
        '--ks-pass', "env:$apksignerPasswordVariable",
        '--key-pass', "env:$apksignerPasswordVariable",
        '--v4-signing-enabled', 'false',
        '--out', $signedStagingApk,
        $alignedApk
    )
} finally {
    if ($hadApkSignerPassword) {
        Set-Item `
            -Path "Env:$apksignerPasswordVariable" `
            -Value $previousApkSignerPassword
    } else {
        Remove-Item `
            "Env:$apksignerPasswordVariable" `
            -ErrorAction SilentlyContinue
    }
    $password = $null
}

$release = Assert-Arm64CompressedArchive `
    -ApkPath $signedStagingApk `
    -MaximumBytes $maximumBytes

$badging = & $aapt dump badging $signedStagingApk
if ($LASTEXITCODE -ne 0) {
    throw 'aapt 无法读取已签名 APK'
}
$packageLine = $badging |
    Where-Object { $_ -like 'package:*' } |
    Select-Object -First 1
if (
    $packageLine -notmatch
        "name='$([regex]::Escape($expectedApplicationId))'" -or
    $packageLine -notmatch
        "versionCode='$expectedVersionCode'" -or
    $packageLine -notmatch
        "versionName='$([regex]::Escape($expectedVersionName))'"
) {
    throw "已签名 APK 元数据错误：$packageLine"
}
if ($badging -notcontains "native-code: '$expectedAbi'") {
    throw "已签名 APK ABI 错误：$($badging -join ' ')"
}

$signatureOutput = @(
    & $apksigner verify `
        --verbose `
        --print-certs `
        $signedStagingApk
)
if ($LASTEXITCODE -ne 0) {
    throw 'APK 签名验证失败'
}
$certificateSha256 = Get-ApkSignerCertificateSha256 `
    -ApkSignerOutput $signatureOutput
Invoke-Checked $zipalign @(
    '-c',
    '-P', '16',
    '-v',
    '4',
    $signedStagingApk
)

$debugApk = Join-Path $repositoryRoot 'dist\mediaviewer-debug.apk'
$debugCertificateComparison = '此前证书对比不可用（未找到 dist/mediaviewer-debug.apk）'
if (Test-Path -LiteralPath $debugApk -PathType Leaf) {
    $debugSignatureOutput = @(
        & $apksigner verify `
            --verbose `
            --print-certs `
            $debugApk
    )
    if ($LASTEXITCODE -ne 0) {
        throw '现有 Debug APK 签名验证失败'
    }
    $debugCertificateSha256 = Get-ApkSignerCertificateSha256 `
        -ApkSignerOutput $debugSignatureOutput
    if ($debugCertificateSha256 -ne $certificateSha256) {
        throw '个人 Release 与现有 Debug APK 的签名证书不一致'
    }
    $debugCertificateComparison = '此前证书对比：通过'
}

$stagingChecksumPath = "$signedStagingApk.sha256"
$delivery = Write-VerifiedSha256 `
    -ApkPath $signedStagingApk `
    -ChecksumPath $stagingChecksumPath
try {
    Move-Item `
        -LiteralPath $signedStagingApk `
        -Destination $finalApk
    Move-Item `
        -LiteralPath $stagingChecksumPath `
        -Destination $checksumPath
} catch {
    Remove-Item `
        -LiteralPath @($finalApk, $checksumPath) `
        -Force `
        -ErrorAction SilentlyContinue
    throw
}
$revision = (& git -C $repositoryRoot rev-parse HEAD).Trim()
$completedAt = [DateTimeOffset]::Now.ToString(
    'yyyy-MM-dd HH:mm:ss zzz'
)
$verificationPath = Join-Path `
    $repositoryRoot `
    'docs\verification\2026-07-29-arm64-compressed-release.md'
$record = @"
# mediaviewer arm64 压缩 Release 验收记录

- 完成时间：$completedAt
- Git 修订：$revision
- 包名：$expectedApplicationId
- 版本：$expectedVersionName ($expectedVersionCode)
- ABI：$expectedAbi
- APK：dist/$artifactName
- APK 大小：$($release.SizeMiB) MiB
- SHA-256：$($delivery.Sha256)
- 签名证书 SHA-256：$certificateSha256
- $debugCertificateComparison

## 自动门禁

- JVM 与 Robolectric 测试：通过
- Release Lint：0 error
- arm64-v8a 为唯一 Native ABI：通过
- LibVLC Native 条目压缩：通过
- 所有 DEX 条目压缩：通过
- APK 小于或等于 70 MiB：通过
- ZIP 对齐与 APK 签名：通过
- SHA-256 二次验证：通过

## 运行时验收边界

- API 36 x86_64 Debug 全功能验收由现有 Android 验收流程完成。
- 当前没有 arm64 真机，本记录不声称完成 arm64 Release 安装或冷启动。
"@
[IO.File]::WriteAllText(
    $verificationPath,
    $record,
    [Text.UTF8Encoding]::new($false)
)

[PSCustomObject]@{
    Apk = $finalApk
    ChecksumFile = $checksumPath
    VerificationRecord = $verificationPath
    SizeMiB = $release.SizeMiB
    Sha256 = $delivery.Sha256
    CertificateSha256 = $certificateSha256
}
