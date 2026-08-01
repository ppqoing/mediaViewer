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
        [switch]$StoreDex,

        [byte[]]$Payload = [byte[]]::new(8192)
    )

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::Open(
        $Path,
        [IO.Compression.ZipArchiveMode]::Create
    )
    try {
        [Array]::Fill[byte]($Payload, 65)
        foreach ($name in $NativeEntries) {
            $level = if ($StoreNative) {
                [IO.Compression.CompressionLevel]::NoCompression
            } else {
                [IO.Compression.CompressionLevel]::Optimal
            }
            $entry = $archive.CreateEntry($name, $level)
            $stream = $entry.Open()
            try {
                $stream.Write($Payload, 0, $Payload.Length)
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
            $dexStream.Write($Payload, 0, $Payload.Length)
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

function Get-FirstZipLocalCompressionMethod {
    param(
        [Parameter(Mandatory)]
        [string]$Path
    )

    $header = [IO.File]::ReadAllBytes($Path)
    if ([BitConverter]::ToUInt32($header, 0) -ne 0x04034b50) {
        throw '测试 APK 缺少 ZIP 本地文件头'
    }
    [BitConverter]::ToUInt16($header, 8)
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

    $deflateButLargerApk = Join-Path $testRoot 'deflate-but-larger.apk'
    New-TestApk -Path $deflateButLargerApk -Payload ([byte[]]@(65))
    $deflateArchive = [IO.Compression.ZipFile]::OpenRead(
        $deflateButLargerApk
    )
    try {
        $deflateEntry = $deflateArchive.GetEntry('lib/arm64-v8a/libvlc.so')
        if ($deflateEntry.CompressedLength -le $deflateEntry.Length) {
            throw '测试 fixture 并非 DEFLATE 后更大'
        }
    } finally {
        $deflateArchive.Dispose()
    }
    if ((Get-FirstZipLocalCompressionMethod -Path $deflateButLargerApk) -ne 8) {
        throw '测试 fixture 并非 DEFLATE 条目'
    }
    $deflateButLargerResult = Assert-Arm64CompressedArchive `
        -ApkPath $deflateButLargerApk `
        -MaximumBytes 1MB
    if ($deflateButLargerResult.Abi -ne 'arm64-v8a') {
        throw 'DEFLATE 后更大的 APK 未通过 arm64 合约'
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

    $sdkRoot = Join-Path $testRoot 'sdk'
    $buildToolsRoot = Join-Path $sdkRoot 'build-tools'
    $stableBuildTools = Join-Path $buildToolsRoot '35.0.0'
    $previewBuildTools = Join-Path $buildToolsRoot '36.0.0-rc1'
    New-Item `
        -ItemType Directory `
        -Path $stableBuildTools, $previewBuildTools `
        -Force | Out-Null
    foreach ($tool in @(
        'aapt.exe',
        'zipalign.exe',
        'apksigner.bat'
    )) {
        [IO.File]::WriteAllText(
            (Join-Path $stableBuildTools $tool),
            ''
        )
        [IO.File]::WriteAllText(
            (Join-Path $previewBuildTools $tool),
            ''
        )
    }
    $selectedBuildTools =
        Find-CompleteAndroidBuildTools `
            -SdkRoot $sdkRoot
    if (
        $selectedBuildTools -ne
            [IO.Path]::GetFullPath($stableBuildTools)
    ) {
        throw (
            '未在 preview 目录存在时选中完整稳定 Build Tools：' +
            $selectedBuildTools
        )
    }

    $validBadging = @(
        (
            "package: name='com.local.mediaviewer' " +
            "versionCode='3' versionName='1.1.0'"
        ),
        "sdkVersion:'29'",
        "targetSdkVersion:'36'",
        "native-code: 'arm64-v8a'"
    )
    $null = Assert-ApkBadgingMetadata `
        -Badging $validBadging `
        -ExpectedApplicationId 'com.local.mediaviewer' `
        -ExpectedVersionCode 3 `
        -ExpectedVersionName '1.1.0' `
        -ExpectedMinSdk 29 `
        -ExpectedTargetSdk 36 `
        -ExpectedAbi 'arm64-v8a'
    Assert-ThrowsLike {
        Assert-ApkBadgingMetadata `
            -Badging (
                $validBadging -replace
                    "sdkVersion:'29'",
                    "sdkVersion:'28'"
            ) `
            -ExpectedApplicationId 'com.local.mediaviewer' `
            -ExpectedVersionCode 3 `
            -ExpectedVersionName '1.1.0' `
            -ExpectedMinSdk 29 `
            -ExpectedTargetSdk 36 `
            -ExpectedAbi 'arm64-v8a'
    } 'minSdk'
    Assert-ThrowsLike {
        Assert-ApkBadgingMetadata `
            -Badging (
                $validBadging -replace
                    "targetSdkVersion:'36'",
                    "targetSdkVersion:'35'"
            ) `
            -ExpectedApplicationId 'com.local.mediaviewer' `
            -ExpectedVersionCode 3 `
            -ExpectedVersionName '1.1.0' `
            -ExpectedMinSdk 29 `
            -ExpectedTargetSdk 36 `
            -ExpectedAbi 'arm64-v8a'
    } 'targetSdk'
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
