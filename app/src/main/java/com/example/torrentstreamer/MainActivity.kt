@file:Suppress("DEPRECATION")
@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.media3.common.util.UnstableApi::class
)

package com.example.torrentstreamer

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle // ВІДНОВЛЕНО КРИТИЧНИЙ СИСТЕМНИЙ ІМПОРТ!
import android.util.Rational
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.view.WindowManager
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.torrentstreamer.ui.theme.TorrentStreamerTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import androidx.media3.common.util.UnstableApi

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private var currentTorrent by mutableStateOf<Torrent?>(null)
    private var currentStreamUrl by mutableStateOf<String?>(null)
    private var currentStreamTitle by mutableStateOf<String?>(null)

    // Стан Picture-in-Picture режиму
    private var isPipModeActive by mutableStateOf(false)

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ініціалізація безшовного Edge-to-Edge з абсолютно прозорими системними панелями
        val transparentStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(
            statusBarStyle = transparentStyle,
            navigationBarStyle = transparentStyle
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // ЛІКВІДАЦІЯ СИСТЕМНОЇ ЗАЛИВКИ: вимикаємо примусове накладання фонового scrim-ефекту Android OS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        // ІГНОРУВАННЯ ВИРІЗУ КАМЕРИ: дозвіл візуалізації інтерфейсу безпосередньо в зоні камери
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Запит дозволу на надсилання сповіщень для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        TorrServerManager.start(this)

        handleIntent(intent)

        // Налаштування автоматичного переходу в режим «картинка в картинці» для Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    combine(
                        viewModel.currentPlayingUrl,
                        viewModel.isAutoPipEnabled,
                        viewModel.videoBounds,
                        viewModel.videoSize,
                        viewModel.isPlayerPlaying
                    ) { activeUrl, autoPipEnabled, bounds, vSize, playing ->
                        DataPipPackage(activeUrl != null && autoPipEnabled && playing, bounds, vSize)
                    }.collect { data ->
                        val paramsBuilder = PictureInPictureParams.Builder()

                        paramsBuilder.setAutoEnterEnabled(data.shouldAutoPip)

                        if (data.shouldAutoPip && data.bounds != null) {
                            paramsBuilder.setSourceRectHint(data.bounds)
                        }

                        val rat = when {
                            data.vSize != null && data.vSize.width > 0 && data.vSize.height > 0 -> {
                                Rational(data.vSize.width, data.vSize.height)
                            }
                            data.bounds != null && data.bounds.width() > 0 && data.bounds.height() > 0 -> {
                                Rational(data.bounds.width(), data.bounds.height())
                            }
                            else -> null
                        }

                        rat?.let { r ->
                            val floatRatio = r.numerator.toFloat() / r.denominator
                            if (floatRatio in 0.4184f..2.39f) {
                                try {
                                    paramsBuilder.setAspectRatio(r)
                                } catch (_: Exception) {}
                            }
                        }

                        setPictureInPictureParams(paramsBuilder.build())
                    }
                }
            }
        }

        setContent {
            TorrentStreamerTheme {
                var showWebAdmin by remember { mutableStateOf(false) }
                var showNativeSettings by remember { mutableStateOf(false) }

                var backProgress by remember { mutableStateOf(0f) }
                val screenScale by animateFloatAsState(targetValue = 1f - (backProgress * 0.08f), label = "")
                val corners by animateDpAsState(targetValue = if (backProgress > 0f) 32.dp else 0.dp, label = "")

                LaunchedEffect(showWebAdmin, showNativeSettings) {
                    if (!showWebAdmin && !showNativeSettings) {
                        viewModel.refreshTorrents(showSpinner = false)
                        viewModel.loadSettings()
                    }
                }

                PredictiveBackHandler(enabled = showWebAdmin || showNativeSettings || currentStreamUrl != null || currentTorrent != null) { progress ->
                    try {
                        progress.collect { event -> backProgress = event.progress }
                        if (currentStreamUrl != null) {
                            currentStreamUrl = null
                            currentStreamTitle = null
                        } else if (currentTorrent != null) {
                            currentTorrent = null
                        }
                        showWebAdmin = false
                        showNativeSettings = false
                    } catch (e: CancellationException) {
                        // Очікувано
                    } finally {
                        backProgress = 0f
                    }
                }

                // ВЕЛИКИЙ FIX: Обгортка, що зафарбовує тло під predictive back та системним баром в AMOLED Black
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = screenScale
                                    scaleY = screenScale
                                    shadowElevation = if (backProgress > 0f) 24f * backProgress else 0f
                                    shape = RoundedCornerShape(corners.coerceAtLeast(0.dp))
                                    clip = true
                                },
                            color = androidx.compose.material3.MaterialTheme.colorScheme.background
                        ) {
                            val activePlaybackUrl by viewModel.currentPlayingUrl.collectAsState()
                            val activePlaybackTitle by viewModel.currentPlayingTitle.collectAsState()

                            when {
                                isPipModeActive && currentStreamUrl == null && activePlaybackUrl != null -> {
                                    PlayerScreen(
                                        videoUrl = activePlaybackUrl!!,
                                        title = activePlaybackTitle ?: "Відтворення",
                                        viewModel = viewModel,
                                        isInPipMode = true,
                                        onBack = {}
                                    )
                                }

                                showWebAdmin -> TorrWebScreen { showWebAdmin = false }
                                showNativeSettings -> VibeSettingsScreen(viewModel, { showNativeSettings = false }, { showWebAdmin = true })

                                currentStreamUrl != null && currentStreamTitle != null -> {
                                    PlayerScreen(
                                        videoUrl = currentStreamUrl!!,
                                        title = currentStreamTitle!!,
                                        viewModel = viewModel,
                                        isInPipMode = isPipModeActive,
                                        onBack = {
                                            currentStreamUrl = null
                                            currentStreamTitle = null
                                        }
                                    )
                                }

                                currentTorrent != null -> {
                                    FileSelectionScreen(
                                        torrent = currentTorrent!!,
                                        viewModel = viewModel,
                                        onBack = { currentTorrent = null },
                                        onFileSelect = { url, title ->
                                            currentStreamUrl = url
                                            currentStreamTitle = title
                                        }
                                    )
                                }

                                else -> VibeHomeScreen(
                                    viewModel = viewModel,
                                    onTorrentClick = { torrent ->
                                        viewModel.loadFiles(torrent.hash, force = false)
                                        currentTorrent = torrent
                                    },
                                    onOpenAdmin = { showNativeSettings = true },
                                    onResumeClick = { historyItem ->
                                        currentStreamUrl = historyItem.videoUrl
                                        currentStreamTitle = historyItem.title
                                    }
                                )
                            }
                        }

                        val isFullScreenActive = currentStreamUrl != null && currentStreamTitle != null
                        val shouldShowMiniPlayer = !isFullScreenActive && !showWebAdmin && !isPipModeActive

                        if (shouldShowMiniPlayer) {
                            val latestSession by viewModel.latestSession.collectAsState()
                            val currentUrl by viewModel.currentPlayingUrl.collectAsState()

                            if (latestSession != null || currentUrl != null) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .navigationBarsPadding()
                                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                                        .zIndex(10f)
                                ) {
                                    MiniPlayer(
                                        viewModel = viewModel,
                                        onClick = {
                                            val activeUrl = currentUrl ?: latestSession?.videoUrl
                                            val activeTitle = viewModel.currentPlayingTitle.value ?: latestSession?.title
                                            if (activeUrl != null && activeTitle != null) {
                                                currentStreamUrl = activeUrl
                                                currentStreamTitle = activeTitle
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "com.example.torrentstreamer.action.OPEN_PLAYER") {
            val activeUrl = PlaybackService.currentPlayingUrl.value
            val activeTitle = PlaybackService.currentTitle.value
            if (activeUrl != null && activeTitle != null) {
                currentStreamUrl = activeUrl
                currentStreamTitle = activeTitle
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val isPlaying = viewModel.isPlayerPlaying.value
        val isAutoPipEnabled = viewModel.isAutoPipEnabled.value

        if (isPlaying && isAutoPipEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val paramsBuilder = PictureInPictureParams.Builder()
                val activeSize = viewModel.videoSize.value
                val bounds = viewModel.videoBounds.value

                val rat = when {
                    activeSize != null && activeSize.width > 0 && activeSize.height > 0 -> {
                        Rational(activeSize.width, activeSize.height)
                    }
                    bounds != null && bounds.width() > 0 && bounds.height() > 0 -> {
                        Rational(bounds.width(), bounds.height())
                    }
                    else -> null
                }

                bounds?.let { paramsBuilder.setSourceRectHint(it) }
                rat?.let { r ->
                    val floatRatio = r.numerator.toFloat() / r.denominator
                    if (floatRatio in 0.4184f..2.39f) {
                        try {
                            paramsBuilder.setAspectRatio(r)
                        } catch (_: Exception) {}
                    }
                }
                enterPictureInPictureMode(paramsBuilder.build())
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipModeActive = isInPictureInPictureMode

        if (!isInPictureInPictureMode) {
            if (currentStreamUrl != null) {
                val autoRotate = viewModel.isAutoRotationEnabled.value
                val lockedPortrait = viewModel.isLockedPortrait.value

                requestedOrientation = when {
                    autoRotate -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                    lockedPortrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            } else {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }
}

private data class DataPipPackage(
    val shouldAutoPip: Boolean,
    val bounds: android.graphics.Rect?,
    val vSize: android.util.Size?
)