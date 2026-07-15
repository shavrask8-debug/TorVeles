@file:Suppress("UNUSED_PARAMETER", "DEPRECATION")

package com.example.torrentstreamer

import android.content.Context
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.overscroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.input.pointer.pointerInput
import coil3.compose.AsyncImage
import com.example.torrentstreamer.ui.theme.SquircleShape
import com.example.torrentstreamer.ui.theme.AppShapes
import com.example.torrentstreamer.data.WatchHistory
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun VibeHomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onTorrentClick: (Torrent) -> Unit,
    onOpenAdmin: () -> Unit,
    onResumeClick: (WatchHistory) -> Unit
) {
    val torrents by viewModel.torrents.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val watchHistory by viewModel.watchHistory.collectAsState()
    val latestSession by viewModel.latestSession.collectAsState()

    var editingTorrent by remember { mutableStateOf<Torrent?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }

    val categoriesList = listOf("Всі", "Фільм", "Серіал", "Аніме", "Інше")
    var selectedCategory by remember { mutableStateOf("Всі") }

    val view = LocalView.current
    val density = LocalDensity.current
    val context = LocalContext.current

    // Pulsing Tor Status Dot Animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    var settingsRotationTrigger by remember { mutableFloatStateOf(0f) }
    val settingsRotationAngle by animateFloatAsState(
        targetValue = settingsRotationTrigger,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "settings_rotation"
    )

    val torrentFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            performConfirmHaptic(view)
            viewModel.addTorrentFromFile(uri)
        }
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    val filteredTorrents = remember(torrents, searchQuery, selectedCategory) {
        torrents.filter { torrent ->
            val matchesSearch = searchQuery.isBlank() || torrent.title.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory == "Всі" || (torrent.type ?: "Фільм") == selectedCategory
            matchesSearch && matchesCategory
        }
    }

    val editableList = remember { mutableStateListOf<Torrent>() }
    var draggedKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(filteredTorrents) {
        if (draggedKey == null) {
            val needsUpdate = editableList.size != filteredTorrents.size ||
                    !editableList.zip(filteredTorrents).all { (a, b) ->
                        a.hash == b.hash && a.title == b.title && a.poster == b.poster && a.type == b.type
                    }
            if (needsUpdate) {
                editableList.clear()
                editableList.addAll(filteredTorrents)
            }
        }
    }

    val activeHistory = remember(watchHistory) {
        watchHistory.filter { !it.isFinished && it.lastPosition > 0L }.take(10)
    }

    val pullState = rememberPullToRefreshState()
    val refreshTargetOffsetPx = remember(density) { with(density) { 80.dp.toPx() } }
    val pullProgress by animateFloatAsState(
        targetValue = pullState.distanceFraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "pull_progress_smooth"
    )

    val currentPullOffsetPx = with(density) { (80.dp * pullProgress).toPx() }
    val contentTargetOffset = if (isRefreshing) refreshTargetOffsetPx else if (pullState.distanceFraction > 0f) currentPullOffsetPx else 0f

    val animatedContentOffset by animateFloatAsState(
        targetValue = contentTargetOffset,
        animationSpec = if (isRefreshing) spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow) else snap(),
        label = "elastic_content_offset"
    )

    val gridState = rememberLazyStaggeredGridState()
    var initiallyDraggedItemOffset by remember { mutableStateOf<IntOffset?>(null) }
    var draggedSize by remember { mutableStateOf<IntSize?>(null) }
    var dragOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var lastSwapTime by remember { mutableLongStateOf(0L) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Пошук медіа...", style = MaterialTheme.typography.bodyLarge) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            shape = SquircleShape(cornerRadiusRatio = 0.3f, smoothing = 0.6f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        searchQuery = ""
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Очистити",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clearFocusOnKeyboardDismiss()
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Vibe",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .graphicsLayer { alpha = pulseAlpha }
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiary)
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (isSearchActive) {
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                isSearchActive = false
                                searchQuery = ""
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад"
                            )
                        }
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                isSearchActive = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Пошук"
                            )
                        }

                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                settingsRotationTrigger += 90f
                                onOpenAdmin()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Налаштування",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer(rotationZ = settingsRotationAngle)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                windowInsets = TopAppBarDefaults.windowInsets
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    showAddDialog = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Додати торент", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = SquircleShape(cornerRadiusRatio = 0.3f, smoothing = 0.6f)
            )
        },
        contentWindowInsets = WindowInsets(0.dp)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .pullToRefresh(
                    isRefreshing = isRefreshing,
                    state = pullState,
                    onRefresh = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        viewModel.refreshTorrents()
                    }
                )
        ) {
            if (pullProgress > 0f || isRefreshing) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .graphicsLayer {
                            scaleX = pullProgress
                            scaleY = pullProgress
                            alpha = pullProgress
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRefreshing) {
                        CircularWavyProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        )
                    } else {
                        CircularWavyProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            progress = { pullProgress },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = animatedContentOffset }
                    .background(MaterialTheme.colorScheme.background)
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                val item = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                    offset.x.toInt() in info.offset.x..(info.offset.x + info.size.width) &&
                                            offset.y.toInt() in info.offset.y..(info.offset.y + info.size.height)
                                }
                                val isMediaItem = editableList.any { it.hash == item?.key }
                                if (item != null && isMediaItem) {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    draggedKey = item.key as String
                                    initiallyDraggedItemOffset = item.offset
                                    draggedSize = item.size
                                    dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount

                                val currentKey = draggedKey ?: return@detectDragGesturesAfterLongPress
                                val originalOffset = initiallyDraggedItemOffset ?: return@detectDragGesturesAfterLongPress

                                val currentDraggedLayout = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == currentKey } ?: return@detectDragGesturesAfterLongPress

                                val currentCenterX = originalOffset.x + currentDraggedLayout.size.width / 2 + dragOffset.x
                                val currentCenterY = originalOffset.y + currentDraggedLayout.size.height / 2 + dragOffset.y

                                val hoverItem = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                    currentCenterX.toInt() in info.offset.x..(info.offset.x + info.size.width) &&
                                            currentCenterY.toInt() in info.offset.y..(info.offset.y + info.size.height)
                                }

                                val currentTime = System.currentTimeMillis()
                                if (hoverItem != null && hoverItem.key != currentKey && (currentTime - lastSwapTime > 180L)) {
                                    val fromIndex = editableList.indexOfFirst { t -> t.hash == currentKey }
                                    val toIndex = editableList.indexOfFirst { t -> t.hash == hoverItem.key }

                                    if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        editableList.add(toIndex, editableList.removeAt(fromIndex))
                                        lastSwapTime = currentTime
                                    }
                                }
                            },
                            onDragEnd = {
                                draggedKey = null
                                initiallyDraggedItemOffset = null
                                draggedSize = null
                                dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                viewModel.saveTorrentOrder(editableList)
                                performConfirmHaptic(view)
                            },
                            onDragCancel = {
                                draggedKey = null
                                initiallyDraggedItemOffset = null
                                draggedSize = null
                                dragOffset = androidx.compose.ui.geometry.Offset.Zero
                            }
                        )
                    }
            ) {
                LazyVerticalStaggeredGrid(
                    state = gridState,
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .overscroll(rememberOverscrollEffect()),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 12.dp,
                        bottom = if (latestSession != null) 100.dp else 12.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp
                ) {

                    // СЕКЦІЯ 1: ПРОДОВЖИТИ ПЕРЕГЛЯД
                    if (activeHistory.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                                Text(
                                    text = "ПРОДОВЖИТИ ПЕРЕГЛЯД",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
                                )

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(activeHistory, key = { it.videoUrl }) { historyItem ->
                                        val torrentHash = remember(historyItem.videoUrl) {
                                            val parts = historyItem.videoUrl.split("/")
                                            if (parts.size >= 5) parts[parts.size - 2] else null
                                        }
                                        val posterUrl = remember(torrentHash) {
                                            torrentHash?.let { context.getSharedPreferences("torrent_posters", Context.MODE_PRIVATE).getString(it, null) }
                                        }

                                        Row(
                                            modifier = Modifier
                                                .width(280.dp)
                                                .clip(SquircleShape(cornerRadiusRatio = 0.2f))
                                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                                .clickable {
                                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                                    onResumeClick(historyItem)
                                                }
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(SquircleShape(cornerRadiusRatio = 0.18f, smoothing = 0.6f))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (!posterUrl.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = posterUrl,
                                                        contentDescription = null,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Icon(Icons.Default.Movie, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = historyItem.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                                )
                                                val remainingMs = historyItem.duration - historyItem.lastPosition
                                                val remainingMin = (remainingMs / 1000 / 60).toInt()
                                                Text(
                                                    text = if (remainingMin > 0) "залишилось $remainingMin хв" else "майже завершено",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                val progress = historyItem.lastPosition.toFloat() / historyItem.duration.toFloat()
                                                LinearProgressIndicator(
                                                    progress = { progress.coerceIn(0f, 1f) },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(3.dp)
                                                        .clip(CircleShape),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // СЕКЦІЯ 2: ФІЛЬТРАЦІЯ КАТЕГОРІЙ
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                            Text(
                                text = "ВАША МЕДІАТЕКА",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
                            )

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(categoriesList) { cat ->
                                    FilterChip(
                                        selected = selectedCategory == cat,
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            selectedCategory = cat
                                        },
                                        label = { Text(cat, fontWeight = FontWeight.Bold) },
                                        shape = SquircleShape(cornerRadiusRatio = 0.35f, smoothing = 0.6f),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // СЕКЦІЯ 3: УСЯ МЕДІАТЕКА
                    if (editableList.isEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Медіафайлів не знайдено",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(
                            items = editableList,
                            key = { it.hash }
                        ) { torrent ->
                            val isCurrentDragged = draggedKey == torrent.hash

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(
                                        placementSpec = if (isCurrentDragged) snap() else spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                    .zIndex(if (isCurrentDragged) 10f else 1f)
                                    .graphicsLayer {
                                        alpha = if (isCurrentDragged) 0f else 1f
                                    }
                            ) {
                                TorrentExpressiveCard(
                                    torrent = torrent,
                                    isDragged = isCurrentDragged,
                                    onClick = { onTorrentClick(torrent) },
                                    onMenuClick = { editingTorrent = torrent }
                                )
                            }
                        }
                    }
                }

                val currentKey = draggedKey
                val originalOffset = initiallyDraggedItemOffset
                val size = draggedSize
                if (currentKey != null && originalOffset != null && size != null) {
                    val draggedTorrent = editableList.find { t -> t.hash == currentKey }
                    if (draggedTorrent != null) {
                        Box(
                            modifier = Modifier
                                .size(
                                    width = with(density) { size.width.toDp() },
                                    height = with(density) { size.height.toDp() }
                                )
                                .graphicsLayer {
                                    translationX = originalOffset.x + dragOffset.x
                                    translationY = originalOffset.y + dragOffset.y
                                    scaleX = 1.05f
                                    scaleY = 1.05f
                                    shadowElevation = 0f
                                }
                        ) {
                            TorrentExpressiveCard(
                                torrent = draggedTorrent,
                                isDragged = true,
                                onClick = {},
                                onMenuClick = {}
                            )
                        }
                    }
                }
            }
        }
    }

    val torrentToEdit = editingTorrent
    if (torrentToEdit != null) {
        EditTorrentBottomSheet(
            torrent = torrentToEdit,
            viewModel = viewModel,
            onDismiss = { editingTorrent = null },
            onConfirm = { title, poster, category ->
                viewModel.updateTorrent(torrentToEdit.hash, title, poster, category)
            },
            onRemove = {
                viewModel.removeTorrent(torrentToEdit.hash)
            }
        )
    }

    if (showAddDialog) {
        AddTorrentDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { link -> viewModel.addTorrent(link) },
            torrentFileLauncher = torrentFileLauncher
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TorrentExpressiveCard(
    torrent: Torrent,
    isDragged: Boolean,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isDragged -> 1.04f
            isPressed -> 0.94f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "card_press_scale"
    )

    val cardShape = SquircleShape(cornerRadiusRatio = 0.16f, smoothing = 0.6f)
    val posterShape = SquircleShape(cornerRadiusRatio = 0.18f, smoothing = 0.6f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(cardShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
                }
            ),
        shape = cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(posterShape)
            ) {
                if (!torrent.poster.isNullOrBlank()) {
                    AsyncImage(
                        model = torrent.poster,
                        contentDescription = torrent.title,
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

                FilledTonalIconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onMenuClick()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(36.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Керування торентом",
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (torrent.downloadSpeed > 0) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = formatDownloadSpeed(torrent.downloadSpeed),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            color = Color.White
                        )
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Text(
                    text = torrent.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = torrent.type ?: "Фільм",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    if (torrent.seeds > 0) {
                        Text(
                            text = "S: ${torrent.seeds} / P: ${torrent.peers}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun EditTorrentBottomSheet(
    torrent: Torrent,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit,
    onRemove: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val view = LocalView.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var title by remember { mutableStateOf(torrent.title) }
    var poster by remember { mutableStateOf(torrent.poster ?: "") }
    val posterHeight = if (poster.isNotBlank()) 96.dp else 0.dp

    val categoriesList = listOf("Фільм", "Серіал", "Аніме", "Музика", "Інше")
    var selectedCategory by remember { mutableStateOf(torrent.type ?: "Фільм") }

    var newMagnetLink by remember { mutableStateOf("") }
    var priorityDownload by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val postersDir = File(context.filesDir, "posters")
                if (!postersDir.exists()) {
                    postersDir.mkdirs()
                }
                val targetFile = File(postersDir, "${torrent.hash}_poster.jpg")
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(targetFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                poster = "file://${targetFile.absolutePath}?t=${System.currentTimeMillis()}"
                performConfirmHaptic(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val replacementTorrentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            performConfirmHaptic(view)
            val clonedTitle = title
            val clonedPoster = poster
            val clonedCategory = selectedCategory
            viewModel.replaceTorrentFromFile(torrent.hash, uri, clonedTitle, clonedPoster, clonedCategory)
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        sheetGesturesEnabled = false,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = SquircleShape(cornerRadiusRatio = 0.35f, smoothing = 0.6f)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                TextButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                        onRemove()
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = SquircleShape(cornerRadiusRatio = 0.3f, smoothing = 0.6f),
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Вилучити", fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(36.dp)
                        .height(4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            shape = CircleShape
                        )
                )

                Button(
                    onClick = {
                        performConfirmHaptic(view)
                        onConfirm(title, poster, selectedCategory)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = SquircleShape(cornerRadiusRatio = 0.3f, smoothing = 0.6f),
                    modifier = Modifier.align(Alignment.CenterEnd),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Зберегти", fontWeight = FontWeight.Bold)
                }
            }
        },
        modifier = Modifier
            .widthIn(max = 560.dp)
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = if (poster.isNotBlank()) 64.dp else 0.dp, height = posterHeight)
                            .clip(SquircleShape(cornerRadiusRatio = 0.18f, smoothing = 0.6f))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (poster.isNotBlank()) {
                            AsyncImage(
                                model = poster,
                                contentDescription = "Прев'ю обкладинки",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.Image, null, modifier = Modifier.size(20.dp))
                        }
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Налаштування контенту",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Назва") },
                            modifier = Modifier.fillMaxWidth().clearFocusOnKeyboardDismiss(),
                            shape = MaterialTheme.shapes.medium,
                            singleLine = true
                        )
                    }
                }

                Text(
                    text = "Тип контенту",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categoriesList.drop(1).forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                selectedCategory = cat
                            },
                            label = { Text(cat, fontWeight = FontWeight.Bold) },
                            shape = SquircleShape(cornerRadiusRatio = 0.3f, smoothing = 0.6f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                ListItem(
                    headlineContent = { Text("Пріоритет завантаження", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("Задіяти максимальний DHT пошук учасників") },
                    trailingContent = {
                        Switch(
                            checked = priorityDownload,
                            onCheckedChange = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                priorityDownload = it
                            },
                            thumbContent = if (priorityDownload) {
                                { Icon(Icons.Default.Check, null, modifier = Modifier.size(12.dp)) }
                            } else null
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                )

                OutlinedTextField(
                    value = poster,
                    onValueChange = { poster = it },
                    label = { Text("URL обкладинки") },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clipData = clipboard.primaryClip
                                if (clipData != null && clipData.itemCount > 0) {
                                    val text = clipData.getItemAt(0).text?.toString() ?: ""
                                    if (text.isNotBlank()) {
                                        poster = text
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Вставити з буфера", modifier = Modifier.size(20.dp))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clearFocusOnKeyboardDismiss(),
                    shape = MaterialTheme.shapes.medium
                )

                Button(
                    onClick = { filePickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Вибрати обкладинку із пристрою")
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Text(
                    text = "Швидка заміна торента",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = newMagnetLink,
                    onValueChange = { newMagnetLink = it },
                    label = { Text("Новий Magnet Link / Hash") },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clipData = clipboard.primaryClip
                                if (clipData != null && clipData.itemCount > 0) {
                                    val text = clipData.getItemAt(0).text?.toString() ?: ""
                                    if (text.isNotBlank()) {
                                        newMagnetLink = text
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Вставити з буфера", modifier = Modifier.size(20.dp))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clearFocusOnKeyboardDismiss(),
                    shape = MaterialTheme.shapes.medium,
                    maxLines = 3
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            replacementTorrentLauncher.launch("*/*")
                        },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.Default.UploadFile, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Файл .torrent")
                    }

                    Button(
                        onClick = {
                            performConfirmHaptic(view)
                            val clonedTitle = title
                            val clonedPoster = poster
                            val clonedCategory = selectedCategory
                            viewModel.replaceTorrentWithMagnet(torrent.hash, newMagnetLink, clonedTitle, clonedPoster, clonedCategory)
                            onDismiss()
                        },
                        enabled = newMagnetLink.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Link, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Замінити Magnet")
                    }
                }
            }
        }
    }
}

@Composable
fun AddTorrentDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    torrentFileLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    val view = LocalView.current
    var link by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Додати новий торент", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = { Text("Магнет-посилання або хеш") },
                    modifier = Modifier.fillMaxWidth().clearFocusOnKeyboardDismiss(),
                    shape = MaterialTheme.shapes.medium,
                    maxLines = 4
                )

                Text(
                    text = "або завантажте локальний файл:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = {
                        onDismiss()
                        torrentFileLauncher.launch("*/*")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.UploadFile, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Вибрати .torrent файл")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (link.isNotBlank()) {
                        performConfirmHaptic(view)
                        onConfirm(link)
                        onDismiss()
                    }
                },
                enabled = link.isNotBlank(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Додати")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onDismiss()
            }) {
                Text("Скасувати")
            }
        }
    )
}

private fun performConfirmHaptic(view: View) {
    val hapticConstant = if (android.os.Build.VERSION.SDK_INT >= 30) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.VIRTUAL_KEY
    }
    view.performHapticFeedback(hapticConstant)
}

private fun formatDownloadSpeed(speed: Long): String {
    if (speed <= 0) return "0 Б/с"
    val units = arrayOf("Б/с", "КБ/с", "МБ/с", "ГБ/с")
    val digitGroups = (Math.log10(speed.toDouble()) / Math.log10(1024.toDouble())).toInt()
    return "%.1f %s".format(Locale.ROOT, speed / Math.pow(1024.toDouble(), digitGroups.toDouble()), units[digitGroups])
}