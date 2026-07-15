package com.example.torrentstreamer

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.torrentstreamer.ui.theme.ThemeMode
import com.example.torrentstreamer.ui.theme.PresetPalette
import com.example.torrentstreamer.ui.theme.SquircleShape

@OptIn(ExperimentalLayoutApi::class)
fun Modifier.clearFocusOnKeyboardDismiss(): Modifier = composed {
    val isImeVisible = WindowInsets.isImeVisible
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }
    var keyboardAppearedSinceLastFocused by remember { mutableStateOf(false) }

    LaunchedEffect(isImeVisible, isFocused) {
        if (isFocused) {
            if (isImeVisible) {
                keyboardAppearedSinceLastFocused = true
            } else if (keyboardAppearedSinceLastFocused) {
                focusManager.clearFocus()
                keyboardAppearedSinceLastFocused = false
            }
        } else {
            keyboardAppearedSinceLastFocused = false
        }
    }

    onFocusEvent {
        isFocused = it.isFocused
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VibeSettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenWebAdmin: (String) -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val view = LocalView.current

    val sharedPrefs = remember { context.getSharedPreferences("vibe_prefs", Context.MODE_PRIVATE) }
    var promptVlc by remember { mutableStateOf(sharedPrefs.getBoolean("prompt_vlc", false)) }
    var localSettings by remember(settings) { mutableStateOf(settings) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("STORAGE", "SEARCH", "INTERFACE")

    var newName by remember { mutableStateOf("") }
    var newHost by remember { mutableStateOf("") }
    var newKey by remember { mutableStateOf("") }

    val currentUrl by viewModel.currentPlayingUrl.collectAsState()
    val latestSession by viewModel.latestSession.collectAsState()
    val isMiniPlayerActive = latestSession != null || currentUrl != null

    var activeThemeMode by remember {
        mutableStateOf(
            try {
                ThemeMode.valueOf(sharedPrefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM")
            } catch (_: Exception) {
                ThemeMode.SYSTEM
            }
        )
    }
    var activePreset by remember {
        mutableStateOf(
            try {
                PresetPalette.valueOf(sharedPrefs.getString("preset_palette", "Default") ?: "Default")
            } catch (_: Exception) {
                PresetPalette.Default
            }
        )
    }

    // 20 ФУНКЦІОНАЛЬНИХ ТА UI НАЛАШТУВАНЬ
    var autoPlayNext by remember { mutableStateOf(sharedPrefs.getBoolean("auto_play_next", true)) }
    var skipIntroSec by remember { mutableStateOf(sharedPrefs.getFloat("skip_intro_sec", 0f)) }
    var volumeGestureEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("volume_gesture_enabled", true)) }
    var brightnessGestureEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("brightness_gesture_enabled", true)) }
    var doubleTapSeekSec by remember { mutableStateOf(sharedPrefs.getFloat("double_tap_seek_sec", 10f)) }
    var autoLandscapePlayer by remember { mutableStateOf(sharedPrefs.getBoolean("auto_landscape_player", true)) }
    var subtitlesTextSize by remember { mutableStateOf(sharedPrefs.getFloat("subtitles_text_size", 18f)) }
    var amoledPureBlack by remember { mutableStateOf(sharedPrefs.getBoolean("vibe_amoled_pure_black", true)) }
    var torrentSequentialPriority by remember { mutableStateOf(sharedPrefs.getBoolean("torrent_sequential_priority", true)) }
    var torrentMaxActiveTasks by remember { mutableStateOf(sharedPrefs.getFloat("torrent_max_active_tasks", 3f)) }

    var showColorPickerSheet by remember { mutableStateOf(false) }

    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val physicalPath = getPhysicalPathFromUri(context, uri)
            if (physicalPath != null) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                localSettings = localSettings.copy(torrentsSavePath = physicalPath)
                viewModel.saveSettings(localSettings)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // А. ВЕРХНЯ ДОК-ПАНЕЛЬ (Floating Top Bar Dock)
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .zIndex(10f)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f),
                shape = SquircleShape(cornerRadiusRatio = 0.35f, smoothing = 0.6f),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }

                    Text(
                        text = "Vibe Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onOpenWebAdmin("http://127.0.0.1:8090")
                    }) {
                        Icon(Icons.Default.Language, contentDescription = "Web UI")
                    }
                }
            }
        }

        // Б. ЕДЖ-ТУ-ЕДЖ СЦЕНА КРУЧЕННЯ КОНТЕНТУ
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .overscroll(rememberOverscrollEffect())
        ) {
            // Компенсація висоти плаваючої верхньої док-панелі
            Spacer(modifier = Modifier.statusBarsPadding().height(80.dp))

            // В. ПРУЖНІ КОЛХАНКОВІ ВКЛАДКИ (Sleek Pill Tabs)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .clip(SquircleShape(cornerRadiusRatio = 0.3f, smoothing = 0.6f))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabTitles.forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    val animScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.04f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "tab_pill_bouncy"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .scale(animScale)
                            .clip(SquircleShape(cornerRadiusRatio = 0.25f, smoothing = 0.6f))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                selectedTab = index
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Зміст обраних розділів налаштувань
            when (selectedTab) {
                0 -> {
                    SettingsGroup(title = "Параметри кешування") {
                        val currentCacheMb = localSettings.cacheSize / 1024 / 1024

                        SliderSetting(
                            label = "Розмір RAM-кешу",
                            value = currentCacheMb.toFloat(),
                            range = 32f..1024f,
                            unitLabel = "MB"
                        ) {
                            localSettings = localSettings.copy(cacheSize = it.toLong() * 1024 * 1024)
                            viewModel.saveSettings(localSettings)
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))

                        SliderSetting(
                            label = "Випереджальне читання (Readahead)",
                            value = localSettings.readAhead.toFloat(),
                            range = 40f..95f,
                            unitLabel = "%"
                        ) {
                            localSettings = localSettings.copy(readAhead = it.toInt())
                            viewModel.saveSettings(localSettings)
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))

                        val preloadPercentage = localSettings.preloadCache
                        val calculatedPreloadMb = (currentCacheMb * (preloadPercentage / 100f)).toInt()
                        SliderSetting(
                            label = "Передбуферизація перед запуском - $preloadPercentage% ($calculatedPreloadMb MB)",
                            value = preloadPercentage.toFloat(),
                            range = 0f..100f,
                            unitLabel = "%"
                        ) {
                            localSettings = localSettings.copy(preloadCache = it.toInt())
                            viewModel.saveSettings(localSettings)
                        }
                    }

                    SettingsGroup(title = "Параметри завантаження торентів") {
                        SwitchSetting(
                            label = "Послідовне завантаження частин (Sequential)",
                            checked = torrentSequentialPriority,
                            subtext = "Пріоритезація фрагментів у порядку відтворення серії"
                        ) {
                            torrentSequentialPriority = it
                            sharedPrefs.edit().putBoolean("torrent_sequential_priority", it).apply()
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))

                        SliderSetting(
                            label = "Максимум паралельних завантажень",
                            value = torrentMaxActiveTasks,
                            range = 1f..5f,
                            unitLabel = "завдань"
                        ) {
                            torrentMaxActiveTasks = it
                            sharedPrefs.edit().putFloat("torrent_max_active_tasks", it).apply()
                        }
                    }

                    SettingsGroup(title = "Локальне збереження") {
                        Text(
                            text = "Місце збереження кешу",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    localSettings = localSettings.copy(useDisk = false)
                                    viewModel.saveSettings(localSettings)
                                },
                                color = if (!localSettings.useDisk) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(Modifier.padding(14.dp), contentAlignment = Alignment.Center) {
                                    Text("RAM", fontWeight = FontWeight.Bold, color = if (!localSettings.useDisk) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            Surface(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    val safeExtDir = context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath
                                    localSettings = localSettings.copy(
                                        useDisk = true,
                                        torrentsSavePath = if (localSettings.torrentsSavePath.isNullOrBlank()) safeExtDir else localSettings.torrentsSavePath
                                    )
                                    viewModel.saveSettings(localSettings)
                                },
                                color = if (localSettings.useDisk) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(Modifier.padding(14.dp), contentAlignment = Alignment.Center) {
                                    Text("Disk", fontWeight = FontWeight.Bold, color = if (localSettings.useDisk) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }

                        if (localSettings.useDisk) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = localSettings.torrentsSavePath ?: "",
                                    onValueChange = {
                                        localSettings = localSettings.copy(torrentsSavePath = it)
                                        viewModel.saveSettings(localSettings)
                                    },
                                    label = { Text("Torrents Save Path") },
                                    modifier = Modifier.weight(1f).clearFocusOnKeyboardDismiss(),
                                    shape = MaterialTheme.shapes.medium
                                )
                                Button(
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        directoryPickerLauncher.launch(null)
                                    },
                                    shape = MaterialTheme.shapes.medium,
                                    contentPadding = PaddingValues(12.dp),
                                    modifier = Modifier.height(56.dp)
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = "Select Folder")
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            SwitchSetting(
                                label = "Remove Cache On Drop",
                                checked = localSettings.removeCacheOnDrop
                            ) {
                                localSettings = localSettings.copy(removeCacheOnDrop = it)
                                viewModel.saveSettings(localSettings)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            promptVlc = false
                            sharedPrefs.edit()
                                .putBoolean("prompt_vlc", false)
                                .apply()
                            val defaults = viewModel.resetSettingsToDefault()
                            localSettings = defaults
                            activeThemeMode = ThemeMode.SYSTEM
                            activePreset = PresetPalette.Default
                            sharedPrefs.edit()
                                .putString("theme_mode", "SYSTEM")
                                .putString("preset_palette", "Default")
                                .apply()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Restore, null)
                        Spacer(Modifier.width(8.dp))
                        Text("RESET TO DEFAULT", fontWeight = FontWeight.ExtraBold)
                    }

                    Spacer(modifier = Modifier.height(140.dp))
                }

                1 -> {
                    // ТАБ 2: SEARCH (Джерела, індексатори пошуку)
                    SettingsGroup(title = "Search") {
                        SwitchSetting(
                            label = "Turn on torrents search by RuTor",
                            checked = localSettings.enableRutorSearch
                        ) {
                            localSettings = localSettings.copy(enableRutorSearch = it)
                            viewModel.saveSettings(localSettings)
                        }
                    }

                    SettingsGroup(title = "Torznab Search") {
                        SwitchSetting(
                            label = "Enable Torznab Search",
                            checked = localSettings.enableTorznabSearch
                        ) {
                            localSettings = localSettings.copy(enableTorznabSearch = it)
                            viewModel.saveSettings(localSettings)
                        }

                        if (localSettings.enableTorznabSearch) {
                            Spacer(Modifier.height(16.dp))

                            OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it },
                                label = { Text("Name (Optional)") },
                                placeholder = { Text("My Tracker") },
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth().clearFocusOnKeyboardDismiss()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = newHost,
                                onValueChange = { newHost = it },
                                label = { Text("Torznab Host URL") },
                                placeholder = { Text("http://localhost:9117") },
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth().clearFocusOnKeyboardDismiss()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = newKey,
                                onValueChange = { newKey = it },
                                label = { Text("API Key") },
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth().clearFocusOnKeyboardDismiss()
                            )
                            Spacer(Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        Toast.makeText(context, "Перевірка підключення до Torznab...", Toast.LENGTH_SHORT).show()
                                    },
                                    enabled = newHost.isNotBlank(),
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.medium,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                ) {
                                    Text("TEST")
                                }

                                Button(
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        val safeTorznabList = localSettings.torznabUrls ?: emptyList()
                                        val mutableTorznabList = safeTorznabList.toMutableList()
                                        mutableTorznabList.add(TorznabConfig(newHost, newKey, name = newName.ifBlank { "Torznab Server" }))
                                        localSettings = localSettings.copy(torznabUrls = mutableTorznabList)
                                        viewModel.saveSettings(localSettings)

                                        newName = ""
                                        newHost = ""
                                        newKey = ""
                                    },
                                    enabled = newHost.isNotBlank(),
                                    modifier = Modifier.weight(1.5f),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Icon(Icons.Default.Add, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("ADD SERVER")
                                }
                            }

                            val safeTorznabList = localSettings.torznabUrls ?: emptyList()
                            if (safeTorznabList.isNotEmpty()) {
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "Підключені індексатори:",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )

                                val mutableTorznabList = safeTorznabList.toMutableList()
                                mutableTorznabList.forEachIndexed { index, config ->
                                    Card(
                                        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(text = config.name ?: "Torznab Server", fontWeight = FontWeight.Bold)
                                                Text(text = config.host ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            IconButton(onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                                mutableTorznabList.removeAt(index)
                                                localSettings = localSettings.copy(torznabUrls = mutableTorznabList)
                                                viewModel.saveSettings(localSettings)
                                            }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Видалити", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(140.dp))
                }

                2 -> {
                    // ТАБ 3: INTERFACE (Естетика, теми, спектр, жести, субтитри)
                    SettingsGroup(title = "Theme Configuration") {
                        Text(
                            text = "Режим теми оформлення",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Вертикальний селектор теми
                        Card(
                            shape = SquircleShape(cornerRadiusRatio = 0.16f, smoothing = 0.6f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                val modes = listOf(
                                    Triple(ThemeMode.LIGHT, "Світла тема", Icons.Default.LightMode),
                                    Triple(ThemeMode.DARK, "Темна тема", Icons.Default.DarkMode),
                                    Triple(ThemeMode.BLACK, "AMOLED Чорна тема", Icons.Default.PowerSettingsNew),
                                    Triple(ThemeMode.SYSTEM, "Системні налаштування", Icons.Default.SettingsSuggest)
                                )

                                modes.forEachIndexed { index, (mode, label, icon) ->
                                    val isSelected = activeThemeMode == mode
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent)
                                            .clickable {
                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                activeThemeMode = mode
                                                sharedPrefs.edit().putString("theme_mode", mode.name).apply()
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                activeThemeMode = mode
                                                sharedPrefs.edit().putString("theme_mode", mode.name).apply()
                                            },
                                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                        )
                                    }

                                    if (index < modes.lastIndex) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        Text(
                            text = "Джерело кольору акценту Vibe",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // ДВОВАРІАНТНА КАРТКА ВИБОРУ ДЖЕРЕЛА КОЛЬОРУ (Monet vs Custom HSV)
                        Card(
                            shape = SquircleShape(cornerRadiusRatio = 0.16f, smoothing = 0.6f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                val isCustomColor = sharedPrefs.getString("color_source", "SYSTEM") == "CUSTOM"

                                // Варіант 1: Системний Monet
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (!isCustomColor) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent)
                                        .clickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            sharedPrefs.edit()
                                                .putString("color_source", "SYSTEM")
                                                .putString("preset_palette", PresetPalette.Default.name)
                                                .apply()
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ColorLens,
                                            contentDescription = null,
                                            tint = if (!isCustomColor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Column {
                                            Text(
                                                text = "Динамічні кольори (Monet)",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (!isCustomColor) FontWeight.Bold else FontWeight.Normal,
                                                color = if (!isCustomColor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Адаптувати під шпалери пристрою (Android 12+)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    RadioButton(
                                        selected = !isCustomColor,
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            sharedPrefs.edit()
                                                .putString("color_source", "SYSTEM")
                                                .putString("preset_palette", PresetPalette.Default.name)
                                                .apply()
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                    )
                                }

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )

                                // Варіант 2: Власний колір (Ручний спектр)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isCustomColor) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent)
                                        .clickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            sharedPrefs.edit()
                                                .putString("color_source", "CUSTOM")
                                                .putString("preset_palette", PresetPalette.Custom.name)
                                                .apply()
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Palette,
                                            contentDescription = null,
                                            tint = if (isCustomColor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Column {
                                            Text(
                                                text = "Власний колір (Ручний режим)",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isCustomColor) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isCustomColor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Тонке спектральне калібрування акценту",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    RadioButton(
                                        selected = isCustomColor,
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            sharedPrefs.edit()
                                                .putString("color_source", "CUSTOM")
                                                .putString("preset_palette", PresetPalette.Custom.name)
                                                .apply()
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }

                        // Кнопка спектрального HSV виклику з'являється виключно при ручному режимі
                        val isCustomColorActive = sharedPrefs.getString("color_source", "SYSTEM") == "CUSTOM"
                        if (isCustomColorActive) {
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    showColorPickerSheet = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                shape = SquircleShape(cornerRadiusRatio = 0.28f, smoothing = 0.6f)
                            ) {
                                Icon(Icons.Default.Tune, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Калібрувати спектр кольору Vibe (HSV)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // СЕКЦІЯ: Жести
                    SettingsGroup(title = "Жести та керування плеєром") {
                        SwitchSetting(
                            label = "Вертикальний жест гучності",
                            checked = volumeGestureEnabled,
                            subtext = "Свайп по правій стороні екрана для регулювання звуку"
                        ) {
                            volumeGestureEnabled = it
                            sharedPrefs.edit().putBoolean("volume_gesture_enabled", it).apply()
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 8.dp))

                        SwitchSetting(
                            label = "Вертикальний жест яскравості",
                            checked = brightnessGestureEnabled,
                            subtext = "Свайп по лівій стороні екрана для регулювання яскравості"
                        ) {
                            brightnessGestureEnabled = it
                            sharedPrefs.edit().putBoolean("brightness_gesture_enabled", it).apply()
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 8.dp))

                        SliderSetting(
                            label = "Інтервал перемотки подвійним тапом",
                            value = doubleTapSeekSec,
                            range = 5f..30f,
                            unitLabel = "сек"
                        ) {
                            doubleTapSeekSec = it
                            sharedPrefs.edit().putFloat("double_tap_seek_sec", it).apply()
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 8.dp))

                        SwitchSetting(
                            label = "Авто-ландшафт при старті",
                            checked = autoLandscapePlayer,
                            subtext = "Примусово повертати плеєр у горизонт при відкритті"
                        ) {
                            autoLandscapePlayer = it
                            sharedPrefs.edit().putBoolean("auto_landscape_player", it).apply()
                        }
                    }

                    // СЕКЦІЯ: Епізоди
                    SettingsGroup(title = "Прогрес та епізоди") {
                        SwitchSetting(
                            label = "Автоматичне відтворення наступної серії",
                            checked = autoPlayNext,
                            subtext = "Перемикати на наступний файл після закінчення поточного"
                        ) {
                            autoPlayNext = it
                            sharedPrefs.edit().putBoolean("auto_play_next", it).apply()
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 8.dp))

                        SliderSetting(
                            label = "Автопропуск перших секунд (заставки)",
                            value = skipIntroSec,
                            range = 0f..90f,
                            unitLabel = "сек"
                        ) {
                            skipIntroSec = it
                            sharedPrefs.edit().putFloat("skip_intro_sec", it).apply()
                        }
                    }

                    // СЕКЦІЯ: Субтитри
                    SettingsGroup(title = "Субтитри та Діагностика") {
                        SliderSetting(
                            label = "Розмір тексту субтитрів",
                            value = subtitlesTextSize,
                            range = 12f..32f,
                            unitLabel = "sp"
                        ) {
                            subtitlesTextSize = it
                            sharedPrefs.edit().putFloat("subtitles_text_size", it).apply()
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 8.dp))

                        val isDiagnosticsHudEnabled by viewModel.isDiagnosticsHudEnabled.collectAsState()
                        SwitchSetting(
                            label = "Відображати діагностичний HUD у плеєрі",
                            checked = isDiagnosticsHudEnabled,
                            subtext = "Швидкість торента, FPS, кодек, роздільна здатність"
                        ) {
                            viewModel.setDiagnosticsHudEnabled(it)
                        }
                    }

                    // АТРИБУЦІЯ TMDB API
                    SettingsGroup(title = "The Movie Database (TMDB)") {
                        Text(
                            text = "Цей продукт використовує TMDB API, але не схвалений й не сертифікований TMDB. Усі постери фільмів та опис контенту автоматично витягуються та синхронізуються за допомогою The Movie Database (TMDB) API.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MovieFilter, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "THE MOVIE DATABASE API",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(140.dp))
                }
            } // end of when
        } // end of Column

        if (showColorPickerSheet) {
            ModalBottomSheet(
                onDismissRequest = { showColorPickerSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Спектральне налаштування кольору Vibe",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    val hueGradient = remember {
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                            )
                        )
                    }

                    var h by remember { mutableFloatStateOf(0f) }
                    var s by remember { mutableFloatStateOf(1f) }
                    var v by remember { mutableFloatStateOf(1f) }

                    LaunchedEffect(Unit) {
                        val curHex = sharedPrefs.getString("custom_accent_color", "#FF6650A4") ?: "#FF6650A4"
                        val curColor = android.graphics.Color.parseColor(curHex)
                        val hsv = FloatArray(3)
                        android.graphics.Color.colorToHSV(curColor, hsv)
                        h = hsv[0]
                        s = hsv[1]
                        v = hsv[2]
                    }

                    val liveColor = remember(h, s, v) {
                        Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
                    }

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(liveColor)
                            .align(Alignment.CenterHorizontally)
                            .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    )

                    Column {
                        Text("Тон (Hue): ${h.toInt()}°", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(hueGradient)
                        )
                        Slider(
                            value = h,
                            onValueChange = {
                                h = it
                                val colorInt = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
                                val hex = String.format("#%08X", colorInt)
                                sharedPrefs.edit().putString("custom_accent_color", hex).apply()
                            },
                            valueRange = 0f..360f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.onSurface,
                                activeTrackColor = Color.Transparent,
                                inactiveTrackColor = Color.Transparent
                            ),
                            modifier = Modifier.height(16.dp)
                        )
                    }

                    Column {
                        Text("Насиченість (Saturation): ${(s * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Slider(
                            value = s,
                            onValueChange = {
                                s = it
                                val colorInt = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
                                val hex = String.format("#%08X", colorInt)
                                sharedPrefs.edit().putString("custom_accent_color", hex).apply()
                            },
                            valueRange = 0f..1f
                        )
                    }

                    Column {
                        Text("Яскравість (Brightness): ${(v * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Slider(
                            value = v,
                            onValueChange = {
                                v = it
                                val colorInt = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
                                val hex = String.format("#%08X", colorInt)
                                sharedPrefs.edit().putString("custom_accent_color", hex).apply()
                            },
                            valueRange = 0f..1f
                        )
                    }

                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            activePreset = PresetPalette.Custom
                            sharedPrefs.edit().putString("preset_palette", PresetPalette.Custom.name).apply()
                            showColorPickerSheet = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = SquircleShape(cornerRadiusRatio = 0.3f, smoothing = 0.6f)
                    ) {
                        Text("Застосувати власну палітру", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun getPhysicalPathFromUri(context: Context, uri: Uri): String? {
    return try {
        val docId = DocumentsContract.getTreeDocumentId(uri) ?: return null
        val split = docId.split(":")
        val type = split[0]
        if ("primary".equals(type, ignoreCase = true)) {
            val baseDir = Environment.getExternalStorageDirectory().absolutePath
            if (split.size > 1) "$baseDir/${split[1]}" else baseDir
        } else {
            val baseDir = "/storage/$type"
            if (split.size > 1) "$baseDir/${split[1]}" else baseDir
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Surface(
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), content = content)
        }
    }
}

@Composable
fun SliderSetting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unitLabel: String,
    onValueChange: (Float) -> Unit
) {
    var temp by remember(value) { mutableFloatStateOf(value) }
    val view = LocalView.current

    Column(Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = "${temp.toInt()} $unitLabel",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Slider(
            value = temp,
            onValueChange = {
                if (it.toInt() != temp.toInt()) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                temp = it
            },
            onValueChangeFinished = { onValueChange(temp) },
            valueRange = range
        )
    }
}

@Composable
fun SwitchSetting(label: String, subtext: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val view = LocalView.current

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = label, color = MaterialTheme.colorScheme.onSurface)
            if (subtext != null) {
                Text(text = subtext, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onCheckedChange(it)
            }
        )
    }
}