package com.fluxio.shared.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Base static color variables
val RedPrimary = Color(0xFFFF0000)
val RedSecondary = Color(0xFFFF3333)
val RedDark = Color(0xFFCC0000)

val NeutralPrimary = Color.White
val NeutralSecondary = Color(0xFFCCCCCC)
val NeutralDark = Color(0xFF444444)

private val DarkPrimaryBg = Color.Black
private val DarkSecondaryBg = Color(0xFF1A1C22)
private val DarkTextPrimary = Color(0xFFFFFFFF)
private val DarkTextSecondary = Color(0xFFD7D7D7)
private val DarkTextTertiary = Color(0xFFA0A0A0)
private val DarkIconInactive = Color(0xFF8A8A8A)
private val DarkIconActive = RedPrimary

private val LightPrimaryBg = Color(0xFFF8F9FA)
private val LightSecondaryBg = Color(0xFFFFFFFF)
private val LightTextPrimary = Color(0xFF111216)
private val LightTextSecondary = Color(0xFF555555)
private val LightTextTertiary = Color(0xFF8A8A8A)
private val LightIconInactive = Color(0xFF8A8A8A)
private val LightIconActive = RedPrimary

// Dynamic theme-aware properties
val PrimaryBg: Color
    @Composable
    get() = MaterialTheme.colorScheme.background

val SecondaryBg: Color
    @Composable
    get() = MaterialTheme.colorScheme.surface

val TextPrimary: Color
    @Composable
    get() = if (MaterialTheme.colorScheme.background == LightPrimaryBg) LightTextPrimary else DarkTextPrimary

val TextSecondary: Color
    @Composable
    get() = if (MaterialTheme.colorScheme.background == LightPrimaryBg) LightTextSecondary else DarkTextSecondary

val TextTertiary: Color
    @Composable
    get() = if (MaterialTheme.colorScheme.background == LightPrimaryBg) LightTextTertiary else DarkTextTertiary

val IconInactive: Color
    @Composable
    get() = if (MaterialTheme.colorScheme.background == LightPrimaryBg) LightIconInactive else DarkIconInactive

val IconActive: Color
    @Composable
    get() = if (MaterialTheme.colorScheme.background == LightPrimaryBg) LightIconActive else DarkIconActive

private val DarkColorScheme = darkColorScheme(
    primary = RedPrimary,
    secondary = NeutralSecondary,
    tertiary = NeutralDark,
    background = DarkPrimaryBg,
    surface = DarkSecondaryBg,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = DarkTextPrimary,
    onSurface = DarkTextSecondary,
    primaryContainer = DarkSecondaryBg,
    onPrimaryContainer = DarkTextPrimary,
    secondaryContainer = DarkSecondaryBg,
    onSecondaryContainer = DarkTextSecondary,
    surfaceVariant = DarkSecondaryBg,
    onSurfaceVariant = DarkTextSecondary
)

private val LightColorScheme = lightColorScheme(
    primary = RedPrimary,
    secondary = NeutralSecondary,
    tertiary = NeutralDark,
    background = LightPrimaryBg,
    surface = LightSecondaryBg,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = LightTextPrimary,
    onSurface = LightTextSecondary,
    primaryContainer = LightSecondaryBg,
    onPrimaryContainer = LightTextPrimary,
    secondaryContainer = LightSecondaryBg,
    onSecondaryContainer = LightTextSecondary,
    surfaceVariant = LightSecondaryBg,
    onSurfaceVariant = LightTextSecondary
)

@Composable
fun FluxioTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
