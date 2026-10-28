package com.equisteg.mydaycompanion.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Light, dark, or whatever the phone uses. Stored as its name in device settings. */
enum class ThemeMode(val label: String) {
    SYSTEM("Match phone"),
    LIGHT("Light"),
    DARK("Dark"),
    ;

    companion object {
        fun of(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

// "Calm dawn": deep teal for doing, warm apricot for fixed events, soft violet for the agent.
private val Light = lightColorScheme(
    primary = Color(0xFF0F6B5C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F2DE),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF5B5BA8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2DFFF),
    onSecondaryContainer = Color(0xFF15134F),
    tertiary = Color(0xFF9A4A1F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBCB),
    onTertiaryContainer = Color(0xFF341000),
    background = Color(0xFFF7FAF8),
    onBackground = Color(0xFF181C1B),
    surface = Color(0xFFF7FAF8),
    onSurface = Color(0xFF181C1B),
    surfaceVariant = Color(0xFFDBE5E1),
    onSurfaceVariant = Color(0xFF3F4946),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F5F2),
    surfaceContainer = Color(0xFFEBF0ED),
    surfaceContainerHigh = Color(0xFFE5EAE7),
    surfaceContainerHighest = Color(0xFFDFE4E1),
    outline = Color(0xFF6F7976),
    outlineVariant = Color(0xFFBFC9C5),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8AD6C2),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF005144),
    onPrimaryContainer = Color(0xFFA6F2DE),
    secondary = Color(0xFFC2C1FF),
    onSecondary = Color(0xFF2A2A77),
    secondaryContainer = Color(0xFF42428F),
    onSecondaryContainer = Color(0xFFE2DFFF),
    tertiary = Color(0xFFFFB691),
    onTertiary = Color(0xFF552000),
    tertiaryContainer = Color(0xFF7A3409),
    onTertiaryContainer = Color(0xFFFFDBCB),
    background = Color(0xFF0F1413),
    onBackground = Color(0xFFDEE4E1),
    surface = Color(0xFF0F1413),
    onSurface = Color(0xFFDEE4E1),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBFC9C5),
    surfaceContainerLowest = Color(0xFF0A0F0E),
    surfaceContainerLow = Color(0xFF171D1B),
    surfaceContainer = Color(0xFF1B211F),
    surfaceContainerHigh = Color(0xFF252B2A),
    surfaceContainerHighest = Color(0xFF303634),
    outline = Color(0xFF899390),
    outlineVariant = Color(0xFF3F4946),
)

private val base = Typography()
private val AppTypography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * The app's look (UPD-010). [dynamicColor] uses the wallpaper's colours on Android 12 and later
 * (Material You); everywhere else, and when it's off, the app's own palette is used.
 */
@Composable
fun MyDayCompanionTheme(mode: ThemeMode = ThemeMode.SYSTEM, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) {
        Dark
    } else {
        Light
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
}
