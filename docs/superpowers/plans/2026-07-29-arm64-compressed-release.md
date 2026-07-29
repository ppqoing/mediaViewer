# arm64 Compressed Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 生成不超过 70 MiB、只包含 `arm64-v8a`、Native 与 DEX 均压缩的 `mediaviewer` 1.0.1 个人 Release APK，并提供可重复签名和验收流程。

**Architecture:** Release 构建类型通过 per-variant NDK ABI 过滤只打包 arm64，模块 Packaging DSL 使用 legacy packaging 压缩 Native 与 DEX；Debug 继续保留全部 ABI 供 x86_64 模拟器回归。PowerShell ZIP 合约模块负责真实 APK 结构与体积检查，个人 Release 脚本负责干净构建、对齐、签名、元数据验证、SHA-256 和中文验收记录。

**Tech Stack:** Kotlin DSL、Android Gradle Plugin 9.3、Gradle 9.5、PowerShell 7、.NET `System.IO.Compression`、Android Build Tools 36.0.0、LibVLC `4.0.0-eap29`

## Global Constraints

- 最终只交付 `arm64-v8a` Release APK，不生成通用、armv7、x86 或 x86_64 Release。
- `versionName` 必须为 `1.0.1`，`versionCode` 必须为 `2`。
- LibVLC 必须保持 `4.0.0-eap29`。
- Release 必须保持 `isMinifyEnabled = false` 和 `isShrinkResources = false`。
- Native `.so` 与所有 DEX 条目必须使用 ZIP 压缩方式。
- 最终签名 APK 必须不超过 `70 MiB`，即 `73400320` 字节。
- Debug 必须继续包含 x86_64 并通过现有 API 36 x86_64 模拟器回归。
- 个人 Release 继续使用本机 Android 调试证书；签名文件和密码不得进入 Git、日志或验收记录。
- arm64 Release 只做结构、压缩、签名和哈希静态验收，不声称完成 arm64 真机运行时验收。
- 旧的 1.0.0 APK 不得被覆盖。

## File Structure

| File | Responsibility |
|---|---|
| `scripts/ReleaseApkTools.psm1` | 读取 APK ZIP 中央目录、执行 arm64/压缩/体积合约、生成并二次验证 SHA-256 |
| `tests/android/Test-ReleaseApkTools.ps1` | 使用确定性 ZIP 夹具验证纯 PowerShell APK 合约模块的成功和失败路径 |
| `tests/android/Test-ReleaseBuild.ps1` | 构建真实 Debug/Release APK，验证版本、Release 单 ABI、压缩和 Debug x86_64 保留 |
| `app/build.gradle.kts` | 版本升级、Release 专用 arm64 ABI 过滤、Native/DEX 压缩和显式关闭 R8 |
| `.gitignore` | 忽略 Gradle/Kotlin 生成的 `.kotlin/` 缓存 |
| `scripts/Build-PersonalRelease.ps1` | 干净构建、对齐、签名、静态验收、复制、SHA-256 和 Release 验收记录 |
| `README.md` | 中文 Release 构建、设备兼容性、安装和空间取舍说明 |
| `docs/verification/2026-07-29-arm64-compressed-release.md` | 最终自动生成的 arm64 Release 静态验收证据 |

---

### Task 1: Testable APK Archive Contract

**Files:**
- Create: `scripts/ReleaseApkTools.psm1`
- Create: `tests/android/Test-ReleaseApkTools.ps1`

**Interfaces:**
- Produces: `Get-ApkArchiveInventory -ApkPath <string> -> PSCustomObject[]`
- Produces: `Assert-Arm64CompressedArchive -ApkPath <string> -MaximumBytes <long> -> PSCustomObject`
- Produces: `Write-VerifiedSha256 -ApkPath <string> -ChecksumPath <string> -> PSCustomObject`
- Consumes: only PowerShell 7 and .NET `System.IO.Compression`

- [ ] **Step 1: Write the failing archive contract tests**

Create `tests/android/Test-ReleaseApkTools.ps1` with deterministic ZIP fixtures:

```powershell
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = [IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot '..\..')
)
$modulePath = Join-Path $repositoryRoot 'scripts\ReleaseApkTools.psm1'
Import-Module $modulePath -Force

function New-TestApk {
    param(
        [Parameter(Mandatory)]
        [string]$Path,

        [string[]]$NativeEntries = @(
            'lib/arm64-v8a/libvlc.so',
            'lib/arm64-v8a/libc++_shared.so'
        ),

        [switch]$StoreNative,
        [switch]$StoreDex
    )

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::Open(
        $Path,
        [IO.Compression.ZipArchiveMode]::Create
    )
    try {
        $payload = [byte[]]::new(8192)
        [Array]::Fill[byte]($payload, 65)
        foreach ($name in $NativeEntries) {
            $level = if ($StoreNative) {
                [IO.Compression.CompressionLevel]::NoCompression
            } else {
                [IO.Compression.CompressionLevel]::Optimal
            }
            $entry = $archive.CreateEntry($name, $level)
            $stream = $entry.Open()
            try {
                $stream.Write($payload, 0, $payload.Length)
            } finally {
                $stream.Dispose()
            }
        }
        $dexLevel = if ($StoreDex) {
            [IO.Compression.CompressionLevel]::NoCompression
        } else {
            [IO.Compression.CompressionLevel]::Optimal
        }
        $dex = $archive.CreateEntry('classes.dex', $dexLevel)
        $dexStream = $dex.Open()
        try {
            $dexStream.Write($payload, 0, $payload.Length)
        } finally {
            $dexStream.Dispose()
        }
    } finally {
        $archive.Dispose()
    }
}

function Assert-ThrowsLike {
    param(
        [Parameter(Mandatory)]
        [scriptblock]$Action,

        [Parameter(Mandatory)]
        [string]$Pattern
    )

    $didThrow = $false
    try {
        & $Action
    } catch {
        $didThrow = $true
        if ($_.Exception.Message -notlike "*$Pattern*") {
            throw (
                "异常信息不匹配。期望 *$Pattern*，实际：" +
                $_.Exception.Message
            )
        }
    }
    if (-not $didThrow) {
        throw "期望操作失败：$Pattern"
    }
}

$testRoot = Join-Path `
    ([IO.Path]::GetTempPath()) `
    ("mediaviewer-release-test-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null
try {
    $validApk = Join-Path $testRoot 'valid.apk'
    New-TestApk -Path $validApk
    $result = Assert-Arm64CompressedArchive `
        -ApkPath $validApk `
        -MaximumBytes 1MB
    if ($result.Abi -ne 'arm64-v8a') {
        throw "返回了错误 ABI：$($result.Abi)"
    }

    $foreignAbiApk = Join-Path $testRoot 'foreign-abi.apk'
    New-TestApk `
        -Path $foreignAbiApk `
        -NativeEntries @(
            'lib/arm64-v8a/libvlc.so',
            'lib/x86_64/libvlc.so'
        )
    Assert-ThrowsLike {
        Assert-Arm64CompressedArchive `
            -ApkPath $foreignAbiApk `
            -MaximumBytes 1MB
    } '只允许 arm64-v8a'

    $storedNativeApk = Join-Path $testRoot 'stored-native.apk'
    New-TestApk -Path $storedNativeApk -StoreNative
    Assert-ThrowsLike {
        Assert-Arm64CompressedArchive `
            -ApkPath $storedNativeApk `
            -MaximumBytes 1MB
    } 'Native 条目未压缩'

    $storedDexApk = Join-Path $testRoot 'stored-dex.apk'
    New-TestApk -Path $storedDexApk -StoreDex
    Assert-ThrowsLike {
        Assert-Arm64CompressedArchive `
            -ApkPath $storedDexApk `
            -MaximumBytes 1MB
    } 'DEX 条目未压缩'

    Assert-ThrowsLike {
        Assert-Arm64CompressedArchive `
            -ApkPath $validApk `
            -MaximumBytes 1
    } '超过体积上限'

    $checksumPath = "$validApk.sha256"
    $checksum = Write-VerifiedSha256 `
        -ApkPath $validApk `
        -ChecksumPath $checksumPath
    $recorded = (
        (Get-Content -LiteralPath $checksumPath -Raw).Trim() `
            -split '\s+'
    )[0]
    if ($recorded -ne $checksum.Sha256) {
        throw 'SHA-256 文件与返回值不一致'
    }
} finally {
    $resolvedTestRoot = [IO.Path]::GetFullPath($testRoot)
    $tempPrefix = (
        [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    ).TrimEnd([IO.Path]::DirectorySeparatorChar) +
        [IO.Path]::DirectorySeparatorChar
    if (-not $resolvedTestRoot.StartsWith(
        $tempPrefix,
        [StringComparison]::OrdinalIgnoreCase
    )) {
        throw '拒绝清理非临时测试目录'
    }
    Remove-Item -LiteralPath $resolvedTestRoot -Recurse -Force
}

Write-Host 'ReleaseApkTools 测试通过'
```

- [ ] **Step 2: Run the tests to verify the missing module fails**

Run:

```powershell
pwsh -NoProfile -File .\tests\android\Test-ReleaseApkTools.ps1
```

Expected: FAIL because `scripts/ReleaseApkTools.psm1` does not exist.

- [ ] **Step 3: Implement the minimal archive contract module**

Create `scripts/ReleaseApkTools.psm1`:

```powershell
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-ApkArchiveInventory {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$ApkPath
    )

    $resolvedApk = [IO.Path]::GetFullPath($ApkPath)
    if (-not (Test-Path -LiteralPath $resolvedApk -PathType Leaf)) {
        throw "未找到 APK：$resolvedApk"
    }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($resolvedApk)
    try {
        @(
            foreach ($entry in $archive.Entries) {
                $abi = $null
                if ($entry.FullName -match '^lib/([^/]+)/[^/]+\.so$') {
                    $abi = $Matches[1]
                }
                [PSCustomObject]@{
                    Path = $entry.FullName
                    Abi = $abi
                    Length = [int64]$entry.Length
                    CompressedLength = [int64]$entry.CompressedLength
                    IsCompressed = (
                        $entry.Length -gt 0 -and
                        $entry.CompressedLength -lt $entry.Length
                    )
                }
            }
        )
    } finally {
        $archive.Dispose()
    }
}

function Assert-Arm64CompressedArchive {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$ApkPath,

        [Parameter(Mandatory)]
        [int64]$MaximumBytes
    )

    $resolvedApk = [IO.Path]::GetFullPath($ApkPath)
    $apkLength = (Get-Item -LiteralPath $resolvedApk).Length
    if ($apkLength -gt $MaximumBytes) {
        throw (
            "APK 超过体积上限：$apkLength > $MaximumBytes 字节"
        )
    }

    $entries = @(Get-ApkArchiveInventory -ApkPath $resolvedApk)
    $nativeEntries = @(
        $entries | Where-Object {
            $null -ne $_.Abi
        }
    )
    if (-not (
        $nativeEntries.Path -contains
            'lib/arm64-v8a/libvlc.so'
    )) {
        throw 'APK 缺少 lib/arm64-v8a/libvlc.so'
    }

    $abis = @(
        $nativeEntries.Abi |
            Sort-Object -Unique
    )
    if ($abis.Count -ne 1 -or $abis[0] -ne 'arm64-v8a') {
        throw (
            'Release 只允许 arm64-v8a，实际 ABI：' +
            ($abis -join ', ')
        )
    }

    $storedNative = @(
        $nativeEntries | Where-Object {
            -not $_.IsCompressed
        }
    )
    if ($storedNative.Count -gt 0) {
        throw (
            'Native 条目未压缩：' +
            ($storedNative.Path -join ', ')
        )
    }

    $dexEntries = @(
        $entries | Where-Object {
            $_.Path -match '^classes\d*\.dex$'
        }
    )
    if ($dexEntries.Count -eq 0) {
        throw 'APK 中没有 DEX'
    }
    $storedDex = @(
        $dexEntries | Where-Object {
            -not $_.IsCompressed
        }
    )
    if ($storedDex.Count -gt 0) {
        throw (
            'DEX 条目未压缩：' +
            ($storedDex.Path -join ', ')
        )
    }

    [PSCustomObject]@{
        Apk = $resolvedApk
        SizeBytes = [int64]$apkLength
        SizeMiB = [math]::Round($apkLength / 1MB, 2)
        Abi = 'arm64-v8a'
        NativeEntryCount = $nativeEntries.Count
        DexEntryCount = $dexEntries.Count
    }
}

function Write-VerifiedSha256 {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$ApkPath,

        [Parameter(Mandatory)]
        [string]$ChecksumPath
    )

    $resolvedApk = [IO.Path]::GetFullPath($ApkPath)
    $resolvedChecksum = [IO.Path]::GetFullPath($ChecksumPath)
    $hash = (
        Get-FileHash `
            -LiteralPath $resolvedApk `
            -Algorithm SHA256
    ).Hash.ToLowerInvariant()
    $utf8NoBom = [Text.UTF8Encoding]::new($false)
    [IO.File]::WriteAllText(
        $resolvedChecksum,
        "$hash  $([IO.Path]::GetFileName($resolvedApk))`n",
        $utf8NoBom
    )
    $recorded = (
        (Get-Content -LiteralPath $resolvedChecksum -Raw).Trim() `
            -split '\s+'
    )[0]
    $verified = (
        Get-FileHash `
            -LiteralPath $resolvedApk `
            -Algorithm SHA256
    ).Hash.ToLowerInvariant()
    if ($recorded -ne $verified -or $hash -ne $verified) {
        throw 'APK SHA-256 二次验证失败'
    }
    [PSCustomObject]@{
        Apk = $resolvedApk
        ChecksumFile = $resolvedChecksum
        Sha256 = $verified
    }
}

Export-ModuleMember -Function @(
    'Get-ApkArchiveInventory',
    'Assert-Arm64CompressedArchive',
    'Write-VerifiedSha256'
)
```

- [ ] **Step 4: Run the archive contract tests**

Run:

```powershell
pwsh -NoProfile -File .\tests\android\Test-ReleaseApkTools.ps1
```

Expected: `ReleaseApkTools 测试通过`.

- [ ] **Step 5: Parse all new PowerShell files**

Run:

```powershell
$parseErrors = @()
@(
  '.\scripts\ReleaseApkTools.psm1',
  '.\tests\android\Test-ReleaseApkTools.ps1'
) | ForEach-Object {
  [Management.Automation.Language.Parser]::ParseFile(
    (Resolve-Path -LiteralPath $_),
    [ref]$null,
    [ref]$parseErrors
  ) | Out-Null
}
if ($parseErrors.Count) {
  $parseErrors | ForEach-Object { $_.ToString() }
  exit 1
}
```

Expected: exit code 0 and no parse errors.

- [ ] **Step 6: Commit the archive contract**

```powershell
git add -- `
  scripts/ReleaseApkTools.psm1 `
  tests/android/Test-ReleaseApkTools.ps1
git -c user.name=Codex -c user.email=codex@local commit `
  -m "test: define arm64 release archive contract"
```

### Task 2: Gradle arm64 and Compression Configuration

**Files:**
- Create: `tests/android/Test-ReleaseBuild.ps1`
- Modify: `app/build.gradle.kts:13-18`
- Modify: `app/build.gradle.kts:23-42`
- Modify: `.gitignore`
- Test: `tests/android/Test-ReleaseApkTools.ps1`
- Test: `tests/android/Test-ReleaseBuild.ps1`

**Interfaces:**
- Consumes: `Assert-Arm64CompressedArchive` and `Get-ApkArchiveInventory` from Task 1
- Produces: `app/build/outputs/apk/release/app-release-unsigned.apk`
- Produces: Debug APK whose ABI set still includes `x86_64`

- [ ] **Step 1: Write the real-build contract test**

Create `tests/android/Test-ReleaseBuild.ps1`:

```powershell
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
if ($packageLine -notmatch "versionCode='2'") {
    throw "versionCode 错误：$packageLine"
}
if ($packageLine -notmatch "versionName='1\.0\.1'") {
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
```

- [ ] **Step 2: Run the real-build test to verify the current universal APK fails**

Run:

```powershell
pwsh -NoProfile -File .\tests\android\Test-ReleaseBuild.ps1 `
  -SdkRoot 'C:\Users\Administrator\AppData\Local\Android\Sdk'
```

Expected: FAIL with `Release 只允许 arm64-v8a` or `Native 条目未压缩`.

- [ ] **Step 3: Add the minimal Gradle packaging configuration**

Modify `app/build.gradle.kts`:

```kotlin
defaultConfig {
    applicationId = "com.local.mediaviewer"
    minSdk = 29
    targetSdk = 36
    versionCode = 2
    versionName = "1.0.1"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
}

buildTypes {
    getByName("release") {
        isMinifyEnabled = false
        isShrinkResources = false
        ndk {
            abiFilters.clear()
            abiFilters += setOf("arm64-v8a")
        }
    }
}

packaging {
    jniLibs.useLegacyPackaging = true
    dex.useLegacyPackaging = true
    resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    resources.excludes += "DebugProbesKt.bin"
}
```

Do not add `splits`, do not change `defaultConfig.ndk`, and do not change
the LibVLC dependency. A release-scoped NDK filter is required so Debug
continues to package x86_64.

- [ ] **Step 4: Ignore the Kotlin build cache**

Append this exact entry to `.gitignore`:

```gitignore
.kotlin/
```

- [ ] **Step 5: Run the real-build test and verify it passes**

Run:

```powershell
pwsh -NoProfile -File .\tests\android\Test-ReleaseBuild.ps1 `
  -SdkRoot 'C:\Users\Administrator\AppData\Local\Android\Sdk'
```

Expected:

```text
真实 Release 合约通过：<70 MiB，ABI=arm64-v8a
```

- [ ] **Step 6: Re-run the deterministic PowerShell tests**

Run:

```powershell
pwsh -NoProfile -File .\tests\android\Test-ReleaseApkTools.ps1
```

Expected: `ReleaseApkTools 测试通过`.

- [ ] **Step 7: Commit the Gradle contract**

```powershell
git add -- `
  app/build.gradle.kts `
  .gitignore `
  tests/android/Test-ReleaseBuild.ps1
git -c user.name=Codex -c user.email=codex@local commit `
  -m "build: package compressed arm64 release"
```

### Task 3: Reproducible Signing and Delivery Pipeline

**Files:**
- Create: `scripts/Build-PersonalRelease.ps1`
- Modify: `scripts/ReleaseApkTools.psm1`
- Modify: `tests/android/Test-ReleaseApkTools.ps1`
- Test: `tests/android/Test-ReleaseBuild.ps1`

**Interfaces:**
- Consumes: `Assert-Arm64CompressedArchive`
- Consumes: `Write-VerifiedSha256`
- Produces: `dist/mediaviewer-v1.0.1-arm64-v8a-release.apk`
- Produces: `dist/mediaviewer-v1.0.1-arm64-v8a-release.apk.sha256`
- Produces: `docs/verification/2026-07-29-arm64-compressed-release.md`

- [ ] **Step 1: Add a certificate digest parser test**

Extend `tests/android/Test-ReleaseApkTools.ps1` before its final success
message:

```powershell
$certificateOutput = @(
    'Verifies',
    (
        'Signer #1 certificate SHA-256 digest: ' +
        'b432a64032601b66f275d0c4b3308d95' +
        'cbb40b58be9269c1494783e82fa5415d'
    )
)
$digest = Get-ApkSignerCertificateSha256 `
    -ApkSignerOutput $certificateOutput
if (
    $digest -ne
        'b432a64032601b66f275d0c4b3308d95' +
        'cbb40b58be9269c1494783e82fa5415d'
) {
    throw "证书摘要解析错误：$digest"
}
Assert-ThrowsLike {
    Get-ApkSignerCertificateSha256 `
        -ApkSignerOutput @('Verifies')
} '没有证书 SHA-256'
```

- [ ] **Step 2: Run the module test to verify the parser is missing**

Run:

```powershell
pwsh -NoProfile -File .\tests\android\Test-ReleaseApkTools.ps1
```

Expected: FAIL because `Get-ApkSignerCertificateSha256` is undefined.

- [ ] **Step 3: Implement and export the certificate digest parser**

Add to `scripts/ReleaseApkTools.psm1`:

```powershell
function Get-ApkSignerCertificateSha256 {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string[]]$ApkSignerOutput
    )

    $line = $ApkSignerOutput |
        Where-Object {
            $_ -match (
                '^Signer #1 certificate SHA-256 digest: ' +
                '([0-9a-fA-F]{64})$'
            )
        } |
        Select-Object -First 1
    if ($null -eq $line) {
        throw 'apksigner 输出中没有证书 SHA-256'
    }
    if ($line -notmatch '([0-9a-fA-F]{64})$') {
        throw '无法解析证书 SHA-256'
    }
    $Matches[1].ToLowerInvariant()
}
```

Add it to `Export-ModuleMember`:

```powershell
'Get-ApkSignerCertificateSha256'
```

- [ ] **Step 4: Run the module tests**

Run:

```powershell
pwsh -NoProfile -File .\tests\android\Test-ReleaseApkTools.ps1
```

Expected: `ReleaseApkTools 测试通过`.

- [ ] **Step 5: Verify the delivery script is absent before implementation**

Run:

```powershell
if (Test-Path -LiteralPath .\scripts\Build-PersonalRelease.ps1) {
  throw 'Build-PersonalRelease.ps1 已意外存在'
}
```

Expected: exit code 0.

- [ ] **Step 6: Implement the personal Release orchestrator**

Create `scripts/Build-PersonalRelease.ps1` with these parameters and
constants:

```powershell
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
```

Implement these exact orchestration boundaries:

```powershell
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
                'aapt.exe',
                'zipalign.exe',
                'apksigner.bat'
            ) | Where-Object {
                -not (Test-Path -LiteralPath (
                    Join-Path $directory.FullName $_
                ))
            }
            $missing.Count -eq 0
        } |
        Select-Object -First 1
    if ($null -eq $candidate) {
        throw 'Android SDK 中缺少完整 Build Tools'
    }
    $candidate.FullName
}
```

The main script must:

```powershell
$repositoryRoot = [IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot '..')
)
$sdkRootFullPath = [IO.Path]::GetFullPath($SdkRoot)
$keystoreFullPath = [IO.Path]::GetFullPath($KeystorePath)
$gradle = Join-Path $repositoryRoot 'gradlew.bat'
$buildTools = Find-BuildTools -ResolvedSdkRoot $sdkRootFullPath
$aapt = Join-Path $buildTools 'aapt.exe'
$zipalign = Join-Path $buildTools 'zipalign.exe'
$apksigner = Join-Path $buildTools 'apksigner.bat'
$module = Join-Path $PSScriptRoot 'ReleaseApkTools.psm1'

foreach ($required in @(
    $gradle,
    $aapt,
    $zipalign,
    $apksigner,
    $keystoreFullPath,
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
```

Run the build without exposing password material:

```powershell
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
```

Use an ignored staging directory and publish only after all checks:

```powershell
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
```

Read the signing password from the named environment variable. Only for
the default Android debug keystore may the script fall back to the standard
debug password `android`. Pass the password to `apksigner` through a
task-specific environment variable, never through command-line text:

```powershell
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
    Invoke-Checked $apksigner @(
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
```

Perform final gates before copying:

```powershell
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
```

If `dist/mediaviewer-debug.apk` exists, compare its certificate digest
with `$certificateSha256` and fail on mismatch. If it does not exist,
record that prior-certificate comparison was unavailable without failing
the build.

Finally copy, hash, and write the UTF-8 no-BOM verification record:

```powershell
$distDirectory = Join-Path $repositoryRoot 'dist'
New-Item `
    -ItemType Directory `
    -Path $distDirectory `
    -Force | Out-Null
$finalApk = Join-Path $distDirectory $artifactName
$checksumPath = "$finalApk.sha256"
Copy-Item `
    -LiteralPath $signedStagingApk `
    -Destination $finalApk `
    -Force
$delivery = Write-VerifiedSha256 `
    -ApkPath $finalApk `
    -ChecksumPath $checksumPath
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
```

- [ ] **Step 7: Parse the script and inspect secret handling**

Run:

```powershell
$parseErrors = @()
[Management.Automation.Language.Parser]::ParseFile(
  (Resolve-Path -LiteralPath .\scripts\Build-PersonalRelease.ps1),
  [ref]$null,
  [ref]$parseErrors
) | Out-Null
if ($parseErrors.Count) {
  $parseErrors | ForEach-Object { $_.ToString() }
  exit 1
}
rg -n 'pass:android|storepwd|keyPassword|storePassword' `
  .\scripts\Build-PersonalRelease.ps1
if ($LASTEXITCODE -eq 0) {
  throw '脚本包含命令行或配置文件密码字样'
}
```

Expected: PowerShell parses and the secret-pattern scan finds no match.
The literal fallback assignment `$password = 'android'` is allowed; the
forbidden pattern is passing it as `pass:android` or writing Gradle signing
properties.

- [ ] **Step 8: Commit the delivery pipeline before running its clean-tree gate**

```powershell
git add -- `
  scripts/Build-PersonalRelease.ps1 `
  scripts/ReleaseApkTools.psm1 `
  tests/android/Test-ReleaseApkTools.ps1
git -c user.name=Codex -c user.email=codex@local commit `
  -m "build: add personal release delivery pipeline"
```

- [ ] **Step 9: Run the personal Release script on the clean commit**

Run:

```powershell
.\scripts\Build-PersonalRelease.ps1 `
  -SdkRoot 'C:\Users\Administrator\AppData\Local\Android\Sdk'
```

Expected:

- exit code 0;
- APK path ends in
  `dist/mediaviewer-v1.0.1-arm64-v8a-release.apk`;
- size is at most 70 MiB;
- the verification record is the only new tracked file.

- [ ] **Step 10: Independently re-check the generated artifact**

Run:

```powershell
$apk = '.\dist\mediaviewer-v1.0.1-arm64-v8a-release.apk'
Import-Module .\scripts\ReleaseApkTools.psm1 -Force
$contract = Assert-Arm64CompressedArchive `
  -ApkPath $apk `
  -MaximumBytes 70MB
$actual = (
  Get-FileHash -LiteralPath $apk -Algorithm SHA256
).Hash.ToLowerInvariant()
$recorded = (
  (Get-Content -Raw -LiteralPath "$apk.sha256").Trim() `
    -split '\s+'
)[0]
if ($actual -ne $recorded) {
  throw '独立 SHA-256 复核失败'
}
$contract
```

Expected: ABI `arm64-v8a`, size at most 70 MiB, hashes equal.

### Task 4: Chinese Documentation and Final Acceptance

**Files:**
- Modify: `README.md`
- Modify: `docs/verification/2026-07-28-android-mediaviewer.md`
- Create: `docs/verification/2026-07-29-arm64-compressed-release.md`
- Test: `scripts/Invoke-AndroidVerification.ps1`
- Test: `scripts/Build-PersonalRelease.ps1`

**Interfaces:**
- Consumes: personal Release APK and checksum from Task 3
- Produces: final user-facing build/install documentation
- Produces: fresh x86_64 Debug runtime evidence and arm64 Release static evidence

- [ ] **Step 1: Add the Release documentation before the build command**

Update `README.md` after `## 构建环境` with:

````markdown
## 个人 Release

个人 Release 只支持 `arm64-v8a` Android 设备。它不包含 armv7、x86 或
x86_64，因此不能安装到本项目使用的 x86_64 模拟器。

Release APK 压缩存储 LibVLC Native 库和 DEX，可以显著减少传输文件大小。
Android 安装时需要解压这些内容，所以安装时间可能更长，安装后的磁盘占用
不会按 APK 体积同比下降。

在 PowerShell 中生成、签名和验收：

```powershell
.\scripts\Build-PersonalRelease.ps1 `
  -SdkRoot 'C:\Users\Administrator\AppData\Local\Android\Sdk'
```

默认使用当前 Windows 用户的
`%USERPROFILE%\.android\debug.keystore`。自定义签名文件时，通过
`MEDIAVIEWER_KEYSTORE_PASSWORD` 环境变量提供密码，不要把密码写进仓库。

交付物：

- `dist/mediaviewer-v1.0.1-arm64-v8a-release.apk`
- `dist/mediaviewer-v1.0.1-arm64-v8a-release.apk.sha256`
````

Add the arm64 installation command under `## 安装`:

```powershell
& 'C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe' `
  install -r .\dist\mediaviewer-v1.0.1-arm64-v8a-release.apk
```

State that `INSTALL_FAILED_NO_MATCHING_ABIS` means the device is not
arm64, and that a certificate mismatch requires uninstalling the old app
before installation, which clears app data.

- [ ] **Step 2: Run documentation and scope scans**

Run:

```powershell
rg -n `
  '1\.0\.1|arm64-v8a|70 MiB|Build-PersonalRelease|安装时间|磁盘占用' `
  .\README.md `
  .\docs\superpowers\specs\2026-07-29-arm64-compressed-release-design.md
rg -n `
  'LibVLC 3|minifyEnabled = true|shrinkResources = true|通用 Release' `
  .\README.md
if ($LASTEXITCODE -eq 0) {
  throw 'README 出现设计范围外的 Release 配置'
}
```

Expected: all required wording is found; no scope-expanding wording is
found in README.

- [ ] **Step 3: Commit source, scripts, tests, and README**

The Release verification record generated in Task 3 is intentionally not
included in this commit. Remove it from the index if it was staged, then
commit README:

```powershell
git add -- README.md
git -c user.name=Codex -c user.email=codex@local commit `
  -m "docs: document compressed arm64 release"
```

- [ ] **Step 4: Run the full x86_64 Debug acceptance on the clean commit**

Run:

```powershell
.\scripts\Invoke-AndroidVerification.ps1 `
  -SdkRoot 'C:\Users\Administrator\AppData\Local\Android\Sdk' `
  -AvdName 'Pixel_3a_API_36_extension_level_17_x86_64' `
  -RealServerBaseUrl 'http://192.168.1.17:8080'
```

Expected:

- JVM, Lint, Compose, Range, LibVLC and media-viewing tests pass;
- API 36 x86_64 Debug APK installs and cold-starts;
- `/middle/` and `/pik/` return HTTP 200 JSON;
- `docs/verification/2026-07-28-android-mediaviewer.md` records the new
  source revision.

- [ ] **Step 5: Commit the refreshed Debug runtime record**

```powershell
git add -- `
  docs/verification/2026-07-28-android-mediaviewer.md
git -c user.name=Codex -c user.email=codex@local commit `
  -m "docs: refresh debug runtime acceptance"
```

- [ ] **Step 6: Regenerate the arm64 Release from that clean commit**

Run:

```powershell
.\scripts\Build-PersonalRelease.ps1 `
  -SdkRoot 'C:\Users\Administrator\AppData\Local\Android\Sdk'
```

Expected: exit code 0 and only
`docs/verification/2026-07-29-arm64-compressed-release.md` is a new
tracked source file.

- [ ] **Step 7: Commit the arm64 Release verification record**

```powershell
git add -- `
  docs/verification/2026-07-29-arm64-compressed-release.md
git -c user.name=Codex -c user.email=codex@local commit `
  -m "docs: record arm64 release acceptance"
```

- [ ] **Step 8: Run fresh final verification on HEAD**

Run the non-device gates:

```powershell
$env:ANDROID_HOME = 'C:\Users\Administrator\AppData\Local\Android\Sdk'
.\gradlew.bat `
  testDebugUnitTest `
  lintDebug `
  lintRelease `
  assembleDebug `
  assembleRelease `
  --no-daemon `
  --stacktrace
```

Run the complete Debug device suite once with the real-server argument.
This keeps all device results in one XML report while the release-only ABI
remains isolated from Debug:

```powershell
$env:ANDROID_SERIAL = 'emulator-5554'
.\gradlew.bat connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.realServerBaseUrl=http://192.168.1.17:8080' `
  --stacktrace
```

Run packaging tests:

```powershell
pwsh -NoProfile -File .\tests\android\Test-ReleaseApkTools.ps1
pwsh -NoProfile -File .\tests\android\Test-ReleaseBuild.ps1 `
  -SdkRoot 'C:\Users\Administrator\AppData\Local\Android\Sdk'
```

Expected: all commands exit 0.

- [ ] **Step 9: Count tests and verify the final APK independently**

Run:

```powershell
$jvmTests = 0
$jvmFailures = 0
Get-ChildItem `
  .\app\build\test-results\testDebugUnitTest `
  -Filter 'TEST-*.xml' |
  ForEach-Object {
    [xml]$xml = Get-Content -Raw -LiteralPath $_.FullName
    $jvmTests += [int]$xml.testsuite.tests
    $jvmFailures += (
      [int]$xml.testsuite.failures +
      [int]$xml.testsuite.errors
    )
  }
if ($jvmTests -lt 126 -or $jvmFailures -ne 0) {
  throw "JVM 测试统计异常：$jvmTests / $jvmFailures"
}

$deviceTests = 0
$deviceFailures = 0
Get-ChildItem `
  .\app\build\outputs\androidTest-results\connected `
  -Filter '*.xml' `
  -Recurse |
  ForEach-Object {
    [xml]$xml = Get-Content -Raw -LiteralPath $_.FullName
    foreach ($suite in @($xml.testsuite)) {
      $deviceTests += [int]$suite.tests
      $deviceFailures += (
        [int]$suite.failures +
        [int]$suite.errors
      )
    }
  }
if ($deviceTests -lt 36 -or $deviceFailures -ne 0) {
  throw "设备测试统计异常：$deviceTests / $deviceFailures"
}

$apk = '.\dist\mediaviewer-v1.0.1-arm64-v8a-release.apk'
Import-Module .\scripts\ReleaseApkTools.psm1 -Force
$release = Assert-Arm64CompressedArchive `
  -ApkPath $apk `
  -MaximumBytes 70MB
$release
```

Expected: at least 126 JVM tests and 36 device tests, zero failures, final
APK reports only arm64 and at most 70 MiB.

- [ ] **Step 10: Confirm privacy, signing scope, and clean source state**

Run:

```powershell
rg -n `
  'I:\\MiddleDir|G:\\pik|MEDIAVIEWER_APKSIGNER_PASSWORD=|pass:android' `
  . `
  --glob '!**/.git/**' `
  --glob '!**/build/**' `
  --glob '!dist/**'
if ($LASTEXITCODE -eq 0) {
  throw '源码或文档包含真实目录或签名密码'
}

git status --short --branch
git diff --check
```

Expected:

- privacy/secret scan finds no match;
- no tracked or untracked source changes;
- ignored APK and checksum files remain available in `dist`.
