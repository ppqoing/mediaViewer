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
