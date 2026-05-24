package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
  )

private val AmoledColorScheme =
  darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = Color.Black,
    surface = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF121212),
    onSurfaceVariant = Color(0xFFE2E2E2)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
  )

@Composable
fun MyApplicationTheme(
  themeMode: Int = 3, // 0: Light, 1: Dark, 2: AMOLED, 3: System
  dynamicColor: Boolean = false, // Disable dynamicColor by default to keep brand consistency and support AMOLED properly
  content: @Composable () -> Unit,
) {
  val isDark = when (themeMode) {
    0 -> false
    1 -> true
    2 -> true
    else -> isSystemInDarkTheme()
  }

  val colorScheme = when {
    themeMode == 2 -> AmoledColorScheme
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    isDark -> DarkColorScheme
    else -> LightColorScheme
  }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

