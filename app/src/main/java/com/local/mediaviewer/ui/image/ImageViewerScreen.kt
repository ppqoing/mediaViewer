package com.local.mediaviewer.ui.image

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.local.mediaviewer.image.ImageViewerUiState
import com.local.mediaviewer.image.MediaImageLoaderFactory
import com.local.mediaviewer.image.ZoomReducer
import com.local.mediaviewer.image.ZoomTransform

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    name: String,
    state: ImageViewerUiState,
    imageLoader: ImageLoader,
    onLoadError: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    var zoom by remember {
        mutableStateOf(ZoomTransform())
    }
    val context = LocalContext.current
    val request = remember(
        context,
        state.requestUrl,
        state.requestGeneration,
    ) {
        MediaImageLoaderFactory.createRequest(
            context = context,
            url = state.requestUrl,
        )
    }

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector =
                                Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .clipToBounds()
                .background(Color.Black),
        ) {
            SubcomposeAsyncImage(
                model = request,
                imageLoader = imageLoader,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("media_image")
                    .pointerInput(Unit) {
                        detectTransformGestures {
                                _,
                                pan,
                                gestureZoom,
                                _,
                            ->
                            zoom = ZoomReducer.gesture(
                                current = zoom,
                                zoomChange = gestureZoom,
                                panChange = pan,
                            )
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                zoom = ZoomReducer.reset()
                            },
                        )
                    }
                    .graphicsLayer {
                        scaleX = zoom.scale
                        scaleY = zoom.scale
                        translationX = zoom.offset.x
                        translationY = zoom.offset.y
                    },
                loading = {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                },
                error = {
                    LaunchedEffect(request) {
                        onLoadError()
                    }
                },
                success = {
                    SubcomposeAsyncImageContent()
                },
            )
            if (state.isRefreshingEndpoint) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            state.errorMessage?.let { message ->
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = message,
                        color = Color.White,
                    )
                    Button(onClick = onRetry) {
                        Text("重试")
                    }
                }
            }
        }
    }
}
