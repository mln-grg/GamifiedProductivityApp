package com.smallwins.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.smallwins.app.R

data class SwColors(
    val bg: Color,
    val surface: Color,
    val sunk: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val ember: Color,
    val emberHi: Color,
    val you: Color,
    val partner: Color,
    val gold: Color,
)

private val Light = SwColors(
    bg = Color(0xFFEEF0F8), surface = Color(0xFFFFFFFF), sunk = Color(0xFFE3E6F3), ink = Color(0xFF1B1F3B),
    muted = Color(0xFF5E6485), line = Color(0xFFD5D9EC), ember = Color(0xFFEE6417), emberHi = Color(0xFFFFB627),
    you = Color(0xFF0C8C99), partner = Color(0xFFCF3F76), gold = Color(0xFFC98A00),
)

private val Dark = SwColors(
    bg = Color(0xFF12142A), surface = Color(0xFF1C1F3D), sunk = Color(0xFF171A34), ink = Color(0xFFF1F2FA),
    muted = Color(0xFFA3A8C7), line = Color(0xFF2F3460), ember = Color(0xFFFF7A2F), emberHi = Color(0xFFFFC14D),
    you = Color(0xFF3CC6D1), partner = Color(0xFFF0709F), gold = Color(0xFFFFC14D),
)

/** Ember's face stays dark ink in both themes so it reads on the flame. */
val EmberFace = Color(0xFF1B1F3B)

private val LocalSwColors = staticCompositionLocalOf { Light }

object Sw {
    val colors: SwColors @Composable get() = LocalSwColors.current
}

private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val DisplayFont = FontFamily(variable(R.font.bricolage_grotesque, 700), variable(R.font.bricolage_grotesque, 800))
val BodyFont = FontFamily(
    variable(R.font.figtree, 400), variable(R.font.figtree, 500), variable(R.font.figtree, 600), variable(R.font.figtree, 700),
)

private fun typography(): Typography {
    val d = Typography()
    fun TextStyle.body() = copy(fontFamily = BodyFont)
    fun TextStyle.display(weight: Int = 700) = copy(fontFamily = DisplayFont, fontWeight = FontWeight(weight))
    return Typography(
        displayLarge = d.displayLarge.display(800), displayMedium = d.displayMedium.display(800), displaySmall = d.displaySmall.display(800),
        headlineLarge = d.headlineLarge.display(800), headlineMedium = d.headlineMedium.display(800), headlineSmall = d.headlineSmall.display(),
        titleLarge = d.titleLarge.display(), titleMedium = d.titleMedium.display().copy(fontSize = 17.sp), titleSmall = d.titleSmall.body(),
        bodyLarge = d.bodyLarge.body(), bodyMedium = d.bodyMedium.body(), bodySmall = d.bodySmall.body(),
        labelLarge = d.labelLarge.body().copy(fontWeight = FontWeight(700)), labelMedium = d.labelMedium.body(),
        labelSmall = d.labelSmall.body().copy(fontWeight = FontWeight(700), letterSpacing = 1.sp),
    )
}

@Composable
fun SmallWinsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val c = if (dark) Dark else Light
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.ember, onPrimary = Color.White, secondary = c.you, onSecondary = c.bg,
            secondaryContainer = c.line, onSecondaryContainer = c.ink,
            background = c.bg, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
            surfaceVariant = c.sunk, onSurfaceVariant = c.muted, outline = c.line, outlineVariant = c.line,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.sunk,
        )
    } else {
        lightColorScheme(
            primary = c.ember, onPrimary = Color.White, secondary = c.you, onSecondary = Color.White,
            secondaryContainer = c.line, onSecondaryContainer = c.ink,
            background = c.bg, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
            surfaceVariant = c.sunk, onSurfaceVariant = c.muted, outline = c.line, outlineVariant = c.line,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.sunk,
        )
    }
    CompositionLocalProvider(LocalSwColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography(), content = content)
    }
}
