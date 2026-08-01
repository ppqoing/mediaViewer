# 播放进度与控件分层验证记录

- 日期：2026-08-02
- 阶段源提交：`183ad4da1a0c3640af1bbe18092dc0c1d0082ed2`
- 设计：`docs/superpowers/specs/2026-08-02-player-progress-controls-layout-design.md`
- 计划：`docs/superpowers/plans/2026-08-02-player-progress-controls-layout.md`

## 1. 验证范围

- 暂停恢复后 Media3 不再在 LibVLC 位置快照上重复推进时间。
- 非默认倍速不会让 UI 位置被二次乘速。
- 普通视频、音频和全屏视频按使用频率分层排列控件。
- 普通与全屏菜单内容、单击/双击、自动隐藏、半透明背景和安全区没有明显回归。
- AndroidTest 编译、Debug 构建和 Lint 基础门禁。

## 2. 进度修复 TDD

测试类：`VlcSessionPlayerTest`

### RED

- 运行：`testDebugUnitTest --tests '*VlcSessionPlayerTest'`
- 结果：22 项中新增的 2 项失败，其余通过。
- 失败项：
  - `Media3 does not extrapolate LibVLC position after pause resume`
  - `Media3 does not multiply LibVLC position by playback speed`
- 结论：旧实现会在播放就绪状态继续外推 LibVLC 已上报的位置，且外推速度受 Media3 倍速再次影响。

### GREEN

- 修复：`VlcSessionPlayer` 使用 `PositionSupplier.getConstant(...)` 发布经边界限制的 LibVLC 位置快照。
- 重验：只重新运行此前包含失败项的 `VlcSessionPlayerTest`。
- 结果：**PASS**，Gradle `BUILD SUCCESSFUL`。

## 3. Compose 设备测试

设备：`Pixel_3a_API_36_extension_level_17_x86_64`，API 36，ABI `x86_64`。

| 测试类 | 数量 | 失败 | 结果 | 覆盖重点 |
|---|---:|---:|---|---|
| `PlaybackControlsTest` | 16 | 0 | PASS | 时间轴、传输层、工具层、主按钮、竖向音量 |
| `PlayerScreenTest` | 18 | 0 | PASS | 普通视频、音频分组、菜单、手势、窄屏、缓冲 |
| `VideoControlsOverlayTest` | 17 | 0 | PASS | 全屏顶部菜单、中央控制、配置层、工具组、Insets、锁定 |

TDD 失败证据：

- 共享控件新增测试最初因不存在 `player_timeline_layer` 失败，完成三层结构后通过。
- 普通视频分组测试最初显示队列仍位于右侧旧槽位，左组尺寸为零；调整后通过。
- 全屏分组测试最初显示“更多播放设置”位于底部而非顶部；移动菜单和队列后通过。

完整类测试只在对应结构改造后运行一次。外层 60 秒工具超时时，使用后台 Gradle 日志和设备 XML/完成计数取得最终结果，没有重复运行已经得到 PASS 的测试。

## 4. 静态与构建门禁

执行：

```text
compileDebugAndroidTestKotlin
lintDebug
assembleDebug
```

结果：**PASS**，Gradle `BUILD SUCCESSFUL in 35s`。

- AndroidTest Kotlin：编译通过。
- Debug APK：构建通过。
- Lint：0 error，36 warning。
- `git diff --check`：在阶段提交前执行并要求无输出。

## 5. 人工与真实媒体边界

- API 36 模拟器自动 Compose 测试：PASS。
- 真实视频执行“播放 → 暂停 → 等待 → 恢复 → seek”并观察声音、画面和时间轴：**NOT RUN**。
- 刘海屏或挖孔屏真机目视检查：**NOT RUN**；自动 Insets 注入测试已通过，但不能替代真机外观确认。
- arm64 真机安装和播放：**NOT RUN**。

本记录不使用编译或模拟器结果代替上述真机人工检查。

## 6. Release 构建与独立校验

- 构建源提交：`d5a7bb903aa272d5e9dbaec07494a23fb7ebb663`
- 构建脚本：`scripts/Build-PersonalRelease.ps1`
- 脚本门禁：`clean`、`testDebugUnitTest`、`lintRelease`、`assembleRelease` 全部 PASS，Gradle `BUILD SUCCESSFUL in 1m 10s`。
- APK：`D:\code\mediaviewer\.worktrees\android-mediaviewer\dist\mediaviewer-v1.1.0-arm64-v8a-release.apk`
- 大小：43,792,510 字节（41.76 MiB）
- SHA-256：`6af0ca59481467ca8e247a6dfbdd83c1613074ef86d0cd5277d60ae6e9b437e9`
- `.sha256` 文件与独立 `Get-FileHash` 结果：一致
- 包名：`com.local.mediaviewer`
- 版本：`1.1.0 (3)`
- `minSdk` / `targetSdk`：29 / 36
- Native ABI：仅 `arm64-v8a`
- `zipalign -c -P 16 -v 4`：`Verification successful`
- 签名：APK Signature Scheme v3 为 `true`，v1/v2/v3.1/v4 为 `false`，签名者数量为 1
- 签名证书 SHA-256：`b432a64032601b66f275d0c4b3308d95cbb40b58be9269c1494783e82fa5415d`
- 证书用途限制：`C=US, O=Android, CN=Android Debug`，仅适合个人安装和测试，不是应用商店正式发布证书
- arm64 真机安装和播放：**NOT RUN**

构建脚本要求干净工作树。构建前只临时 stash 精确列出的 17 个未跟踪文件；构建完成后已恢复全部文件并删除临时 stash。旧同名 APK 和 `.sha256` 被本次已确认的 Release 产物替换，其他 `dist` 文件未删除或移动。
