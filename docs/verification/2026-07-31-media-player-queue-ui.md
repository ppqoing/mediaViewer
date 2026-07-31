# Player 计划 Task 7 验证记录（Player Integration and Verification, Step 4–5）

- 验证时间：2026-07-31 22:11 – 2026-08-01 06:20 +08:00（verifier 执行窗口）
- HEAD commit：60f5954780e40aee49954c96de3f08fc2f654f04（`fix(android): integrate recoverable app flows`，Flow Task 7 根集成）
- 分支：feature/android-mediaviewer（工作树干净，仅用户未跟踪路径 `.superpowers/brainstorm/` 与 `docs/verification/2026-07-30-arm64-compressed-release.md`，未触碰）
- 设备：emulator-5554（Pixel_3a_API_36_extension_level_17_x86_64，API 36），全程在线，无重启
- 环境：`ANDROID_HOME=C:\Users\Administrator\AppData\Local\Android\Sdk`；Git Bash；每条命令带 `'-Pkotlin.incremental=false' --no-daemon`，串行执行

## 门禁命令与结果

1. `./gradlew.bat :app:testDebugUnitTest --tests 'com.local.mediaviewer.ui.player.*' --tests 'com.local.mediaviewer.player.*' --tests 'com.local.mediaviewer.queue.*' '-Pkotlin.incremental=false' --no-daemon`
   — **PASS**，BUILD SUCCESSFUL，21 个测试类共 111 tests，0 failures / 0 errors / 0 skipped。
   （player 包 7 类 53；queue 包 3 类 37；ui.player 包 11 类 21。）

2. `./gradlew.bat :app:compileDebugAndroidTestKotlin '-Pkotlin.incremental=false' --no-daemon`
   — **PASS**，BUILD SUCCESSFUL。

3. `./gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.PlayerScreenTest,com.local.mediaviewer.PlaybackControlsTest,com.local.mediaviewer.VideoControlsOverlayTest,com.local.mediaviewer.VideoGestureLayerTest,com.local.mediaviewer.PlaybackQueueUiTest,com.local.mediaviewer.MediaViewerNavigationTest,com.local.mediaviewer.BackgroundPlaybackTest,com.local.mediaviewer.LibVlcVideoOutputTest' '-Pkotlin.incremental=false' --no-daemon`
   — **PASS**，BUILD SUCCESSFUL，8 个类共 87 tests，0 failures / 0 errors / 0 skipped：
   - PlayerScreenTest 12；PlaybackControlsTest 15；VideoControlsOverlayTest 15；
     VideoGestureLayerTest 4；PlaybackQueueUiTest 27；MediaViewerNavigationTest 11；
     BackgroundPlaybackTest 1；LibVlcVideoOutputTest 2。

已知预存失败 `MediaSessionControlsTest.stopReleasesOnceAndColdControllerRestoresQueuePaused`
不在本门禁类集合中（归 Flow Task 8 定性），本次未运行该类，本记录不覆盖它。

## 证据矩阵（对照 Player 计划 Task 7 Step 5）

- **三界面 IDLE/Buffering 主动作 — PASS（connected）**
  普通界面：`PlaybackControlsTest.ordinaryIdleUsesPlayCallback`、
  `ordinaryBufferingStaysEnabledAndPauses`、`ordinaryEndedUsesReplayCallback`；
  全屏：`VideoControlsOverlayTest.fullscreenPrimaryUsesRealPlayPauseAndReplayCallbacks`、
  `fullscreenPrimaryUsesSeventyTwoDpTouchTarget`、
  `PlayerScreenTest.fullscreenBufferingHasOneFeedbackOwnerAndDoesNotOverlapPrimary`、
  `audioBufferingShowsAudioSpinner`、`videoBufferingShowsCentralSpinnerAndSingleTimeline`；
  迷你条：`PlaybackQueueUiTest.miniPrimaryUsesRealPlayPauseAndReplayCallbacks`、
  `bufferingRingIsVisibleButDecorative`。单元侧 `PlaybackPrimaryActionTest` 1/1 PASS。

- **音视频视觉边界 — PASS（connected）**
  `PlayerScreenTest.audioPlayerHasLocalArtworkAndNoVideoOnlyControls`、
  `audioScreenShowsControlsWithoutVideoSurface`、
  `audioScreenExposesVolumeControlsWithoutVideoCapabilities`、
  `ordinaryVideoKeepsOneStableSurfaceAndMovesLowFrequencyOptionsToMenu`、
  `lowFrequencyOptionsWorkInNormalAndFullscreen`。

- **单时间轴与暂停 seek 回归 — PASS（connected + JVM）**
  `PlaybackControlsTest.bufferingPlayerKeepsSingleTimelineWithoutSecondBufferingBar`、
  `timelineUsesSeparateStableCurrentAndDurationLabels`；
  `PlayerScreenTest.videoBufferingShowsCentralSpinnerAndSingleTimeline`、
  `openingBufferingAndErrorKeepNavigationAndSingleTimeline`；
  暂停态 seek 预览/提交语义：`VideoGestureLayerTest.horizontalDragPreviewsMoreThanOnceAndCommitsExactlyOnceOnUp`；
  单元侧 `SeekSyncStateTest` 3/3 PASS。

- **普通竖向音量与全屏 rails — PASS（connected + JVM）**
  普通竖向音量：`PlaybackControlsTest.volumeButtonOpensTrueVerticalVolumePopup`、
  `verticalAdjustmentResetsVolumePopupDeadline`、`volumePopupClosesAfterThreeSecondsWithoutInteraction`、
  `muteAndExternalVolumeChangesEachResetThePopupDeadline`、`systemBackClosesVolumePopupWithoutLeavingPlayer`、
  `volumePollingContinuesWhileExpandedAndStopsWhenClosed`、`volumeTriggerDoesNotToggleMute`、
  `tappingOutsideDismissesVolumePopup`；
  全屏 rails：`VideoControlsOverlayTest.fullscreenGestureRailLivesUntilEightHundredMillisecondDeadline`、
  `backClosesVolumeBeforeExitingFullscreen`；
  手势分层：`VideoGestureLayerTest.verticalDragsUseLeftForBrightnessAndRightForVolume`、
  `normalVerticalGestureReleaseLeavesFeedbackForScreenOwner`；
  单元侧 `PlaybackVolumeControlTest` 3/3、`SystemVolumeControllerTest` 2/2、
  `WindowBrightnessControllerTest` 1/1 PASS。

- **全屏队列/帮助/锁定/安全区 — PASS（connected；根级全屏队列为结构等价，见备注）**
  `VideoControlsOverlayTest.fullscreenQueueEntryInvokesTheRootCallback`、
  `dismissingGestureHelpWithBackDoesNotExitFullscreen`、
  `fullscreenLockExposesToggleStateAndOnlyOneUnlockAction`、
  `pausedControlsRemainVisibleAndLockShowsOnlyUnlockAction`、
  `lockClearsGestureFeedbackImmediately`、
  `fullscreenControlsStayInsideInjectedSafeDrawingInsets`、
  `backClosesFullscreenOptionMenuBeforeExit`；
  根级队列入口实测 mini/ordinary 两腿：`PlaybackQueueUiTest.miniThenOrdinaryQueueEntriesOpenTheSameRootQueueSheet`。
  备注：全屏入口的根级端到端腿为结构等价（compose 测试宿主 EmptyActivity 未声明
  configChanges，进全屏触发 Activity 重建），属 Flow Task 7 交接的已知限制，
  不算失败；`queue_entry_fullscreen` 按钮本身由上述 Overlay 用例锁定。

- **响应式迷你条 — PASS（connected）**
  `PlaybackQueueUiTest.miniDockIsSeventyTwoDpAndActionsDoNotOverlapTextAtLargeFont`、
  `responsiveThresholdTreatsOnlyWidthsBelow360AsCompact`、
  `compactMiniKeepsPrimaryAndQueueWithoutNextOrVolume`、`twoXFontUsesCompactActionsEvenAtWideWidth`、
  `miniProgressUsesClampedActualPosition`、`miniPlayerShowsRealProgressAndDisablesUnavailableNext`、
  `unavailableNextIsDisabledAndCannotInvokeCallback`、`emptyQueueDoesNotRenderMiniPlayer`、
  `miniQueueEntryUsesStableTagDescriptionAndCallback`、`mediaIdentityOpensPlayerWithoutStealingControlClicks`；
  根集成：`MediaViewerNavigationTest.homeShowsMiniPlayerForCurrentQueueItem`。

- **多行队列拖动、删除/撤销与持久化反馈 — PASS（connected + JVM）**
  拖动：`PlaybackQueueUiTest.handleDragAcrossThreeRowsCommitsOneFinalMoveAndNeverSelects`、
  `handleOvershootThenReverseCommitsTheNetIndexExactlyOnce`、
  `draggingNearViewportEdgeAutoScrollsButStillCommitsOnce`、
  `swipingQueueNormallyScrollsWithoutReordering`、
  `queueSheetShowsLayeredRowsAndExposesActionsWithoutVisibleMoveButtons`；
  删除/撤销：`ordinaryDeleteUndoRestoresTheOriginalItemAndIndex`、
  `deletingCurrentQueueItemRequiresConfirmation`、`oneItemDisablesClearOtherAndEmptyQueueHasAState`、
  `emptyQueueShowsExplicitStateAndCanClose`、`queueStatesCustomActionsAndRemovalContractsAreExplicit`、
  `modeButtonCyclesAllModesWithTextualState`、`longQueueUsesAvailableHeightAndStaysAboveInjectedNavigationInset`；
  持久化反馈：`persistenceNoticeRetryKeepsTheRootQueueSheetOpenAndDeduplicatesId`、
  `persistence_notice_is_visible_with_queue_open_and_retry_keeps_the_sheet_open`；
  单元侧 `QueueDragSessionTest` 3/3、`PlaybackQueueDaoTest` 4/4 PASS。

- **后台/Surface 测试 — PASS（connected）**
  `BackgroundPlaybackTest.videoKeepsPlayingWithoutSurfaceAndReattachesContinuously`；
  `LibVlcVideoOutputTest.refreshKeepsAttachedLayoutStateAndMediaIdentity`、
  `videoLayoutFillsHostAcceptsScaleModesAndReattaches`。
  与门禁前基线相比无变化（计划预期「remain unchanged」成立）。

## 根集成回归附带证据（connected）

`MediaViewerNavigationTest` 11/11 PASS：`app_scope_connects_once_and_navigation_does_not_connect_again`、
`browser_remains_visible_during_global_reconnect`、`failed_player_has_reconnect_and_back_without_an_infinite_spinner`、
`notification_request_from_browser_returns_home_and_empty_queue_exits_once`、
`dirty_settings_back_uses_discard_confirmation`、`browser_player_back_returns_to_the_same_directory`、
`homeOpensNestedAudio`/`homeOpensNestedVideo`/`homeOpensNestedImage`、
`homeUsesConfiguredSingleImageMode`、`homeShowsMiniPlayerForCurrentQueueItem`。

## 边界声明

- 本记录仅覆盖上述三条门禁命令；全量 `:app:testDebugUnitTest`（316 基线）与
  `MediaSessionControlsTest` 等其余 connected 类不在本次运行范围。
- Snackbar 与 Modal Sheet 的实际视觉 z-order 未经真机人工复核（connected 测试
  以语义可见性/语义点击验证）。
- 全屏队列入口的真机端到端验证需带 configChanges 的宿主或人工操作，本记录
  不声称完成该项。
