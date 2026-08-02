# 暂停恢复后使用原始播放位置设计

- 日期：2026-08-02
- 状态：已确认
- 根因依据：`docs/analysis/2026-08-02-player-resume-progress-root-cause.md`
- 适用范围：Android 应用内的视频与音频播放页

## 1. 目标

修复暂停后恢复播放时，下方时间数字和进度滑块快于实际画面的问题。

应用内 UI 的播放位置必须直接来自 LibVLC `TimeChanged` 写入的会话快照，不再使用 Media3 `MediaController.currentPosition` 的墙钟外推值。队列、播放状态、倍速、缓冲、循环模式和系统媒体控制仍由现有 Media3 会话链路负责。

## 2. 已确认根因

当前 UI 每 250ms 读取一次 `MediaController.currentPosition`。Media3 1.10.1 会在客户端根据最近一次会话位置、墙钟流逝和播放倍速继续外推该值。暂停、恢复、重新缓冲及视频输出刷新会让 LibVLC 的位置事件暂时变稀，使客户端估值领先实际解码和显示进度。

提交 `19fb089` 将 `VlcSessionPlayer` 改为 `PositionSupplier.getConstant`，只停止了会话进程内的外推；真实 UI 仍经过 `MediaController` 客户端外推，因此该修改保留但不足以独立解决问题。

## 3. 方案选择

采用自定义 MediaSession 命令，由应用控制器按固定频率向播放服务拉取 LibVLC 原始位置快照。

未采用以下方案：

- 不缩短 MediaSession 周期位置更新时间，因为这只能缩小误差，不能消除客户端外推。
- 不在每次 LibVLC `TimeChanged` 时主动向所有控制器推送，因为事件频率不可控，会增加 Binder 通信和服务端广播负担。
- 不在 UI 中对外推值做限速或平滑，因为这仍以错误数据为输入，无法保证暂停恢复和非默认倍速下的正确性。

## 4. 组件与职责

### 4.1 原始位置快照及编解码

新增只承担跨 MediaSession 边界传输职责的位置快照类型和 Bundle 编解码器。快照包含：

- 当前媒体的稳定 `mediaKey`；
- LibVLC 原始 `positionMs`；
- 当前 `durationMs`。

编码前把位置限制为非负数；时长有效时，位置不得超过时长。解码时拒绝缺少媒体标识、缺少位置、负数位置或其他格式无效的数据，不把损坏的 Bundle 注入 UI 状态。

### 4.2 播放服务命令

新增“读取原始播放位置”的自定义 `SessionCommand`：

- `PlaybackSessionCallback.onConnect` 将其加入可用会话命令；
- `onCustomCommand` 从 `PlaybackCoordinator.sessionState.value` 同步捕获当前媒体与播放位置；
- 成功时通过 `SessionResult` extras 返回编码后的快照；
- 当前没有媒体时返回 `RESULT_ERROR_INVALID_STATE` 和空 extras，不伪造位置。

快照来源保持为现有 `PlaybackCoordinator`，不直接让 UI 或回调访问 LibVLC 实例。

### 4.3 应用控制器轮询

`Media3PlaybackController` 建立连接后立即请求一次原始位置；每次请求完成后等待 250ms，再串行发起下一次：

- 只有前一次请求完成后才发起下一次，避免慢响应造成请求堆积；
- 成功解码且 `mediaKey` 与控制器当前媒体一致时才接受；
- 媒体不匹配的迟到响应直接丢弃；
- 请求失败、命令暂不可用或返回无效数据时，保留最后一个与当前媒体匹配的有效快照，并在下一轮重试；
- 断开连接、关闭控制器或切换到其他媒体时，清除不再匹配的缓存。

### 4.4 UI 状态发布

`Media3PlaybackController.publish` 继续从 `MediaController` 读取除位置以外的会话状态。位置字段只从最近一次匹配当前媒体的原始快照读取：

- 不再回退到 `MediaController.currentPosition`；
- 尚未取得当前媒体的首个有效快照时显示 `0`，首轮请求完成后立即更新；
- 暂停、恢复、倍速变化、缓冲和普通 Player 事件只能触发重新发布，不能改变位置的数据来源。

该控制器同时服务视频页和音频页，因此两者共享同一修复；视频专有的 Surface 刷新逻辑不进入位置通道。

## 5. 数据流

1. LibVLC 发出 `TimeChanged`。
2. `AndroidVlcPlaybackEngine` 将原始时间写入 `PlaybackState.positionMs`。
3. `PlaybackCoordinator` 将该状态发布到 `sessionState`。
4. 应用控制器发送自定义位置命令。
5. `PlaybackSessionCallback` 从当前 `sessionState` 捕获并返回带 `mediaKey` 的位置快照。
6. `Media3PlaybackController` 校验媒体标识并缓存快照。
7. UI 时间数字和进度滑块使用该快照；Media3 客户端的墙钟估值不再进入应用 UI。

## 6. 异常与竞态处理

- 同一时刻最多存在一个位置请求。
- 媒体切换期间，旧媒体响应不能覆盖新媒体进度。
- 允许同一媒体向前或向后跳变，以支持正常 seek；不得以“单调递增”规则阻止向后 seek。
- 暂停时服务端位置冻结，UI 也保持该原始快照。
- 恢复播放但 LibVLC 尚未产生新位置时，UI 保持最后快照，不按墙钟空转。
- 服务重连后不复用旧连接的位置缓存，连接成功后立即重新获取。
- 位置命令失败不应断开 MediaController，也不影响播放、队列或系统通知控制。

## 7. 测试设计

### 7.1 JVM/Robolectric 自动测试

- 位置快照 Bundle 往返保持 `mediaKey`、位置和时长。
- 编解码拒绝缺字段、负数和格式错误数据，并正确裁剪超过时长的位置。
- `PlaybackSessionCallback` 对连接公开位置命令，并返回 `PlaybackCoordinator` 当前原始位置。
- 当前没有媒体时不返回伪造快照。
- 位置缓存只接受当前媒体响应，拒绝迟到的其他媒体响应。
- 当模拟的 `MediaController.currentPosition` 已超前时，发布状态仍使用原始快照。
- 暂停、恢复和 2 倍速状态变化期间，原始位置未更新时 UI 位置保持不变。
- 向后 seek 的新原始快照可以覆盖较大的旧位置。

### 7.2 Android 集成与人工验证

- 运行受影响的 MediaSession/播放器自动测试，验证真实自定义命令连接和返回链路。
- 在可用设备上执行“播放 → 暂停 → 等待 → 恢复 → seek”，核对画面、声音、时间数字和进度滑块。
- 至少以 1 倍速和 2 倍速各执行一次，确认 UI 没有二次乘速。
- 真机不可用时必须将该项记录为 `NOT RUN`，不能用 JVM、编译或模拟器结果替代。

## 8. 明确不在本次范围内

- 不升级或降级 libvlc 4.0.0-eap29。
- 不修改 `--network-caching=1500`。
- 不移除或改变暂停恢复时的 `refreshVideoOutput()` / `updateVideoSurfaces()`。
- 不修改 Media3 框架或依赖版本。
- 不调整系统通知或其他第三方控制器自身显示的 Media3 外推位置。
- 不进行与本缺陷无关的播放器重构或控件布局调整。

如果后续真机日志证明 LibVLC `TimeChanged` 本身已领先实际画面，应把 VLC4 报告层作为独立问题重新分析，不能在本修复中凭推测混入 vout 或版本调整。

## 9. 完成标准

- 应用 UI 的位置发布路径不再读取 `MediaController.currentPosition`。
- 自动测试能够在 Media3 位置超前的输入下证明 UI 使用 LibVLC 原始快照。
- 暂停和恢复不会使位置在缺少新 LibVLC 快照时自行增长。
- 媒体切换、seek、倍速、队列和后台播放没有基础功能回归。
- 验证记录明确区分自动测试结果与未运行的真机检查。
