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
    primary = GeometricPrimaryDarkTheme,
    secondary = GeometricPrimaryContainerDarkTheme,
    tertiary = GeometricHighlightRedDarkTheme,
    background = GeometricBackgroundDarkTheme,
    surface = GeometricSurfaceDarkTheme,
    onPrimary = Color.Black,
    onSecondary = GeometricTextDarkTheme,
    onTertiary = Color.Black,
    onBackground = GeometricTextDarkTheme,
    onSurface = GeometricTextDarkTheme,
    surfaceVariant = GeometricSurfaceSecondaryDarkTheme,
    onSurfaceVariant = GeometricTextSecondaryDarkTheme,
    outline = GeometricBorderDarkTheme
  )

private val LightColorScheme =
  lightColorScheme(
    primary = GeometricPrimary,
    secondary = GeometricPrimaryContainer,
    tertiary = GeometricHighlightRed,
    background = GeometricBackground,
    surface = GeometricSurface,
    onPrimary = Color.White,
    onSecondary = GeometricText,
    onTertiary = Color.White,
    onBackground = GeometricText,
    onSurface = GeometricText,
    surfaceVariant = GeometricSurfaceSecondary,
    onSurfaceVariant = GeometricTextSecondary,
    outline = GeometricBorder
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Force Geometric Balance theme colors by default rather than dynamic Android colors
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
