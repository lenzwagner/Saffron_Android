package com.zephron.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiFoodBeverage
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zephron.app.R
import com.zephron.app.data.Recipe
import com.zephron.app.ui.theme.LocalAppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeOfDayScreen(
    recipes: List<Recipe>,
    userName: String = "",
    currentUserId: String = "",
    friendNames: Map<String, String> = emptyMap(),
    friendNicknames: Map<String, String> = emptyMap(),
    onRecipeClick: (Recipe) -> Unit,
    onOpenSettings: () -> Unit = {},
    onToggleAssistantBubble: () -> Unit = {},
    isAssistantVisible: Boolean = false,
    onScrollLock: (Boolean) -> Unit = {}
) {
    val secondary = LocalAppColors.current.secondary

    val vegetarianRecipes = remember(recipes) {
        recipes.filter { com.zephron.app.network.TagDetector.isVeg(it.isVegetarian, it.tags) }
    }
    val meatRecipes = remember(recipes) {
        recipes.filter { !com.zephron.app.network.TagDetector.isVeg(it.isVegetarian, it.tags) }
    }

    var shuffleSeed by rememberSaveable { mutableIntStateOf(0) }
    var iconRotation by remember { mutableFloatStateOf(0f) }
    val animatedRotation by animateFloatAsState(
        targetValue = iconRotation,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "shuffle"
    )

    val vegPicks = remember(vegetarianRecipes, shuffleSeed) {
        vegetarianRecipes.shuffled(java.util.Random(shuffleSeed.toLong())).take(3)
    }
    val meatPicks = remember(meatRecipes, shuffleSeed) {
        meatRecipes.shuffled(java.util.Random(shuffleSeed.toLong())).take(3)
    }

    val hasPicks = vegPicks.isNotEmpty() || meatPicks.isNotEmpty()
    val canShuffle = vegetarianRecipes.size > 1 || meatRecipes.size > 1

    val accent = LocalAppColors.current.accent
    val secondaryColor = LocalAppColors.current.secondary

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val bottomBarPadding = 110.dp
        
        val finalItemWidth = (screenWidth * 0.65f).coerceAtMost(240.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // ── Header Section ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hallo, ${userName.ifBlank { "Lorenz" }}!",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "Deine Picks für heute",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
                Row {
                    if (canShuffle) {
                        IconButton(onClick = {
                            shuffleSeed++
                            iconRotation += 360f
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Shuffle,
                                contentDescription = "Mischen",
                                modifier = Modifier.size(22.dp).rotate(animatedRotation),
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }
                    }
                    IconButton(onClick = { onToggleAssistantBubble() }) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = "Assistant",
                            modifier = Modifier.size(22.dp),
                            tint = if (isAssistantVisible) secondary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            if (!hasPicks) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.EmojiFoodBeverage, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.today_no_recipes), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                AnimatedContent(
                    targetState = shuffleSeed,
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(250))
                    },
                    label = "shuffle_transition",
                    modifier = Modifier.weight(1f)
                ) { seed ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        key(seed) {
                            if (meatPicks.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                RecipeCarousel(
                                    label = stringResource(R.string.today_meat_pick),
                                    labelColor = accent,
                                    picks = meatPicks,
                                    onRecipeClick = onRecipeClick,
                                    modifier = Modifier.weight(1f).heightIn(max = 350.dp),
                                    itemWidth = finalItemWidth,
                                    onScrollLock = onScrollLock
                                )
                            }
                            if (vegPicks.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(20.dp))
                                RecipeCarousel(
                                    label = stringResource(R.string.today_vegetarian_pick),
                                    labelColor = secondaryColor,
                                    picks = vegPicks,
                                    onRecipeClick = onRecipeClick,
                                    modifier = Modifier.weight(1f).heightIn(max = 350.dp),
                                    itemWidth = finalItemWidth,
                                    onScrollLock = onScrollLock
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(bottomBarPadding))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeCarousel(
    label: String,
    labelColor: Color,
    picks: List<Recipe>,
    onRecipeClick: (Recipe) -> Unit,
    modifier: Modifier = Modifier,
    itemWidth: androidx.compose.ui.unit.Dp,
    onScrollLock: (Boolean) -> Unit = {}
) {
    val carouselShape = RoundedCornerShape(
        topStart = 28.dp, 
        topEnd = 12.dp, 
        bottomStart = 12.dp, 
        bottomEnd = 28.dp
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            color = labelColor.copy(alpha = 0.12f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (label.contains("Vegetarisch", ignoreCase = true) || label.contains("Vegan", ignoreCase = true)) Icons.Filled.Spa else Icons.Filled.Restaurant,
                    contentDescription = null,
                    tint = labelColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = labelColor,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.sp
                )
            }
        }

        val carouselState = rememberCarouselState { picks.size }
        HorizontalMultiBrowseCarousel(
            state = carouselState,
            preferredItemWidth = itemWidth,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.pressed }) {
                                onScrollLock(true)
                            } else if (event.changes.all { !it.pressed }) {
                                onScrollLock(false)
                            }
                        }
                    }
                },
            itemSpacing = 16.dp,
            flingBehavior = CarouselDefaults.singleAdvanceFlingBehavior(
                state = carouselState,
                snapAnimationSpec = spring(stiffness = Spring.StiffnessLow)
            ),
            contentPadding = PaddingValues(0.dp)
        ) { index ->
            val recipe = picks[index]
            val density = androidx.compose.ui.platform.LocalDensity.current
            val info = carouselItemInfo

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .maskClip(carouselShape)
                    .graphicsLayer {
                        val fraction = if (info.maxSize > info.minSize)
                            ((info.size - info.minSize) / (info.maxSize - info.minSize)).coerceIn(0f, 1f)
                        else 1f

                        scaleX = 0.95f + (fraction * 0.05f)
                        scaleY = 0.95f + (fraction * 0.05f)
                    }
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val fraction = if (info.maxSize > info.minSize)
                                    ((info.size - info.minSize) / (info.maxSize - info.minSize)).coerceIn(0f, 1f)
                                else 1f

                                val blurPx = (0.5f + (1f - fraction) * 8f) * density.density
                                if (android.os.Build.VERSION.SDK_INT >= 31) {
                                    renderEffect = android.graphics.RenderEffect.createBlurEffect(
                                        blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP
                                    ).asComposeRenderEffect()
                                }
                            }
                            .drawWithContent {
                                drawContent()
                                if (android.os.Build.VERSION.SDK_INT < 31) {
                                    val fraction = if (info.maxSize > info.minSize)
                                        ((info.size - info.minSize) / (info.maxSize - info.minSize)).coerceIn(0f, 1f)
                                    else 1f
                                    val dimAlpha = (1f - fraction) * 0.4f
                                    if (dimAlpha > 0f) drawRect(Color.Black.copy(alpha = dimAlpha))
                                }
                            }
                    ) {
                        if (recipe.thumbnailUrl.isNotBlank()) {
                            AsyncImage(
                                model = recipe.thumbnailUrl,
                                contentDescription = recipe.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.EmojiFoodBeverage, null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(14.dp)
                            .graphicsLayer {
                                val fraction = if (info.maxSize > info.minSize)
                                    ((info.size - info.minSize) / (info.maxSize - info.minSize)).coerceIn(0f, 1f)
                                else 1f
                                alpha = ((fraction - 0.75f) / 0.25f).coerceIn(0f, 1f)
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .background(labelColor.copy(alpha = 0.85f))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = recipe.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }

                    Surface(
                        onClick = { onRecipeClick(recipe) },
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent
                    ) {}
                }
            }
        }
    }
}
