package com.equisteg.mydaycompanion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(primary = Color(0xFF1E5B4F), secondary = Color(0xFF4F6358))
private val Dark = darkColorScheme(primary = Color(0xFF8ED3C1), secondary = Color(0xFFB4CCBF))

/** Placeholder palette until brand assets are approved. */
@Composable
fun MyDayCompanionTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
