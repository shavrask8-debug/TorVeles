package com.example.torrentstreamer.ui.theme

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color // Імпорт кольорів зафіксовано
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TorrentStreamerTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember(context) { context.getSharedPreferences("vibe_prefs", Context.MODE_PRIVATE) }

    // Реактивне зчитування налаштувань теми та джерела кольорів Vibe
    var themeModeStr by remember { mutableStateOf(sharedPrefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM") }
    var presetStr by remember { mutableStateOf(sharedPrefs.getString("preset_palette", "Default") ?: "Default") }
    var customColorHex by remember { mutableStateOf(sharedPrefs.getString("custom_accent_color", "#FF6650A4") ?: "#FF6650A4") }

    // Нативний слухач SharedPreferences для миттєвої синхронізації при перемиканні кольорів у реальному часі
    DisposableEffect(sharedPrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "theme_mode") {
                themeModeStr = prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM"
            }
            if (key == "preset_palette") {
                presetStr = prefs.getString("preset_palette", "Default") ?: "Default"
            }
            if (key == "custom_accent_color") {
                customColorHex = prefs.getString("custom_accent_color", "#FF6650A4") ?: "#FF6650A4"
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val themeMode = remember(themeModeStr) {
        try { ThemeMode.valueOf(themeModeStr) } catch (_: Exception) { ThemeMode.SYSTEM }
    }
    val preset = remember(presetStr) {
        try { PresetPalette.valueOf(presetStr) } catch (_: Exception) { PresetPalette.Default }
    }

    // Парсинг кастомного HEX-коду
    val customSeed = remember(customColorHex) {
        try {
            Color(android.graphics.Color.parseColor(customColorHex))
        } catch (_: Exception) {
            Color(0xFF6650A4)
        }
    }

    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.BLACK -> true
        ThemeMode.SYSTEM -> systemDark
    }

    val colorScheme = appColorScheme(
        themeMode = themeMode,
        darkTheme = isDark,
        preset = preset,
        customSeed = customSeed
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        shapes = ExpressiveShapes,
        motionScheme = MotionScheme.expressive(),
        typography = AppTypography,
        content = content
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PredictiveBackContainer(
    enabled: Boolean = true,
    onBack: () -> Unit,
    onProgress: (Float, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    val backProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    var swipeEdgeFromLeft by remember { mutableStateOf(true) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val motionScheme = MaterialTheme.motionScheme
    val translationMaxPx = with(density) { 80.dp.toPx() }

    androidx.activity.compose.PredictiveBackHandler(enabled = enabled) { backEvents ->
        try {
            backEvents.collect { event ->
                swipeEdgeFromLeft = event.swipeEdge == androidx.activity.BackEventCompat.EDGE_LEFT
                backProgress.snapTo(event.progress)
                onProgress(event.progress, swipeEdgeFromLeft)
            }
            backProgress.animateTo(1f, androidx.compose.animation.core.tween(150))
            onBack()
        } catch (e: CancellationException) {
            backProgress.animateTo(0f, motionScheme.fastSpatialSpec())
            onProgress(0f, swipeEdgeFromLeft)
        }
    }

    val backScale = 1f - (backProgress.value * 0.08f)
    val backCornerRadius = 32.dp * backProgress.value
    val backTranslateX = if (swipeEdgeFromLeft) {
        backProgress.value * translationMaxPx
    } else {
        -backProgress.value * translationMaxPx
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = backScale
                scaleY = backScale
                translationX = backTranslateX
                shadowElevation = 24f * backProgress.value
                shape = RoundedCornerShape(backCornerRadius)
                clip = true
            },
        content = content
    )
}