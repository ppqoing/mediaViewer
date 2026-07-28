# TODO 10 Documentation, APK, and Final Acceptance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 更新中文使用文档，在干净实现提交上完成含真实目录 JSON 烟测的统一验收，并交付新 APK 和 SHA-256。

**Architecture:** 先提交代码、测试、README 和验收脚本更新，保证统一脚本从干净提交启动；脚本生成 APK、校验和及新的验收记录，再单独提交验收记录。

**Tech Stack:** PowerShell、Gradle、adb、aapt、Git、现有 Android 验收脚本。

## Global Constraints

- 真实服务器只验证 `/middle/` 与 `/pik/` 的 HTTP 200 和 Caddy JSON。
- 不读取或输出真实目录条目和媒体正文。
- `dist/*.apk` 与 `dist/*.sha256` 继续被 Git 忽略。
- 不新增运行时依赖；若依赖未变化，只核对而不改第三方说明版本表。
- 验收记录必须指向干净的实现/文档提交。

## Files

- Modify: `README.md`
- Modify: `scripts/Invoke-AndroidVerification.ps1`
- Modify only if required: `THIRD_PARTY_NOTICES.md`
- Regenerate: `docs/verification/2026-07-28-android-mediaviewer.md`
- Generate ignored: `dist/mediaviewer-debug.apk`
- Generate ignored: `dist/mediaviewer-debug.apk.sha256`

## Documentation Content

README must explain:

- 四种视频模式及“每个新视频恢复等比适应”；
- 全屏模式按钮；
- 条漫/单图默认设置；
- 当前目录、初始锚点和六种排序；
- 条漫统一 `1×–5×` 缩放；
- 动态加载与超大图片可能降采样；
- 仍无磁盘缓存和离线副本；
- 原有 HTTP、IPv4、安装和常见问题。

Verification record must include:

```text
LibVLC VLCVideoLayout 输出几何：通过
四种视频画面模式：通过
条漫/单图默认设置：通过
六种图片排序与锚点：通过
50 图片动态加载：通过
统一缩放与解码上限：通过
```

## Steps

- [ ] **Step 1: Update README in Chinese**

Add a “视频画面模式” section:

```markdown
## 视频画面模式

视频控制栏和全屏页均可选择：

- 等比适应：完整显示，可能出现黑边；
- 裁剪铺满：保持比例铺满屏幕，边缘可能被裁剪；
- 强制拉伸：宽高铺满屏幕，画面可能变形；
- 原始尺寸：按视频原始输出尺寸显示。

选择仅对当前视频有效，打开下一个视频时恢复“等比适应”。
```

Add “图片阅读” with the exact behavior and memory note described above.

- [ ] **Step 2: Update the unified verification record template**

In `Invoke-AndroidVerification.ps1`, add bullets for the six new gates. Do not
change:

- clean-worktree requirement;
- exact AVD/API/ABI checks;
- real server root loop;
- media-body privacy sentence;
- APK copy and SHA-256 behavior.

Parse the script:

```powershell
$errors = $null
[System.Management.Automation.Language.Parser]::ParseFile(
  (Resolve-Path .\scripts\Invoke-AndroidVerification.ps1),
  [ref]$null,
  [ref]$errors
) | Out-Null
if ($errors) { $errors; exit 1 }
```

- [ ] **Step 3: Verify dependency notice scope**

Run:

```powershell
git diff ba7c3bb -- gradle/libs.versions.toml app/build.gradle.kts
```

Expected: no new dependency. Keep `THIRD_PARTY_NOTICES.md` unchanged except a
wording update if necessary to describe the same Coil/LibVLC use.

- [ ] **Step 4: Run pre-document commit gates**

Run:

```powershell
$env:ANDROID_HOME = 'C:\Users\Administrator\AppData\Local\Android\Sdk'
$env:ANDROID_SERIAL = 'emulator-5554'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --stacktrace
.\gradlew.bat connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.realServerBaseUrl=http://192.168.1.17:8080' `
  --stacktrace
```

Expected: zero failure, zero skip, Lint 0 error.

- [ ] **Step 5: Commit docs and verification script**

Run:

```powershell
git diff --check
git status --short
git add README.md scripts/Invoke-AndroidVerification.ps1
git diff --quiet -- THIRD_PARTY_NOTICES.md
if ($LASTEXITCODE -eq 0) {
  Write-Host '第三方说明无需修改'
} else {
  git add THIRD_PARTY_NOTICES.md
}
git commit -m "docs: document video scaling and comic reader"
```

After commit:

```powershell
git status --short
```

Expected: no output.

- [ ] **Step 6: Run unified verification on the clean commit**

Run:

```powershell
.\scripts\Invoke-AndroidVerification.ps1 `
  -SdkRoot 'C:\Users\Administrator\AppData\Local\Android\Sdk' `
  -AvdName 'Pixel_3a_API_36_extension_level_17_x86_64' `
  -RealServerBaseUrl 'http://192.168.1.17:8080'
```

Expected:

- all JVM/Lint/build/device gates pass;
- real server test passes inside the app;
- APK installs and cold-starts;
- both root JSON checks pass;
- delivery APK, checksum and verification record are generated.

- [ ] **Step 7: Verify APK metadata and native libraries**

Run:

```powershell
$apk = '.\dist\mediaviewer-debug.apk'
& 'C:\Users\Administrator\AppData\Local\Android\Sdk\build-tools\36.0.0\aapt.exe' `
  dump badging $apk |
  Select-String "package:|sdkVersion:|targetSdkVersion:|application-label:"
```

Assert:

```text
name='com.local.mediaviewer'
sdkVersion:'29'
targetSdkVersion:'36'
application-label:'mediaviewer'
```

Open the APK as ZIP and assert `libvlc.so` exists for:

```text
arm64-v8a
armeabi-v7a
x86
x86_64
```

- [ ] **Step 8: Verify checksum independently**

Run:

```powershell
$expected = (
  Get-Content .\dist\mediaviewer-debug.apk.sha256
).Split(' ')[0]
$actual = (
  Get-FileHash `
    .\dist\mediaviewer-debug.apk `
    -Algorithm SHA256
).Hash.ToLowerInvariant()
if ($expected -ne $actual) {
  throw 'SHA-256 不一致'
}
Write-Output $actual
```

- [ ] **Step 9: Verify runtime dependency boundary**

Run:

```powershell
$report = .\gradlew.bat `
  :app:dependencies `
  --configuration debugRuntimeClasspath `
  --console=plain
$text = $report -join "`n"
```

Assert present:

```text
org.videolan.android:libvlc-all:4.0.0-eap29
io.coil-kt.coil3:coil-compose:3.5.0
com.squareup.okhttp3:okhttp:5.3.0
androidx.room:room-runtime:2.8.4
```

Assert absent:

```text
mockwebserver3
org.robolectric:robolectric
androidx.compose.ui:ui-test-junit4
junit:junit:4.13.2
```

- [ ] **Step 10: Verify installed UI without real media names**

Using `uiautomator dump`, inspect only the home screen and assert:

```text
MiddleDir
pik
192.168.1.17
```

Do not navigate into a real root for UI dumping. Media behavior has already
been verified against generated fixtures.

- [ ] **Step 11: Inspect verification record and Git status**

Assert the record contains the clean implementation commit SHA and all new
gate bullets. Privacy scan:

```powershell
rg -n "sample\\.|I:\\\\|G:\\\\" `
  docs/verification/2026-07-28-android-mediaviewer.md
```

Expected: no output.

Run:

```powershell
git status --short --untracked-files=all
git check-ignore `
  dist/mediaviewer-debug.apk `
  dist/mediaviewer-debug.apk.sha256
```

Expected: only the verification Markdown is uncommitted; both dist files are
ignored.

- [ ] **Step 12: Commit actual acceptance record**

Run:

```powershell
git add docs/verification/2026-07-28-android-mediaviewer.md
git diff --cached --check
git commit -m "docs: record media viewing enhancement acceptance"
git status --short
```

Expected final status: no output.

- [ ] **Step 13: Final delivery report**

Deliver absolute clickable paths for:

```text
dist/mediaviewer-debug.apk
dist/mediaviewer-debug.apk.sha256
README.md
docs/verification/2026-07-28-android-mediaviewer.md
```

Report:

- final SHA-256;
- JVM and device test totals from XML;
- Lint error count;
- real server two-root result;
- branch name and latest commit;
- whether the feature branch remains unmerged.
