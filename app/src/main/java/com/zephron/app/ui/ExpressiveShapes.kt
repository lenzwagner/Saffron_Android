package com.zephron.app.ui

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * A custom shape that implements the "expressive" look of Material 3.
 * It uses asymmetric rounding or specific cut corners to create a unique brand identity.
 */
class BentoShape(
    val topStart: Dp = 24.dp,
    val topEnd: Dp = 24.dp,
    val bottomEnd: Dp = 24.dp,
    val bottomStart: Dp = 24.dp
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            val ts = with(density) { topStart.toPx() }
            val te = with(density) { topEnd.toPx() }
            val be = with(density) { bottomEnd.toPx() }
            val bs = with(density) { bottomStart.toPx() }
            
            val w = size.width
            val h = size.height

            moveTo(ts, 0f)
            lineTo(w - te, 0f)
            quadraticTo(w, 0f, w, te)
            lineTo(w, h - be)
            quadraticTo(w, h, w - be, h)
            lineTo(bs, h)
            quadraticTo(0f, h, 0f, h - bs)
            lineTo(0f, ts)
            quadraticTo(0f, 0f, ts, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * A "Scalloped" or "Ticket" shape often seen in Material 3 Expressive designs.
 */
class TicketShape(val cornerRadius: Dp = 16.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { cornerRadius.toPx() }
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(r, 0f)
            lineTo(w - r, 0f)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(w - 2 * r, -r, w, r),
                startAngleDegrees = 180f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )
            lineTo(w, h - r)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(w - r, h - r, w + r, h + r),
                startAngleDegrees = 270f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )
            lineTo(r, h)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(-r, h - r, r, h + r),
                startAngleDegrees = 0f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )
            lineTo(0f, r)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(-r, -r, r, r),
                startAngleDegrees = 90f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )
            close()
        }
        return Outline.Generic(path)
    }
}

object ZephronShapes {
    val HeroCard = BentoShape(topStart = 48.dp, topEnd = 16.dp, bottomEnd = 32.dp, bottomStart = 16.dp)
    val ActionCard = BentoShape(topStart = 16.dp, topEnd = 32.dp, bottomEnd = 16.dp, bottomStart = 32.dp)
    val RecipeCard = RoundedCornerShape(28.dp)
    val Tag = RoundedCornerShape(12.dp)
}
