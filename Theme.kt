package com.next.files

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val LightFallback = lightColorScheme(
    primary = Color(0xFF0B57D0), onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD), onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF565F71), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDAE2F9), onSecondaryContainer = Color(0xFF131C2B),
    tertiary = Color(0xFF146C2E), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC4EED0), onTertiaryContainer = Color(0xFF072711),
    background = Color(0xFFF8F9FF), onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF), onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC), onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFF74777F), outlineVariant = Color(0xFFC4C6D0),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F3F9),
    surfaceContainer = Color(0xFFECEEF4), surfaceContainerHigh = Color(0xFFE6E8EE),
    surfaceContainerHighest = Color(0xFFE1E2E8),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002)
)

private val DarkFallback = darkColorScheme(
    primary = Color(0xFFA8C7FA), onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0), onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFBDC7DC), onSecondary = Color(0xFF283141),
    secondaryContainer = Color(0xFF3F4759), onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFF6DD58C), onTertiary = Color(0xFF003915),
    tertiaryContainer = Color(0xFF0F5223), onTertiaryContainer = Color(0xFFC4EED0),
    background = Color(0xFF111318), onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318), onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474E), onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099), outlineVariant = Color(0xFF44474E),
    surfaceContainerLowest = Color(0xFF0C0E13), surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024), surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6)
)

private val AppFont = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.Bold),
    Font(R.font.tajawal_extrabold, FontWeight.ExtraBold)
)

private fun TextStyle.f(w: FontWeight? = null) = copy(fontFamily = AppFont, fontWeight = w ?: fontWeight)
private val T0 = Typography()
private val AppType = Typography(
    displayLarge = T0.displayLarge.f(FontWeight.ExtraBold),
    displayMedium = T0.displayMedium.f(FontWeight.ExtraBold),
    displaySmall = T0.displaySmall.copy(fontSize = 36.sp, lineHeight = 52.sp).f(FontWeight.ExtraBold),
    headlineLarge = T0.headlineLarge.f(FontWeight.ExtraBold),
    headlineMedium = T0.headlineMedium.f(FontWeight.ExtraBold),
    headlineSmall = T0.headlineSmall.f(FontWeight.Bold),
    titleLarge = T0.titleLarge.f(FontWeight.Bold),
    titleMedium = T0.titleMedium.f(FontWeight.Bold),
    titleSmall = T0.titleSmall.f(FontWeight.Bold),
    bodyLarge = T0.bodyLarge.f(FontWeight.Normal),
    bodyMedium = T0.bodyMedium.f(FontWeight.Normal),
    bodySmall = T0.bodySmall.f(FontWeight.Normal),
    labelLarge = T0.labelLarge.f(FontWeight.Bold),
    labelMedium = T0.labelMedium.f(FontWeight.Medium),
    labelSmall = T0.labelSmall.f(FontWeight.Medium)
)

val AccentColors = listOf(0xFF0B57D0, 0xFF1B6C3A, 0xFF6750A4, 0xFF9A4600, 0xFFA8305F)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = when (Prefs.theme) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val ctx = LocalContext.current
    val base: ColorScheme = when {
        Prefs.accent < 0 && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        else -> if (dark) DarkFallback else LightFallback
    }
    var cs = base
    if (Prefs.accent in AccentColors.indices) {
        val a = Color(AccentColors[Prefs.accent])
        cs = if (dark) {
            cs.copy(primary = a.copy(alpha = 1f).let { lighten(it) }, onPrimary = Color(0xFF111318),
                primaryContainer = a, onPrimaryContainer = Color.White)
        } else {
            cs.copy(primary = a, onPrimary = Color.White,
                primaryContainer = lighten(a, 0.82f), onPrimaryContainer = Color(0xFF111318))
        }
    }
    if (dark && Prefs.amoled) {
        cs = cs.copy(
            background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0A), surfaceContainer = Color(0xFF121212),
            surfaceContainerHigh = Color(0xFF1A1A1A), surfaceContainerHighest = Color(0xFF222222)
        )
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = cs, typography = AppType, content = content)
    }
}

private fun lighten(c: Color, f: Float = 0.6f): Color =
    Color(c.red + (1f - c.red) * f, c.green + (1f - c.green) * f, c.blue + (1f - c.blue) * f, 1f)

class CookieShape(private val lobes: Int = 9, private val amp: Float = 0.06f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = min(size.width, size.height) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val p = Path()
        val steps = 180
        for (i in 0..steps) {
            val a = (2.0 * PI * i / steps).toFloat()
            val rad = r * (1f - amp + amp * cos(lobes * a))
            val x = cx + rad * cos(a)
            val y = cy + rad * sin(a)
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return Outline.Generic(p)
    }
}

@Composable
fun CookieIcon(
    icon: ImageVector, size: Dp, container: Color, tint: Color, modifier: Modifier = Modifier,
    spin: Boolean = true, desc: String? = null, onClick: (() -> Unit)? = null
) {
    val shape = remember { CookieShape() }
    val rot by rememberInfiniteTransition(label = "cookie").animateFloat(
        0f, 360f, infiniteRepeatable(tween(26000, easing = LinearEasing)), label = "rot"
    )
    val click = if (onClick != null) Modifier.clip(CircleShape).clickable(onClick = onClick) else Modifier
    Box(modifier.size(size).then(click), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = if (spin) rot else 0f }.clip(shape).background(container))
        Icon(icon, desc, Modifier.size(size * 0.44f), tint = tint)
    }
}

@Composable
fun Switcher(opts: List<String>, sel: Int, modifier: Modifier = Modifier, on: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.fillMaxWidth().height(54.dp).clip(CircleShape).background(cs.surfaceContainerHigh).padding(4.dp)) {
        val w = maxWidth / opts.size
        val x by animateDpAsState(w * sel.coerceAtLeast(0), spring(0.8f, 420f), label = "sw")
        if (sel >= 0) Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(CircleShape).background(cs.primary))
        Row(Modifier.fillMaxSize()) {
            opts.forEachIndexed { i, o ->
                val c by animateColorAsState(if (i == sel) cs.onPrimary else cs.onSurface, label = "swc")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { on(i) },
                    contentAlignment = Alignment.Center
                ) { Text(o, color = c, style = MaterialTheme.typography.titleSmall, maxLines = 1) }
            }
        }
    }
}

@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, tonal: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val r by animateDpAsState(if (pressed) 18.dp else 32.dp, spring(0.55f, 600f), label = "r")
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, spring(0.55f, 600f), label = "s")
    val bg = if (tonal) cs.secondaryContainer else cs.primary
    val fg = if (tonal) cs.onSecondaryContainer else cs.onPrimary
    Row(
        modifier.fillMaxWidth().height(64.dp).graphicsLayer { scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(r)).background(bg)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(24.dp), tint = fg)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun fieldColors(): TextFieldColors {
    val c = MaterialTheme.colorScheme.surfaceContainerHigh
    return TextFieldDefaults.colors(
        focusedContainerColor = c, unfocusedContainerColor = c, disabledContainerColor = c,
        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CookieIcon(icon, 128.dp, cs.primaryContainer, cs.onPrimaryContainer)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
