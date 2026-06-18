package com.zephron.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import coil.compose.AsyncImage
import com.zephron.app.R
import com.zephron.app.data.Recipe
import com.zephron.app.ui.TAG_ICONS
import com.zephron.app.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun RecipeTinderScreen(
    recipes: List<Recipe>,
    currentUserId: String = "",
    friendNames: Map<String, String> = emptyMap(),
    friendNicknames: Map<String, String> = emptyMap(),
    newMatchFlow: kotlinx.coroutines.flow.SharedFlow<Recipe>? = null,
    eligibleEmpty: Boolean = false,
    onRecipeClick: (Recipe) -> Unit,
    onSwipe: (Recipe, Boolean) -> Unit = { _, _ -> },
    onUndo: (Recipe) -> Unit = {},
    onReset: () -> Unit = {}
) {
    val accent   = LocalAppColors.current.accent
    val secondary = LocalAppColors.current.secondary
    val haptic = LocalHapticFeedback.current

    val currentRecipe = recipes.getOrNull(0)
    val nextRecipe    = recipes.getOrNull(1)

    var undoStack by remember { mutableStateOf<List<Recipe>>(emptyList()) }
    var showMatchOverlay by remember { mutableStateOf<Recipe?>(null) }

    LaunchedEffect(newMatchFlow) {
        newMatchFlow?.collect { recipe -> showMatchOverlay = recipe }
    }

    var swipeDirection by remember { mutableStateOf<Int?>(null) }

    // ── Ambient background orbs ───────────────────────────────────────────────
    val pulse = rememberInfiniteTransition(label = "bg")
    val orb1 by pulse.animateFloat(0f, 1f,
        infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse), label = "o1")
    val orb2 by pulse.animateFloat(0.4f, 1f,
        infiniteRepeatable(tween(5500, easing = LinearEasing), RepeatMode.Reverse), label = "o2")

    Box(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surface)
    ) {
        // Subtle ambient orbs derived from accent colour
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(accent.copy(alpha = 0.04f + orb1 * 0.03f),
                radius = size.width * (0.6f + orb1 * 0.1f),
                center = Offset(size.width * 0.85f, size.height * 0.1f))
            drawCircle(secondary.copy(alpha = 0.03f + orb2 * 0.03f),
                radius = size.width * (0.5f + orb2 * 0.1f),
                center = Offset(size.width * 0.1f, size.height * 0.75f))
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (recipes.isEmpty()) {
                if (eligibleEmpty) EmptyTinderState() else AllSwipedState(onReset = onReset)
            } else if (currentRecipe != null) {

                // ── Card stack ────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Card 3 — deep background
                    TinderCard(
                        recipe = recipes.getOrNull(2) ?: recipes.first(),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp)
                            .graphicsLayer {
                                scaleX = 0.87f; scaleY = 0.87f
                                translationY = 48f
                                alpha = if (recipes.size > 2) 0.45f else 0f
                            },
                        shape = cardShape(2)
                    )
                    // Card 2 — next card
                    if (nextRecipe != null) {
                        TinderCard(
                            recipe = nextRecipe,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp)
                                .graphicsLayer {
                                    scaleX = 0.93f; scaleY = 0.93f
                                    translationY = 24f
                                },
                            shape = cardShape(1),
                            ownerName = if (nextRecipe.ownerId != currentUserId)
                                friendNicknames[nextRecipe.ownerId] ?: friendNames[nextRecipe.ownerId]
                            else null
                        )
                    }
                    // Card 1 — active, draggable
                    key(currentRecipe.id) {
                        SwipeableCard(
                            recipe = currentRecipe,
                            ownerName = if (currentRecipe.ownerId != currentUserId)
                                friendNicknames[currentRecipe.ownerId] ?: friendNames[currentRecipe.ownerId]
                            else null,
                            forceSwipeDirection = swipeDirection,
                            onSwiped = { dir ->
                                swipeDirection = null
                                undoStack = undoStack + currentRecipe
                                onSwipe(currentRecipe, dir > 0)
                            },
                            onClick = { onRecipeClick(currentRecipe) }
                        )
                    }

                }

                // ── Action buttons ────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 100.dp, start = 28.dp, end = 28.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Nope — morphing expressive FAB
                    MorphFAB(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            swipeDirection = -1
                        },
                        icon = Icons.Default.Close,
                        color = secondary,
                        size = 68.dp,
                        label = "Nope"
                    )

                    // Undo — smaller tonal button
                    UndoButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            undoStack.lastOrNull()?.let { last ->
                                undoStack = undoStack.dropLast(1)
                                onUndo(last)
                            }
                        },
                        enabled = undoStack.isNotEmpty(),
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            undoStack = emptyList(); onReset()
                        }
                    )

                    // Like — morphing expressive FAB
                    MorphFAB(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            swipeDirection = 1
                        },
                        icon = Icons.Default.Favorite,
                        color = accent,
                        size = 68.dp,
                        label = "Like"
                    )
                }
            }
        }
    }

    showMatchOverlay?.let { matched ->
        MatchOverlay(recipe = matched, onClose = { showMatchOverlay = null })
    }
}

// Asymmetric shape that shifts subtly per stack position
private fun cardShape(stackDepth: Int) = when (stackDepth) {
    0 -> RoundedCornerShape(topStart = 36.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 36.dp)
    1 -> RoundedCornerShape(topStart = 28.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 28.dp)
    else -> RoundedCornerShape(24.dp)
}

// ── Morphing FAB ──────────────────────────────────────────────────────────────

@Composable
private fun MorphFAB(
    onClick: () -> Unit,
    icon: ImageVector,
    color: Color,
    size: androidx.compose.ui.unit.Dp,
    label: String
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val morphTarget by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "morph"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "scale"
    )

    val squircle = remember { RoundedPolygon(numVertices = 4, rounding = CornerRounding(1f)) }
    val blob = remember {
        RoundedPolygon.star(numVerticesPerRadius = 4, innerRadius = 0.88f, rounding = CornerRounding(0.7f))
    }
    val morph = remember { Morph(squircle, blob) }

    val sizePx = with(LocalDensity.current) { size.toPx() }

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path()
            var first = true
            morph.asCubics(morphTarget).forEach { c ->
                val ax = center.x + c.anchor0X * sizePx / 2f
                val ay = center.y + c.anchor0Y * sizePx / 2f
                if (first) { path.moveTo(ax, ay); first = false }
                path.cubicTo(
                    center.x + c.control0X * sizePx / 2f, center.y + c.control0Y * sizePx / 2f,
                    center.x + c.control1X * sizePx / 2f, center.y + c.control1Y * sizePx / 2f,
                    center.x + c.anchor1X * sizePx / 2f, center.y + c.anchor1Y * sizePx / 2f
                )
            }
            path.close()
            drawPath(path, color)
            // Subtle inner glow
            drawPath(path, Color.White.copy(alpha = 0.12f))
        }
        IconButton(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

// ── Undo button ───────────────────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun UndoButton(onClick: () -> Unit, enabled: Boolean, onLongClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label = "undoScale"
    )
    Surface(
        modifier = Modifier
            .size(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.38f }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.AutoMirrored.Filled.Undo,
                contentDescription = stringResource(R.string.crave_undo),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ── Swipeable card ────────────────────────────────────────────────────────────

@Composable
private fun SwipeableCard(
    recipe: Recipe,
    ownerName: String? = null,
    forceSwipeDirection: Int?,
    onSwiped: (Int) -> Unit,
    onClick: () -> Unit
) {
    val accent   = LocalAppColors.current.accent
    val secondary = LocalAppColors.current.secondary
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val swipeThreshold = with(density) { screenWidth.toPx() * 0.30f }
    val offscreenX    = with(density) { screenWidth.toPx() * 1.5f }

    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    // Track whether we already fired the "threshold crossed" tick
    var hapticFiredForCurrentDrag by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(forceSwipeDirection) {
        if (forceSwipeDirection != null) {
            offsetX = if (forceSwipeDirection > 0) offscreenX else -offscreenX
            kotlinx.coroutines.delay(340)
            onSwiped(forceSwipeDirection)
        }
    }

    val animOffsetX by animateFloatAsState(offsetX,
        tween(if (forceSwipeDirection != null) 380 else 280, easing = FastOutSlowInEasing), label = "ox")
    val animOffsetY by animateFloatAsState(offsetY, tween(280), label = "oy")
    val rotation = (animOffsetX / 28f).coerceIn(-22f, 22f)
    val swipeProgress = (animOffsetX / swipeThreshold).coerceIn(-1.5f, 1.5f)

    val shape = cardShape(0)

    // Like/Nope gradient feedback
    val likeAlpha  = (swipeProgress - 0.1f).coerceIn(0f, 1f) * 0.65f
    val nopeAlpha  = (-swipeProgress - 0.1f).coerceIn(0f, 1f) * 0.65f
    val labelAlpha = (kotlin.math.abs(swipeProgress) - 0.25f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .offset { IntOffset(animOffsetX.roundToInt(), animOffsetY.roundToInt()) }
            .graphicsLayer {
                rotationZ = rotation
                val s = 1f + (kotlin.math.abs(swipeProgress) * 0.03f).coerceAtMost(0.03f)
                scaleX = s; scaleY = s
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { hapticFiredForCurrentDrag = false },
                    onDragEnd = {
                        when {
                            offsetX > swipeThreshold -> scope.launch {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                offsetX = offscreenX
                                kotlinx.coroutines.delay(80); onSwiped(1)
                            }
                            offsetX < -swipeThreshold -> scope.launch {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                offsetX = -offscreenX
                                kotlinx.coroutines.delay(80); onSwiped(-1)
                            }
                            else -> { offsetX = 0f; offsetY = 0f }
                        }
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        offsetX += drag.x
                        offsetY += drag.y
                        // Tick when crossing the threshold while dragging
                        if (!hapticFiredForCurrentDrag && kotlin.math.abs(offsetX) > swipeThreshold) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            hapticFiredForCurrentDrag = true
                        } else if (hapticFiredForCurrentDrag && kotlin.math.abs(offsetX) < swipeThreshold) {
                            // Dragged back across threshold — reset so it can fire again
                            hapticFiredForCurrentDrag = false
                        }
                    }
                )
            }
    ) {
        TinderCard(recipe = recipe, ownerName = ownerName, onClick = onClick, shape = shape)

        // Like overlay — right side radial green
        if (likeAlpha > 0f) {
            Box(
                modifier = Modifier.fillMaxSize().clip(shape)
                    .background(Brush.radialGradient(
                        0f to accent.copy(alpha = likeAlpha),
                        1f to Color.Transparent,
                        center = Offset(Float.POSITIVE_INFINITY, 0f)
                    ))
            )
        }
        // Nope overlay — left side radial secondary
        if (nopeAlpha > 0f) {
            Box(
                modifier = Modifier.fillMaxSize().clip(shape)
                    .background(Brush.radialGradient(
                        0f to secondary.copy(alpha = nopeAlpha),
                        1f to Color.Transparent,
                        center = Offset(0f, 0f)
                    ))
            )
        }

        // YUMMY / NOPE stamp
        if (labelAlpha > 0f) {
            val isLike = swipeProgress > 0
            val stampColor = if (isLike) accent else secondary
            val stampText  = if (isLike) "YUMMY" else "NOPE"
            Box(
                modifier = Modifier
                    .align(if (isLike) Alignment.TopEnd else Alignment.TopStart)
                    .padding(horizontal = 20.dp, vertical = 28.dp)
                    .graphicsLayer {
                        alpha = labelAlpha
                        rotationZ = if (isLike) -12f else 12f
                        scaleX = 0.8f + labelAlpha * 0.2f
                        scaleY = 0.8f + labelAlpha * 0.2f
                    }
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = stampColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(3.dp, stampColor)
                ) {
                    Text(
                        text = stampText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = stampColor,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

// ── Tinder card visual ────────────────────────────────────────────────────────

@Composable
private fun TinderCard(
    recipe: Recipe,
    ownerName: String? = null,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = cardShape(0),
    onClick: () -> Unit = {}
) {
    val accent = LocalAppColors.current.accent

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxSize().clip(shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Image
            if (recipe.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = recipe.thumbnailUrl,
                    contentDescription = recipe.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.EmojiFoodBeverage, null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                }
            }

            // Gradient scrim — taller, deeper
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.4f to Color.Transparent,
                        0.75f to Color.Black.copy(alpha = 0.5f),
                        1f to Color.Black.copy(alpha = 0.82f)
                    )
                )
            )

            // Owner badge (top-left chip)
            if (ownerName != null) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.46f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Default.Person, null,
                            tint = Color.White, modifier = Modifier.size(14.dp))
                        Text("Von $ownerName",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Info section — bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, end = 20.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Chips row (tags + cook time)
                val tags = try {
                    com.google.gson.Gson().fromJson(recipe.tags,
                        object : com.google.gson.reflect.TypeToken<List<String>>() {}.type)
                        as? List<String> ?: emptyList()
                } catch (_: Exception) { emptyList<String>() }

                val visibleTags = tags.take(2)
                val accentColor = LocalAppColors.current.accent
                if (visibleTags.isNotEmpty() || recipe.cookingTimeMinutes > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (recipe.cookingTimeMinutes > 0) {
                            InfoChip(
                                icon = Icons.Default.Timer,
                                label = "${recipe.cookingTimeMinutes} min",
                                accentColor = accentColor
                            )
                        }
                        visibleTags.forEach { tag ->
                            InfoChip(
                                icon = TAG_ICONS[tag],
                                label = tag,
                                accentColor = accentColor
                            )
                        }
                    }
                }

                // Title
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun InfoChip(icon: ImageVector? = null, label: String, accentColor: Color = Color.White) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.75f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(12.dp))
            }
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Match overlay ─────────────────────────────────────────────────────────────

@Composable
private fun MatchOverlay(recipe: Recipe, onClose: () -> Unit) {
    val accent   = LocalAppColors.current.accent
    val secondary = LocalAppColors.current.secondary
    val puns = stringArrayResource(R.array.crave_match_puns)
    val pun  = remember(recipe.id) { puns.random() }

    Dialog(onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {

        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val scale by animateFloatAsState(
            targetValue = if (shown) 1f else 0.55f,
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
            label = "mScale"
        )
        val alpha by animateFloatAsState(
            targetValue = if (shown) 1f else 0f,
            animationSpec = tween(300), label = "mAlpha"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(accent, secondary)))
                .pointerInput(Unit) { detectDragGestures { _, _ -> } },
            contentAlignment = Alignment.Center
        ) {
            FloatingFoodEmojis()

            // Close button
            FilledTonalIconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(16.dp)
                    .size(52.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.22f),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Close, stringResource(R.string.crave_close),
                    modifier = Modifier.size(28.dp))
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            ) {
                Text("❤️", fontSize = 56.sp)
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(30.dp),
                    color = Color.White.copy(alpha = 0.18f)
                ) {
                    Text(
                        stringResource(R.string.crave_match_title),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
                Spacer(Modifier.height(22.dp))
                TinderCard(
                    recipe = recipe,
                    shape = RoundedCornerShape(topStart = 44.dp, topEnd = 16.dp,
                        bottomEnd = 44.dp, bottomStart = 16.dp),
                    modifier = Modifier.height(320.dp).width(248.dp)
                )
                Spacer(Modifier.height(20.dp))
                Text(pun,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.crave_match_cook, recipe.title),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.88f), textAlign = TextAlign.Center)
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White, contentColor = accent),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().height(58.dp)
                ) {
                    Text(stringResource(R.string.crave_keep_swiping),
                        fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Food emoji rain ───────────────────────────────────────────────────────────

@Composable
private fun FloatingFoodEmojis() {
    val emojis = listOf("🍕","🍝","😋","🔥","🧀","🥑","🍳","❤️","🌮","🍜")
    val transition = rememberInfiniteTransition(label = "food")
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        repeat(12) { i ->
            val progress by transition.animateFloat(0f, 1f,
                infiniteRepeatable(
                    tween(3200 + (i % 5) * 500, delayMillis = i * 220, easing = LinearEasing),
                    RepeatMode.Restart
                ), label = "f$i")
            val xPx = w * (((i * 0.173f) + 0.06f) % 1f)
            val yPx = h * (1f - progress) - 60f
            Text(
                text = emojis[i % emojis.size],
                fontSize = (22 + (i % 4) * 9).sp,
                modifier = Modifier.graphicsLayer {
                    translationX = xPx; translationY = yPx
                    alpha = (1f - progress).coerceIn(0f, 0.9f)
                    rotationZ = (if (i % 2 == 0) 1f else -1f) * progress * 220f
                }
            )
        }
    }
}

// ── Empty states ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyTinderState() {
    val accent = LocalAppColors.current.accent
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(0.85f, 1.0f,
        infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ring")

    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(120.dp).graphicsLayer { scaleX = ring; scaleY = ring }
                .background(accent.copy(alpha = 0.08f), CircleShape))
            Icon(Icons.Default.EmojiFoodBeverage, null,
                modifier = Modifier.size(56.dp), tint = accent)
        }
        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.crave_no_recipes),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.crave_no_recipes_sub),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AllSwipedState(onReset: () -> Unit) {
    val accent = LocalAppColors.current.accent
    val secondary = LocalAppColors.current.secondary
    val pulse = rememberInfiniteTransition(label = "done")
    val hue by pulse.animateFloat(0f, 1f,
        infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse), label = "hue")

    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
            Box(modifier = Modifier.fillMaxSize()
                .background(
                    Brush.radialGradient(listOf(
                        accent.copy(alpha = 0.15f + hue * 0.1f),
                        secondary.copy(alpha = 0.08f)
                    )), CircleShape
                )
            )
            Text("✨", fontSize = 48.sp)
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.crave_done_today),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.crave_done_today_sub),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(36.dp))
        FilledTonalButton(
            onClick = onReset,
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = accent.copy(alpha = 0.14f),
                contentColor = accent
            ),
            modifier = Modifier.height(52.dp)
        ) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.crave_restart),
                fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
