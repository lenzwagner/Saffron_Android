package com.zephron.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star

/**
 * Material-Expressive style loading indicator: a single shape that continuously
 * morphs between a rounded "cookie" and an 8-point star while rotating.
 */
@Composable
fun ExpressiveLoader(
    modifier: Modifier = Modifier,
    color: Color,
    size: Dp = 48.dp
) {
    val cookie = remember { RoundedPolygon(numVertices = 8, rounding = CornerRounding(0.5f)) }
    val star = remember {
        RoundedPolygon.star(numVerticesPerRadius = 8, innerRadius = 0.62f, rounding = CornerRounding(0.4f))
    }
    val morph = remember { Morph(cookie, star) }

    val transition = rememberInfiniteTransition(label = "loader")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "morph"
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "rotation"
    )

    Canvas(modifier = modifier.size(size).rotate(rotation)) {
        val radius = this.size.minDimension / 2f
        val path = morph.toComposePath(progress, radius)
        translate(this.size.width / 2f, this.size.height / 2f) {
            drawPath(path, color)
        }
    }
}

/**
 * Expressive progress bar: a pill-shaped track with a squircle-headed fill that
 * bounces into place with a spring when progress changes. Replaces LinearProgressIndicator.
 */
@Composable
fun ExpressiveProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = color.copy(alpha = 0.18f),
    barHeight: Dp = 10.dp
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "progress"
    )

    // Leading blob that morphs slightly when progress changes
    val blob = remember { RoundedPolygon(numVertices = 6, rounding = CornerRounding(0.9f)) }
    val pill = remember { RoundedPolygon(numVertices = 4, rounding = CornerRounding(1f)) }
    val morph = remember { Morph(pill, blob) }

    val transition = rememberInfiniteTransition(label = "head")
    val headPulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "head_pulse"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
    ) {
        val r = size.height / 2f
        val trackW = size.width
        val fillW = (trackW * animatedProgress).coerceAtLeast(size.height)

        // Track
        drawRoundRect(
            color = trackColor,
            size = Size(trackW, size.height),
            cornerRadius = CornerRadius(r)
        )

        // Fill bar (clipped pill)
        drawRoundRect(
            color = color.copy(alpha = 0.35f),
            size = Size(fillW, size.height),
            cornerRadius = CornerRadius(r)
        )

        // Leading expressive blob head
        val blobSize = size.height * 1.6f
        val cx = fillW - blobSize / 2f + r * 0.3f
        val cy = size.height / 2f
        val path = Path()
        var first = true
        morph.asCubics(headPulse * 0.4f).forEach { c ->
            val ax = cx + c.anchor0X * blobSize / 2f
            val ay = cy + c.anchor0Y * blobSize / 2f
            if (first) { path.moveTo(ax, ay); first = false }
            path.cubicTo(
                cx + c.control0X * blobSize / 2f, cy + c.control0Y * blobSize / 2f,
                cx + c.control1X * blobSize / 2f, cy + c.control1Y * blobSize / 2f,
                cx + c.anchor1X * blobSize / 2f, cy + c.anchor1Y * blobSize / 2f
            )
        }
        path.close()
        drawPath(path, color)
    }
}

/** Builds a Compose Path for the morph at [progress], scaled around the origin. */
private fun Morph.toComposePath(progress: Float, scale: Float, path: Path = Path()): Path {
    path.rewind()
    var first = true
    asCubics(progress).forEach { cubic ->
        if (first) {
            path.moveTo(cubic.anchor0X * scale, cubic.anchor0Y * scale)
            first = false
        }
        path.cubicTo(
            cubic.control0X * scale, cubic.control0Y * scale,
            cubic.control1X * scale, cubic.control1Y * scale,
            cubic.anchor1X * scale, cubic.anchor1Y * scale
        )
    }
    path.close()
    return path
}
