[CmdletBinding()]
param(
    [string]$ApkPath = (
        Join-Path $PSScriptRoot `
            '..\app\build\outputs\apk\debug\app-debug.apk'
    )
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = [IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot '..')
)
$sourceApk = [IO.Path]::GetFullPath($ApkPath)
$distDirectory = Join-Path $repositoryRoot 'dist'
$targetApk = Join-Path $distDirectory 'mediaviewer-debug.apk'
$checksumPath = Join-Path `
    $distDirectory `
    'mediaviewer-debug.apk.sha256'

if (-not (Test-Path -LiteralPath $sourceApk -PathType Leaf)) {
    throw "未找到 Debug APK：$sourceApk"
}

New-Item `
    -ItemType Directory `
    -Path $distDirectory `
    -Force | Out-Null
Copy-Item `
    -LiteralPath $sourceApk `
    -Destination $targetApk `
    -Force
$hash = (
    Get-FileHash `
        -LiteralPath $targetApk `
        -Algorithm SHA256
).Hash.ToLowerInvariant()
$utf8NoBom = [Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText(
    $checksumPath,
    "$hash  mediaviewer-debug.apk`n",
    $utf8NoBom
)

$verified = (
    Get-FileHash `
        -LiteralPath $targetApk `
        -Algorithm SHA256
).Hash.ToLowerInvariant()
if ($verified -ne $hash) {
    throw '复制后的 APK SHA-256 二次校验失败'
}

[PSCustomObject]@{
    Apk = $targetApk
    Sha256File = $checksumPath
    Sha256 = $hash
}
