package com.zephron.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zephron.app.R
import com.zephron.app.ui.theme.LocalAppColors
import com.zephron.app.viewmodel.ChatMessage
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class BlobShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            val w = size.width
            val h = size.height
            moveTo(w * 0.23f, 0f)
            cubicTo(w * 0.55f, -h * 0.1f, w * 0.9f, h * 0.1f, w * 0.95f, h * 0.35f)
            cubicTo(w * 1.05f, h * 0.7f, w * 0.8f, h * 0.95f, w * 0.5f, h)
            cubicTo(w * 0.15f, h * 1.05f, -w * 0.05f, h * 0.75f, 0f, h * 0.45f)
            cubicTo(w * 0.05f, h * 0.1f, w * 0.1f, 0f, w * 0.23f, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Draggable floating "chat-head" bubble. Can be moved anywhere; on release it
 * snaps to the nearest side and, after a moment of inactivity, tucks itself
 * half off-screen. Tapping it: opens the assistant when out, or slides it back
 * out when tucked.
 */
@Composable
fun MovableAssistantBubble(
    modifier: Modifier = Modifier,
    onPositionChange: (Offset) -> Unit = {},
    onOpen: () -> Unit
) {
    val colors = LocalAppColors.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val bubbleDp = 60.dp
        val padDp = 12.dp
        val bubblePx = with(density) { bubbleDp.toPx() }
        val padPx = with(density) { padDp.toPx() }
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()

        val offsetX = remember { Animatable(padPx) }
        val offsetY = remember { Animatable((maxH * 0.12f).coerceAtLeast(padPx * 10)) }
        var onRight by remember { mutableStateOf(false) }
        var collapsed by remember { mutableStateOf(false) }
        var idleTick by remember { mutableIntStateOf(0) }

        fun edgeX() = if (onRight) maxW - bubblePx - padPx else padPx
        fun tuckedX() = if (onRight) maxW - bubblePx * 0.40f else -bubblePx * 0.60f

        LaunchedEffect(offsetX.value, offsetY.value) {
            onPositionChange(Offset(offsetX.value + bubblePx / 2, offsetY.value + bubblePx / 2))
        }

        LaunchedEffect(idleTick, collapsed) {
            if (!collapsed) {
                kotlinx.coroutines.delay(4000)
                collapsed = true
                offsetX.animateTo(tuckedX(), tween(600, easing = FastOutSlowInEasing))
            }
        }

        val infiniteTransition = rememberInfiniteTransition(label = "bubble")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(6000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                .size(bubbleDp)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                    rotationZ = rotation
                }
                .pointerInput(maxW, maxH) {
                    detectDragGestures(
                        onDragEnd = {
                            onRight = (offsetX.value + bubblePx / 2) > maxW / 2
                            collapsed = false
                            idleTick++
                            scope.launch { offsetX.animateTo(edgeX(), spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)) }
                            scope.launch {
                                offsetY.animateTo(
                                    offsetY.value.coerceIn(padPx, (maxH - bubblePx - padPx).coerceAtLeast(padPx)),
                                    spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)
                                )
                            }
                        }
                    ) { change, drag ->
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + drag.x) }
                        scope.launch { offsetY.snapTo(offsetY.value + drag.y) }
                    }
                }
                .clip(BlobShape())
                .background(Brush.sweepGradient(listOf(colors.accent, colors.secondary, colors.accent)))
                .alpha(if (collapsed) 0.6f else 1f)
                .clickable {
                    if (collapsed) {
                        collapsed = false
                        idleTick++
                        scope.launch { offsetX.animateTo(edgeX(), spring(Spring.DampingRatioMediumBouncy)) }
                    } else {
                        onOpen()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .clip(BlobShape())
                    .background(Color.White.copy(alpha = 0.2f))
            )
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = stringResource(R.string.assistant_open),
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
fun AssistantFloatingWindow(
    messages: List<ChatMessage>,
    isLoading: Boolean,
    remaining: Int,
    onSend: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    resolveRecipe: (String) -> com.zephron.app.data.Recipe? = { null },
    onOpenRecipe: (com.zephron.app.data.Recipe) -> Unit = {}
) {
    val accent = LocalAppColors.current.accent
    val secondary = LocalAppColors.current.secondary
    val scope = rememberCoroutineScope()
    
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    LaunchedEffect(messages.size, isLoading) {
        val count = messages.size + if (isLoading) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        )

        Surface(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.72f)
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount.x) }
                        scope.launch { offsetY.snapTo(offsetY.value + dragAmount.y) }
                    }
                },
            shape = RoundedCornerShape(36.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            tonalElevation = 12.dp,
            shadowElevation = 32.dp,
            border = BorderStroke(2.dp, Brush.linearGradient(listOf(accent.copy(alpha = 0.5f), secondary.copy(alpha = 0.2f))))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Background Pattern (Subtle Gradient)
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize().background(
                        Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.03f), Color.Transparent),
                            center = Offset(0f, 0f),
                            radius = 800f
                        )
                    ))

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(modifier = Modifier.size(40.dp, 5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = BlobShape(),
                                color = accent.copy(alpha = 0.15f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.AutoAwesome, null, tint = accent, modifier = Modifier.size(24.dp))
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.assistant_title),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-0.8).sp
                                )
                                Text(
                                    stringResource(R.string.assistant_remaining, remaining),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (remaining > 0) secondary else MaterialTheme.colorScheme.error
                                )
                            }
                            if (messages.isNotEmpty()) {
                                IconButton(onClick = onClear) {
                                    Icon(Icons.Filled.DeleteSweep, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                }
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            }
                        }

                        // Messages
                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            if (messages.isEmpty()) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(56.dp).alpha(0.08f), tint = accent)
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        stringResource(R.string.assistant_empty),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(horizontal = 48.dp),
                                        textAlign = TextAlign.Center,
                                        lineHeight = 24.sp
                                    )
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(messages, key = { it.id }) { msg ->
                                        Box(modifier = Modifier.animateItem()) {
                                            MessageView(msg, accent, resolveRecipe, onOpenRecipe)
                                        }
                                    }
                                    if (isLoading) {
                                        item(key = "loading") {
                                            Row(modifier = Modifier.padding(8.dp).animateItem()) {
                                                CircularProgressIndicator(modifier = Modifier.size(26.dp), color = accent, strokeWidth = 3.dp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Input
                        Surface(
                            modifier = Modifier.fillMaxWidth().imePadding(),
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = input,
                                    onValueChange = { input = it },
                                    placeholder = { Text(stringResource(R.string.assistant_placeholder), fontSize = 15.sp) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(30.dp),
                                    maxLines = 4,
                                    enabled = remaining > 0 && !isLoading,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = accent,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                    )
                                )
                                Spacer(Modifier.width(12.dp))
                                val canSend = input.isNotBlank() && remaining > 0 && !isLoading
                                FloatingActionButton(
                                    onClick = { if (canSend) { onSend(input); input = "" } },
                                    modifier = Modifier.size(52.dp),
                                    shape = CircleShape,
                                    containerColor = if (canSend) accent else accent.copy(alpha = 0.2f),
                                    contentColor = Color.White,
                                    elevation = FloatingActionButtonDefaults.elevation(0.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val RECIPE_MARKER = Regex("""\[\[recipe:([^\]]+)\]\]""")

@Composable
private fun MessageView(
    msg: ChatMessage,
    accent: Color,
    resolveRecipe: (String) -> com.zephron.app.data.Recipe?,
    onOpenRecipe: (com.zephron.app.data.Recipe) -> Unit
) {
    if (msg.fromUser) {
        ChatBubble(msg, accent)
        return
    }
    val ids = RECIPE_MARKER.findAll(msg.text).map { it.groupValues[1].trim() }.toList()
    val cleanText = msg.text.replace(RECIPE_MARKER, "").trim()
    val recipes = ids.mapNotNull { resolveRecipe(it) }.distinctBy { it.id }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (cleanText.isNotEmpty()) {
            ChatBubble(ChatMessage(fromUser = false, text = cleanText), accent)
        }
        recipes.forEach { recipe ->
            RecipeLinkChip(recipe = recipe, accent = accent) { onOpenRecipe(recipe) }
        }
    }
}

@Composable
private fun RecipeLinkChip(
    recipe: com.zephron.app.data.Recipe,
    accent: Color,
    onClick: () -> Unit
) {
    val expressiveShape = RoundedCornerShape(
        topStart = 24.dp, 
        topEnd = 10.dp, 
        bottomStart = 10.dp, 
        bottomEnd = 24.dp
    )
    ElevatedCard(
        onClick = onClick,
        shape = expressiveShape,
        colors = CardDefaults.elevatedCardColors(containerColor = accent.copy(alpha = 0.07f)),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(0.92f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AsyncImage(
                model = recipe.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(expressiveShape)
            )
            Text(
                recipe.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                null,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage, accent: Color) {
    val secondary = LocalAppColors.current.secondary
    val bubbleColor = if (msg.fromUser) accent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val textColor = if (msg.fromUser) Color.White else MaterialTheme.colorScheme.onSurface
    
    val expressiveShape = if (msg.fromUser) {
        RoundedCornerShape(topStart = 28.dp, topEnd = 6.dp, bottomEnd = 28.dp, bottomStart = 28.dp)
    } else {
        RoundedCornerShape(topStart = 6.dp, topEnd = 28.dp, bottomEnd = 28.dp, bottomStart = 28.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = expressiveShape,
            color = if (msg.fromUser) Color.Transparent else bubbleColor,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .then(
                    if (msg.fromUser) Modifier.background(
                        Brush.linearGradient(listOf(accent, secondary)),
                        expressiveShape
                    ) else Modifier
                ),
            tonalElevation = if (msg.fromUser) 0.dp else 1.dp
        ) {
            Text(
                text = msg.text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (msg.fromUser) FontWeight.Medium else FontWeight.Normal
                ),
                color = textColor,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                lineHeight = 24.sp
            )
        }
    }
}
