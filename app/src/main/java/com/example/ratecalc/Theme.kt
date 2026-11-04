package com.example.ratecalc

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF006A60),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9EF2E3),
    onPrimaryContainer = Color(0xFF00201C),
    secondaryContainer = Color(0xFFCCE8E2),
    surface = Color(0xFFF8FAF9),
    background = Color(0xFFF8FAF9)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82D5C7),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFF9EF2E3),
    secondaryContainer = Color(0xFF334B47),
    surface = Color(0xFF0F1514),
    background = Color(0xFF0F1514)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
