# Windows HTTP Range 服务器实施计划

> **自动化执行要求：** 必须使用 `superpowers:subagent-driven-development`（推荐）
> 或 `superpowers:executing-plans` 逐项执行本计划。所有步骤均使用复选框跟踪。

**目标：** 在 Windows 上部署一个开机自启、仅允许局域网访问、支持目录浏览和
HTTP Range 的只读媒体文件服务器。

**架构：** 使用 Caddy 2.11.4 原生 Windows 二进制监听 TCP 8080。Caddy 将
`/middle/` 和 `/pik/` 分别映射到两个本地磁盘目录；Windows 服务使用
`LocalService` 账户和专用服务 SID，Windows Defender 防火墙将来源限制为
`LocalSubnet`。

**技术栈：** Caddy 2.11.4、Windows PowerShell、Windows Service Control
Manager、Windows Defender Firewall、curl.exe。

## 全局约束

- 服务仅使用明文 HTTP，不配置 TLS 或自动 HTTPS。
- 服务监听 TCP `8080`。
- `/middle/` 映射 `I:\MiddleDir`，并开启目录列表。
- `/pik/` 映射 `G:\pik`，并开启目录列表。
- 不提供身份认证、上传、删除、重命名或 WebDAV。
- 防火墙远程地址必须严格限制为 `LocalSubnet`。
- 服务不得以 `LocalSystem` 身份运行；使用 `NT AUTHORITY\LocalService`
  并通过 `NT SERVICE\MediaRangeCaddy` 获得必要权限。
- 安装程序必须校验官方发布包的 SHA-256 校验和。
- 所有操作说明和项目文档使用中文。

---

## 文件结构

- `deploy/windows/Caddyfile`：定义 HTTP 路由、目录映射、目录列表和日志。
- `deploy/windows/install.ps1`：下载、校验并安装 Caddy，注册服务和防火墙。
- `deploy/windows/uninstall.ps1`：删除服务、防火墙和专用 ACL，保留媒体文件。
- `deploy/windows/verify.ps1`：验证配置、服务、目录列表和 Range 行为。
- `deploy/windows/README.md`：中文运维说明。
- `tests/windows/Test-Caddyfile.ps1`：启动临时 Caddy 进程并验证真实 HTTP 行为。
- `tests/windows/Test-Scripts.ps1`：执行安装预检并验证脚本的可运行契约。

### 任务 1：Caddy 路由配置

**文件：**

- 新建：`tests/windows/Test-Caddyfile.ps1`
- 新建：`deploy/windows/Caddyfile`

**接口：**

- 输入：`I:\MiddleDir`、`G:\pik`、TCP 8080。
- 输出：`/middle/`、`/pik/` 两个可浏览的只读 HTTP 路径。

- [ ] **步骤 1：编写失败的 Caddy HTTP 行为测试**

```powershell
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$CaddyExe
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$configPath = Join-Path $repoRoot 'deploy\windows\Caddyfile'

if (-not (Test-Path -LiteralPath $configPath -PathType Leaf)) {
    throw "缺少 Caddyfile：$configPath"
}

if (-not (Test-Path -LiteralPath $CaddyExe -PathType Leaf)) {
    throw "缺少 Caddy 测试二进制：$CaddyExe"
}

$temporaryDirectory = Join-Path ([IO.Path]::GetTempPath()) (
    'media-range-test-' + [guid]::NewGuid().ToString('N')
)
New-Item -ItemType Directory -Path $temporaryDirectory | Out-Null
$oldLogPath = $env:MEDIA_RANGE_LOG
$env:MEDIA_RANGE_LOG = Join-Path $temporaryDirectory 'access.log'

$process = Start-Process -FilePath $CaddyExe -PassThru -WindowStyle Hidden `
    -ArgumentList @('run', '--config', $configPath, '--adapter', 'caddyfile')

try {
    $ready = $false
    foreach ($attempt in 1..50) {
        $status = & curl.exe --silent --output NUL --write-out '%{http_code}' `
            'http://127.0.0.1:8080/middle/'
        if ($status -eq '200') {
            $ready = $true
            break
        }
        Start-Sleep -Milliseconds 100
    }
    if (-not $ready) {
        throw '临时 Caddy 未在 5 秒内就绪。'
    }

    foreach ($path in @('/middle/', '/pik/')) {
        $status = & curl.exe --silent --output NUL --write-out '%{http_code}' `
            ("http://127.0.0.1:8080" + $path)
        if ($status -ne '200') {
            throw "$path 未返回目录列表，状态码：$status"
        }
    }

    $sample = Get-ChildItem -LiteralPath 'I:\MiddleDir' -File -Recurse |
        Where-Object Length -GE 1024 |
        Select-Object -First 1
    if (-not $sample) {
        throw 'I:\MiddleDir 中没有可用于 Range 测试的文件。'
    }

    $relative = $sample.FullName.Substring('I:\MiddleDir'.Length).TrimStart('\')
    $encoded = (($relative -split '\\') |
        ForEach-Object { [uri]::EscapeDataString($_) }) -join '/'
    $url = 'http://127.0.0.1:8080/middle/' + $encoded
    $headers = Join-Path $temporaryDirectory 'headers.txt'
    $body = Join-Path $temporaryDirectory 'body.bin'

    & curl.exe --silent --show-error --dump-header $headers --output $body `
        --header 'Range: bytes=0-1023' $url
    if ($LASTEXITCODE -ne 0) {
        throw 'Range 请求失败。'
    }
    if (-not (Select-String -LiteralPath $headers -Pattern '^HTTP/\S+ 206 ')) {
        throw 'Range 请求未返回 206。'
    }
    if (-not (Select-String -LiteralPath $headers -Pattern '^Content-Range:' )) {
        throw 'Range 响应缺少 Content-Range。'
    }
    if ((Get-Item -LiteralPath $body).Length -ne 1024) {
        throw 'Range 响应体不是 1024 字节。'
    }

    $invalidStart = $sample.Length + 1
    $status416 = & curl.exe --silent --output NUL --write-out '%{http_code}' `
        --header "Range: bytes=$invalidStart-" $url
    if ($status416 -ne '416') {
        throw "越界 Range 未返回 416，而是 $status416。"
    }

    $status404 = & curl.exe --silent --output NUL --write-out '%{http_code}' `
        'http://127.0.0.1:8080/not-configured'
    if ($status404 -ne '404') {
        throw "未配置路径未返回 404，而是 $status404。"
    }
} finally {
    if ($process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id -Force
        $process.WaitForExit()
    }
    $env:MEDIA_RANGE_LOG = $oldLogPath
    Remove-Item -LiteralPath $temporaryDirectory -Recurse -Force
}

Write-Host 'Caddy HTTP 行为验证通过。'
```

- [ ] **步骤 2：运行测试并确认失败**

运行：

```powershell
pwsh -NoProfile -File tests/windows/Test-Caddyfile.ps1 `
    -CaddyExe C:\Temp\caddy-2.11.4\caddy.exe
```

预期：失败并报告缺少 `deploy\windows\Caddyfile`。

- [ ] **步骤 3：创建最小 Caddyfile**

```caddyfile
{
	auto_https off
	admin 127.0.0.1:2019
}

http://:8080 {
	log {
		output file {$MEDIA_RANGE_LOG:C:/ProgramData/Caddy/logs/access.log} {
			roll_size 10MiB
			roll_keep 10
			roll_keep_for 720h
		}
		format json
	}

	redir /middle /middle/ 308
	redir /pik /pik/ 308

	handle_path /middle/* {
		root * I:/MiddleDir
		file_server browse
	}

	handle_path /pik/* {
		root * G:/pik
		file_server browse
	}

	respond 404
}
```

- [ ] **步骤 4：重新运行测试**

运行：

```powershell
pwsh -NoProfile -File tests/windows/Test-Caddyfile.ps1 `
    -CaddyExe C:\Temp\caddy-2.11.4\caddy.exe
```

预期：临时启动 Caddy，并输出 `Caddy HTTP 行为验证通过。`

- [ ] **步骤 5：提交**

```powershell
git add deploy/windows/Caddyfile tests/windows/Test-Caddyfile.ps1
git commit -m "feat: add Caddy media routes"
```

### 任务 2：可重复安装与回滚脚本

**文件：**

- 新建：`tests/windows/Test-Scripts.ps1`
- 新建：`deploy/windows/install.ps1`
- 新建：`deploy/windows/uninstall.ps1`

**接口：**

- 输入：管理员权限、互联网连接、两个已存在的媒体目录。
- 输出：`MediaRangeCaddy` 服务、`MediaRange HTTP 8080` 防火墙规则和已校验的
  Caddy 2.11.4 安装。

- [ ] **步骤 1：编写失败的安装预检行为测试**

`tests/windows/Test-Scripts.ps1` 接受已校验的 Caddy 测试二进制路径，首先使用
PowerShell 解析器拒绝语法错误，然后实际执行安装脚本的 `-ValidateOnly`
模式。测试断言预检结果明确报告版本、服务名、端口、两个媒体目录以及配置
验证成功：

```powershell
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$CaddyExe
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$installPath = Join-Path $repoRoot 'deploy\windows\install.ps1'
$uninstallPath = Join-Path $repoRoot 'deploy\windows\uninstall.ps1'

foreach ($path in @($installPath, $uninstallPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "缺少部署脚本：$path"
    }

    $tokens = $null
    $errors = $null
    [System.Management.Automation.Language.Parser]::ParseFile(
        $path,
        [ref]$tokens,
        [ref]$errors
    ) | Out-Null
    if ($errors.Count -ne 0) {
        throw "PowerShell 语法错误：$($errors[0])"
    }
}

$result = & $installPath -ValidateOnly -CaddyExe $CaddyExe
if ($result.CaddyVersion -ne '2.11.4') {
    throw '安装预检返回了错误的 Caddy 版本。'
}
if ($result.ServiceName -ne 'MediaRangeCaddy' -or $result.Port -ne 8080) {
    throw '安装预检返回了错误的服务名或端口。'
}
if ($result.MediaDirectories.Count -ne 2) {
    throw '安装预检没有返回两个媒体目录。'
}
if (-not $result.ConfigValid) {
    throw '安装预检没有验证 Caddyfile。'
}

Write-Host 'PowerShell 部署脚本预检验证通过。'
```

安装脚本必须：

1. 使用 `#Requires -RunAsAdministrator`。
2. 提供不修改系统的
   `-ValidateOnly -CaddyExe C:\Temp\caddy-2.11.4\caddy.exe` 模式，用于检查
   媒体目录、8080 端口和 Caddyfile。
3. 从 Caddy GitHub 官方 `v2.11.4` 发布页下载 Windows AMD64 ZIP 和校验和文件。
4. 从校验和文件提取 ZIP 的预期 SHA-256，并与 `Get-FileHash` 结果比较。
5. 将二进制安装到 `C:\Program Files\Caddy`，配置安装到
   `C:\ProgramData\Caddy`。
6. 在注册服务之前运行 `caddy validate`。
7. 注册 `MediaRangeCaddy` 自动启动服务，使用
   `NT AUTHORITY\LocalService`，并启用专用服务 SID。
8. 仅向 `NT SERVICE\MediaRangeCaddy` 授予媒体目录读取权限和日志目录修改权限。
9. 创建仅允许 `LocalSubnet` 访问 TCP 8080 的防火墙规则。
10. 启动服务；任一步骤失败时停止并删除新建服务及防火墙规则。

卸载脚本必须在删除服务之前移除专用服务 SID 的 ACL，并且只能删除以下明确
路径：

```text
C:\Program Files\Caddy
C:\ProgramData\Caddy
```

- [ ] **步骤 2：运行测试并确认失败**

```powershell
pwsh -NoProfile -File tests/windows/Test-Scripts.ps1 `
    -CaddyExe C:\Temp\caddy-2.11.4\caddy.exe
```

预期：失败并报告缺少安装或卸载脚本。

- [ ] **步骤 3：实现安装脚本**

实现以下固定变量和阶段：

```powershell
$CaddyVersion = '2.11.4'
$ServiceName = 'MediaRangeCaddy'
$ServiceAccount = 'NT AUTHORITY\LocalService'
$ServicePrincipal = 'NT SERVICE\MediaRangeCaddy'
$FirewallRuleName = 'MediaRange HTTP 8080'
$InstallDirectory = 'C:\Program Files\Caddy'
$DataDirectory = 'C:\ProgramData\Caddy'
$MediaDirectories = @('I:\MiddleDir', 'G:\pik')
```

安装脚本参数固定为：

```powershell
[CmdletBinding()]
param(
    [switch]$ValidateOnly,
    [string]$CaddyExe
)
```

`-ValidateOnly` 不创建目录、服务、ACL 或防火墙规则；成功时返回：

```powershell
[pscustomobject]@{
    CaddyVersion = '2.11.4'
    ServiceName = 'MediaRangeCaddy'
    Port = 8080
    MediaDirectories = @('I:\MiddleDir', 'G:\pik')
    ConfigValid = $true
}
```

下载地址固定为：

```text
https://github.com/caddyserver/caddy/releases/download/v2.11.4/caddy_2.11.4_windows_amd64.zip
https://github.com/caddyserver/caddy/releases/download/v2.11.4/caddy_2.11.4_checksums.txt
```

服务命令行固定为：

```text
"C:\Program Files\Caddy\caddy.exe" run --config "C:\ProgramData\Caddy\Caddyfile" --adapter caddyfile
```

使用 `sc.exe create`、`sc.exe config` 和 `sc.exe sidtype ... unrestricted`
完成服务注册；检查每次 `sc.exe` 调用的退出码。

- [ ] **步骤 4：实现卸载脚本**

卸载顺序固定为：

1. 停止 `MediaRangeCaddy`。
2. 删除 `MediaRange HTTP 8080` 防火墙规则。
3. 从两个媒体目录和 Caddy 目录移除
   `NT SERVICE\MediaRangeCaddy` 的显式 ACL。
4. 使用 `sc.exe delete MediaRangeCaddy` 删除服务。
5. 核验两个安装目录的规范化绝对路径，再递归删除它们。
6. 不接触 `I:\MiddleDir` 和 `G:\pik` 中的任何文件。

- [ ] **步骤 5：验证语法和契约**

```powershell
pwsh -NoProfile -File tests/windows/Test-Scripts.ps1 `
    -CaddyExe C:\Temp\caddy-2.11.4\caddy.exe
```

预期：输出 `PowerShell 部署脚本预检验证通过。`

- [ ] **步骤 6：提交**

```powershell
git add deploy/windows/install.ps1 deploy/windows/uninstall.ps1 tests/windows/Test-Scripts.ps1
git commit -m "feat: add Windows Caddy installer"
```

### 任务 3：运行时验证和中文运维文档

**文件：**

- 修改：`tests/windows/Test-Scripts.ps1`
- 新建：`deploy/windows/verify.ps1`
- 新建：`deploy/windows/README.md`

**接口：**

- 输入：已运行的 `MediaRangeCaddy` 服务。
- 输出：服务、ACL、防火墙、目录浏览、206、416、404 的验证报告。

- [ ] **步骤 1：扩充失败的验证脚本行为测试**

修改 `tests/windows/Test-Scripts.ps1`：要求 `verify.ps1` 存在且无 PowerShell
语法错误，然后执行其不依赖已安装服务的配置验证模式：

```powershell
$verifyPath = Join-Path $repoRoot 'deploy\windows\verify.ps1'
if (-not (Test-Path -LiteralPath $verifyPath -PathType Leaf)) {
    throw "缺少运行时验证脚本：$verifyPath"
}

$tokens = $null
$errors = $null
[System.Management.Automation.Language.Parser]::ParseFile(
    $verifyPath,
    [ref]$tokens,
    [ref]$errors
) | Out-Null
if ($errors.Count -ne 0) {
    throw "verify.ps1 存在语法错误：$($errors[0])"
}

$configResult = & $verifyPath -ConfigOnly -CaddyExe $CaddyExe
if (-not $configResult.ConfigValid) {
    throw 'verify.ps1 未能验证 Caddyfile。'
}
```

README 是面向人的运维文档，不做文字匹配测试；任务完成时直接审阅内容。

- [ ] **步骤 2：运行测试并确认失败**

```powershell
pwsh -NoProfile -File tests/windows/Test-Scripts.ps1 `
    -CaddyExe C:\Temp\caddy-2.11.4\caddy.exe
```

预期：失败并报告缺少 `verify.ps1`。

- [ ] **步骤 3：实现运行时验证脚本**

验证脚本必须：

1. 支持 `-ConfigOnly -CaddyExe C:\Temp\caddy-2.11.4\caddy.exe`，只运行
   Caddyfile 语法验证并返回 `ConfigValid = $true`。
2. 检查服务状态为 `Running`、启动类型为 `Auto`、账户为
   `NT AUTHORITY\LocalService`。
3. 检查防火墙规则启用且远程地址包含 `LocalSubnet`。
4. 使用 `curl.exe` 验证 `/middle/` 和 `/pik/` 返回 `200`。
5. 在两个目录中选择首个长度不少于 1024 字节的普通文件，对相应 URL 发出
   `Range: bytes=0-1023`，断言状态码为 `206`、存在 `Content-Range`，
   且响应体为 1024 字节。
6. 对同一文件发送超过文件末尾的 Range，断言状态码为 `416`。
7. 请求 `/not-configured`，断言状态码为 `404`。
8. 输出服务器所有可用的私有 IPv4 访问地址。

- [ ] **步骤 4：编写中文 README**

README 必须记录：

- 两个入口 URL。
- 目录列表已启用。
- 服务启动、停止、重启、查看状态和验证命令。
- Caddyfile 修改后的验证与重启流程。
- 日志位置。
- 局域网 HTTP 的安全边界。
- 卸载命令和不会删除媒体文件的保证。

- [ ] **步骤 5：运行静态测试**

```powershell
pwsh -NoProfile -File tests/windows/Test-Caddyfile.ps1 `
    -CaddyExe C:\Temp\caddy-2.11.4\caddy.exe
pwsh -NoProfile -File tests/windows/Test-Scripts.ps1 `
    -CaddyExe C:\Temp\caddy-2.11.4\caddy.exe
```

预期：两个测试均通过。

- [ ] **步骤 6：提交**

```powershell
git add deploy/windows/verify.ps1 deploy/windows/README.md tests/windows/Test-Scripts.ps1
git commit -m "test: add range server verification"
```

### 任务 4：安装并完成端到端验收

**文件：**

- 不新增仓库文件。
- 修改系统：Caddy 程序、Windows 服务、ACL 和防火墙规则。

**接口：**

- 输入：任务 1 至任务 3 的已提交部署文件。
- 输出：运行中的局域网 HTTP Range 服务。

- [ ] **步骤 1：再次运行仓库行为测试**

```powershell
pwsh -NoProfile -File tests/windows/Test-Caddyfile.ps1 `
    -CaddyExe C:\Temp\caddy-2.11.4\caddy.exe
pwsh -NoProfile -File tests/windows/Test-Scripts.ps1 `
    -CaddyExe C:\Temp\caddy-2.11.4\caddy.exe
```

预期：两个测试均通过。

- [ ] **步骤 2：以管理员权限执行安装**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File deploy/windows/install.ps1
```

预期：Caddy 2.11.4 校验成功，`MediaRangeCaddy` 启动，防火墙规则创建成功。

- [ ] **步骤 3：运行端到端验证**

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File deploy/windows/verify.ps1
```

预期：配置、服务、ACL、防火墙、两个目录列表、206 Range、416 和 404 全部通过。

- [ ] **步骤 4：验证服务重启**

```powershell
Restart-Service MediaRangeCaddy
pwsh -NoProfile -ExecutionPolicy Bypass -File deploy/windows/verify.ps1
```

预期：重启后所有验证仍通过。

- [ ] **步骤 5：检查仓库状态**

```powershell
git status --short
git log --oneline -5
```

预期：工作区干净，部署文件和测试均已提交。
