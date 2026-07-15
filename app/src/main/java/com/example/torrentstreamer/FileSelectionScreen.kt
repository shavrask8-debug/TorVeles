@file:Suppress("UnstableApiUsage", "DEPRECATION", "deprecation")

package com.example.torrentstreamer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.toShape // Нативний M3E Composable-імпорт
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.example.torrentstreamer.data.AppDatabase
import com.example.torrentstreamer.data.WatchHistory
import com.example.torrentstreamer.ui.theme.SquircleShape
import com.example.torrentstreamer.ui.theme.AppShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

/**
 * Керування тактильним відгуком для фізичної взаємодії.
 */
object VibeHaptics {
    fun performTick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun performConfirm(view: View) {
        val hapticConstant: Int = if (Build.VERSION.SDK_INT >= 30) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performHapticFeedback(hapticConstant)
    }

    fun performLongPress(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun performSegmentTick(view: View) {
        val hapticConstant: Int = if (Build.VERSION.SDK_INT >= 34) {
            HapticFeedbackConstants.SEGMENT_TICK
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        view.performHapticFeedback(hapticConstant)
    }

    fun performGestureThreshold(view: View) {
        val hapticConstant: Int = if (Build.VERSION.SDK_INT >= 34) {
            HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performHapticFeedback(hapticConstant)
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FileSelectionScreen(
    torrent: Torrent,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onFileSelect: (String, String) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val files by viewModel.files.collectAsState()
    val isLoadingFiles by viewModel.isLoadingFiles.collectAsState()
    val watchHistory by viewModel.watchHistory.collectAsState()
    val latestSession by viewModel.latestSession.collectAsState()
    val activeTorrentState by viewModel.activeTorrentState.collectAsState()

    val isPlayerPlaying by viewModel.isPlayerPlaying.collectAsState()
    val isPlayerBuffering by viewModel.isBuffering.collectAsState()

    val density = LocalDensity.current
    val dao = remember { AppDatabase.getDatabase(context.applicationContext).watchHistoryDao() }
    var activeActionFile by remember { mutableStateOf<TorrentFile?>(null) }

    var showTorCircuitSheet by remember { mutableStateOf(false) }
    var showCacheConfigSheet by remember { mutableStateOf(false) }

    val currentUrl by viewModel.currentPlayingUrl.collectAsState()
    val isMiniPlayerActive = latestSession != null || currentUrl != null

    val navBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val animatedDockBottomPadding by animateDpAsState(
        targetValue = if (isMiniPlayerActive) (112.dp + navBarsPadding) else (24.dp + navBarsPadding),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "dock_jump_animation"
    )

    val isToolbarExpanded = rememberSaveable { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()

    val isToolbarVisible = remember { mutableStateOf(true) }
    val lastScrollIndex = remember { mutableIntStateOf(0) }
    val lastScrollOffset = remember { mutableIntStateOf(0) }

    LaunchedEffect(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset) {
        val currentIndex = lazyListState.firstVisibleItemIndex
        val currentOffset = lazyListState.firstVisibleItemScrollOffset

        isToolbarVisible.value = if (currentIndex > lastScrollIndex.value) {
            false
        } else if (currentIndex < lastScrollIndex.value) {
            true
        } else {
            currentOffset <= lastScrollOffset.value
        }

        lastScrollIndex.value = currentIndex
        lastScrollOffset.value = currentOffset
    }

    val toolbarScrollOffset by animateDpAsState(
        targetValue = if (isToolbarVisible.value) 0.dp else 120.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "toolbar_slide"
    )

    val pullState = rememberPullToRefreshState()
    val pullProgress = pullState.distanceFraction.coerceIn(0f, 1f)

    val elasticOffset by animateDpAsState(
        targetValue = if (isLoadingFiles) 48.dp else (100.dp * pullProgress),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "page_elastic_bounce"
    )

    val targetNotchOffset = if (isLoadingFiles) {
        88.dp
    } else if (pullProgress > 0.01f) {
        (-150).dp + (238.dp * pullProgress)
    } else {
        (-150).dp
    }

    val refreshNotchOffset by animateDpAsState(
        targetValue = targetNotchOffset,
        animationSpec = if (isLoadingFiles) {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        },
        label = "notch_refresh_slide"
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            val isExpanded = maxWidth >= 680.dp

            if (isExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                    ) {
                        Box(modifier = Modifier.weight(1f).clipToBounds()) {
                            FilesLazyList(
                                filesList = files,
                                watchHistory = watchHistory,
                                torrent = torrent,
                                lazyListState = lazyListState,
                                currentPlayingUrl = currentUrl,
                                isPlayerPlaying = isPlayerPlaying,
                                isPlayerBuffering = isPlayerBuffering,
                                onBack = onBack,
                                onFileClick = { streamUrl, cleanFileName ->
                                    VibeHaptics.performConfirm(view)
                                    onFileSelect(streamUrl, cleanFileName)
                                },
                                onFileLongClick = { file ->
                                    VibeHaptics.performLongPress(view)
                                    activeActionFile = file
                                },
                                onPlayPauseClick = { streamUrl, cleanFileName, isActive ->
                                    if (isActive) {
                                        viewModel.togglePlayback()
                                    } else {
                                        VibeHaptics.performConfirm(view)
                                        onFileSelect(streamUrl, cleanFileName)
                                    }
                                },
                                listBottomPadding = 24.dp,
                                listTopPadding = 0.dp
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TorCircuitPanel(
                            activeTorrent = activeTorrentState ?: torrent,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OfflineDownloaderPanel(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    PullToRefreshBox(
                        isRefreshing = isLoadingFiles,
                        onRefresh = {
                            VibeHaptics.performConfirm(view)
                            viewModel.loadFiles(torrent.hash, force = true)
                        },
                        state = pullState,
                        indicator = {},
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .offset(y = elasticOffset)
                        ) {
                            FilesLazyList(
                                filesList = files,
                                watchHistory = watchHistory,
                                torrent = torrent,
                                lazyListState = lazyListState,
                                currentPlayingUrl = currentUrl,
                                isPlayerPlaying = isPlayerPlaying,
                                isPlayerBuffering = isPlayerBuffering,
                                onBack = onBack,
                                onFileClick = { streamUrl, cleanFileName ->
                                    VibeHaptics.performConfirm(view)
                                    onFileSelect(streamUrl, cleanFileName)
                                },
                                onFileLongClick = { file ->
                                    VibeHaptics.performLongPress(view)
                                    activeActionFile = file
                                },
                                onPlayPauseClick = { streamUrl, cleanFileName, isActive ->
                                    if (isActive) {
                                        viewModel.togglePlayback()
                                    } else {
                                        VibeHaptics.performConfirm(view)
                                        onFileSelect(streamUrl, cleanFileName)
                                    }
                                },
                                listBottomPadding = if (isMiniPlayerActive) (210.dp + navBarsPadding) else (120.dp + navBarsPadding),
                                listTopPadding = statusBarHeight + 12.dp
                            )
                        }
                    }

                    val indicatorAlpha = ((refreshNotchOffset + 150.dp) / 238.dp).coerceIn(0f, 1f)

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .zIndex(200f)
                            .offset(y = refreshNotchOffset)
                            .graphicsLayer { alpha = indicatorAlpha }
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularWavyProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        )
                    }

                    VerticalFloatingToolbar(
                        expanded = isToolbarExpanded.value,
                        floatingActionButton = {
                            FloatingToolbarDefaults.VibrantFloatingActionButton(
                                onClick = {
                                    VibeHaptics.performConfirm(view)
                                    isToolbarExpanded.value = !isToolbarExpanded.value
                                }
                            ) {
                                Icon(
                                    imageVector = if (isToolbarExpanded.value) Icons.Default.MenuOpen else Icons.Default.Menu,
                                    contentDescription = "Керування доком"
                                )
                            }
                        },
                        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = animatedDockBottomPadding, end = 16.dp)
                            .offset(x = toolbarScrollOffset)
                            .zIndex(10f),
                        content = {
                            IconButton(
                                onClick = {
                                    VibeHaptics.performConfirm(view)
                                    showTorCircuitSheet = true
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Маршрутизація Tor"
                                )
                            }
                            IconButton(
                                onClick = {
                                    VibeHaptics.performConfirm(view)
                                    showCacheConfigSheet = true
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DownloadForOffline,
                                    contentDescription = "Офлайн завантаження та кеш"
                                )
                            }
                            IconButton(
                                onClick = {
                                    VibeHaptics.performConfirm(view)
                                    viewModel.loadFiles(torrent.hash, force = true)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Оновити список"
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    if (showTorCircuitSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTorCircuitSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)) {
                TorCircuitPanel(
                    activeTorrent = activeTorrentState ?: torrent,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showCacheConfigSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCacheConfigSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)) {
                OfflineDownloaderPanel(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun FilesLazyList(
    filesList: List<TorrentFile>,
    watchHistory: List<WatchHistory>,
    torrent: Torrent,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    currentPlayingUrl: String?,
    isPlayerPlaying: Boolean,
    isPlayerBuffering: Boolean,
    onBack: () -> Unit,
    onFileClick: (String, String) -> Unit,
    onFileLongClick: (TorrentFile) -> Unit,
    onPlayPauseClick: (String, String, Boolean) -> Unit,
    listBottomPadding: androidx.compose.ui.unit.Dp,
    listTopPadding: androidx.compose.ui.unit.Dp
) {
    val context = LocalContext.current
    val promptVlcEnabled = remember(context) {
        context.getSharedPreferences("vibe_prefs", Context.MODE_PRIVATE).getBoolean("prompt_vlc", false)
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = listTopPadding,
            bottom = listBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            TorrentHeroHeader(
                torrent = torrent,
                filesCount = filesList.size,
                watchHistory = watchHistory,
                onBack = onBack,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        if (filesList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Серій не знайдено",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(
                items = filesList,
                key = { file: TorrentFile -> file.index }
            ) { file: TorrentFile ->
                val cleanFileName = file.path.substringAfterLast("/")
                val streamUrl = "http://127.0.0.1:8090/play/${torrent.hash}/${file.index}"
                val historyItem = watchHistory.find { it.videoUrl == streamUrl }

                val isActivePlaying = streamUrl == currentPlayingUrl
                val isCurrentlyMoving = isActivePlaying && isPlayerPlaying
                val isCurrentlyBuffering = isActivePlaying && isPlayerBuffering

                TorrentFileExpressiveCard(
                    fileName = cleanFileName,
                    fileSize = file.size,
                    historyItem = historyItem,
                    promptVlcEnabled = promptVlcEnabled,
                    isActivePlaying = isActivePlaying,
                    isCurrentlyMoving = isCurrentlyMoving,
                    isCurrentlyBuffering = isCurrentlyBuffering,
                    onClick = { onFileClick(streamUrl, cleanFileName) },
                    onLongClick = { onFileLongClick(file) },
                    onPlayPauseClick = { onPlayPauseClick(streamUrl, cleanFileName, isActivePlaying) },
                    onExternalClick = { openInVlc(context, streamUrl, cleanFileName) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TorrentHeroHeader(
    torrent: Torrent,
    filesCount: Int,
    watchHistory: List<WatchHistory>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "hero_scale"
    )

    val watchedCount = watchHistory.count { it.videoUrl.contains(torrent.hash) && it.isFinished }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale),
        shape = SquircleShape(cornerRadiusRatio = 0.16f, smoothing = 0.6f),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                if (!torrent.poster.isNullOrBlank()) {
                    AsyncImage(
                        model = torrent.poster,
                        contentDescription = "Обкладинка",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.45f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.75f)
                                )
                            )
                        )
                )

                FilledTonalIconButton(
                    onClick = {
                        VibeHaptics.performTick(view)
                        onBack()
                    },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopStart)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                    shape = SquircleShape(cornerRadiusRatio = 0.3f, smoothing = 0.6f),
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Text(
                        text = torrent.type ?: "Медіа",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = torrent.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (watchedCount > 0) {
                            "Переглянуто серій: $watchedCount з $filesCount"
                        } else {
                            "Всього серій: $filesCount"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (watchedCount > 0 && filesCount > 0) {
                val totalProgress = watchedCount.toFloat() / filesCount.toFloat()
                LinearProgressIndicator(
                    progress = totalProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TorrentFileExpressiveCard(
    fileName: String,
    fileSize: Long,
    historyItem: WatchHistory?,
    promptVlcEnabled: Boolean,
    isActivePlaying: Boolean,
    isCurrentlyMoving: Boolean,
    isCurrentlyBuffering: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onExternalClick: () -> Unit
) {
    val view = LocalView.current
    val cardShape = SquircleShape(cornerRadiusRatio = 0.16f, smoothing = 0.6f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "file_card_scale"
    )

    val playPauseInteractionSource = remember { MutableInteractionSource() }
    val isPlayPausePressed by playPauseInteractionSource.collectIsPressedAsState()
    val playPauseScale by animateFloatAsState(
        targetValue = if (isPlayPausePressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "play_pause_scale"
    )

    val animatedColor by animateColorAsState(
        targetValue = if (isActivePlaying) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "wave_color"
    )

    val animatedTrackColor by animateColorAsState(
        targetValue = if (isActivePlaying) {
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "wave_track_color"
    )

    val isFinished = historyItem?.isFinished == true
    val progress = if (historyItem != null && historyItem.duration > 0) {
        historyItem.lastPosition.toFloat() / historyItem.duration.toFloat()
    } else 0f

    val iconVector = when {
        isActivePlaying && isCurrentlyMoving -> Icons.Default.Pause
        isActivePlaying && !isCurrentlyMoving -> Icons.Default.PlayArrow
        isFinished -> Icons.Default.CheckCircle
        else -> Icons.Default.PlayArrow
    }

    // Dynamic Shape Morphing для іконки відтворення (M3E Hero moment)
    val morphProgress by animateFloatAsState(
        targetValue = if (isActivePlaying) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "status_badge_morph"
    )

    // КРИТИЧНИЙ ПАТЧ: Викликаємо toShape() в Composable-контексті безпосередньо!
    val playActiveShape = AppShapes.PlayActive.toShape()
    val staticSquircleShape = remember { SquircleShape(cornerRadiusRatio = 0.3f, smoothing = 0.6f) }

    val polymorphicShape: androidx.compose.ui.graphics.Shape = if (morphProgress > 0.5f) {
        playActiveShape
    } else {
        staticSquircleShape
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(cardShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = cardShape
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (isActivePlaying) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else if (isFinished) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    shape = polymorphicShape,
                    modifier = Modifier
                        .size(40.dp)
                        .scale(playPauseScale)
                        .clickable(
                            interactionSource = playPauseInteractionSource,
                            indication = null,
                            onClick = {
                                VibeHaptics.performConfirm(view)
                                onPlayPauseClick()
                            }
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isCurrentlyBuffering) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = if (isActivePlaying) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                trackColor = Color.Transparent
                            )
                        } else {
                            AnimatedContent(
                                targetState = iconVector,
                                transitionSpec = {
                                    (scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) + fadeIn())
                                        .togetherWith(scaleOut(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) + fadeOut())
                                },
                                label = "play_pause_icon_transition"
                            ) { targetIcon ->
                                Icon(
                                    imageVector = targetIcon,
                                    contentDescription = if (isActivePlaying && isCurrentlyMoving) "Пауза" else "Відтворити",
                                    tint = if (isActivePlaying) {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    } else if (isFinished) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = formatFileSize(fileSize),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (historyItem != null && !isFinished && historyItem.duration > 0) {
                            val remainingMs = historyItem.duration - historyItem.lastPosition
                            val remainingMin = (remainingMs / 1000 / 60).toInt()
                            if (remainingMin > 0) {
                                Text(
                                    text = "· залишок $remainingMin хв",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (progress > 0f && !isFinished) {
                        Spacer(modifier = Modifier.height(6.dp))

                        val progressHeight = if (isActivePlaying) 6.dp else 3.dp

                        LinearProgressIndicator(
                            progress = progress.coerceIn(0f, 1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(progressHeight)
                                .clip(RoundedCornerShape(progressHeight / 2)),
                            color = animatedColor,
                            trackColor = animatedTrackColor,
                            strokeCap = StrokeCap.Round
                        )
                    }
                }

                if (promptVlcEnabled) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            VibeHaptics.performConfirm(view)
                            onExternalClick()
                        },
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                SquircleShape(cornerRadiusRatio = 0.3f, smoothing = 0.6f)
                            )
                            .size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Відкрити у VLC",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TorCircuitPanel(
    activeTorrent: Torrent,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var isRawNodesModeEnabled by remember { mutableStateOf(false) }

    Card(
        modifier = modifier,
        shape = SquircleShape(cornerRadiusRatio = 0.16f, smoothing = 0.6f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "TOR CIRCUIT & SHIELD",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                AssistChip(
                    onClick = {
                        VibeHaptics.performTick(view)
                        isRawNodesModeEnabled = !isRawNodesModeEnabled
                    },
                    label = {
                        Text(
                            text = if (isRawNodesModeEnabled) "ПРИХОВАТИ IP" else "ПОКАЗАТИ IP",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = SquircleShape(cornerRadiusRatio = 0.35f, smoothing = 0.6f)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Text(
                text = "Маршрут анонімізації трафіку:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TorNodeHopCard(
                    nodeName = "Вхідний вузол",
                    ipAddress = if (isRawNodesModeEnabled) "109.112.4.92" else "109.***.***.92",
                    countryCode = "DE",
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = Icons.Default.KeyboardDoubleArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                TorNodeHopCard(
                    nodeName = "Ретранслятор",
                    ipAddress = if (isRawNodesModeEnabled) "185.220.101.4" else "185.***.***.4",
                    countryCode = "NL",
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = Icons.Default.KeyboardDoubleArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                TorNodeHopCard(
                    nodeName = "Вихідна заслінка",
                    ipAddress = if (isRawNodesModeEnabled) "45.153.160.132" else "45.***.***.132",
                    countryCode = "SE",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active роздавачі: ${activeTorrent.seeds}  ·  Учасники: ${activeTorrent.peers}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )

                val speedMbps = activeTorrent.downloadSpeed / 1024f / 1024f
                Text(
                    text = "%.2f MB/s".format(speedMbps),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
fun TorNodeHopCard(
    nodeName: String,
    ipAddress: String,
    countryCode: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = SquircleShape(cornerRadiusRatio = 0.22f, smoothing = 0.6f)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = nodeName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "[$countryCode]",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 11.sp
                )
                Icon(
                    imageVector = Icons.Default.DoneOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(10.dp)
                )
            }

            Text(
                text = ipAddress,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun OfflineDownloaderPanel(
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var isSequentialDownloadEnabled by remember { mutableStateOf(true) }
    var reserveCacheMb by remember { mutableFloatStateOf(256f) }

    Card(
        modifier = modifier,
        shape = SquircleShape(cornerRadiusRatio = 0.16f, smoothing = 0.6f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DownloadForOffline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "ОФЛАЙН-СХОВИЩЕ ТА КЕШ",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Послідовне завантаження фрагментів",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Пріоритет фрагментів за порядком відтворення серії",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = isSequentialDownloadEnabled,
                    onCheckedChange = {
                        VibeHaptics.performTick(view)
                        isSequentialDownloadEnabled = it
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Зарезервувати обсяг офлайн-кешу:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${reserveCacheMb.toInt()} MB",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Slider(
                value = reserveCacheMb,
                onValueChange = { newValue ->
                    val lastTickVal = reserveCacheMb.toInt()
                    val newTickVal = newValue.toInt()
                    if (newTickVal / 32 != lastTickVal / 32) {
                        VibeHaptics.performSegmentTick(view)
                    }
                    reserveCacheMb = newValue
                },
                valueRange = 64f..1024f,
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    thumbColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FileActionsBottomSheet(
    fileName: String,
    fileSize: Long,
    isWatched: Boolean,
    promptVlcEnabled: Boolean,
    onDismiss: () -> Unit,
    onToggleWatched: () -> Unit,
    onOpenInExternal: () -> Unit,
    onCopyLink: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val view = LocalView.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        sheetGesturesEnabled = false,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier
            .widthIn(max = 560.dp)
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = SquircleShape(cornerRadiusRatio = 0.25f, smoothing = 0.6f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatFileSize(fileSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            ListItem(
                headlineContent = {
                    Text(
                        text = if (isWatched) "Позначити як непереглянуте" else "Позначити як переглянуте",
                        fontWeight = FontWeight.Bold
                    )
                },
                supportingContent = {
                    Text(text = if (isWatched) "Скинути позначку завершення" else "Встановити прапорець завершення")
                },
                leadingContent = {
                    Icon(
                        imageVector = if (isWatched) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                        VibeHaptics.performGestureThreshold(view)
                        onToggleWatched()
                        onDismiss()
                    }
            )

            if (promptVlcEnabled) {
                ListItem(
                    headlineContent = { Text("Відкрити у зовнішньому плеєрі", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("Запустити відтворення через VLC або інший плеєр") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            VibeHaptics.performConfirm(view)
                            onOpenInExternal()
                            onDismiss()
                        }
                )
            }

            ListItem(
                headlineContent = { Text("Копіювати посилання", fontWeight = FontWeight.Bold) },
                supportingContent = { Text("Зберегти пряму адресу потоку в буфер обміну") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                        VibeHaptics.performConfirm(view)
                        onCopyLink()
                        onDismiss()
                    }
            )
        }
    }
}

/**
 * Локальна допоміжна функція для відкриття VLC.
 */
private fun openInVlc(context: Context, videoUrl: String, title: String) {
    try {
        val uri = videoUrl.toUri()
        val vlcIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            setPackage("org.videolan.vlc")
            putExtra("title", title)
            putExtra("from_start", false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(vlcIntent)
    } catch (_: Exception) {
        val genericIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(videoUrl.toUri(), "video/*")
            putExtra("title", title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(genericIntent, "Оберіть плеєр для відтворення"))
    }
}

/**
 * Локальна допоміжна функція форматування розміру файлу.
 */
private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 Б"
    val units = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ")
    val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
    val value = size / 1024.0.pow(digitGroups.toDouble())
    return "%.2f %s".format(Locale.ROOT, value, units[digitGroups])
}