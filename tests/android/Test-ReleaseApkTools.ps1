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
