package com.zephron.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zephron.app.data.Recipe
import com.zephron.app.data.RecipeStep
import com.zephron.app.ui.theme.LocalAppColors
import kotlinx.coroutines.delay

// ── Phase model ───────────────────────────────────────────────────────────────

private sealed class CookPhase {
    object Intro : CookPhase()
    data class Step(val index: Int) : CookPhase()
    object Done : CookPhase()
}

// ── Entry point ───────────────────────────────────────────────────────────────

@Composable
fun CookModeScreen(
    recipe: Recipe,
    steps: List<RecipeStep>,
    onDismiss: () -> Unit
) {
    // If no structured steps exist, fall back to notes as a single step
    val effectiveSteps: List<RecipeStep> = remember(steps, recipe.notes) {
        when {
            steps.isNotEmpty() -> steps
            recipe.notes.isNotBlank() -> listOf(RecipeStep(text = recipe.notes))
            else -> emptyList()
        }
    }

    var phase by remember { mutableStateOf<CookPhase>(CookPhase.Intro) }
    val accent = LocalAppColors.current.accent

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- FIXED HEADER (Outside AnimatedContent) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.2f), Color.Transparent)
                    )
                )
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Schließen", tint = accent)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "REZEPT-MODUS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = accent.copy(alpha = 0.8f)
                )
                Spacer(Modifier.weight(1f))
                
                // Show counter only in Step phase
                val counterText = when (val p = phase) {
                    is CookPhase.Step -> "${p.index + 1} / ${effectiveSteps.size}"
                    else -> ""
                }
                Text(
                    text = counterText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.End
                )
            }
        }

        AnimatedContent(
            targetState = phase,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val goForward = when {
                    targetState is CookPhase.Done -> true
                    initialState is CookPhase.Done -> false
                    initialState is CookPhase.Intro -> true
                    targetState is CookPhase.Intro -> false
                    else -> {
                        val from = (initialState as? CookPhase.Step)?.index ?: 0
                        val to = (targetState as? CookPhase.Step)?.index ?: 0
                        to > from
                    }
                }
                if (goForward) {
                    (slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { it } +
                            fadeIn(tween(180, easing = LinearOutSlowInEasing))) togetherWith
                            (slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { -it } +
                                    fadeOut(tween(120)))
                } else {
                    (slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { -it } +
                            fadeIn(tween(180, easing = LinearOutSlowInEasing))) togetherWith
                            (slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { it } +
                                    fadeOut(tween(120)))
                }
            },
            label = "cook_phase"
        ) { currentPhase ->
            when (currentPhase) {
                is CookPhase.Intro -> CookModeIntro(
                    recipe = recipe,
                    totalSteps = effectiveSteps.size,
                    onStart = { phase = CookPhase.Step(0) }
                )
                is CookPhase.Step -> CookModeStepView(
                    step = effectiveSteps[currentPhase.index],
                    stepIndex = currentPhase.index,
                    totalSteps = effectiveSteps.size,
                    onPrev = {
                        phase = if (currentPhase.index > 0)
                            CookPhase.Step(currentPhase.index - 1)
                        else CookPhase.Intro
                    },
                    onNext = {
                        phase = if (currentPhase.index < effectiveSteps.size - 1)
                            CookPhase.Step(currentPhase.index + 1)
                        else CookPhase.Done
                    }
                )
                is CookPhase.Done -> CookModeDone(
                    onDismiss = onDismiss,
                    onRestart = { phase = CookPhase.Step(0) }
                )
            }
        }
    }
}

// ── Intro ─────────────────────────────────────────────────────────────────────

@Composable
private fun CookModeIntro(
    recipe: Recipe,
    totalSteps: Int,
    onStart: () -> Unit
) {
    val accent = LocalAppColors.current.accent

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Space for fixed header
        Spacer(Modifier.height(100.dp).statusBarsPadding())

        Spacer(Modifier.weight(1f))

        // Icon
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = accent.copy(alpha = 0.12f),
            modifier = Modifier.size(100.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Restaurant,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(52.dp)
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = recipe.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(Modifier.height(16.dp))

        // Meta chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (recipe.cookingTimeMinutes > 0) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = accent.copy(alpha = 0.10f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.Timer, null, tint = accent, modifier = Modifier.size(16.dp))
                        Text(
                            text = "${recipe.cookingTimeMinutes} Min",
                            color = accent,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "$totalSteps Schritte",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Pulsing play button
        Surface(
            onClick = onStart,
            shape = CircleShape,
            color = accent,
            modifier = Modifier
                .size(96.dp)
                .scale(pulseScale),
            shadowElevation = 16.dp,
            tonalElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Starten",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Tippe zum Starten",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
        )

        Spacer(Modifier.weight(1f))
        Spacer(modifier = Modifier.height(20.dp))
    }
}

// ── Step view ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CookModeStepView(
    step: RecipeStep,
    stepIndex: Int,
    totalSteps: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val accent = LocalAppColors.current.accent
    val hasTimer = step.timeMinutes > 0

    var timerSeconds by remember(stepIndex) { mutableIntStateOf(step.timeMinutes * 60) }
    var timerRunning by remember(stepIndex) { mutableStateOf(false) }

    LaunchedEffect(timerRunning, timerSeconds) {
        if (timerRunning && timerSeconds > 0) {
            delay(1000L)
            timerSeconds--
            if (timerSeconds == 0) timerRunning = false
        }
    }

    fun formatTime(s: Int) = "%d:%02d".format(s / 60, s % 60)

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Space for fixed header
        Spacer(Modifier.height(100.dp).statusBarsPadding())

        Spacer(Modifier.weight(0.5f))

        // Circular Progress Indicator around Step number
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { (stepIndex + 1).toFloat() / totalSteps },
                modifier = Modifier.size(82.dp),
                color = accent,
                strokeWidth = 4.dp,
                trackColor = accent.copy(alpha = 0.1f),
            )
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .background(accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${stepIndex + 1}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = 24.sp
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // Step text
        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 28.dp)
                .verticalScroll(scrollState),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step.text,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Step ingredients chips
        if (step.stepIngredients.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                step.stepIngredients.forEach { ing ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = accent.copy(alpha = 0.10f)
                    ) {
                        Text(
                            text = ing,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = accent
                        )
                    }
                }
            }
        }

        // Timer card
        if (hasTimer) {
            Spacer(Modifier.height(20.dp))
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (timerRunning) accent.copy(alpha = 0.10f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = null,
                        tint = if (timerRunning) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = formatTime(timerSeconds),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (timerRunning) accent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    if (timerSeconds < step.timeMinutes * 60) {
                        IconButton(
                            onClick = {
                                timerSeconds = step.timeMinutes * 60
                                timerRunning = false
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Zurücksetzen",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Surface(
                        onClick = { timerRunning = !timerRunning },
                        shape = CircleShape,
                        color = accent,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (timerRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (timerRunning) "Pause" else "Start",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Navigation buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedButton(
                onClick = onPrev,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.5.dp, accent.copy(alpha = 0.35f))
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = accent
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (stepIndex == 0) "Intro" else "Zurück",
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
            }
            Button(
                onClick = onNext,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                Text(
                    text = if (stepIndex == totalSteps - 1) "Fertig 🎉" else "Weiter",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                if (stepIndex < totalSteps - 1) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ── Done ──────────────────────────────────────────────────────────────────────

@Composable
private fun CookModeDone(
    onDismiss: () -> Unit,
    onRestart: () -> Unit
) {
    val accent = LocalAppColors.current.accent

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Space for fixed header
        Spacer(Modifier.height(100.dp).statusBarsPadding())

        Text(
            text = "Guten Appetit!",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Du hast alle Schritte abgeschlossen.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(52.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(56.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent)
        ) {
            Text(
                text = "Zurück zum Rezept",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }
        Spacer(Modifier.height(14.dp))
        TextButton(onClick = onRestart) {
            Text(
                text = "Nochmal von vorne",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
            )
        }
    }
}
