package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant
)

private val AmoledColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = AmoledBackground,
    surface = AmoledSurface,
    surfaceVariant = AmoledSurfaceVariant
)

@Composable
fun NextTheme(
    themeMode: String = "system",
    fontSizeScale: String = "normal",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        "amoled" -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        themeMode == "amoled" -> AmoledColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
        }
    }

    val multiplier = when (fontSizeScale) {
        "small" -> 0.85f
        "large" -> 1.25f
        else -> 1.0f
    }

    val scaledTypography = androidx.compose.material3.Typography(
        displayLarge = Typography.displayLarge.scale(multiplier),
        displayMedium = Typography.displayMedium.scale(multiplier),
        displaySmall = Typography.displaySmall.scale(multiplier),
        headlineLarge = Typography.headlineLarge.scale(multiplier),
        headlineMedium = Typography.headlineMedium.scale(multiplier),
        headlineSmall = Typography.headlineSmall.scale(multiplier),
        titleLarge = Typography.titleLarge.scale(multiplier),
        titleMedium = Typography.titleMedium.scale(multiplier),
        titleSmall = Typography.titleSmall.scale(multiplier),
        bodyLarge = Typography.bodyLarge.scale(multiplier),
        bodyMedium = Typography.bodyMedium.scale(multiplier),
        bodySmall = Typography.bodySmall.scale(multiplier),
        labelLarge = Typography.labelLarge.scale(multiplier),
        labelMedium = Typography.labelMedium.scale(multiplier),
        labelSmall = Typography.labelSmall.scale(multiplier)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = scaledTypography,
        content = content
    )
}

private fun TextStyle.scale(factor: Float): TextStyle {
    val currentSize = fontSize
    if (currentSize.isSp) {
        return copy(
            fontSize = (currentSize.value * factor).sp,
            lineHeight = if (lineHeight.isSp) (lineHeight.value * factor).sp else lineHeight
        )
    }
    return this
}
