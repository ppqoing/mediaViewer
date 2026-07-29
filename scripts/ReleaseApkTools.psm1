Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Find-CompleteAndroidBuildTools {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$SdkRoot
    )

    $resolvedSdkRoot = [IO.Path]::GetFullPath($SdkRoot)
    $versionedDirectories = @(
        foreach ($directory in Get-ChildItem `
            -LiteralPath (
                Join-Path $resolvedSdkRoot 'build-tools'
            ) `
            -Directory) {
            $version = $null
            if (-not [version]::TryParse(
                $directory.Name,
                [ref]$version
            )) {
                continue
            }
            [PSCustomObject]@{
                Directory = $directory
                Version = $version
            }
        }
    )
    $candidate = $versionedDirectories |
        Sort-Object Version -Descending |
        Where-Object {
            $directory = $_.Directory
            @(
                @(
                    'aapt.exe',
                    'zipalign.exe',
                    'apksigner.bat'
                ) | Where-Object {
                    -not (Test-Path -LiteralPath (
                        Join-Path $directory.FullName $_
                    ) -PathType Leaf)
                }
            ).Count -eq 0
        } |
        Select-Object -First 1
    if ($null -eq $candidate) {
        throw 'Android SDK 中缺少完整稳定 Build Tools'
    }
    [IO.Path]::GetFullPath(
        $candidate.Directory.FullName
    )
}

function Assert-ApkBadgingMetadata {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string[]]$Badging,

        [Parameter(Mandatory)]
        [string]$ExpectedApplicationId,

        [Parameter(Mandatory)]
        [int]$ExpectedVersionCode,

        [Parameter(Mandatory)]
        [string]$ExpectedVersionName,

        [Parameter(Mandatory)]
        [int]$ExpectedMinSdk,

        [Parameter(Mandatory)]
        [int]$ExpectedTargetSdk,

        [Parameter(Mandatory)]
        [string]$ExpectedAbi
    )

    $packageLine = $Badging |
        Where-Object { $_ -like 'package:*' } |
        Select-Object -First 1
    if (
        $packageLine -notmatch
            "name='$([regex]::Escape($ExpectedApplicationId))'" -or
        $packageLine -notmatch
            "versionCode='$ExpectedVersionCode'" -or
        $packageLine -notmatch
            "versionName='$([regex]::Escape($ExpectedVersionName))'"
    ) {
        throw "APK package/version 元数据错误：$packageLine"
    }
    if ($Badging -notcontains "sdkVersion:'$ExpectedMinSdk'") {
        throw "APK minSdk 不是 $ExpectedMinSdk"
    }
    if (
        $Badging -notcontains
            "targetSdkVersion:'$ExpectedTargetSdk'"
    ) {
        throw "APK targetSdk 不是 $ExpectedTargetSdk"
    }
    if ($Badging -notcontains "native-code: '$ExpectedAbi'") {
        throw "APK ABI 不是严格的 $ExpectedAbi"
    }

    [PSCustomObject]@{
        ApplicationId = $ExpectedApplicationId
        VersionCode = $ExpectedVersionCode
        VersionName = $ExpectedVersionName
        MinSdk = $ExpectedMinSdk
        TargetSdk = $ExpectedTargetSdk
        Abi = $ExpectedAbi
    }
}

function Get-ZipArchiveCompressionMethods {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$ApkPath
    )

    $stream = [IO.File]::OpenRead($ApkPath)
    try {
        $minimumEndOfCentralDirectorySize = 22
        $maximumCommentLength = 65535
        if ($stream.Length -lt $minimumEndOfCentralDirectorySize) {
            throw 'APK 不是有效的 ZIP：缺少中央目录结束记录'
        }

        $searchLength = [int][math]::Min(
            $stream.Length,
            $minimumEndOfCentralDirectorySize + $maximumCommentLength
        )
        $tail = [byte[]]::new($searchLength)
        $stream.Position = $stream.Length - $searchLength
        [void]$stream.Read($tail, 0, $tail.Length)

        $endOfCentralDirectoryOffset = -1
        for ($index = $tail.Length - $minimumEndOfCentralDirectorySize;
            $index -ge 0;
            $index--) {
            if ([BitConverter]::ToUInt32($tail, $index) -ne 0x06054b50) {
                continue
            }
            $commentLength = [BitConverter]::ToUInt16($tail, $index + 20)
            if ($index + $minimumEndOfCentralDirectorySize +
                $commentLength -eq $tail.Length) {
                $endOfCentralDirectoryOffset = $index
                break
            }
        }
        if ($endOfCentralDirectoryOffset -lt 0) {
            throw 'APK 不是有效的 ZIP：找不到中央目录结束记录'
        }

        $entryCount = [BitConverter]::ToUInt16(
            $tail,
            $endOfCentralDirectoryOffset + 10
        )
        $centralDirectoryOffset = [BitConverter]::ToUInt32(
            $tail,
            $endOfCentralDirectoryOffset + 16
        )
        if ($entryCount -eq [uint16]::MaxValue -or
            $centralDirectoryOffset -eq [uint32]::MaxValue) {
            throw '不支持 ZIP64 APK'
        }

        $stream.Position = $centralDirectoryOffset
        $reader = [IO.BinaryReader]::new(
            $stream,
            [Text.Encoding]::UTF8,
            $true
        )
        try {
            $methods = @{}
            for ($entryIndex = 0; $entryIndex -lt $entryCount; $entryIndex++) {
                if ($reader.ReadUInt32() -ne 0x02014b50) {
                    throw 'APK 不是有效的 ZIP：中央目录条目无效'
                }
                [void]$reader.ReadUInt16()
                [void]$reader.ReadUInt16()
                $flags = $reader.ReadUInt16()
                $compressionMethod = $reader.ReadUInt16()
                [void]$reader.ReadUInt16()
                [void]$reader.ReadUInt16()
                [void]$reader.ReadUInt32()
                [void]$reader.ReadUInt32()
                [void]$reader.ReadUInt32()
                $nameLength = $reader.ReadUInt16()
                $extraLength = $reader.ReadUInt16()
                $commentLength = $reader.ReadUInt16()
                [void]$reader.ReadUInt16()
                [void]$reader.ReadUInt16()
                [void]$reader.ReadUInt32()
                [void]$reader.ReadUInt32()

                $nameBytes = $reader.ReadBytes($nameLength)
                if ($nameBytes.Length -ne $nameLength) {
                    throw 'APK 不是有效的 ZIP：条目路径不完整'
                }
                $encoding = if ($flags -band 0x0800) {
                    [Text.Encoding]::UTF8
                } else {
                    [Text.Encoding]::GetEncoding(437)
                }
                $name = $encoding.GetString($nameBytes)
                $stream.Position += $extraLength + $commentLength
                $methods[$name] = [int]$compressionMethod
            }
            $methods
        } finally {
            $reader.Dispose()
        }
    } finally {
        $stream.Dispose()
    }
}

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
    $compressionMethods = Get-ZipArchiveCompressionMethods -ApkPath $resolvedApk
    $archive = [IO.Compression.ZipFile]::OpenRead($resolvedApk)
    try {
        @(
            foreach ($entry in $archive.Entries) {
                $abi = $null
                if ($entry.FullName -match '^lib/([^/]+)/[^/]+\.so$') {
                    $abi = $Matches[1]
                }
                if (-not $compressionMethods.ContainsKey($entry.FullName)) {
                    throw "APK ZIP 中央目录缺少条目：$($entry.FullName)"
                }
                $compressionMethod = $compressionMethods[$entry.FullName]
                [PSCustomObject]@{
                    Path = $entry.FullName
                    Abi = $abi
                    Length = [int64]$entry.Length
                    CompressedLength = [int64]$entry.CompressedLength
                    CompressionMethod = [int]$compressionMethod
                    IsCompressed = ($compressionMethod -eq 8)
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

Export-ModuleMember -Function @(
    'Find-CompleteAndroidBuildTools',
    'Assert-ApkBadgingMetadata',
    'Get-ApkArchiveInventory',
    'Assert-Arm64CompressedArchive',
    'Write-VerifiedSha256',
    'Get-ApkSignerCertificateSha256'
)
