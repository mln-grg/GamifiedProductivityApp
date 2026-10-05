package com.smallwins.app.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
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

/** One dark "system window" look: cold blue panels with Ember as the only warm thing on screen. */
data class SwColors(
    val bg: Color = Color(0xFF070B14),
    val panel: Color = Color(0xFF0D1524),
    val panelHi: Color = Color(0xFF132038),
    val line: Color = Color(0xFF1B2A47),
    val edge: Color = Color(0xFF2E7BFF),
    val glow: Color = Color(0xFF7CC4FF),
    val ink: Color = Color(0xFFE6F0FF),
    val muted: Color = Color(0xFF8A9BBD),
    val ember: Color = Color(0xFFFF7A2F),
    val gold: Color = Color(0xFFFFC14D),
    val ok: Color = Color(0xFF4BE3A4),
    val partner: Color = Color(0xFFF0709F),
)

val EmberFace = Color(0xFF10182B)

private val LocalSwColors = staticCompositionLocalOf { SwColors() }

object Sw {
    val colors: SwColors @Composable get() = LocalSwColors.current
}

val DisplayFont = FontFamily(
    Font(R.font.rajdhani_medium, FontWeight.Medium),
    Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
    Font(R.font.rajdhani_bold, FontWeight.Bold),
)

private fun variable(weight: Int) =
    Font(R.font.figtree, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val BodyFont = FontFamily(variable(400), variable(500), variable(600), variable(700))

private fun typography(): Typography {
    val d = Typography()
    fun TextStyle.body() = copy(fontFamily = BodyFont)
    fun TextStyle.display(weight: FontWeight = FontWeight.Bold) = copy(fontFamily = DisplayFont, fontWeight = weight)
    return Typography(
        displayLarge = d.displayLarge.display(), displayMedium = d.displayMedium.display(), displaySmall = d.displaySmall.display(),
        headlineLarge = d.headlineLarge.display(), headlineMedium = d.headlineMedium.display(), headlineSmall = d.headlineSmall.display(),
        titleLarge = d.titleLarge.display(), titleMedium = d.titleMedium.display(FontWeight.SemiBold).copy(fontSize = 20.sp),
        titleSmall = d.titleSmall.body(),
        bodyLarge = d.bodyLarge.body(), bodyMedium = d.bodyMedium.body(), bodySmall = d.bodySmall.body(),
        labelLarge = d.labelLarge.display().copy(fontSize = 16.sp, letterSpacing = 1.5.sp), labelMedium = d.labelMedium.body(),
        labelSmall = d.labelSmall.display().copy(fontSize = 13.sp, letterSpacing = 2.sp),
    )
}

@Composable
fun SmallWinsTheme(content: @Composable () -> Unit) {
    val c = SwColors()
    val scheme = darkColorScheme(
        primary = c.edge, onPrimary = Color.White, secondary = c.glow, onSecondary = c.bg,
        secondaryContainer = c.panelHi, onSecondaryContainer = c.ink,
        background = c.bg, onBackground = c.ink, surface = c.panel, onSurface = c.ink,
        surfaceVariant = c.panelHi, onSurfaceVariant = c.muted, outline = c.line, outlineVariant = c.line,
        surfaceContainer = c.panel, surfaceContainerHigh = c.panelHi, surfaceContainerHighest = c.panelHi,
        error = c.partner,
    )
    // No Surface wraps the screens, so the default text colour has to be set here.
    CompositionLocalProvider(LocalSwColors provides c, LocalContentColor provides c.ink) {
        MaterialTheme(colorScheme = scheme, typography = typography(), content = content)
    }
}
