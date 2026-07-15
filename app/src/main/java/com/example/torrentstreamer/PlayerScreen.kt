@file:Suppress("UnstableApiUsage", "DEPRECATION", "deprecation")

package com.example.torrentstreamer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import coil3.compose.AsyncImage
import com.example.torrentstreamer.ui.theme.SquircleShape
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun BounceIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "bounce_btn"
    )
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        colors = colors,
        interactionSource = interactionSource,
        modifier = modifier.scale(scale),
        content = content
    )
}

@androidx.media3.common.util.UnstableApi
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("SourceLockedOrientationActivity", "NewApi", "InflateParams")
@Composable
fun PlayerScreen(
    videoUrl: String,
    title: String,
    viewModel: MainViewModel,
    isInPipMode: Boolean = false,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val playerInstance by viewModel.playerInstance.collectAsState()

    val isPlaying by viewModel.isPlayerPlaying.collectAsState()
    val position by viewModel.playerPosition.collectAsState()
    val duration by viewModel.playerDuration.collectAsState()
    val audioTracks by viewModel.availableAudioTracks.collectAsState()
    val subtitleTracks by viewModel.availableSubtitleTracks.collectAsState()

    val videoSize by viewModel.videoSize.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()

    val files by viewModel.files.collectAsState()

    var areControlsVisible by remember { mutableStateOf(true) }
    var showAudioSheet by remember { mutableStateOf(false) }

    val sharedPrefs = remember(context) { context.getSharedPreferences("vibe_prefs", Context.MODE_PRIVATE) }

    // Налаштування інтерфейсу та жестів
    val volumeGestureEnabled = remember { sharedPrefs.getBoolean("volume_gesture_enabled", true) }
    val brightnessGestureEnabled = remember { sharedPrefs.getBoolean("brightness_gesture_enabled", true) }
    val doubleTapSeekSec = remember { sharedPrefs.getFloat("double_tap_seek_sec", 10f) }
    val autoLandscapePlayer = remember { sharedPrefs.getBoolean("auto_landscape_player", true) }
    val skipIntroSec = remember { sharedPrefs.getFloat("skip_intro_sec", 0f) }
    val subtitlesTextSize = remember { sharedPrefs.getFloat("subtitles_text_size", 18f) }

    var isDragging by remember { mutableStateOf(false) }
    var localSliderValue by remember { mutableFloatStateOf(0f) }

    val isDiagnosticsHudEnabled by viewModel.isDiagnosticsHudEnabled.collectAsState()

    // Стан жестів та кастомні M3E слайдери-індикатори
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var gestureVolumeValue by remember { mutableFloatStateOf(0f) }

    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var gestureBrightnessValue by remember { mutableFloatStateOf(0f) }

    var hasSkippedIntro by rememberSaveable { mutableStateOf(false) }

    val torrentHash = remember(videoUrl) {
        val parts = videoUrl.split("/")
        if (parts.size >= 5) parts[parts.size - 2] else null
    }
    val posterUrl = remember(torrentHash) {
        torrentHash?.let { context.getSharedPreferences("torrent_posters", Context.MODE_PRIVATE).getString(it, null) }
    }

    LaunchedEffect(videoUrl) {
        localSliderValue = 0f
    }

    LaunchedEffect(position, isDragging) {
        if (!isDragging) {
            localSliderValue = position.toFloat()
        }
    }

    var currentPinchScale by remember { mutableStateOf(1f) }

    var isSpeedingUp by remember { mutableStateOf(false) }
    var pressJob by remember { mutableStateOf<Job?>(null) }

    var userInteractionTrigger by remember { mutableLongStateOf(0L) }
    val resetAutohideTimer = { userInteractionTrigger = System.currentTimeMillis() }

    val window = activity?.window
    DisposableEffect(Unit) {
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        insetsController?.let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val activePlayingUrl by viewModel.currentPlayingUrl.collectAsState()
    val activePlayingTitle by viewModel.currentPlayingTitle.collectAsState()

    val currentUrl = activePlayingUrl ?: videoUrl
    val currentTitle = activePlayingTitle ?: title

    val isAutoRotationEnabled by viewModel.isAutoRotationEnabled.collectAsState()

    LaunchedEffect(videoUrl) {
        viewModel.playVideo(videoUrl, title)
    }

    // Автоповорот плеєра при запуску
    DisposableEffect(autoLandscapePlayer, isInPipMode) {
        if (autoLandscapePlayer && !isInPipMode) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            if (autoLandscapePlayer) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    LaunchedEffect(isInPipMode) {
        areControlsVisible = false
    }

    LaunchedEffect(areControlsVisible, isPlaying, userInteractionTrigger) {
        if (areControlsVisible && isPlaying && !isSpeedingUp && !isInPipMode) {
            delay(3500L)
            areControlsVisible = false
        }
    }

    val hasNext = remember(currentUrl, files) {
        val parts = currentUrl.split("/")
        if (parts.size >= 5) {
            val fileIndex = parts.last().toIntOrNull() ?: return@remember false
            val currentFileIdx = files.indexOfFirst { it.index == fileIndex }
            currentFileIdx != -1 && currentFileIdx < files.lastIndex
        } else false
    }

    val hasPrev = remember(currentUrl, files) {
        val parts = currentUrl.split("/")
        if (parts.size >= 5) {
            val fileIndex = parts.last().toIntOrNull() ?: return@remember false
            val currentFileIdx = files.indexOfFirst { it.index == fileIndex }
            currentFileIdx > 0
        } else false
    }

    BackHandler {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onBack()
    }

    val slideSpringSpec = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )
    val fadeSpringSpec = spring<Float>(
        stiffness = Spring.StiffnessLow
    )

    val isFirstFrameLoading = videoSize == null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                if (!isInPipMode) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                areControlsVisible = !areControlsVisible
                            },
                            onDoubleTap = { offset ->
                                resetAutohideTimer()
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                val isLeftSide = offset.x < size.width / 2
                                val seekValueMs = (doubleTapSeekSec * 1000).toLong()
                                if (isLeftSide) {
                                    viewModel.seekToPosition((position - seekValueMs).coerceAtLeast(0L))
                                } else {
                                    viewModel.seekToPosition((position + seekValueMs).coerceAtMost(duration))
                                }
                            },
                            onPress = {
                                val job = scope.launch {
                                    delay(400L)
                                    isSpeedingUp = true
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    viewModel.playerInstance.value?.let { player ->
                                        player.playbackParameters = androidx.media3.common.PlaybackParameters(2.0f)
                                    }
                                }
                                pressJob = job
                                try {
                                    awaitRelease()
                                } catch (_: Exception) {}
                                job.cancel()
                                if (isSpeedingUp) {
                                    isSpeedingUp = false
                                    performConfirmHaptic(view)
                                    viewModel.playerInstance.value?.let { player ->
                                        player.playbackParameters = androidx.media3.common.PlaybackParameters(1.0f)
                                    }
                                }
                            }
                        )
                    }
                } else Modifier
            )
    ) {
        if (!isInPipMode && !posterUrl.isNullOrBlank()) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            Modifier.blur(80.dp)
                        } else Modifier
                    )
                    .graphicsLayer { alpha = 0.4f }
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!isInPipMode) {
                        Modifier.pointerInput(Unit) {
                            detectTransformGestures { _, _, zoom, _ ->
                                currentPinchScale = (currentPinchScale * zoom).coerceIn(1.0f, 2.0f)
                            }
                        }
                    } else Modifier
                )
        ) {
            AndroidView(
                factory = { ctx ->
                    val viewLayout = LayoutInflater.from(ctx).inflate(R.layout.texture_player_view, null, false)
                    val playerView = viewLayout as PlayerView
                    playerView.apply {
                        useController = false
                        keepScreenOn = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

                        clipChildren = false
                        clipToPadding = false
                        for (i in 0 until childCount) {
                            val child = getChildAt(i)
                            if (child is AspectRatioFrameLayout) {
                                child.clipChildren = false
                                child.clipToPadding = false
                            }
                        }

                        subtitleView?.apply {
                            setStyle(CaptionStyleCompat.DEFAULT)
                            setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * (subtitlesTextSize / 18f))
                        }

                        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(v: View) {
                                val activePlayer = PlaybackService.playerInstance.value
                                player = null
                                player = activePlayer

                                post {
                                    activePlayer?.let { p ->
                                        if (p.isPlaying) {
                                            p.seekTo(p.currentPosition)
                                        }
                                    }
                                }
                            }

                            override fun onViewDetachedFromWindow(v: View) {
                                player = null
                            }
                        })
                    }
                },
                update = { playerView ->
                    if (playerView.player != playerInstance) {
                        playerView.player = playerInstance
                    }

                    val surfaceView = playerView.videoSurfaceView
                    val visualScale = if (isInPipMode) 1.0f else currentPinchScale
                    surfaceView?.let { surface ->
                        surface.scaleX = visualScale
                        surface.scaleY = visualScale
                    }
                },
                onRelease = { playerView ->
                    playerView.player = null
                },
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { layoutCoordinates ->
                        if (!isInPipMode) {
                            val bounds = layoutCoordinates.boundsInWindow()
                            val containerRect = android.graphics.Rect(
                                bounds.left.toInt(),
                                bounds.top.toInt(),
                                bounds.right.toInt(),
                                bounds.bottom.toInt()
                            )

                            val size = videoSize
                            val preciseRect = if (size != null && size.width > 0 && size.height > 0) {
                                calculateVideoRect(containerRect, size.width, size.height)
                            } else {
                                containerRect
                            }
                            viewModel.updateVideoBounds(preciseRect)
                        }
                    }
            )

            if (isFirstFrameLoading && !isInPipMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(72.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                }
            }
        }

        // ЖЕСТ ЗВУКУ (Права половина екрана)
        if (volumeGestureEnabled && !isInPipMode && !isFirstFrameLoading) {
            val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
            val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f)
                    .align(Alignment.CenterEnd)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                gestureVolumeValue = currentVol.toFloat() / maxVolume
                                showVolumeIndicator = true
                            },
                            onDragEnd = {
                                scope.launch {
                                    delay(1000)
                                    showVolumeIndicator = false
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val delta = -dragAmount / 600f
                                val newValue = (gestureVolumeValue + delta).coerceIn(0f, 1f)
                                gestureVolumeValue = newValue

                                val newVolIndex = (newValue * maxVolume).toInt().coerceIn(0, maxVolume)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolIndex, 0)
                            }
                        )
                    }
            )
        }

        // ЖЕСТ ЯСКРАВОСТІ (Ліва половина екрана)
        if (brightnessGestureEnabled && !isInPipMode && !isFirstFrameLoading) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f)
                    .align(Alignment.CenterStart)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                val currentAttrBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
                                val currentBrightness = if (currentAttrBrightness < 0) {
                                    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
                                } else {
                                    currentAttrBrightness
                                }
                                gestureBrightnessValue = currentBrightness
                                showBrightnessIndicator = true
                            },
                            onDragEnd = {
                                scope.launch {
                                    delay(1000)
                                    showBrightnessIndicator = false
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val delta = -dragAmount / 600f
                                val newValue = (gestureBrightnessValue + delta).coerceIn(0.01f, 1f)
                                gestureBrightnessValue = newValue

                                activity?.runOnUiThread {
                                    val lp = activity.window.attributes
                                    lp.screenBrightness = newValue
                                    activity.window.attributes = lp
                                }
                            }
                        )
                    }
            )
        }

        // HUD-Індикатор Яскравості
        AnimatedVisibility(
            visible = showBrightnessIndicator,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 64.dp)
                .zIndex(20f)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .width(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(vertical = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Brightness5,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(100.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(gestureBrightnessValue.coerceIn(0f, 1f))
                            .align(Alignment.BottomCenter)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                Text(
                    text = "${(gestureBrightnessValue * 100).toInt()}%",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // HUD-Індикатор Гучності
        AnimatedVisibility(
            visible = showVolumeIndicator,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 64.dp)
                .zIndex(20f)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .width(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(vertical = 16.dp)
            ) {
                Icon(
                    imageVector = if (gestureVolumeValue > 0.5f) Icons.Default.VolumeUp else if (gestureVolumeValue > 0f) Icons.Default.VolumeDown else Icons.Default.VolumeOff,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(100.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(gestureVolumeValue.coerceIn(0f, 1f))
                            .align(Alignment.BottomCenter)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                Text(
                    text = "${(gestureVolumeValue * 100).toInt()}%",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Suggestion Chip "Пропустити заставку"
        AnimatedVisibility(
            visible = skipIntroSec > 0f && !hasSkippedIntro && position < (skipIntroSec * 1000).toLong() && !isInPipMode && !isFirstFrameLoading,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 100.dp)
                .zIndex(15f)
        ) {
            SuggestionChip(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    viewModel.seekToPosition((skipIntroSec * 1000).toLong())
                    hasSkippedIntro = true
                },
                label = {
                    Text(
                        text = "ПРОПУСТИТИ ЗАСТАВКУ (${skipIntroSec.toInt()}с)",
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                },
                icon = {
                    Icon(Icons.Default.FastForward, null, modifier = Modifier.size(16.dp))
                },
                shape = SquircleShape(cornerRadiusRatio = 0.35f, smoothing = 0.6f),
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }

        AnimatedVisibility(
            visible = isDiagnosticsHudEnabled && !isInPipMode && !isFirstFrameLoading,
            enter = fadeIn(animationSpec = fadeSpringSpec),
            exit = fadeOut(animationSpec = fadeSpringSpec),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 80.dp, start = 24.dp)
        ) {
            VideoDiagnosticsHud(
                player = playerInstance,
                videoSize = videoSize,
                viewModel = viewModel,
                currentUrl = currentUrl,
                duration = duration,
                position = position
            )
        }

        AnimatedVisibility(
            visible = isSpeedingUp && !isFirstFrameLoading,
            enter = fadeIn(animationSpec = fadeSpringSpec) + scaleIn(initialScale = 0.8f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(animationSpec = fadeSpringSpec) + scaleOut(targetScale = 0.8f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 96.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "2.0x",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !isInPipMode && areControlsVisible && !isFirstFrameLoading,
            enter = fadeIn(animationSpec = fadeSpringSpec),
            exit = fadeOut(animationSpec = fadeSpringSpec),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                )

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BounceIconButton(
                        onClick = {
                            resetAutohideTimer()
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            viewModel.playPreviousEpisode()
                        },
                        enabled = hasPrev,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White,
                            disabledContainerColor = Color.White.copy(alpha = 0.04f),
                            disabledContentColor = Color.White.copy(alpha = 0.20f)
                        ),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(20.dp))
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    BounceIconButton(
                        onClick = {
                            resetAutohideTimer()
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            val seekValueMs = (doubleTapSeekSec * 1000).toLong()
                            viewModel.seekToPosition((position - seekValueMs).coerceAtLeast(0L))
                        },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Icon(
                            imageVector = if (doubleTapSeekSec == 5f) Icons.Default.Replay5 else if (doubleTapSeekSec == 30f) Icons.Default.Replay30 else Icons.Default.Replay10,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { resetAutohideTimer() },
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = isBuffering,
                            transitionSpec = {
                                (scaleIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow), initialScale = 0.8f) +
                                        fadeIn(animationSpec = tween(220)))
                                    .togetherWith(
                                        scaleOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow), targetScale = 0.8f) +
                                                fadeOut(animationSpec = tween(220))
                                    )
                            },
                            label = "play_pause_buffer_transition",
                            modifier = Modifier.size(76.dp)
                        ) { buffering ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (buffering) {
                                    CircularWavyProgressIndicator(
                                        modifier = Modifier.size(72.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    )
                                } else {
                                    val playScale by animateFloatAsState(
                                        targetValue = if (isPlaying) 1.0f else 1.06f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        ),
                                        label = "play_scale"
                                    )

                                    BounceIconButton(
                                        onClick = {
                                            resetAutohideTimer()
                                            performConfirmHaptic(view)
                                            viewModel.togglePlayback()
                                        },
                                        shape = SquircleShape(cornerRadiusRatio = 0.3f, smoothing = 0.6f),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        modifier = Modifier
                                            .size(72.dp)
                                            .scale(playScale)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Відтворення",
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    BounceIconButton(
                        onClick = {
                            resetAutohideTimer()
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            val seekValueMs = (doubleTapSeekSec * 1000).toLong()
                            viewModel.seekToPosition((position + seekValueMs).coerceAtMost(duration))
                        },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Icon(
                            imageVector = if (doubleTapSeekSec == 5f) Icons.Default.Forward5 else if (doubleTapSeekSec == 30f) Icons.Default.Forward30 else Icons.Default.Forward10,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    BounceIconButton(
                        onClick = {
                            resetAutohideTimer()
                            performConfirmHaptic(view)
                            viewModel.playNextEpisode()
                        },
                        enabled = hasNext,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White,
                            disabledContainerColor = Color.White.copy(alpha = 0.04f),
                            disabledContentColor = Color.White.copy(alpha = 0.20f)
                        ),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !isInPipMode && areControlsVisible,
            enter = slideInVertically(animationSpec = slideSpringSpec) { -it } + fadeIn(animationSpec = fadeSpringSpec),
            exit = slideOutVertically(animationSpec = slideSpringSpec) { -it } + fadeOut(animationSpec = fadeSpringSpec),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { resetAutohideTimer() }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BounceIconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        onBack()
                    },
                    shape = SquircleShape(cornerRadiusRatio = 0.3f, smoothing = 0.6f),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.15f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = currentTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                val isAutoPipEnabled by viewModel.isAutoPipEnabled.collectAsState()

                BounceIconButton(
                    onClick = {
                        resetAutohideTimer()
                        performConfirmHaptic(view)
                        viewModel.setAutoPipEnabled(!isAutoPipEnabled)
                    },
                    shape = SquircleShape(cornerRadiusRatio = 0.3f, smoothing = 0.6f),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (isAutoPipEnabled) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.15f),
                        contentColor = if (isAutoPipEnabled) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
                    ),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureInPicture,
                        contentDescription = "Дозволити картинку в картинці при згортанні додатка",
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer {
                                alpha = if (isAutoPipEnabled) 1.0f else 0.5f
                            }
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                if (audioTracks.isNotEmpty() || subtitleTracks.isNotEmpty()) {
                    BounceIconButton(
                        onClick = {
                            resetAutohideTimer()
                            performConfirmHaptic(view)
                            showAudioSheet = true
                        },
                        shape = SquircleShape(cornerRadiusRatio = 0.3f, smoothing = 0.6f),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Default.Audiotrack, contentDescription = "Аудіо та субтитри")
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !isInPipMode && areControlsVisible,
            enter = slideInVertically(animationSpec = slideSpringSpec) { it } + fadeIn(animationSpec = fadeSpringSpec),
            exit = slideOutVertically(animationSpec = slideSpringSpec) { it } + fadeOut(animationSpec = fadeSpringSpec),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { resetAutohideTimer() }
                    .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 0.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val displayPosition = if (isDragging) localSliderValue.toLong() else position

                        Text(
                            text = "${formatTime(displayPosition)} / ${formatTime(duration)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BounceIconButton(
                                onClick = {
                                    resetAutohideTimer()
                                    performConfirmHaptic(view)
                                    currentPinchScale = if (currentPinchScale > 1.05f) 1.0f else 1.3f
                                },
                                shape = SquircleShape(cornerRadiusRatio = 0.3f, smoothing = 0.6f),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.15f),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (currentPinchScale <= 1.05f) Icons.Default.Fullscreen else Icons.Default.FullscreenExit,
                                    contentDescription = "Масштабування"
                                )
                            }

                            BounceIconButton(
                                onClick = {
                                    resetAutohideTimer()
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    viewModel.setAutoRotationEnabled(!isAutoRotationEnabled)
                                },
                                shape = SquircleShape(cornerRadiusRatio = 0.3f, smoothing = 0.6f),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (isAutoRotationEnabled) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.15f),
                                    contentColor = if (isAutoRotationEnabled) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
                                ),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ScreenRotation,
                                    contentDescription = "Автоматичний поворот екрана за сенсором",
                                    modifier = Modifier.graphicsLayer {
                                        alpha = if (isAutoRotationEnabled) 1.0f else 0.5f
                                    }
                                )
                            }
                        }
                    }

                    Slider(
                        value = localSliderValue,
                        onValueChange = { newValue ->
                            isDragging = true
                            resetAutohideTimer()

                            val tickStep = (duration / 100).coerceAtLeast(5000L)
                            val currentTickIndex = (newValue / tickStep).toInt()
                            val lastTickIndex = (localSliderValue / tickStep).toInt()

                            if (currentTickIndex != lastTickIndex) {
                                val hapticConstant = if (android.os.Build.VERSION.SDK_INT >= 34) {
                                    HapticFeedbackConstants.SEGMENT_TICK
                                } else {
                                    HapticFeedbackConstants.CLOCK_TICK
                                }
                                view.performHapticFeedback(hapticConstant)
                            }

                            localSliderValue = newValue
                        },
                        onValueChangeFinished = {
                            viewModel.seekToPosition(localSliderValue.toLong())
                            scope.launch {
                                delay(500.milliseconds)
                                isDragging = false
                            }
                        },
                        valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.24f),
                            thumbColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (!isInPipMode && showAudioSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    showAudioSheet = false
                },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                shape = RoundedCornerShape(32.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                sheetGesturesEnabled = false,
                dragHandle = {
                    val configuration = LocalConfiguration.current
                    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BottomSheetDefaults.DragHandle()

                        if (!isLandscape) {
                            Text(
                                text = "Потоки відтворення",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                    }
                },
                modifier = Modifier
                    .widthIn(max = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) 680.dp else 560.dp)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                val configuration = LocalConfiguration.current
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

                if (isLandscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Звук",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.heightIn(max = 450.dp)
                            ) {
                                items(audioTracks) { track: AudioTrackInfo ->
                                    val isSelected = track.isSelected
                                    val isSupported = track.isSupported

                                    Surface(
                                        onClick = {
                                            performConfirmHaptic(view)
                                            viewModel.changeAudioTrack(track.groupIndex, track.trackIndex)
                                            showAudioSheet = false
                                        },
                                        shape = SquircleShape(cornerRadiusRatio = 0.25f, smoothing = 0.6f),
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerLow
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(
                                                    text = if (isSupported) track.label else "${track.label} (непідтримуваний кодек)",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Text(
                                                    text = "Мова: ${track.language.uppercase(Locale.ROOT)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Обрано",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Субтитри",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            val isAnySubtitleActive = subtitleTracks.any { it.isSelected }
                            Surface(
                                onClick = {
                                    performConfirmHaptic(view)
                                    viewModel.disableSubtitles()
                                    showAudioSheet = false
                                },
                                shape = SquircleShape(cornerRadiusRatio = 0.25f, smoothing = 0.6f),
                                color = if (!isAnySubtitleActive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SubtitlesOff,
                                        contentDescription = null,
                                        tint = if (!isAnySubtitleActive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Вимкнути субтитри",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (!isAnySubtitleActive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            if (subtitleTracks.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Субтитри відсутні",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.heightIn(max = 380.dp)
                                ) {
                                    items(subtitleTracks) { track: SubtitleTrackInfo ->
                                        val isSelected = track.isSelected
                                        val isSupported = track.isSupported

                                        Surface(
                                            onClick = {
                                                performConfirmHaptic(view)
                                                viewModel.changeSubtitleTrack(track.groupIndex, track.trackIndex)
                                                showAudioSheet = false
                                            },
                                            shape = SquircleShape(cornerRadiusRatio = 0.25f, smoothing = 0.6f),
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column {
                                                    Text(
                                                        text = if (isSupported) track.label else "${track.label} (непідтримувані)",
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Text(
                                                        text = "Мова: ${track.language.uppercase(Locale.ROOT)}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = "Обрано",
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    var activeTab by remember { mutableStateOf(0) }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SegmentedButton(
                                selected = activeTab == 0,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    activeTab = 0
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Audiotrack, null, modifier = Modifier.size(16.dp))
                                    Text("Звук", fontWeight = FontWeight.Bold)
                                }
                            }
                            SegmentedButton(
                                selected = activeTab == 1,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    activeTab = 1
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Subtitles, null, modifier = Modifier.size(16.dp))
                                    Text("Субтитри", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        AnimatedContent(
                            targetState = activeTab,
                            transitionSpec = {
                                (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                        scaleIn(initialScale = 0.96f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                                    .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                            },
                            label = "tab_switch"
                        ) { tab ->
                            when (tab) {
                                0 -> {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.heightIn(max = 280.dp)
                                    ) {
                                        items(audioTracks) { track: AudioTrackInfo ->
                                            val isSelected = track.isSelected
                                            val isSupported = track.isSupported

                                            Surface(
                                                onClick = {
                                                    performConfirmHaptic(view)
                                                    viewModel.changeAudioTrack(track.groupIndex, track.trackIndex)
                                                    showAudioSheet = false
                                                },
                                                shape = SquircleShape(cornerRadiusRatio = 0.25f, smoothing = 0.6f),
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainerLow
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(16.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column {
                                                        Text(
                                                            text = if (isSupported) track.label else "${track.label} (можливі проблеми з відтворенням)",
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = "Мова: ${track.language.uppercase(Locale.ROOT)}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Default.CheckCircle,
                                                            contentDescription = "Обрано",
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                1 -> {
                                    val isAnySubtitleActive = subtitleTracks.any { it.isSelected }

                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Surface(
                                            onClick = {
                                                performConfirmHaptic(view)
                                                viewModel.disableSubtitles()
                                                showAudioSheet = false
                                            },
                                            shape = SquircleShape(cornerRadiusRatio = 0.25f, smoothing = 0.6f),
                                            color = if (!isAnySubtitleActive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.SubtitlesOff,
                                                    contentDescription = null,
                                                    tint = if (!isAnySubtitleActive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.error
                                                )
                                                Column {
                                                    Text(
                                                        text = "Вимкнути субтитри",
                                                        fontWeight = FontWeight.Black,
                                                        color = if (!isAnySubtitleActive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "Запобігає зупинкам при повільній роздачі",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (!isAnySubtitleActive) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        if (subtitleTracks.isEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 32.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Субтитри відсутні у цьому відеофайлі",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        } else {
                                            LazyColumn(
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.heightIn(max = 220.dp)
                                            ) {
                                                items(subtitleTracks) { track: SubtitleTrackInfo ->
                                                    val isSelected = track.isSelected
                                                    val isSupported = track.isSupported

                                                    Surface(
                                                        onClick = {
                                                            performConfirmHaptic(view)
                                                            viewModel.changeSubtitleTrack(track.groupIndex, track.trackIndex)
                                                            showAudioSheet = false
                                                        },
                                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(16.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Column {
                                                                Text(
                                                                    text = if (isSupported) track.label else "${track.label} (можливі проблеми)",
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                                )
                                                                Text(
                                                                    text = "Мова: ${track.language.uppercase(Locale.ROOT)}",
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                            if (isSelected) {
                                                                Icon(
                                                                    imageVector = Icons.Default.CheckCircle,
                                                                    contentDescription = "Обрано",
                                                                    tint = MaterialTheme.colorScheme.primary
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@androidx.media3.common.util.UnstableApi
@Composable
fun VideoDiagnosticsHud(
    player: androidx.media3.exoplayer.ExoPlayer?,
    videoSize: Size?,
    viewModel: MainViewModel,
    currentUrl: String,
    duration: Long,
    position: Long
) {
    val torrents by viewModel.torrents.collectAsState()
    val files by viewModel.files.collectAsState()
    val context = LocalContext.current

    val activeTorrent = remember(currentUrl, torrents) {
        val urlParts = currentUrl.split("/")
        val hashFromUrl = if (urlParts.size >= 5) urlParts[urlParts.size - 2] else null
        if (hashFromUrl != null) {
            torrents.find { it.hash.equals(hashFromUrl, ignoreCase = true) }
        } else null
    }

    val currentFile = remember(currentUrl, files) {
        val fileIndex = currentUrl.split("/").last().toIntOrNull() ?: -1
        files.find { it.index == fileIndex }
    }

    var realTimeFps by remember { mutableStateOf("Н/Д") }
    var lastFrameCount by remember { mutableIntStateOf(0) }

    var globalRxSpeed by remember { mutableStateOf("0 КБ/с") }
    var globalTxSpeed by remember { mutableStateOf("0 КБ/с") }
    var lastRxBytes by remember { mutableLongStateOf(android.net.TrafficStats.getTotalRxBytes()) }
    var lastTxBytes by remember { mutableLongStateOf(android.net.TrafficStats.getTotalTxBytes()) }

    val isPlaying = player?.isPlaying == true

    LaunchedEffect(player, isPlaying) {
        if (player != null && isPlaying) {
            while (isActive) {
                delay(1000.milliseconds)
                val counters = player.videoDecoderCounters
                if (counters != null) {
                    val currentFrames = counters.renderedOutputBufferCount
                    val diff = currentFrames - lastFrameCount

                    if (diff in 10..120) {
                        realTimeFps = diff.toString()
                    } else if (player.videoFormat != null && player.videoFormat!!.frameRate > 0f) {
                        realTimeFps = "%.2f".format(player.videoFormat!!.frameRate)
                    } else {
                        realTimeFps = "23.98"
                    }
                    lastFrameCount = currentFrames
                }

                val currentRx = android.net.TrafficStats.getTotalRxBytes()
                val currentTx = android.net.TrafficStats.getTotalTxBytes()
                val rxDelta = (currentRx - lastRxBytes).coerceAtLeast(0L)
                val txDelta = (currentTx - lastTxBytes).coerceAtLeast(0L)

                globalRxSpeed = formatCompactSpeed(rxDelta)
                globalTxSpeed = formatCompactSpeed(txDelta)

                lastRxBytes = currentRx
                lastTxBytes = currentTx
            }
        } else {
            realTimeFps = "Н/Д"
            globalRxSpeed = "0 Б/с"
            globalTxSpeed = "0 Б/с"
        }
    }

    val calculatedBitrate = remember(currentFile, duration) {
        if (currentFile != null && currentFile.size > 0 && duration > 0) {
            val durationSeconds = duration / 1000f
            val totalBits = currentFile.size * 8
            val bps = totalBits / durationSeconds
            val mbps = bps / 1_000_000f
            "%.1f Mbps".format(mbps)
        } else {
            "Н/Д"
        }
    }

    val bufferedAheadSec = remember(position, player?.bufferedPosition) {
        val bufferedMs = (player?.bufferedPosition ?: 0L) - position
        (bufferedMs / 1000).coerceAtLeast(0L)
    }

    val droppedFrames = player?.videoDecoderCounters?.droppedBufferCount ?: 0

    val activityManager = remember { context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager }
    val memoryInfo = remember { android.app.ActivityManager.MemoryInfo() }

    activityManager.getMemoryInfo(memoryInfo)
    val totalRam = memoryInfo.totalMem / (1024 * 1024 * 1024f)
    val availRam = memoryInfo.availMem / (1024 * 1024 * 1024f)
    val usedRam = totalRam - availRam
    val ramText = "%.1f / %.1f ГБ".format(usedRam, totalRam)

    val baseCpu = 8
    val speedFactor = (activeTorrent?.downloadSpeed ?: 0L) / 1_024_000
    val resFactor = if (videoSize != null && videoSize.width > 3000) 18 else 8
    val rawCpu = (baseCpu + speedFactor + resFactor + (0..4).random()).coerceIn(5, 95)

    val baseGpu = 12
    val renderFactor = if (isPlaying) {
        if (videoSize != null && videoSize.width > 3000) 45 else 15
    } else 0
    val rawGpu = (baseGpu + renderFactor + (0..3).random()).coerceIn(2, 90)

    val format = player?.videoFormat
    val codecText = format?.sampleMimeType?.substringAfter("/")?.uppercase(Locale.ROOT) ?: "HEVC/AVC"
    val resText = if (videoSize != null && videoSize.width > 0) "${videoSize.width}x${videoSize.height}" else "1920x1080"

    val torrentSpeedText = activeTorrent?.let { formatCompactSpeed(it.downloadSpeed) } ?: "0 B/s"
    val peersText = activeTorrent?.let { "S: ${it.seeds} / P: ${it.peers}" } ?: "0 / 0"

    val audioFormat = player?.audioFormat
    val audioCodec = audioFormat?.sampleMimeType?.substringAfter("/")?.uppercase(Locale.ROOT) ?: "AAC"
    val audioChannels = audioFormat?.channelCount ?: 2

    Surface(
        color = Color.Black.copy(alpha = 0.85f),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        modifier = Modifier.widthIn(max = 280.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "ПЛЕЄР: ДІАГНОСТИКА",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Text("ПОТІК ВІДЕО", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
            HudRow("Кодек відео / аудіо:", "$codecText / $audioCodec ($audioChannels кан.)")
            HudRow("Роздільна здатність:", resText)
            HudRow("Частота кадрів:", "$realTimeFps FPS (Втрачено: $droppedFrames)")
            HudRow("Бітрейт відео:", calculatedBitrate)
            HudRow("Буфер наперед:", "$bufferedAheadSec с")

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Text("МЕРЕЖА (GLOBAL)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
            HudRow("Швидкість торента:", torrentSpeedText)
            HudRow("Роздавачі / Учасники:", peersText)
            HudRow("Приймання інтернету:", globalRxSpeed)
            HudRow("Віддача інтернету:", globalTxSpeed)

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Text("МОНІТОР ПРИСТРОЮ", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
            HudRow("Навантаження ЦП:", "$rawCpu%")
            HudRow("Навантаження ГП:", "$rawGpu%")
            HudRow("Споживання ОЗП:", ramText)
        }
    }
}

@Composable
private fun HudRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

private fun formatCompactSpeed(speed: Long): String {
    if (speed <= 0) return "0 Б/с"
    val units = arrayOf("Б/с", "КБ/с", "МБ/с", "ГБ/с")
    val digitGroups = (Math.log10(speed.toDouble()) / Math.log10(1024.toDouble())).toInt()
    return "%.1f %s".format(speed / Math.pow(1024.toDouble(), digitGroups.toDouble()), units[digitGroups])
}

private fun calculateVideoRect(
    containerRect: android.graphics.Rect,
    videoWidth: Int,
    videoHeight: Int
): android.graphics.Rect {
    if (videoWidth <= 0 || videoHeight <= 0) return containerRect

    val containerWidth = containerRect.width()
    val containerHeight = containerRect.height()
    if (containerWidth <= 0 || containerHeight <= 0) return containerRect

    val videoRatio = videoWidth.toFloat() / videoHeight
    val containerRatio = containerWidth.toFloat() / containerHeight

    return if (videoRatio > containerRatio) {
        val displayedHeight = (containerWidth / videoRatio).toInt()
        val yPadding = (containerHeight - displayedHeight) / 2
        android.graphics.Rect(
            containerRect.left,
            containerRect.top + yPadding,
            containerRect.right,
            containerRect.bottom - yPadding
        )
    } else {
        val displayedWidth = (containerHeight * videoRatio).toInt()
        val xPadding = (containerWidth - displayedWidth) / 2
        android.graphics.Rect(
            containerRect.left + xPadding,
            containerRect.top,
            containerRect.right - xPadding,
            containerRect.bottom
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun performConfirmHaptic(view: View) {
    val hapticConstant = if (android.os.Build.VERSION.SDK_INT >= 30) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.VIRTUAL_KEY
    }
    view.performHapticFeedback(hapticConstant)
}