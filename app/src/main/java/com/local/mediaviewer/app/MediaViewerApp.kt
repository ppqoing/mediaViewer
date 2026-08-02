package com.local.mediaviewer.app

import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.local.mediaviewer.browser.BrowserPlaybackAction
import com.local.mediaviewer.browser.BrowserViewModel
import com.local.mediaviewer.home.HomeViewModel
import com.local.mediaviewer.image.ImageReaderViewModel
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.navigation.BrowserRoute
import com.local.mediaviewer.navigation.CurrentPlayerNavigationRequests
import com.local.mediaviewer.navigation.HomeRoute
import com.local.mediaviewer.navigation.ImageReaderRoute
import com.local.mediaviewer.navigation.PLAYER_ENTRY_WAIT_TIMEOUT_MS
import com.local.mediaviewer.navigation.PlayerEntryState
import com.local.mediaviewer.navigation.PlayerRoute
import com.local.mediaviewer.navigation.PlayerRouteExitAction
import com.local.mediaviewer.navigation.PlayerRouteLifecyclePolicy
import com.local.mediaviewer.navigation.PlayerRouteLifecycleState
import com.local.mediaviewer.navigation.SettingsRoute
import com.local.mediaviewer.navigation.leavePlayerSafely
import com.local.mediaviewer.navigation.resolvePlayerEntryState
import com.local.mediaviewer.player.PlayerRequest
import com.local.mediaviewer.player.PlayerViewModel
import com.local.mediaviewer.player.VideoBackgroundLifecycleAction
import com.local.mediaviewer.player.VideoBackgroundLifecycleState
import com.local.mediaviewer.player.VideoBackgroundLifecycleTransition
import com.local.mediaviewer.player.VideoBackgroundPlaybackPolicy
import com.local.mediaviewer.player.VideoSessionExitReason
import com.local.mediaviewer.queue.PlaybackNoticeAction
import com.local.mediaviewer.session.ServerSessionState
import com.local.mediaviewer.settings.SettingsViewModel
import com.local.mediaviewer.ui.browser.BrowserScreen
import com.local.mediaviewer.ui.components.MediaAction
import com.local.mediaviewer.ui.components.MediaAppScaffold
import com.local.mediaviewer.ui.components.MediaSnackbarKind
import com.local.mediaviewer.ui.components.MediaSnackbarVisuals
import com.local.mediaviewer.ui.components.MediaStateKind
import com.local.mediaviewer.ui.components.MediaStatePanel
import com.local.mediaviewer.ui.home.HomeScreen
import com.local.mediaviewer.ui.image.ImageReaderScreen
import com.local.mediaviewer.ui.player.AudioPlayerScreen
import com.local.mediaviewer.ui.player.FullscreenController
import com.local.mediaviewer.ui.player.NowPlayingBar
import com.local.mediaviewer.ui.player.PlaybackQueueSheet
import com.local.mediaviewer.ui.player.PlayerBootstrapContent
import com.local.mediaviewer.ui.player.SystemVolumeController
import com.local.mediaviewer.ui.player.VideoPlayerScreen
import com.local.mediaviewer.ui.player.WindowBrightnessController
import com.local.mediaviewer.ui.settings.SettingsScreen
import com.local.mediaviewer.ui.theme.MediaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MediaViewerApp(
    container: AppContainer,
    currentPlayerNavigationRequests:
        CurrentPlayerNavigationRequests = remember {
            CurrentPlayerNavigationRequests()
        },
) {
    val navController = rememberNavController()
    val activity = requireNotNull(LocalActivity.current) {
        "MediaViewer 必须托管在 Activity 中"
    }
    val volumeController = remember(activity) {
        SystemVolumeController(
            requireNotNull(activity.getSystemService(AudioManager::class.java)),
        )
    }
    val playbackController = container.playbackController
    val playbackSession by playbackController.sessionState
        .collectAsStateWithLifecycle()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentPlayerRequestNonce by currentPlayerNavigationRequests
        .requestNonce
        .collectAsStateWithLifecycle()

    // 应用级唯一 server session 生命周期：默认 Activity owner
    // 跨 Home/Settings/Browser/Player 存活，全局只自动连接一次。
    val appSessionViewModel: AppSessionViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                AppSessionViewModel(
                    session = container.sessionManager,
                    settings = container.settingsRepository,
                )
            }
        },
    )
    val appSession by appSessionViewModel.uiState.collectAsStateWithLifecycle()

    val globalSnackbarHostState = remember { SnackbarHostState() }
    val rootScope = rememberCoroutineScope()
    var queueSheetVisible by rememberSaveable { mutableStateOf(false) }
    var activeVideoEntryId by remember { mutableStateOf<String?>(null) }
    var activeVideoBackgroundPlaybackEnabled by remember {
        mutableStateOf(false)
    }
    var videoBackgroundLifecycleState by remember {
        mutableStateOf(VideoBackgroundLifecycleState())
    }
    val applyVideoBackgroundTransition =
        { transition: VideoBackgroundLifecycleTransition ->
            videoBackgroundLifecycleState = transition.state
            when (transition.action) {
                VideoBackgroundLifecycleAction.NONE -> Unit
                VideoBackgroundLifecycleAction.PAUSE -> playbackController.pause()
                VideoBackgroundLifecycleAction.PLAY -> playbackController.play()
            }
        }
    var handledNoticeIds by rememberSaveable {
        mutableStateOf(arrayListOf<Long>())
    }
    val showsMiniPlayer = playbackSession.currentItem != null && (
        currentEntry?.destination?.hasRoute<HomeRoute>() == true ||
            currentEntry?.destination?.hasRoute<BrowserRoute>() == true
        )

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        volumeController.refresh()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        playbackController.onAppStarted()
        videoBackgroundLifecycleState =
            VideoBackgroundPlaybackPolicy.onAppStarted(
                videoBackgroundLifecycleState,
            )
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activeVideoEntryId != null) {
            applyVideoBackgroundTransition(
                VideoBackgroundPlaybackPolicy.onAppStopped(
                    state = videoBackgroundLifecycleState,
                    backgroundPlaybackEnabled =
                        activeVideoBackgroundPlaybackEnabled,
                    reason = if (activity.isChangingConfigurations) {
                        VideoSessionExitReason.CONFIGURATION_CHANGE
                    } else {
                        VideoSessionExitReason.APP_BACKGROUND
                    },
                    currentMediaKey = playbackSession.currentItem?.mediaKey,
                    playWhenReady = playbackSession.playWhenReady,
                ),
            )
        } else {
            videoBackgroundLifecycleState =
                VideoBackgroundPlaybackPolicy.clearPending(
                    videoBackgroundLifecycleState.copy(isForeground = false),
                )
        }
        playbackController.onAppStopped()
    }

    LaunchedEffect(
        videoBackgroundLifecycleState.isForeground,
        videoBackgroundLifecycleState.pendingResumeMediaKey,
        activeVideoEntryId,
        playbackSession.currentItem?.mediaKey,
    ) {
        applyVideoBackgroundTransition(
            VideoBackgroundPlaybackPolicy.reconcileForeground(
                state = videoBackgroundLifecycleState,
                currentMediaKey = playbackSession.currentItem?.mediaKey,
                hasActiveVideo = activeVideoEntryId != null,
            ),
        )
    }

    // 通知请求统一规整为 Home 基础栈：冷启动与前台 Browser 栈
    // 都形成 Home → Player，返回不会误回旧 Browser。
    LaunchedEffect(
        currentPlayerRequestNonce,
        playbackSession.currentItem,
    ) {
        currentPlayerNavigationRequests
            .consumeIfReady(playbackSession.currentItem)
            ?.let { mediaKey ->
                navController.navigate(PlayerRoute(mediaKey)) {
                    popUpTo(navController.graph.startDestinationId) {
                        inclusive = false
                    }
                    launchSingleTop = true
                }
            }
    }

    // 播放持久化 notice 只在根消费一次：先记 id 再显示 Snackbar，
    // 最近 32 个已处理 id 精确去重重组、重复投递与 Activity 重建。
    LaunchedEffect(playbackController) {
        playbackController.notices.collect { notice ->
            if (notice.id in handledNoticeIds) return@collect
            handledNoticeIds = ArrayList(
                (handledNoticeIds + notice.id).takeLast(32),
            )
            val result = globalSnackbarHostState.showSnackbar(
                MediaSnackbarVisuals(
                    message = notice.message,
                    kind = MediaSnackbarKind.ERROR,
                    actionLabel = if (
                        notice.action == PlaybackNoticeAction.RETRY_PERSISTENCE
                    ) {
                        "重试"
                    } else {
                        null
                    },
                ),
            )
            if (result == SnackbarResult.ActionPerformed) {
                playbackController.retryPersistence()
            }
        }
    }

    // 队列变空时关闭 Sheet；渲染条件同时要求队列非空，
    // 避免未来新队列因旧标记自动重开。
    LaunchedEffect(playbackSession.queue.items.isEmpty()) {
        if (playbackSession.queue.items.isEmpty()) {
            queueSheetVisible = false
        }
    }

    MediaAppScaffold(
        snackbarHostState = globalSnackbarHostState,
        bottomBar = {
            if (showsMiniPlayer) {
                // 系统导航条 inset 的唯一 owner 在这里：
                // 72dp dock 本体不膨胀，Scaffold 把系统栏+dock 作为真实底部占位。
                Box(Modifier.navigationBarsPadding()) {
                    NowPlayingBar(
                        state = playbackSession,
                        onPlay = playbackController::play,
                        onPause = playbackController::pause,
                        onReplay = {
                            playbackController.seekTo(0L)
                            playbackController.play()
                        },
                        onNext = playbackController::skipNext,
                        onOpenQueue = { queueSheetVisible = true },
                        onOpenPlayer = {
                            playbackSession.currentItem?.let { item ->
                                navController.navigate(PlayerRoute(item.mediaKey)) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        modifier = Modifier.testTag("now_playing_bar"),
                    )
                }
            }
        },
    ) { appPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(appPadding),
        ) {
        composable<HomeRoute> {
            val home: HomeViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        HomeViewModel(container.sessionManager)
                    }
                },
            )
            val state by home.uiState.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                onRetry = appSessionViewModel::retry,
                onOpenSettings = {
                    navController.navigate(SettingsRoute) {
                        launchSingleTop = true
                    }
                },
                onOpenShare = { share ->
                    navController.navigate(BrowserRoute(share.id))
                },
            )
        }
        composable<SettingsRoute> {
            val settings: SettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        SettingsViewModel(
                            settings = container.settingsRepository,
                            readerPreferences =
                                container.readerPreferencesRepository,
                            session = container.sessionManager,
                            playerPreferences =
                                container.playerPreferencesRepository,
                        )
                    }
                },
            )
            val state by settings.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(settings) {
                settings.saved.collect {
                    navController.popBackStack()
                }
            }
            SettingsScreen(
                state = state,
                onInputChanged = settings::onInputChanged,
                onTest = settings::testConnection,
                onSave = settings::save,
                onDefaultImageModeChanged =
                    settings::onDefaultImageModeChanged,
                onVideoControlsAutoHideChanged =
                    settings::onVideoControlsAutoHideChanged,
                onBack = { navController.popBackStack() },
                onBackRequest = settings::requestBack,
                onDiscardConfirmed = { navController.popBackStack() },
                onOpenSources = {
                    navController.navigate(HomeRoute) {
                        popUpTo(navController.graph.startDestinationId) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable<BrowserRoute> { entry ->
            val route = entry.toRoute<BrowserRoute>()
            // 全局 Connecting/Failed 不销毁已保留的连接：
            // 目录内容留在原地，另显非阻塞重连状态。
            val visibleConnection = when (
                val current = appSession.current
            ) {
                is ServerSessionState.Connected -> current
                else -> appSession.lastConnected
            }
            if (visibleConnection == null) {
                when (val current = appSession.current) {
                    ServerSessionState.Connecting -> MediaStatePanel(
                        kind = MediaStateKind.LOADING,
                        title = "正在连接服务器",
                    )

                    is ServerSessionState.Failed -> MediaStatePanel(
                        kind = MediaStateKind.OFFLINE,
                        title = current.error.userMessage,
                        primaryAction = if (appSession.needsConfiguration) {
                            MediaAction("服务器设置") {
                                navController.navigate(SettingsRoute)
                            }
                        } else {
                            MediaAction("重试") {
                                appSessionViewModel.retry()
                            }
                        },
                        secondaryAction = if (appSession.needsConfiguration) {
                            MediaAction("重试") {
                                appSessionViewModel.retry()
                            }
                        } else {
                            MediaAction("服务器设置") {
                                navController.navigate(SettingsRoute)
                            }
                        },
                    )

                    is ServerSessionState.Connected -> Unit
                }
                return@composable
            }
            val root = visibleConnection.shares
                .firstOrNull { share -> share.id == route.rootId }
            if (root == null) {
                MediaStatePanel(
                    kind = MediaStateKind.EMPTY,
                    title = "共享不存在或已从服务器移除",
                    primaryAction = MediaAction("返回") {
                        navController.popBackStack()
                    },
                )
                return@composable
            }
            val browser: BrowserViewModel = viewModel(
                key = "browser:${root.id}",
                factory = viewModelFactory {
                    initializer {
                        BrowserViewModel(
                            root = root,
                            repository = container.browserRepository,
                        )
                    }
                },
            )
            val state by browser.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(browser) {
                browser.mediaLaunches.collect { media ->
                    navController.navigate(
                        ImageReaderRoute(
                            rootId = media.rootId,
                            directoryLogicalUrl =
                                media.directoryLogicalUrl,
                            selectedLogicalUrl = media.logicalUrl,
                            selectedName = media.name,
                        ),
                    )
                }
            }
            LaunchedEffect(browser) {
                browser.playbackRequests.collect { request ->
                    when (request.action) {
                        BrowserPlaybackAction.PLAY_DIRECTORY -> {
                            playbackController.replaceQueue(
                                request.directoryItems,
                                request.selected.mediaKey,
                            )
                            navController.navigate(
                                PlayerRoute(request.selected.mediaKey),
                            )
                        }

                        BrowserPlaybackAction.PLAY_NEXT -> {
                            playbackController.playNext(
                                request.selected,
                            )
                            globalSnackbarHostState.showSnackbar(
                                "已加入下一项播放",
                            )
                        }

                        BrowserPlaybackAction.ADD_TO_QUEUE -> {
                            playbackController.append(
                                request.selected,
                            )
                            globalSnackbarHostState.showSnackbar(
                                "已添加到队列",
                            )
                        }
                    }
                }
            }
            BackHandler {
                if (!browser.goBack()) {
                    navController.popBackStack()
                }
            }
            Column {
                GlobalReconnectBanner(
                    current = appSession.current,
                    onRetry = appSessionViewModel::retry,
                    onOpenSettings = {
                        navController.navigate(SettingsRoute)
                    },
                )
                Box(Modifier.weight(1f)) {
                    BrowserScreen(
                        state = state,
                        onEntryClick = browser::open,
                        onBreadcrumbClick = browser::openBreadcrumb,
                        onPlaybackAction = browser::requestPlayback,
                        onRetry = browser::retry,
                        onBack = {
                            if (!browser.goBack()) {
                                navController.popBackStack()
                            }
                        },
                    )
                }
            }
        }
        composable<PlayerRoute> { entry ->
            val route = entry.toRoute<PlayerRoute>()
            var hasPresentedItem by rememberSaveable(route.mediaKey) {
                mutableStateOf(false)
            }
            var waitExpired by rememberSaveable(route.mediaKey) {
                mutableStateOf(false)
            }
            var lastPresentedMediaKind by rememberSaveable(entry.id) {
                mutableStateOf<MediaKind?>(null)
            }
            var videoBackgroundPlaybackEnabled by rememberSaveable(entry.id) {
                mutableStateOf(false)
            }
            val routeLifecycleState = PlayerRouteLifecycleState(
                lastPresentedKind = lastPresentedMediaKind,
            )
            val latestVideoBackgroundLifecycleState by rememberUpdatedState(
                videoBackgroundLifecycleState,
            )
            val latestActiveVideoEntryId by rememberUpdatedState(
                activeVideoEntryId,
            )

            DisposableEffect(entry.id) {
                onDispose {
                    videoBackgroundLifecycleState =
                        VideoBackgroundPlaybackPolicy.clearPending(
                            latestVideoBackgroundLifecycleState,
                        )
                    if (latestActiveVideoEntryId == entry.id) {
                        activeVideoEntryId = null
                        activeVideoBackgroundPlaybackEnabled = false
                    }
                }
            }

            val leaveBootstrap = {
                if (
                    PlayerRouteLifecyclePolicy.exitAction(routeLifecycleState) ==
                    PlayerRouteExitAction.STOP_AND_CLEAR
                ) {
                    videoBackgroundLifecycleState =
                        VideoBackgroundPlaybackPolicy.clearPending(
                            videoBackgroundLifecycleState,
                        )
                    playbackController.clearAll()
                }
                navController.leavePlayerSafely()
            }

            // 有限等待随 destination 离开而取消：
            // Connecting 中返回后不再由 timeout 触发导航。
            LaunchedEffect(route.mediaKey, playbackSession.currentItem?.mediaKey) {
                waitExpired = false
                if (playbackSession.currentItem == null) {
                    delay(PLAYER_ENTRY_WAIT_TIMEOUT_MS)
                    waitExpired = true
                }
            }

            when (
                val playerEntryState = resolvePlayerEntryState(
                    session = playbackSession,
                    hasPresentedItem = hasPresentedItem,
                    waitExpired = waitExpired,
                )
            ) {
                is PlayerEntryState.Ready -> {
                    val item = playerEntryState.item
                    val readyRouteLifecycleState =
                        PlayerRouteLifecyclePolicy.observeCurrentItem(
                            state = routeLifecycleState,
                            currentKind = item.kind,
                        )
                    LaunchedEffect(item.mediaKey) {
                        hasPresentedItem = true
                    }
                    val player: PlayerViewModel = viewModel(
                        key = "player-session",
                        factory = viewModelFactory {
                            initializer {
                                PlayerViewModel(
                                    initialRequest = PlayerRequest(
                                        name = item.name,
                                        logicalUrl = item.logicalUrl,
                                        requestUrl = item.logicalUrl,
                                        mediaKey = item.mediaKey,
                                        kind = item.kind,
                                    ),
                                    controller = playbackController,
                                    positionStore =
                                        container.playbackPositionStore,
                                    session = container.sessionManager,
                                    autoStart = false,
                                )
                            }
                        },
                    )
                    val state by player.uiState.collectAsStateWithLifecycle()
                    val fullscreenController = remember(activity) {
                        FullscreenController(activity)
                    }
                    DisposableEffect(fullscreenController) {
                        onDispose {
                            fullscreenController.close()
                        }
                    }
                    SideEffect {
                        lastPresentedMediaKind =
                            readyRouteLifecycleState.lastPresentedKind
                        if (
                            readyRouteLifecycleState.lastPresentedKind ==
                            MediaKind.VIDEO
                        ) {
                            activeVideoEntryId = entry.id
                            activeVideoBackgroundPlaybackEnabled =
                                videoBackgroundPlaybackEnabled
                        } else if (activeVideoEntryId == entry.id) {
                            videoBackgroundLifecycleState =
                                VideoBackgroundPlaybackPolicy.clearPending(
                                    videoBackgroundLifecycleState,
                                )
                            activeVideoEntryId = null
                            activeVideoBackgroundPlaybackEnabled = false
                        }
                    }

                    val leave = {
                        if (
                            PlayerRouteLifecyclePolicy.exitAction(
                                readyRouteLifecycleState,
                            ) == PlayerRouteExitAction.STOP_AND_CLEAR
                        ) {
                            videoBackgroundLifecycleState =
                                VideoBackgroundPlaybackPolicy.clearPending(
                                    videoBackgroundLifecycleState,
                                )
                            player.stopAndClear {
                                navController.leavePlayerSafely()
                            }
                        } else {
                            player.leave {
                                navController.leavePlayerSafely()
                            }
                        }
                    }

                    if (state.kind == MediaKind.AUDIO) {
                        AudioPlayerScreen(
                            state = state,
                            onPlay = player::play,
                            onPause = player::pause,
                            onReplay = player::replay,
                            onSeekBack = player::seekBack,
                            onSeekForward = player::seekForward,
                            onBeginScrub = player::beginScrub,
                            onPreviewScrub = player::previewScrub,
                            onCommitScrub = player::commitScrub,
                            onPrevious = player::previous,
                            onNext = player::next,
                            onSpeedChanged = player::setPlaybackSpeed,
                            playbackMode = playbackSession.queue.mode,
                            onPlaybackModeChanged =
                                playbackController::setPlaybackMode,
                            onOpenQueue = { queueSheetVisible = true },
                            onRetry = player::retry,
                            volumeController = volumeController,
                            onResumeHintShown = player::onResumeHintShown,
                            onBack = leave,
                        )
                    } else {
                        val brightnessController = remember(activity) {
                            WindowBrightnessController(activity)
                        }
                        DisposableEffect(brightnessController) {
                            onDispose(brightnessController::close)
                        }
                        VideoPlayerScreen(
                            state = state,
                            controller = player.controller,
                            fullscreenController = fullscreenController,
                            preferences = container.playerPreferencesRepository,
                            volumeController = volumeController,
                            brightnessController = brightnessController,
                            onPlay = player::play,
                            onPause = player::pause,
                            onReplay = player::replay,
                            onSeekBack = player::seekBack,
                            onSeekForward = player::seekForward,
                            onBeginScrub = player::beginScrub,
                            onPreviewScrub = player::previewScrub,
                            onCommitScrub = player::commitScrub,
                            onPrevious = player::previous,
                            onNext = player::next,
                            onSpeedChanged = player::setPlaybackSpeed,
                            backgroundPlaybackEnabled =
                                videoBackgroundPlaybackEnabled,
                            onBackgroundPlaybackChanged = {
                                videoBackgroundPlaybackEnabled = it
                            },
                            playbackMode = playbackSession.queue.mode,
                            onPlaybackModeChanged =
                                playbackController::setPlaybackMode,
                            onOpenQueue = { queueSheetVisible = true },
                            onRetry = player::retry,
                            onResumeHintShown = player::onResumeHintShown,
                            onVideoScaleModeChanged =
                                player::setVideoScaleMode,
                            onBack = leave,
                        )
                    }
                }

                PlayerEntryState.Connecting,
                is PlayerEntryState.Failed,
                PlayerEntryState.Empty,
                -> {
                    PlayerBootstrapContent(
                        state = playerEntryState,
                        onReconnect = playbackController::reconnect,
                        onBack = leaveBootstrap,
                    )
                    if (playerEntryState == PlayerEntryState.Empty) {
                        // 当前项由非空变空：单次安全退出，effect 只执行一次。
                        LaunchedEffect(playerEntryState) {
                            leaveBootstrap()
                        }
                    }
                }
            }
        }
        composable<ImageReaderRoute> { entry ->
            val route =
                entry.toRoute<ImageReaderRoute>()
            val reader: ImageReaderViewModel = viewModel(
                key =
                    "image-reader:" +
                        route.selectedLogicalUrl,
                factory = viewModelFactory {
                    initializer {
                        ImageReaderViewModel(
                            directoryLogicalUrl =
                                route.directoryLogicalUrl,
                            selectedLogicalUrl =
                                route.selectedLogicalUrl,
                            contentRepository =
                                container
                                    .directoryContentRepository,
                            preferences =
                                container
                                    .readerPreferencesRepository,
                            session =
                                container.sessionManager,
                        )
                    }
                },
            )
            val state by
                reader.uiState
                    .collectAsStateWithLifecycle()
            ImageReaderScreen(
                state = state,
                imageLoader = container.imageLoader,
                onModeChanged = reader::setMode,
                onSortChanged =
                    reader::setSortOrder,
                onAnchorChanged =
                    reader::updateAnchor,
                onRetryDirectory =
                    reader::retryDirectory,
                onImageLoadError =
                    reader::onImageLoadError,
                onImageLoadSuccess =
                    reader::onImageLoadSuccess,
                onRetryImage =
                    reader::retryImage,
                onBack = {
                    navController.popBackStack()
                },
            )
        }
        }
    }
    if (queueSheetVisible && playbackSession.queue.items.isNotEmpty()) {
        PlaybackQueueSheet(
            queue = playbackSession.queue,
            onSelect = { mediaKey ->
                playbackController.select(mediaKey)
                queueSheetVisible = false
            },
            onMove = playbackController::move,
            onRemove = playbackController::remove,
            onClearExceptCurrent = playbackController::clearExceptCurrent,
            onStopAndClear = playbackController::clearAll,
            onDismiss = { queueSheetVisible = false },
            onRemoveRequest = { item, originalIndex ->
                playbackController.remove(item.mediaKey)
                rootScope.launch {
                    val result = globalSnackbarHostState.showSnackbar(
                        MediaSnackbarVisuals(
                            message = "已从队列删除 ${item.name}",
                            kind = MediaSnackbarKind.SUCCESS,
                            actionLabel = "撤销",
                            withDismissAction = true,
                        ),
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        playbackController.append(item)
                        playbackController.move(item.mediaKey, originalIndex)
                    }
                }
            },
        )
    }
}

/**
 * 全局重连/断连的非阻塞状态条：保留 Browser 内容的同时提示连接状态。
 * Connecting 只展示持续状态；Failed 附带根级重试与设置动作。
 */
@Composable
private fun GlobalReconnectBanner(
    current: ServerSessionState,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    when (current) {
        ServerSessionState.Connecting -> Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = MediaTheme.spacing.md,
                        vertical = MediaTheme.spacing.xs,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(16.dp))
                Text(
                    text = "正在重新连接",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(
                        start = MediaTheme.spacing.sm,
                    ),
                )
            }
        }

        is ServerSessionState.Failed -> Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = MediaTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = current.error.userMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) {
                    Text("重试")
                }
                TextButton(onClick = onOpenSettings) {
                    Text("服务器设置")
                }
            }
        }

        is ServerSessionState.Connected -> Unit
    }
}
