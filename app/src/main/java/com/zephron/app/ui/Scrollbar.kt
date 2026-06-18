package com.zephron.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import kotlin.math.max

// ── Shared morph shapes for the expressive thumb ─────────────────────────────
private val pillShape by lazy { RoundedPolygon(numVertices = 4, rounding = CornerRounding(1f)) }
private val blobShape by lazy {
    RoundedPolygon.star(numVerticesPerRadius = 4, innerRadius = 0.85f, rounding = CornerRounding(0.6f))
}
private val thumbMorph by lazy { Morph(pillShape, blobShape) }

/**
 * Draws an expressive Morphing scrollbar thumb on a [ScrollState] column.
 * The thumb morphs from a pill → organic blob as scroll speed increases.
 */
fun Modifier.verticalScrollbar(
    scrollState: ScrollState,
    width: Dp = 5.dp,
    color: Color = Color(0x80808080)
): Modifier = drawWithContent {
    drawContent()
    val maxScroll = scrollState.maxValue
    if (maxScroll > 0) {
        val viewH = size.height
        val total = viewH + maxScroll
        val thumbH = max((viewH / total) * viewH, 32.dp.toPx())
        val thumbOffset = (scrollState.value.toFloat() / maxScroll) * (viewH - thumbH)
        drawExpressiveThumb(
            color = color,
            left = size.width - width.toPx() - 3.dp.toPx(),
            top = thumbOffset + 4.dp.toPx(),
            w = width.toPx(),
            h = thumbH - 8.dp.toPx(),
            morphProgress = 0f   // static pill for ScrollState (no velocity info)
        )
    }
}

/**
 * Expressive scrollbar for [LazyGridState] (used in the recipe grid).
 * The thumb morphs from pill → blob based on whether the list is actively scrolling.
 */
fun Modifier.lazyGridScrollbar(
    state: LazyGridState,
    width: Dp = 5.dp,
    color: Color = Color(0x80808080)
): Modifier = composed {
    val isScrolling = state.isScrollInProgress
    val morphProgress by animateFloatAsState(
        targetValue = if (isScrolling) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "scrollbar_morph"
    )
    drawWithContent {
        drawContent()
        val totalItems = state.layoutInfo.totalItemsCount.takeIf { it > 0 } ?: return@drawWithContent
        val visibleItems = state.layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return@drawWithContent
        val visibleCount = visibleItems.size
        if (visibleCount >= totalItems) return@drawWithContent   // all items visible → no bar

        val viewH = size.height
        val thumbH = max((visibleCount.toFloat() / totalItems) * viewH, 32.dp.toPx())
        val firstIndex = state.firstVisibleItemIndex
        val scrollable = totalItems - visibleCount
        val thumbOffset = (firstIndex.toFloat() / scrollable.coerceAtLeast(1)) * (viewH - thumbH)

        drawExpressiveThumb(
            color = color,
            left = size.width - width.toPx() - 3.dp.toPx(),
            top = thumbOffset.coerceIn(0f, viewH - thumbH) + 4.dp.toPx(),
            w = width.toPx(),
            h = thumbH - 8.dp.toPx(),
            morphProgress = morphProgress
        )
    }
}

/**
 * Expressive scrollbar for [LazyListState].
 */
fun Modifier.lazyListScrollbar(
    state: LazyListState,
    width: Dp = 5.dp,
    color: Color = Color(0x80808080)
): Modifier = composed {
    val isScrolling = state.isScrollInProgress
    val morphProgress by animateFloatAsState(
        targetValue = if (isScrolling) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "scrollbar_morph"
    )
    drawWithContent {
        drawContent()
        val totalItems = state.layoutInfo.totalItemsCount.takeIf { it > 0 } ?: return@drawWithContent
        val visibleItems = state.layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return@drawWithContent
        val visibleCount = visibleItems.size
        if (visibleCount >= totalItems) return@drawWithContent

        val viewH = size.height
        val thumbH = max((visibleCount.toFloat() / totalItems) * viewH, 32.dp.toPx())
        val firstIndex = state.firstVisibleItemIndex
        val scrollable = totalItems - visibleCount
        val thumbOffset = (firstIndex.toFloat() / scrollable.coerceAtLeast(1)) * (viewH - thumbH)

        drawExpressiveThumb(
            color = color,
            left = size.width - width.toPx() - 3.dp.toPx(),
            top = thumbOffset.coerceIn(0f, viewH - thumbH) + 4.dp.toPx(),
            w = width.toPx(),
            h = thumbH - 8.dp.toPx(),
            morphProgress = morphProgress
        )
    }
}

// ── Draws a morphing pill→blob thumb ─────────────────────────────────────────
private fun DrawScope.drawExpressiveThumb(
    color: Color,
    left: Float,
    top: Float,
    w: Float,
    h: Float,
    morphProgress: Float
) {
    if (morphProgress < 0.01f) {
        // Fast path: just a rounded rect (pill)
        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(w, h),
            cornerRadius = CornerRadius(w / 2)
        )
    } else {
        // Morph the thumb shape
        val cx = left + w / 2f
        val cy = top + h / 2f
        val rx = w / 2f
        val ry = h / 2f
        val path = Path()
        var first = true
        thumbMorph.asCubics(morphProgress).forEach { c ->
            // Cubics are normalized [-1,1] in both axes from Morph; scale to our rect
            val ax = cx + c.anchor0X * rx
            val ay = cy + c.anchor0Y * ry
            val c0x = cx + c.control0X * rx
            val c0y = cy + c.control0Y * ry
            val c1x = cx + c.control1X * rx
            val c1y = cy + c.control1Y * ry
            val ex = cx + c.anchor1X * rx
            val ey = cy + c.anchor1Y * ry
            if (first) { path.moveTo(ax, ay); first = false }
            path.cubicTo(c0x, c0y, c1x, c1y, ex, ey)
        }
        path.close()
        drawPath(path, color)
    }
}
