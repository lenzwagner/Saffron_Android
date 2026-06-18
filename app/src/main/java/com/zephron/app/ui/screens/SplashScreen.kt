package com.zephron.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zephron.app.R
import com.zephron.app.ui.AppLogo
import com.zephron.app.ui.theme.LocalAppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onSplashComplete: () -> Unit) {
    val orange = LocalAppColors.current.accent
    val currentOnComplete by rememberUpdatedState(onSplashComplete)

    // ── Animatables ──────────────────────────────────────────────────────────
    val logoScale  = remember { Animatable(0.4f) }
    val logoAlpha  = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleOff   = remember { Animatable(32f) }   // dp offset downward
    val sloganAlpha = remember { Animatable(0f) }
    val sloganOff  = remember { Animatable(24f) }
    val screenAlpha = remember { Animatable(1f) }

    // ── Orb pulse (infinite, background depth) ───────────────────────────────
    val pulse = rememberInfiniteTransition(label = "orb_pulse")
    val orb1 by pulse.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse),
        label = "orb1"
    )
    val orb2 by pulse.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse),
        label = "orb2"
    )
    val orb3 by pulse.animateFloat(
        initialValue = 0.2f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "orb3"
    )

    // ── Entry sequence ───────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        // Logo: spring scale + quick fade
        launch {
            logoAlpha.animateTo(1f, tween(260))
        }
        logoScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )

        // Title: slide up + fade (220ms after logo starts)
        delay(60)
        launch {
            titleAlpha.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
        }
        titleOff.animateTo(0f, tween(400, easing = EaseOutCubic))

        // Slogan: slide up + fade (180ms after title)
        delay(140)
        launch {
            sloganAlpha.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
        }
        sloganOff.animateTo(0f, tween(360, easing = EaseOutCubic))

        // Hold for a moment
        delay(820)

        // Exit: fade whole screen out
        screenAlpha.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
        currentOnComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(screenAlpha.value)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        orange.copy(red = (orange.red * 1.12f).coerceAtMost(1f)),
                        orange,
                        orange.copy(alpha = 0.92f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // ── Floating background orbs (depth layer) ───────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Large orb top-right
            drawCircle(
                color = Color.White.copy(alpha = 0.06f + orb1 * 0.05f),
                radius = w * (0.55f + orb1 * 0.08f),
                center = Offset(w * 0.85f, h * 0.18f)
            )
            // Medium orb bottom-left
            drawCircle(
                color = Color.White.copy(alpha = 0.04f + orb2 * 0.06f),
                radius = w * (0.40f + orb2 * 0.06f),
                center = Offset(w * 0.12f, h * 0.78f)
            )
            // Small accent orb center-left
            drawCircle(
                color = Color.White.copy(alpha = 0.07f + orb3 * 0.05f),
                radius = w * (0.20f + orb3 * 0.04f),
                center = Offset(w * 0.22f, h * 0.42f)
            )
            // Tiny bright dot top-left
            drawCircle(
                color = Color.White.copy(alpha = 0.10f + orb1 * 0.08f),
                radius = w * 0.08f,
                center = Offset(w * 0.08f, h * 0.14f)
            )
        }

        // ── Content ──────────────────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo: spring scale + alpha
            AppLogo(
                modifier = Modifier
                    .size(100.dp)
                    .graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                        alpha = logoAlpha.value
                    }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Title: slide-up + fade
            Text(
                text = stringResource(R.string.app_name),
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .alpha(titleAlpha.value)
                    .offset(y = titleOff.value.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Slogan: slide-up + fade (delayed)
            Text(
                text = stringResource(R.string.app_slogan),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .alpha(sloganAlpha.value)
                    .offset(y = sloganOff.value.dp)
            )
        }
    }
}
