package com.zephron.app.ui.screens

import android.content.Intent
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zephron.app.R
import com.zephron.app.data.Recipe
import com.zephron.app.data.RecipeStep
import com.zephron.app.ui.ExpressiveCheckbox
import com.zephron.app.ui.TAG_GROUPS
import com.zephron.app.ui.TAG_ICONS
import com.zephron.app.ui.theme.LocalAppColors
import com.zephron.app.ui.verticalScrollbar
import java.io.File

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipe: Recipe,
    onBack: () -> Unit,
    onDelete: (Recipe) -> Unit = {},
    onTagClick: (String) -> Unit = {},
    onUpdateTags: (Recipe, List<String>) -> Unit = { _, _ -> },
    onUpdateTitle: (Recipe, String) -> Unit = { _, _ -> },
    onUpdateCookTime: (Recipe, Int) -> Unit = { _, _ -> },
    onUpdateServings: (Recipe, Int) -> Unit = { _, _ -> },
    onUpdateIngredients: (Recipe, List<String>) -> Unit = { _, _ -> },
    onUpdateSteps: (Recipe, List<RecipeStep>) -> Unit = { _, _ -> },
    onUpdateRating: (Recipe, Int) -> Unit = { _, _ -> },
    onUpdateIsVegetarian: (Recipe, Boolean) -> Unit = { _, _ -> },
    onUpdateThumbnail: (Recipe, String) -> Unit = { _, _ -> },
    onToggleFavorite: (Recipe) -> Unit = {},
    onToggleCooked: (Recipe) -> Unit = {},
    isOwnRecipe: Boolean = true,
    alreadyOwned: Boolean = false,
    onAdopt: ((Recipe) -> Unit)? = null
) {
    val orange = LocalAppColors.current.accent
    val secondary = LocalAppColors.current.secondary
    val context = LocalContext.current
    val density = LocalDensity.current
    
    // States
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditTagsSheet by remember { mutableStateOf(false) }
    var showTimeDialog by remember { mutableStateOf(false) }
    var isEditingTitle by remember { mutableStateOf(false) }
    var isEditingBody by remember { mutableStateOf(false) }
    var showCookMode by remember { mutableStateOf(false) }
    
    var titleDraft by remember(recipe.title) { mutableStateOf(recipe.title) }
    val dietTags = setOf("Vegetarisch", "Vegan", "Fleisch")
    var tagsDraft by remember { mutableStateOf(emptySet<String>()) }
    // baseServings is fixed per recipe — only resets when a different recipe is opened.
    // displayServings changes with +/- and is persisted, but scaleFactor is always
    // relative to the original base so the display doesn't snap back after a save.
    val baseServings by remember(recipe.id) { mutableIntStateOf(recipe.servings) }
    var displayServings by remember(recipe.id) { mutableIntStateOf(recipe.servings) }
    val scaleFactor = if (baseServings > 0) displayServings.toDouble() / baseServings else 1.0

    // Data parsing
    val ingredients: List<String> = remember(recipe.ingredients) {
        try { Gson().fromJson(recipe.ingredients, object : TypeToken<List<String>>() {}.type) ?: emptyList() }
        catch (e: Exception) { emptyList() }
    }
    val tags: List<String> = remember(recipe.tags, recipe.isVegetarian) {
        val base = try { com.zephron.app.network.TagDetector.normalize(Gson().fromJson(recipe.tags, object : TypeToken<List<String>>() {}.type) ?: emptyList()) }
        catch (e: Exception) { emptyList() }
        // Synthesise diet tag using the shared isVeg helper (checks both tags and boolean)
        val hasDietTag = base.any { it == "Vegetarisch" || it == "Vegan" || it == "Fleisch" }
        val veg = com.zephron.app.network.TagDetector.isVeg(recipe.isVegetarian, recipe.tags)
        when {
            !hasDietTag && veg -> base + "Vegetarisch"
            !hasDietTag && !veg -> base + "Fleisch"
            else -> base
        }
    }
    val steps: List<RecipeStep> = remember(recipe.steps) {
        try { Gson().fromJson(recipe.steps, object : TypeToken<List<RecipeStep>>() {}.type) ?: emptyList() }
        catch (e: Exception) { emptyList() }
    }
    val slideImages: List<String> = remember(recipe.slideImages) {
        try { Gson().fromJson(recipe.slideImages, object : TypeToken<List<String>>() {}.type) ?: emptyList() }
        catch (e: Exception) { emptyList() }
    }
    
    var ingredientsDraft by remember(ingredients) { mutableStateOf(ingredients.toMutableList()) }
    var checkedIngredients by remember(recipe.id) { mutableStateOf(emptySet<Int>()) }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val imageHeightPx = with(density) { 300.dp.toPx() }
    val stickyHeaderAlpha by remember {
        derivedStateOf {
            ((scrollState.value - (imageHeightPx * 0.7f)) / (imageHeightPx * 0.2f)).coerceIn(0f, 1f)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            copyImageToInternalStorage(context, it, recipe.id)?.let { path -> onUpdateThumbnail(recipe, path) }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            BottomActionBar(
                recipe = recipe,
                hasCookableContent = steps.isNotEmpty() || recipe.notes.isNotBlank(),
                accent = orange,
                onStartCooking = { showCookMode = true }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            
            // ── Main Scrollable Content ───────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scrollState)
            ) {
                // 1. Hero Image / Slideshow
                Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                    if (slideImages.size > 1) {
                        val pagerState = rememberPagerState { slideImages.size }
                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                            AsyncImage(model = slideImages[page], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    } else {
                        AsyncImage(
                            model = recipeImageModel(recipe.thumbnailUrl),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // 2. Info Card
                Surface(
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().offset(y = (-20).dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                        // Title row
                        if (isEditingTitle) {
                            OutlinedTextField(
                                value = titleDraft,
                                onValueChange = { titleDraft = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                trailingIcon = { IconButton(onClick = { onUpdateTitle(recipe, titleDraft); isEditingTitle = false }) { Icon(Icons.Filled.Check, null, tint = orange) } },
                                shape = RoundedCornerShape(12.dp)
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = recipe.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                IconButton(onClick = {
                                    isEditingTitle = true
                                    tagsDraft = tags.toSet() // includes diet tag
                                    showEditTagsSheet = true
                                }) { Icon(Icons.Filled.Edit, null, modifier = Modifier.size(20.dp)) }
                            }
                        }

                        // ── Non-diet tags ───────────────────────────────────────
                        val tagOrder = TAG_GROUPS.flatMap { it.second }.withIndex().associate { (i, t) -> t to i }
                        val displayTags = tags.filter { it !in dietTags }.sortedBy { tagOrder[it] ?: Int.MAX_VALUE }
                        if (displayTags.isNotEmpty() || showEditTagsSheet) {
                            Spacer(Modifier.height(12.dp))
                        }
                        if (showEditTagsSheet) {
                            // ── Diet cycler inside edit panel ─────────────────
                            val editDietTag = tagsDraft.firstOrNull { it in dietTags }
                                ?: tags.firstOrNull { it in dietTags }
                                ?: if (recipe.isVegetarian) "Vegetarisch" else "Fleisch"
                            val editNextDiet = mapOf("Fleisch" to "Vegetarisch", "Vegetarisch" to "Vegan", "Vegan" to "Fleisch")[editDietTag] ?: "Vegetarisch"
                            val editDietEmoji = when (editDietTag) { "Vegan" -> "🌱"; "Vegetarisch" -> "🌿"; else -> "🍖" }
                            Surface(
                                onClick = { tagsDraft = (tagsDraft.filter { it !in dietTags } + editNextDiet).toSet() },
                                shape = RoundedCornerShape(20.dp),
                                color = if (editDietTag != "Fleisch") secondary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = if (editDietTag != "Fleisch") BorderStroke(1.dp, secondary.copy(alpha = 0.5f)) else null,
                                modifier = Modifier.padding(bottom = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    Text(editDietEmoji, fontSize = 14.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(editDietTag, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                        color = if (editDietTag != "Fleisch") secondary else MaterialTheme.colorScheme.onSurface)
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            // Inline tag editor — all non-diet TAG_GROUPS
                            FlowRow(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                TAG_GROUPS.filter { it.first != "Ernährung" }.forEach { (_, groupTags) ->
                                    groupTags.forEach { tag ->
                                        val selected = tag in tagsDraft
                                        val icon = TAG_ICONS[tag]
                                        FilterChip(
                                            selected = selected,
                                            onClick = { tagsDraft = if (selected) tagsDraft - tag else tagsDraft + tag },
                                            label = { Text(tag, fontSize = 12.sp) },
                                            leadingIcon = icon?.let { { Icon(it, null, Modifier.size(14.dp)) } },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = secondary.copy(alpha = 0.15f),
                                                selectedLabelColor = secondary,
                                                selectedLeadingIconColor = secondary
                                            )
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = { showEditTagsSheet = false }) { Text("Abbrechen") }
                                Spacer(Modifier.weight(1f))
                                Button(
                                    onClick = {
                                        // Close panel first so the UI snaps to view-mode
                                        // before the recipe-update recomposition arrives
                                        showEditTagsSheet = false
                                        isEditingTitle = false
                                        onUpdateTags(recipe, tagsDraft.toList())
                                        coroutineScope.launch { scrollState.animateScrollTo(0) }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = orange)
                                ) { Text("Speichern") }
                            }
                        } else if (displayTags.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                displayTags.forEach { tag -> TagPill(tag = tag, onClick = { onTagClick(tag) }) }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // ── Diet chip (display only) + rating ──
                        val dietTag = tags.firstOrNull { it in dietTags } ?: if (recipe.isVegetarian) "Vegetarisch" else "Fleisch"
                        val dietEmoji = when (dietTag) { "Vegan" -> "🌱"; "Vegetarisch" -> "🌿"; else -> "🍖" }
                        val dietSelected = dietTag != "Fleisch"
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (dietSelected) secondary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = if (dietSelected) BorderStroke(1.dp, secondary.copy(alpha = 0.5f)) else null
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(dietEmoji, fontSize = 14.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        dietTag,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (dietSelected) secondary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            StarRatingRow(rating = recipe.rating, onRate = { onUpdateRating(recipe, it) })
                        }

                        Spacer(Modifier.height(24.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(24.dp))

                        // 3. Ingredients Section
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Zutaten", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (recipe.servings > 0) {
                                    Surface(shape = RoundedCornerShape(20.dp), color = orange.copy(alpha = 0.1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                                            IconButton(onClick = { if (displayServings > 1) { displayServings--; onUpdateServings(recipe, displayServings) } }, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.Remove, null, tint = orange, modifier = Modifier.size(16.dp)) }
                                            Text(text = "🍴 $displayServings", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = orange)
                                            IconButton(onClick = { displayServings++; onUpdateServings(recipe, displayServings) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.Add, null, tint = orange, modifier = Modifier.size(16.dp)) }
                                        }
                                    }
                                }
                                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.clickable { showTimeDialog = true }) {
                                    Text(text = if (recipe.cookingTimeMinutes > 0) "${recipe.cookingTimeMinutes} Min" else "Zeit", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        
                        // Ingredient List
                        ingredients.forEachIndexed { index, ingredient ->
                            val checked = index in checkedIngredients
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { checkedIngredients = if (checked) checkedIngredients - index else checkedIngredients + index }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ExpressiveCheckbox(checked = checked, onCheckedChange = { }, color = orange)
                                Spacer(Modifier.width(12.dp))
                                Text(text = scaleIngredient(ingredient, scaleFactor), style = MaterialTheme.typography.bodyLarge, textDecoration = if (checked) TextDecoration.LineThrough else null, color = if (checked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        // 4. Steps Section
                        Spacer(Modifier.height(32.dp))
                        Text(text = "Rezept", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        if (steps.isNotEmpty()) {
                            steps.forEachIndexed { idx, step -> StepViewCard(index = idx, step = step, accent = orange) }
                        } else {
                            Text(text = recipe.notes.ifBlank { "Keine Schritte verfügbar." }, style = MaterialTheme.typography.bodyMedium, lineHeight = 24.sp)
                        }
                        
                        Spacer(Modifier.height(100.dp))
                    }
                }
            }

            // ── Fixed Status Bar Scrim ──────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth().height(80.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent))))

            // ── Sticky Banner Header ──────────────────────────────────────────
            // Only render when visible — graphicsLayer keeps the composable in the tree
            // even at alpha=0, which intercepts touches. Conditional avoids that.
            if (stickyHeaderAlpha > 0.01f) Surface(
                modifier = Modifier.fillMaxWidth().height(110.dp).graphicsLayer { alpha = stickyHeaderAlpha },
                color = Color.Transparent
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Modern Gradient Banner
                    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), Color.Transparent))))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 8.dp).height(56.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                        Text(
                            text = "REZEPT-MODUS",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            textAlign = TextAlign.Center,
                            color = orange
                        )
                        IconButton(onClick = { onToggleCooked(recipe) }) { Icon(if (recipe.isCooked) Icons.Filled.RestaurantMenu else Icons.Outlined.RestaurantMenu, null, tint = if (recipe.isCooked) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface) }
                        IconButton(onClick = { onToggleFavorite(recipe) }) { Icon(if (recipe.isFavorite) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, null, tint = if (recipe.isFavorite) orange else MaterialTheme.colorScheme.onSurface) }
                    }
                }
            }

            // ── Floating Action Buttons (when not sticky) ─────────────────────
            if (stickyHeaderAlpha < 0.5f) {
                Row(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 12.dp).graphicsLayer { alpha = 1f - (stickyHeaderAlpha * 2f).coerceIn(0f, 1f) },
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FloatingCircleButton(icon = Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!isOwnRecipe) {
                            var adopted by remember { mutableStateOf(false) }
                            if (alreadyOwned || adopted) {
                                // Already in library — show pill badge
                                Row(
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.25f), shape = androidx.compose.foundation.shape.CircleShape)
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Text(if (adopted) "Importiert" else "In Bibliothek", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                // Not yet imported — show download button
                                Row(
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .background(orange, shape = androidx.compose.foundation.shape.CircleShape)
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                        .clickable { onAdopt?.invoke(recipe); adopted = true }
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Text("In meine Rezepte", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        } else {
                            FloatingCircleButton(icon = if (recipe.isFavorite) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, onClick = { onToggleFavorite(recipe) }, tint = if (recipe.isFavorite) orange else Color.White)
                            FloatingCircleButton(icon = if (recipe.isCooked) Icons.Filled.RestaurantMenu else Icons.Outlined.RestaurantMenu, onClick = { onToggleCooked(recipe) }, tint = if (recipe.isCooked) Color(0xFF4CAF50) else Color.White)
                            FloatingCircleButton(icon = Icons.Filled.PhotoLibrary, onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                            FloatingCircleButton(icon = Icons.Filled.Delete, onClick = { showDeleteDialog = true })
                        }
                    }
                }
            }
        }
    }

    // Overlays & Dialogs
    if (showCookMode) CookModeScreen(recipe = recipe, steps = steps, onDismiss = { showCookMode = false })
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Rezept löschen?") },
            text = { Text("\"${recipe.title}\" wird dauerhaft entfernt.") },
            confirmButton = { TextButton(onClick = { onDelete(recipe); showDeleteDialog = false; onBack() }) { Text("Löschen", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun FloatingCircleButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, tint: Color = Color.White) {
    Surface(modifier = Modifier.size(48.dp), shape = CircleShape, color = Color.Black.copy(alpha = 0.45f), onClick = onClick) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
private fun BottomActionBar(recipe: Recipe, hasCookableContent: Boolean, accent: Color, onStartCooking: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val secondary = LocalAppColors.current.secondary
    
    if (!hasCookableContent && recipe.url.isBlank()) return

    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 16.dp, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), tonalElevation = 2.dp) {
        Column(modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (hasCookableContent) {
                Button(onClick = onStartCooking, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                    Icon(Icons.Filled.Restaurant, null)
                    Spacer(Modifier.width(12.dp))
                    Text("Kochmodus starten", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            }
            if (recipe.url.isNotBlank()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val label = if (recipe.url.contains("instagram")) "Instagram" else "TikTok"
                    FilledTonalButton(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(recipe.url))) },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = secondary.copy(alpha = 0.12f),
                            contentColor = secondary
                        )
                    ) {
                        Icon(Icons.Filled.Link, null)
                        Spacer(Modifier.width(8.dp))
                        Text("In $label öffnen", fontWeight = FontWeight.Bold)
                    }
                    FilledTonalButton(
                        onClick = { clipboardManager.setText(AnnotatedString(recipe.url)) },
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TagPill(tag: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = LocalAppColors.current.secondary.copy(alpha = 0.12f), modifier = Modifier.height(32.dp).clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp)) {
            val icon = TAG_ICONS[tag]
            if (icon != null) { Icon(icon, null, tint = LocalAppColors.current.secondary, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(5.dp)) }
            Text(text = tag, color = LocalAppColors.current.secondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StepViewCard(index: Int, step: RecipeStep, accent: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Surface(modifier = Modifier.size(28.dp), shape = CircleShape, color = accent) {
            Box(contentAlignment = Alignment.Center) { Text("${index + 1}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.width(16.dp))
        Text(text = step.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), lineHeight = 26.sp)
    }
}

@Composable
private fun StarRatingRow(rating: Int, onRate: (Int) -> Unit) {
    val gold = Color(0xFFFFBB00)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = gold.copy(alpha = 0.10f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            (1..5).forEach { star ->
                Icon(
                    imageVector = if (star <= rating) Icons.Filled.Star else Icons.Filled.StarOutline,
                    contentDescription = null,
                    tint = if (star <= rating) gold else gold.copy(alpha = 0.28f),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onRate(if (rating == star) 0 else star) }
                )
            }
            if (rating > 0) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "$rating",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = gold
                )
            }
        }
    }
}

/**
 * Scales numeric quantities in an ingredient string by [factor].
 * Handles integers (200), decimals (1.5), and fractions (1/2, ½ etc.).
 * Only the leading number(s) are scaled — units and ingredient name are kept as-is.
 */
fun scaleIngredient(ingredient: String, factor: Double): String {
    if (factor == 1.0) return ingredient

    // Regex: optional integer part, optional fraction (e.g. "1 1/2", "200", "0.5", "½")
    val pattern = Regex(
        """^(\d+(?:[.,]\d+)?(?:\s+\d+/\d+)?|\d+/\d+|[¼½¾⅓⅔⅛⅜⅝⅞])"""
    )
    val match = pattern.find(ingredient.trim()) ?: return ingredient

    val rawNumber = match.value.trim()
    val value = parseIngredientNumber(rawNumber) ?: return ingredient
    val scaled = value * factor

    val formatted = formatIngredientNumber(scaled)
    return formatted + ingredient.substring(match.range.last + 1)
}

private fun parseIngredientNumber(s: String): Double? {
    // Unicode fractions
    val unicodeFractions = mapOf('¼' to 0.25, '½' to 0.5, '¾' to 0.75,
        '⅓' to 1.0/3, '⅔' to 2.0/3, '⅛' to 0.125, '⅜' to 0.375, '⅝' to 0.625, '⅞' to 0.875)
    if (s.length == 1 && unicodeFractions.containsKey(s[0])) return unicodeFractions[s[0]]

    // "1 1/2" mixed number
    val mixed = Regex("""^(\d+)\s+(\d+)/(\d+)$""").matchEntire(s)
    if (mixed != null) {
        val (whole, num, den) = mixed.destructured
        return whole.toDouble() + num.toDouble() / den.toDouble()
    }
    // plain fraction "3/4"
    val frac = Regex("""^(\d+)/(\d+)$""").matchEntire(s)
    if (frac != null) {
        val (num, den) = frac.destructured
        return num.toDouble() / den.toDouble()
    }
    return s.replace(',', '.').toDoubleOrNull()
}

private fun formatIngredientNumber(v: Double): String {
    // Try to express as a nice fraction if close to common ones
    val fractions = listOf(
        0.25 to "¼", 0.5 to "½", 0.75 to "¾",
        1.0/3 to "⅓", 2.0/3 to "⅔", 0.125 to "⅛"
    )
    for ((fval, fsym) in fractions) {
        val whole = v.toLong()
        val remainder = v - whole
        if (kotlin.math.abs(remainder - fval) < 0.05) {
            return if (whole > 0) "$whole $fsym" else fsym
        }
    }
    // Round to 1 decimal, drop ".0"
    val rounded = kotlin.math.round(v * 10) / 10.0
    return if (rounded == kotlin.math.floor(rounded)) rounded.toLong().toString()
    else rounded.toString().replace('.', ',')
}

private fun recipeImageModel(url: String): Any = if (url.startsWith("/")) File(url) else url

private fun copyImageToInternalStorage(context: Context, uri: Uri, recipeId: Int): String? {
    return try {
        val dir = File(context.filesDir, "covers").also { it.mkdirs() }
        val dest = File(dir, "recipe_${recipeId}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
        dest.absolutePath
    } catch (_: Exception) { null }
}
