package com.example.torrentstreamer.ui.theme

import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

enum class ThemeMode { LIGHT, DARK, BLACK, SYSTEM }

enum class PresetPalette(val nameUk: String, val seed: Color) {
    Default("Дефолтна Vibe", Color(0xFF6650A4)),
    Custom("Власний колір (Ручний режим)", Color(0xFF6650A4))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun appColorScheme(
    themeMode: ThemeMode,
    darkTheme: Boolean,
    preset: PresetPalette,
    customSeed: Color? = null
): ColorScheme {
    val context = LocalContext.current

    // Вибір опорного seed-кольору
    val seedColor = if (preset == PresetPalette.Custom && customSeed != null) customSeed else preset.seed

    val baseScheme = when {
        // Якщо обрано системний колір Monet та пристрій підтримує динамічні палітри
        preset != PresetPalette.Custom && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> {
            darkColorScheme(
                primary = seedColor,
                secondary = seedColor.copy(alpha = 0.8f),
                tertiary = Color(0xFF3DDC84) // Pulse Tor-green
            )
        }
        else -> {
            lightColorScheme(
                primary = seedColor,
                secondary = seedColor.copy(alpha = 0.8f),
                tertiary = Color(0xFF2E7D32)
            )
        }
    }

    return if (themeMode == ThemeMode.BLACK && darkTheme) {
        baseScheme.copy(
            surface = Color.Black,
            background = Color.Black,
            surfaceContainerLow = Color(0xFF0C0C0C),
            surfaceContainer = Color(0xFF121212),
            surfaceContainerHigh = Color(0xFF1A1A1A),
            surfaceContainerHighest = Color(0xFF242424)
        )
    } else {
        baseScheme
    }
}