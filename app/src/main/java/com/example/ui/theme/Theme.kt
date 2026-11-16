package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = MintElectric,
    onPrimary = MintOnPrimary,
    primaryContainer = MintContainer,
    onPrimaryContainer = MintOnContainer,
    secondary = CyanSignal,
    onSecondary = Color(0xFF002C42),
    secondaryContainer = CyanContainer,
    onSecondaryContainer = CyanOnContainer,
    tertiary = XiaomiCoral,
    onTertiary = Color(0xFF3E1500),
    tertiaryContainer = XiaomiCoralContainer,
    onTertiaryContainer = XiaomiCoralOnContainer,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = ObsidianOutline,
    error = AlertRed,
    errorContainer = AlertRedContainer,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = MintDeep,
    onPrimary = Color.White,
    primaryContainer = MintDeepContainer,
    onPrimaryContainer = MintDeepOnContainer,
    secondary = CyanDeep,
    onSecondary = Color.White,
    secondaryContainer = CyanDeepContainer,
    onSecondaryContainer = CyanDeepOnContainer,
    tertiary = CoralDeep,
    onTertiary = Color.White,
    tertiaryContainer = CoralDeepContainer,
    onTertiaryContainer = CoralDeepOnContainer,
    background = DaylightBg,
    onBackground = TextPrimaryLight,
    surface = DaylightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = DaylightOutline,
    error = Color(0xFFDC2626),
    errorContainer = Color(0xFFFEE2E2),
  )

@Composable
fun BudsPulseTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = AppTypography,
    content = content,
  )
}
