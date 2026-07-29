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
$buildScript = Join-Path `
    $repositoryRoot `
    'scripts\Build-PersonalRelease.ps1'
$defaultDebugKeystore = Join-Path `
    $env:USERPROFILE `
    '.android\debug.keystore'
if (-not (Test-Path -LiteralPath $defaultDebugKeystore -PathType Leaf)) {
    throw '测试前提不满足：默认 Android debug keystore 不存在'
}

function Invoke-ExpectedBuildFailure {
    param(
        [Parameter(Mandatory)]
        [string[]]$Arguments
    )

    $output = (
        & pwsh `
            -NoProfile `
            -File $buildScript `
            -SdkRoot $SdkRoot `
            @Arguments 2>&1 |
            Out-String
    )
    if ($LASTEXITCODE -eq 0) {
        throw '预期个人 Release 脚本失败，但它成功了'
    }
    $output
}

$failures = @()
$missingKeystore = Join-Path `
    ([IO.Path]::GetTempPath()) `
    ('missing-keystore-' + [guid]::NewGuid().ToString('N') + '.jks')
$missingKeystoreOutput = Invoke-ExpectedBuildFailure -Arguments @(
    '-KeystorePath',
    $missingKeystore
)
if ($missingKeystoreOutput.Contains($missingKeystore)) {
    $failures += '缺失 keystore 错误泄露完整路径'
}

$distDirectory = Join-Path $repositoryRoot 'dist'
$officialApk = Join-Path `
    $distDirectory `
    'mediaviewer-v1.0.1-arm64-v8a-release.apk'
$officialChecksum = "$officialApk.sha256"
New-Item `
    -ItemType Directory `
    -Path $distDirectory `
    -Force | Out-Null
[IO.File]::WriteAllText($officialApk, 'sentinel-old-apk')
[IO.File]::WriteAllText(
    $officialChecksum,
    'sentinel-old-checksum'
)
$signingOutput = Invoke-ExpectedBuildFailure -Arguments @(
    '-KeystorePath',
    $defaultDebugKeystore,
    '-KeyAlias',
    'alias-that-must-not-exist'
)
if ($signingOutput.Contains($defaultDebugKeystore)) {
    $failures += '签名失败错误泄露 keystore 路径'
}
if ($signingOutput.Contains('--ks')) {
    $failures += '签名失败错误泄露签名参数'
}
if (Test-Path -LiteralPath $officialApk) {
    $failures += '签名失败后仍保留旧的官方 APK'
}
if (Test-Path -LiteralPath $officialChecksum) {
    $failures += '签名失败后仍保留旧的官方校验文件'
}

if ($failures.Count -gt 0) {
    throw ($failures -join '; ')
}

Write-Host 'Build-PersonalRelease 敏感失败路径测试通过'
