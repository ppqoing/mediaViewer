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
- `tests/windows/Test-Caddyfile.ps1`：静态验证 Caddyfile 安全边界。
- `tests/windows/Test-Scripts.ps1`：验证 PowerShell 语法和安装契约。

### 任务 1：Caddy 路由配置

**文件：**

- 新建：`tests/windows/Test-Caddyfile.ps1`
- 新建：`deploy/windows/Caddyfile`

**接口：**

- 输入：`I:\MiddleDir`、`G:\pik`、TCP 8080。
- 输出：`/middle/`、`/pik/` 两个可浏览的只读 HTTP 路径。

- [ ] **步骤 1：编写失败的 Caddyfile 契约测试**

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$configPath = Join-Path $repoRoot 'deploy\windows\Caddyfile'

if (-not (Test-Path -LiteralPath $configPath -PathType Leaf)) {
    throw "缺少 Caddyfile：$configPath"
}

$text = Get-Content -Raw -LiteralPath $configPath
$required = @(
    'http://:8080',
    'root * I:/MiddleDir',
    'root * G:/pik',
    'redir /middle /middle/ 308',
    'redir /pik /pik/ 308',
    'respond 404'
)

foreach ($item in $required) {
    if (-not $text.Contains($item)) {
        throw "Caddyfile 缺少：$item"
    }
}

if ([regex]::Matches($text, 'file_server\s+browse').Count -ne 2) {
    throw 'Caddyfile 必须且只能配置两个 file_server browse。'
}

foreach ($forbidden in @('https://', 'basic_auth', 'basicauth', 'encode ')) {
    if ($text.Contains($forbidden)) {
        throw "Caddyfile 包含禁止配置：$forbidden"
    }
}

Write-Host 'Caddyfile 静态契约验证通过。'
```

- [ ] **步骤 2：运行测试并确认失败**

运行：

```powershell
pwsh -NoProfile -File tests/windows/Test-Caddyfile.ps1
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
		output file C:/ProgramData/Caddy/logs/access.log {
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
pwsh -NoProfile -File tests/windows/Test-Caddyfile.ps1
```

预期：输出 `Caddyfile 静态契约验证通过。`

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

- [ ] **步骤 1：编写失败的脚本契约测试**

测试必须先确认两个脚本存在，再通过
`System.Management.Automation.Language.Parser.ParseFile()` 验证无语法错误，
并验证以下不可变条件：

```powershell
$requiredInstallText = @(
    "`$CaddyVersion = '2.11.4'",
    "`$ServiceName = 'MediaRangeCaddy'",
    "`$FirewallRuleName = 'MediaRange HTTP 8080'",
    'caddy_2.11.4_windows_amd64.zip',
    'caddy_2.11.4_checksums.txt',
    'Get-FileHash',
    'LocalSubnet',
    'NT AUTHORITY\LocalService',
    'NT SERVICE\MediaRangeCaddy',
    'I:\MiddleDir',
    'G:\pik',
    'validate --config',
    'Start-Service'
)

$requiredUninstallText = @(
    'Stop-Service',
    'Remove-NetFirewallRule',
    'NT SERVICE\MediaRangeCaddy',
    'sc.exe',
    'delete'
)
```

安装脚本必须：

1. 使用 `#Requires -RunAsAdministrator`。
2. 检查媒体目录和 8080 端口。
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
pwsh -NoProfile -File tests/windows/Test-Scripts.ps1
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
pwsh -NoProfile -File tests/windows/Test-Scripts.ps1
```

预期：输出 `PowerShell 部署脚本契约验证通过。`

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

- [ ] **步骤 1：扩充失败的脚本契约测试**

增加对 `verify.ps1` 和 `README.md` 的存在性、PowerShell 语法及以下验证关键词
的断言：

```powershell
$requiredVerifyText = @(
    'MediaRangeCaddy',
    'MediaRange HTTP 8080',
    'LocalSubnet',
    'http://127.0.0.1:8080',
    '/middle/',
    '/pik/',
    'bytes=0-1023',
    '206',
    'Content-Range',
    '416',
    '404'
)
```

- [ ] **步骤 2：运行测试并确认失败**

```powershell
pwsh -NoProfile -File tests/windows/Test-Scripts.ps1
```

预期：失败并报告缺少 `verify.ps1` 或 `README.md`。

- [ ] **步骤 3：实现运行时验证脚本**

验证脚本必须：

1. 运行 Caddyfile 语法验证。
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
pwsh -NoProfile -File tests/windows/Test-Caddyfile.ps1
pwsh -NoProfile -File tests/windows/Test-Scripts.ps1
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

- [ ] **步骤 1：再次运行仓库静态测试**

```powershell
pwsh -NoProfile -File tests/windows/Test-Caddyfile.ps1
pwsh -NoProfile -File tests/windows/Test-Scripts.ps1
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
