package com.zephron.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.NightlightRound
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
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
import com.zephron.app.ui.ExpressiveCheckbox
import com.zephron.app.ui.PartnerAvatar
import com.zephron.app.ui.theme.LocalAppColors
import com.zephron.app.viewmodel.PlanEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val DAY_ICONS = mapOf(
    "MON" to Icons.Filled.Today,
    "TUE" to Icons.Filled.CalendarViewDay,
    "WED" to Icons.Filled.CalendarToday,
    "THU" to Icons.Filled.Event,
    "FRI" to Icons.Filled.Brightness2,
    "SAT" to Icons.Filled.Weekend,
    "SUN" to Icons.Filled.WbSunny
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    friends: List<String>,
    friendNames: Map<String, String>,
    friendNicknames: Map<String, String>,
    planPartnerId: String?,
    planEntries: List<PlanEntry>,
    recipes: List<Recipe>,
    currentUserId: String = "",
    onSelectPartner: (String?) -> Unit,
    onAddToPlan: (String, Recipe, String) -> Unit,
    onRemoveFromPlan: (String) -> Unit,
    onRecipeClick: (Recipe) -> Unit,
    onOpenSettings: () -> Unit,
    onToggleAssistantBubble: () -> Unit = {},
    isAssistantVisible: Boolean = false
) {
    val accent = LocalAppColors.current.accent
    val secondary = LocalAppColors.current.secondary
    var isShoppingTab by remember { mutableStateOf(false) }
    var expandedDays by remember { mutableStateOf(setOf<String>()) }
    var pickerForDay by remember { mutableStateOf<Pair<String, String>?>(null) } // dayKey to mealType
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Rolling 7 days as absolute date strings (yyyy-MM-dd) so entries never
    // bleed into the next week. Each pair: (dateString, display label).
    val rollingPlanDays = remember {
        val keyFmt   = SimpleDateFormat("yyyy-MM-dd", Locale.GERMAN)
        val dayFmt   = SimpleDateFormat("d. MMMM", Locale.GERMAN)         // "14. Juni"
        val weekdayFmt = SimpleDateFormat("EEEE", Locale.GERMAN)          // "Samstag"
        List(7) { i ->
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i) }
            val dateStr = keyFmt.format(cal.time)
            // "Heute" for index 0; "14. Juni 2026 (Samstag)" for the rest
            val label = if (i == 0) "Heute"
                        else "${dayFmt.format(cal.time)} (${weekdayFmt.format(cal.time)})"
            dateStr to label
        }
    }

    // Helper: weekday code for icon lookup from a yyyy-MM-dd date string
    fun weekdayCode(dateStr: String): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.GERMAN)
        val cal = Calendar.getInstance().apply { time = fmt.parse(dateStr) ?: Date() }
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY    -> "MON"
            Calendar.TUESDAY   -> "TUE"
            Calendar.WEDNESDAY -> "WED"
            Calendar.THURSDAY  -> "THU"
            Calendar.FRIDAY    -> "FRI"
            Calendar.SATURDAY  -> "SAT"
            else               -> "SUN"
        }
    }

    // Automatically expand the first day ("Heute") by default
    LaunchedEffect(rollingPlanDays) {
        if (expandedDays.isEmpty() && rollingPlanDays.isNotEmpty()) {
            expandedDays = setOf(rollingPlanDays.first().first)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.tab_plan),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(onClick = onToggleAssistantBubble) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = "Assistant",
                    tint = if (isAssistantVisible) accent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Tab Toggle (Wochenplan / Einkaufsliste) ───────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .height(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val indicatorOffset by animateFloatAsState(
                    targetValue = if (isShoppingTab) 1f else 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "plan_tab_indicator"
                )
                
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val indicatorWidth = this.maxWidth / 2
                    Box(
                        modifier = Modifier
                            .offset(x = indicatorWidth * indicatorOffset)
                            .width(indicatorWidth)
                            .fillMaxHeight()
                            .padding(4.dp)
                            .background(secondary, CircleShape)
                    )
                }

                Row(modifier = Modifier.fillMaxSize()) {
                    listOf(
                        "Wochenplan" to false,
                        "Einkaufsliste" to true
                    ).forEach { (label, isShopping) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { isShoppingTab = isShopping },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isShoppingTab == isShopping) Color.White 
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (isShoppingTab) {
            ShoppingListView(
                planEntries = planEntries,
                allRecipes = recipes,
                planPartnerId = planPartnerId,
                secondary = secondary
            )
        } else {
            // ── Partner picker ────────────────────────────────────────────────────
            PlanPartnerDropdown(
                friends = friends,
                friendNames = friendNames,
                friendNicknames = friendNicknames,
                selectedId = planPartnerId,
                accent = accent,
                onSelect = onSelectPartner
            )

            // Solo or partner — always show the week grid
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(rollingPlanDays) { (dateStr, label) ->
                    val isExpanded = dateStr in expandedDays
                    DayCard(
                        label = label,
                        icon = DAY_ICONS[weekdayCode(dateStr)] ?: Icons.Filled.CalendarMonth,
                        isExpanded = isExpanded,
                        entries = planEntries.filter { it.day == dateStr },
                        accent = accent,
                        secondary = secondary,
                        onToggle = {
                            expandedDays = if (isExpanded) expandedDays - dateStr else expandedDays + dateStr
                        },
                        onAdd = { mealType ->
                            pickerForDay = dateStr to mealType
                        },
                        onRemove = { onRemoveFromPlan(it) },
                        onEntryClick = { entry ->
                            recipes.find { it.url == entry.recipeUrl }?.let(onRecipeClick)
                        }
                    )
                }
            }
        }
    }

    // ── Recipe picker sheet ───────────────────────────────────────────────────
    if (pickerForDay != null) {
        ModalBottomSheet(
            onDismissRequest = { pickerForDay = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            RecipePickerContent(
                recipes = recipes,
                currentUserId = currentUserId,
                accent = accent,
                onPick = { recipe ->
                    pickerForDay?.let { (day, mealType) ->
                        onAddToPlan(day, recipe, mealType)
                    }
                    pickerForDay = null
                }
            )
        }
    }
}

@Composable
fun ShoppingListView(
    planEntries: List<PlanEntry>,
    allRecipes: List<Recipe>,
    planPartnerId: String?,
    secondary: Color
) {
    val accent = LocalAppColors.current.accent
    var targetServings by remember { mutableIntStateOf(2) }
    var hungerFactor by remember { mutableFloatStateOf(1.0f) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var recipesExpanded by remember { mutableStateOf(true) }
    var portionenExpanded by remember { mutableStateOf(true) }
    
    val sliderInteractionSource = remember { MutableInteractionSource() }
    val isDragging by sliderInteractionSource.collectIsDraggedAsState()
    
    val effectiveHungerFactor = hungerFactor
    
    val checkedItems = remember(planPartnerId) { mutableStateOf(setOf<String>()) }
    
    val todayKey = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    val todayEntries = planEntries.filter { it.day == todayKey }
    val todayRecipes = todayEntries.mapNotNull { entry -> allRecipes.find { it.url == entry.recipeUrl } }

    val allItems = remember(todayRecipes, targetServings, effectiveHungerFactor) {
        val gson = Gson()
        val typeToken = object : TypeToken<List<String>>() {}.type
        // Collect (scaledText, recipeTitle) pairs from all selected recipes
        val pairs = mutableListOf<Pair<String, String>>()
        todayRecipes.forEach { recipe ->
            val ingredients: List<String> = try {
                gson.fromJson(recipe.ingredients, typeToken) ?: emptyList()
            } catch (_: Exception) { emptyList() }
            ingredients.forEach { rawIng ->
                val scaled = scaleIngredient(rawIng, recipe.servings, targetServings, effectiveHungerFactor)
                pairs.add(scaled to recipe.title)
            }
        }
        com.zephron.app.ui.IngredientAggregator.aggregate(pairs)
    }

    val unchecked = allItems.filter { !checkedItems.value.contains(it.key) }
    val checked = allItems.filter { checkedItems.value.contains(it.key) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (planPartnerId == null) {
            item {
                Box(modifier = Modifier.fillParentMaxHeight(0.7f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Bitte wähle zuerst einen Partner im Wochenplan aus.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@LazyColumn
        }

        // ── Recipe selector ─────────────────────────────────────────────────────
        if (todayRecipes.isNotEmpty()) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        // Collapsible header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { recipesExpanded = !recipesExpanded }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Checklist, null, tint = accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Rezepte für heute", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (recipesExpanded) {
                                TextButton(
                                    onClick = {
                                        selectedIds = if (selectedIds.size == todayRecipes.size) emptySet()
                                                   else todayRecipes.map { it.id.toString() }.toSet()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        if (selectedIds.size == todayRecipes.size) "Alle abwählen" else "Alle wählen",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accent
                                    )
                                }
                            }
                            val chevronRotation by animateFloatAsState(
                                targetValue = if (recipesExpanded) 0f else -90f,
                                label = "recipes_chevron"
                            )
                            Icon(
                                Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp).rotate(chevronRotation)
                            )
                        }
                        AnimatedVisibility(
                            visible = recipesExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier.padding(bottom = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                todayRecipes.forEach { recipe ->
                                    val isOn = recipe.id.toString() in selectedIds
                                    ListItem(
                                        headlineContent = {
                                            Text(
                                                recipe.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isOn) MaterialTheme.colorScheme.onSurface
                                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        leadingContent = {
                                            ExpressiveCheckbox(
                                                checked = isOn,
                                                onCheckedChange = {
                                                    val rid = recipe.id.toString()
                                                    selectedIds = if (isOn) selectedIds - rid else selectedIds + rid
                                                    if (!isOn) {
                                                        checkedItems.value = emptySet() // reset when recipe selection changes
                                                    }
                                                },
                                                color = accent
                                            )
                                        },
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                val rid = recipe.id.toString()
                                                selectedIds = if (isOn) selectedIds - rid else selectedIds + rid
                                                if (!isOn) {
                                                    checkedItems.value = emptySet() // reset when recipe selection changes
                                                }
                                            },
                                        colors = ListItemDefaults.colors(
                                            containerColor = if (isOn) accent.copy(alpha = 0.08f)
                                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Portionen + Hunger ───────────────────────────────────────────────────
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    // Collapsible header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { portionenExpanded = !portionenExpanded }
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Groups, null, tint = secondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Portionen & Appetit", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        val chevronRotation by animateFloatAsState(
                            targetValue = if (portionenExpanded) 0f else -90f,
                            label = "portionen_chevron"
                        )
                        Icon(
                            Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp).rotate(chevronRotation)
                        )
                    }
                    AnimatedVisibility(
                        visible = portionenExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Groups, null, tint = secondary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Portionen", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = { if (targetServings > 1) targetServings-- }) { Icon(Icons.Filled.Remove, null, tint = secondary) }
                                Text("$targetServings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
                                IconButton(onClick = { targetServings++ }) { Icon(Icons.Filled.Add, null, tint = secondary) }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val hungerScale by animateFloatAsState(targetValue = 0.8f + (hungerFactor - 0.7f), animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "hunger")
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Fastfood, null, tint = secondary, modifier = Modifier.size(20.dp).scale(hungerScale))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Appetit", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    val hungerLabel = when {
                                        hungerFactor <= 0.75f -> "Wenig"
                                        hungerFactor <= 0.85f -> "Klein"
                                        hungerFactor <= 0.95f -> "Mittel"
                                        hungerFactor <= 1.05f -> "Normal"
                                        hungerFactor <= 1.15f -> "Viel"
                                        hungerFactor <= 1.25f -> "Groß"
                                        else -> "Bärenhunger"
                                    }
                                    Text(hungerLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = secondary)
                                }
                                Slider(
                                    value = hungerFactor,
                                    onValueChange = { hungerFactor = (kotlin.math.round(it * 10) / 10f) },
                                    valueRange = 0.7f..1.3f, steps = 5,
                                    interactionSource = sliderInteractionSource,
                                    colors = SliderDefaults.colors(thumbColor = secondary, activeTrackColor = secondary, inactiveTrackColor = secondary.copy(alpha = 0.24f)),
                                    modifier = Modifier.graphicsLayer {
                                        val s = if (isDragging) 1.05f else 1f
                                        scaleX = s
                                        scaleY = s
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Shopping List Header ──
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Filled.ShoppingCart, null, tint = secondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Einkaufsliste für heute", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        if (todayRecipes.isEmpty() || selectedIds.isEmpty()) {
            item {
                Text(
                    text = if (todayRecipes.isEmpty()) "Keine Rezepte für heute geplant." else "Wähle Rezepte aus, um Zutaten zu sehen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            // selectedTitles: titles of selected recipes for filtering
            val selectedTitles = todayRecipes
                .filter { it.id.toString() in selectedIds }
                .map { it.title }.toSet()

            // ── Unchecked Items ──
            items(
                unchecked.filter { item -> item.sources.any { it in selectedTitles } },
                key = { it.key }
            ) { item ->
                ShoppingRow(
                    item = item,
                    isChecked = false,
                    isSplashy = isDragging,
                    splashFactor = effectiveHungerFactor,
                    secondary = secondary,
                    modifier = Modifier.animateItem(),
                    onToggle = { checkedItems.value = checkedItems.value + item.key }
                )
            }

            // ── Checked Items Section ──
            val filteredChecked = checked.filter { item -> item.sources.any { it in selectedTitles } }
            if (filteredChecked.isNotEmpty()) {
                item(key = "divider_checked") {
                    Column(modifier = Modifier.animateItem()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Abgehakt",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                items(filteredChecked, key = { it.key }) { item ->
                    ShoppingRow(
                        item = item,
                        isChecked = true,
                        isSplashy = isDragging,
                        splashFactor = effectiveHungerFactor,
                        secondary = secondary,
                        modifier = Modifier.animateItem(),
                        onToggle = { checkedItems.value = checkedItems.value - item.key }
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(100.dp)) }
    }
}

// ShoppingItem is now IngredientAggregator.AggregatedIngredient — no separate class needed.

@Composable
private fun ShoppingRow(
    item: com.zephron.app.ui.IngredientAggregator.AggregatedIngredient,
    isChecked: Boolean,
    isSplashy: Boolean,
    splashFactor: Float,
    secondary: Color,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit
) {
    val animatedElevation by animateDpAsState(
        targetValue = if (isSplashy && !isChecked) (4.dp + (splashFactor * 4).dp) else if (isChecked) 0.dp else 1.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "elevation"
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (isSplashy && !isChecked) 1.02f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "card_scale"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (isChecked) 1.1f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "check_scale"
    )
    val icon = remember<ImageVector>(item.nameText) { getExpressiveIcon(item.nameText) }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = animatedScale; scaleY = animatedScale },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isChecked) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = animatedElevation)
    ) {
        ListItem(
            headlineContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Amount badge (only when parsed)
                    if (item.amountText.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isChecked) secondary.copy(alpha = 0.05f) else secondary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = item.amountText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isChecked) secondary.copy(alpha = 0.4f) else secondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = item.nameText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isChecked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (isChecked) TextDecoration.LineThrough else null
                    )
                }
            },
            supportingContent = if (!isChecked) ({
                val sourcesText = item.sources.joinToString(" · ")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (item.isAggregated) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Filled.MergeType,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = secondary.copy(alpha = 0.6f)
                        )
                    }
                    Text(
                        text = sourcesText,
                        style = MaterialTheme.typography.labelSmall,
                        color = secondary.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }) else null,
            leadingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ExpressiveCheckbox(
                        checked = isChecked,
                        onCheckedChange = { onToggle() },
                        color = secondary,
                        modifier = Modifier.scale(checkScale)
                    )
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isChecked) secondary.copy(alpha = 0.3f) else secondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            modifier = Modifier.clickable { onToggle() },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

private fun getExpressiveIcon(text: String): ImageVector {
    val t = text.lowercase()
    return when {
        t.contains("milch") || t.contains("sahne") || t.contains("käse") || t.contains("quark") || t.contains("joghurt") || t.contains("butter") -> Icons.Filled.Kitchen
        t.contains("brot") || t.contains("brötchen") || t.contains("mehl") || t.contains("nudeln") || t.contains("teig") || t.contains("reis") -> Icons.Filled.BakeryDining
        t.contains("apfel") || t.contains("birne") || t.contains("banane") || t.contains("gemüse") || t.contains("salat") || t.contains("tomate") || t.contains("zwiebel") || t.contains("kartoffel") -> Icons.Filled.Spa
        t.contains("fleisch") || t.contains("hähnchen") || t.contains("rind") || t.contains("schwein") || t.contains("wurst") || t.contains("steak") -> Icons.Filled.LunchDining
        t.contains("wasser") || t.contains("saft") || t.contains("wein") || t.contains("bier") || t.contains("cola") || t.contains("limo") -> Icons.Filled.LocalDrink
        t.contains("ei ") || t.contains(" eier") -> Icons.Filled.Egg
        t.contains("gewürz") || t.contains("salz") || t.contains("pfeffer") || t.contains("kräuter") || t.contains("basilikum") || t.contains("öl") || t.contains("essig") -> Icons.Filled.Restaurant
        else -> Icons.Filled.ShoppingCart
    }
}

/**
 * Basic heuristic to scale quantities in ingredient strings.
 * Example: "200g Mehl" -> "400g Mehl" (if servings double)
 */
fun scaleIngredient(raw: String, originalServings: Int, targetServings: Int, hungerFactor: Float): String {
    val factor = (targetServings.toFloat() / (if (originalServings > 0) originalServings.toFloat() else 1f)) * hungerFactor
    
    // Regex to find leading numbers (integer or decimal)
    val regex = Regex("^(\\d+[.,]?\\d*)(\\s*.*)$")
    val match = regex.find(raw.trim())
    
    return if (match != null) {
        val numberPart = match.groupValues[1].replace(",", ".")
        val rest = match.groupValues[2]
        
        val value = numberPart.toDoubleOrNull()
        if (value != null) {
            val newValue = value * factor
            val formattedValue = if (newValue % 1.0 == 0.0) newValue.toInt().toString() else "%.1f".format(newValue)
            "$formattedValue$rest"
        } else {
            raw
        }
    } else {
        raw
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanPartnerDropdown(
    friends: List<String>,
    friendNames: Map<String, String>,
    friendNicknames: Map<String, String>,
    selectedId: String?,
    accent: Color,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = selectedId?.let { friendNicknames[it] ?: friendNames[it] ?: it }

    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(22.dp),
            color = accent.copy(alpha = 0.14f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PartnerAvatar(label = selectedLabel ?: "Solo", accent = accent)
                Text(
                    text = selectedLabel ?: "Solo",
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(Icons.Filled.ArrowDropDown, null, tint = accent)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            // Solo option (always first)
            DropdownMenuItem(
                text = {
                    Text(
                        "Solo",
                        fontWeight = if (selectedId == null) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedId == null) accent else MaterialTheme.colorScheme.onSurface
                    )
                },
                onClick = { onSelect(null); expanded = false },
                leadingIcon = {
                    Icon(Icons.Filled.Person, null,
                        tint = if (selectedId == null) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (selectedId == null) Icon(Icons.Filled.CheckCircle, null, tint = accent, modifier = Modifier.size(20.dp))
                }
            )
            if (friends.isNotEmpty()) {
                androidx.compose.material3.HorizontalDivider()
                friends.forEach { fid ->
                    val label = friendNicknames[fid] ?: friendNames[fid] ?: fid
                    val isSelected = fid == selectedId
                    DropdownMenuItem(
                        text = {
                            Text(
                                label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = { onSelect(fid); expanded = false },
                        leadingIcon = { PartnerAvatar(label = label, accent = accent) },
                        trailingIcon = {
                            if (isSelected) Icon(Icons.Filled.CheckCircle, null, tint = accent, modifier = Modifier.size(20.dp))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCard(
    label: String,
    icon: ImageVector,
    isExpanded: Boolean,
    entries: List<PlanEntry>,
    accent: Color,
    secondary: Color,
    onToggle: () -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onEntryClick: (PlanEntry) -> Unit
) {
    val expressiveShape = RoundedCornerShape(
        topStart = 28.dp, 
        topEnd = 12.dp, 
        bottomStart = 12.dp, 
        bottomEnd = 28.dp
    )

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
        shape = expressiveShape,
        onClick = onToggle
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = accent.copy(alpha = 0.7f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                val rotation by animateFloatAsState(
                    targetValue = if (isExpanded) 180f else 0f,
                    label = "chevron_rotation"
                )
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) "Einklappen" else "Ausklappen",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.rotate(rotation)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(150)) + expandVertically(animationSpec = tween(200)),
                exit = fadeOut(tween(150)) + shrinkVertically(animationSpec = tween(200))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        Triple("BREAKFAST", "Frühstück", Icons.Filled.Coffee),
                        Triple("LUNCH", "Mittagessen", Icons.Filled.LunchDining),
                        Triple("DINNER", "Abendessen", Icons.Filled.DinnerDining)
                    ).forEach { (mealType, mealLabel, mealIcon) ->
                        val mealEntries = entries.filter { it.mealType == mealType }
                        
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = mealIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = secondary.copy(alpha = 0.6f)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = mealLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    modifier = Modifier.weight(1f)
                                )
                                if (mealEntries.isEmpty()) {
                                    IconButton(onClick = { onAdd(mealType) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Filled.Add, contentDescription = null, tint = secondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            if (mealEntries.isEmpty()) {
                                Text(
                                    text = "Nichts geplant",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.padding(start = 20.dp, bottom = 4.dp)
                                )
                            } else {
                                mealEntries.forEach { entry ->
                                    key(entry.id) {
                                        val dismissState = rememberSwipeToDismissBoxState(
                                            confirmValueChange = { value ->
                                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                                    onRemove(entry.id); true
                                                } else false
                                            },
                                            positionalThreshold = { it * 0.4f }
                                        )
                                        SwipeToDismissBox(
                                            state = dismissState,
                                            enableDismissFromStartToEnd = false,
                                            backgroundContent = {
                                                val color by animateColorAsState(
                                                    if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart)
                                                        MaterialTheme.colorScheme.errorContainer
                                                    else Color.Transparent,
                                                    label = "swipe_bg"
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(start = 20.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(color),
                                                    contentAlignment = Alignment.CenterEnd
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Delete,
                                                        contentDescription = "Löschen",
                                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                                        modifier = Modifier.padding(end = 16.dp)
                                                    )
                                                }
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 20.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(MaterialTheme.colorScheme.surface)
                                                    .clickable { onEntryClick(entry) }
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                AsyncImage(
                                                    model = entry.thumbnailUrl,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                )
                                                Spacer(Modifier.width(10.dp))
                                                Text(
                                                    text = entry.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Icon(
                                                    Icons.Filled.ChevronRight,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                                    modifier = Modifier.size(20.dp).padding(end = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (mealType != "DINNER") {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipePickerContent(
    recipes: List<Recipe>,
    currentUserId: String = "",
    accent: Color,
    onPick: (Recipe) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    val secondary = LocalAppColors.current.secondary

    val filtered = remember(query, recipes, showAll) {
        val base = if (showAll || currentUserId.isEmpty()) recipes
                   else recipes.filter { it.ownerId == currentUserId || it.ownerId.isEmpty() }
        if (query.isBlank()) base
        else base.filter { it.title.contains(query, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 580.dp)) {
        Text(
            text = stringResource(R.string.plan_picker_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
        )

        // ── Owner toggle ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .fillMaxWidth()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            listOf("Meine" to false, "Alle" to true).forEach { (label, all) ->
                val selected = showAll == all
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (selected) secondary else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showAll = all }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = {},
                    expanded = false,
                    onExpandedChange = {},
                    placeholder = { Text(stringResource(R.string.plan_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = null)
                            }
                        }
                    }
                )
            },
            expanded = false,
            onExpandedChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            windowInsets = WindowInsets(0),
        ) {}
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered) { recipe ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = recipe.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingContent = {
                        AsyncImage(
                            model = recipe.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    },
                    trailingContent = {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = accent)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPick(recipe) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                )
            }
        }
    }
}
