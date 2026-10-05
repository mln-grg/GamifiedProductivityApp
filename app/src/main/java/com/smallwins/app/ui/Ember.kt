package com.smallwins.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.random.Random

/** How the shared flame feels about today: at risk, won, or everything done. */
enum class Mood { LOW, OK, BLAZE }

private const val OUTER = "M50 6C60 28 86 40 86 64a36 34 0 0 1-72 0c0-18 13-26 19-40 5 9 11 8 17-18Z"
private const val INNER = "M50 44c6 12 20 16 20 30a20 20 0 0 1-40 0c0-10 8-14 11-22 3 5 6 4 9-8Z"

@Composable
fun Ember(mood: Mood, modifier: Modifier = Modifier) {
    val colors = Sw.colors
    val outer = remember { PathParser().parsePathString(OUTER).toPath() }
    val inner = remember { PathParser().parsePathString(INNER).toPath() }
    val grow by animateFloatAsState(
        targetValue = when (mood) { Mood.LOW -> 0.74f; Mood.OK -> 0.9f; Mood.BLAZE -> 1f },
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 220f),
        label = "emberSize",
    )
    val flicker by rememberInfiniteTransition(label = "ember").animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "flicker",
    )
    val dull = mood == Mood.LOW
    val outerColor = if (dull) lerp(colors.ember, colors.muted, 0.4f) else colors.ember
    val innerColor = if (dull) lerp(colors.gold, colors.muted, 0.3f) else colors.gold

    Canvas(modifier) {
        val unit = size.minDimension / 100f
        val base = Offset(size.width / 2f, size.height * 0.95f)
        val sway = if (mood == Mood.BLAZE) flicker else 0f
        scale(grow * (1f + 0.03f * sway), pivot = base) {
            rotate(1.5f * sway, pivot = base) {
                scale(unit, pivot = Offset.Zero) {
                    drawPath(outer, outerColor)
                    drawPath(inner, innerColor)
                    drawCircle(EmberFace, 3.4f, Offset(42f, 70f))
                    drawCircle(EmberFace, 3.4f, Offset(58f, 70f))
                    val mouth = Path().apply {
                        when (mood) {
                            Mood.LOW -> { moveTo(44f, 83f); quadraticTo(50f, 78f, 56f, 83f) }
                            Mood.OK -> { moveTo(45f, 81f); lineTo(55f, 81f) }
                            Mood.BLAZE -> { moveTo(43f, 79f); quadraticTo(50f, 87f, 57f, 79f) }
                        }
                    }
                    drawPath(mouth, EmberFace, style = Stroke(width = 3f, cap = StrokeCap.Round))
                }
            }
        }
    }
}

private class Spark(val x: Float, val delay: Float, val speed: Float, val size: Float, val drift: Float, val color: Int)

/** A short burst of falling sparks for a won day. Replays whenever [key] changes. */
@Composable
fun Sparks(key: Any, modifier: Modifier = Modifier) {
    val colors = Sw.colors
    val palette = listOf(colors.ember, colors.gold, colors.glow, colors.partner)
    val sparks = remember(key) {
        List(48) { Spark(Random.nextFloat(), Random.nextFloat() * 0.3f, 0.7f + Random.nextFloat() * 0.6f, 6f + Random.nextFloat() * 8f, Random.nextFloat() - 0.5f, it % palette.size) }
    }
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(2200, easing = LinearEasing)) }

    Canvas(modifier) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        for (s in sparks) {
            val local = ((t - s.delay) / (1f - s.delay)).coerceIn(0f, 1f)
            if (local <= 0f) continue
            val x = (s.x + s.drift * 0.15f * local) * size.width
            val y = -20f + local * s.speed * size.height * 1.1f
            val px = s.size * density
            drawRect(palette[s.color].copy(alpha = 1f - local * local), Offset(x, y), Size(px * 0.6f, px))
        }
    }
}
