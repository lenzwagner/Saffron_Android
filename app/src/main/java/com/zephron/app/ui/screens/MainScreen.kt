package com.zephron.app.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.zephron.app.R
import com.zephron.app.ui.PartnerAvatar
import com.zephron.app.ui.TAG_GROUPS
import com.zephron.app.ui.TAG_ICONS
import com.zephron.app.data.Recipe
import com.zephron.app.viewmodel.BatchImportState
import com.zephron.app.viewmodel.ImportViewModel
import com.zephron.app.viewmodel.NotificationViewModel
import com.zephron.app.viewmodel.RecipeViewModel
import com.zephron.app.viewmodel.SearchMode
import com.zephron.app.ui.theme.LocalAppColors
import com.zephron.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

data class NavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    recipeViewModel: RecipeViewModel = viewModel(),
    importViewModel: ImportViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    assistantViewModel: com.zephron.app.viewmodel.AssistantViewModel = viewModel(),
    notificationViewModel: NotificationViewModel = viewModel(),
    onLanguageChange: () -> Unit = {}
) {
    val orange = LocalAppColors.current.accent
    val secondary = LocalAppColors.current.secondary
    val navItems = listOf(
        NavItem(stringResource(R.string.tab_today), Icons.Filled.WbSunny, Icons.Outlined.WbSunny),
        NavItem(stringResource(R.string.tab_tinder), Icons.Filled.Favorite, Icons.Outlined.Favorite),
        NavItem(stringResource(R.string.tab_recipes), Icons.Filled.Apps, Icons.Outlined.Apps),
        NavItem(stringResource(R.string.tab_plan), Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
        NavItem(stringResource(R.string.tab_import), Icons.Filled.AddLink, Icons.Outlined.AddLink)
    )

    val pagerState = rememberPagerState { navItems.size }
    val coroutineScope = rememberCoroutineScope()
    val selectedTab by remember { derivedStateOf { pagerState.currentPage } }

    var selectedRecipe by remember { mutableStateOf<Recipe?>(null) }
    // true = opened from own gallery; false = opened from Crave (partner recipe)
    var selectedRecipeIsPartner by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAssistant by remember { mutableStateOf(false) }
    var showAssistantBubble by remember { mutableStateOf(false) }
    var assistantBubblePosition by remember { mutableStateOf(Offset.Zero) }
    
    var pagerScrollEnabled by remember { mutableStateOf(true) }
    val assistantMessages by assistantViewModel.messages.collectAsState()
    val assistantLoading by assistantViewModel.isLoading.collectAsState()
    val assistantRemaining by assistantViewModel.remaining.collectAsState()

    val recipes by recipeViewModel.recipes.collectAsState()
    val allRecipes by recipeViewModel.allRecipes.collectAsState()

    // ── Widget deep-link: open recipe tapped from home screen widget ──────────
    val pendingOpenRecipeId by recipeViewModel.pendingOpenRecipeId.collectAsState()
    LaunchedEffect(pendingOpenRecipeId, allRecipes) {
        val id = pendingOpenRecipeId ?: return@LaunchedEffect
        val recipe = allRecipes.find { it.id == id }
        if (recipe != null) {
            selectedRecipe = recipe
            recipeViewModel.consumePendingOpenRecipe()
        }
    }
    val searchQuery by recipeViewModel.searchQuery.collectAsState()
    val selectedCategory by recipeViewModel.selectedCategory.collectAsState()
    val selectedTags by recipeViewModel.selectedTags.collectAsState()
    val searchMode by recipeViewModel.searchMode.collectAsState()
    val maxCookTime by recipeViewModel.maxCookTime.collectAsState()
    val minRating by recipeViewModel.minRating.collectAsState()
    val showFavoritesOnly by recipeViewModel.showFavoritesOnly.collectAsState()
    val filterOwnerId by recipeViewModel.filterOwnerId.collectAsState()
    val importUrl by importViewModel.url.collectAsState()
    val importState by importViewModel.importState.collectAsState()
    val batchState by importViewModel.batchState.collectAsState()
    val batchInputText by importViewModel.batchInputText.collectAsState()
    val isBatchTab by importViewModel.isBatchTab.collectAsState()
    val isManualTab by importViewModel.isManualTab.collectAsState()
    val pendingSharedUrl by importViewModel.pendingSharedUrl.collectAsState()
    val userName by settingsViewModel.userName.collectAsState()
    val friends by settingsViewModel.friends.collectAsState()
    val activeFriends by settingsViewModel.activeFriends.collectAsState()
    val pendingRequests by settingsViewModel.pendingRequests.collectAsState()
    val friendNames by settingsViewModel.friendNames.collectAsState()
    val friendNicknames by settingsViewModel.friendNicknames.collectAsState()
    val friendPhotoUrls by settingsViewModel.friendPhotoUrls.collectAsState()
    val cravePartnerId by settingsViewModel.cravePartnerId.collectAsState()
    val craveSyncing by settingsViewModel.craveSyncing.collectAsState()
    val swipedUrls by settingsViewModel.swipedUrls.collectAsState()
    val planPartnerId by settingsViewModel.planPartnerId.collectAsState()
    val planEntries by settingsViewModel.planEntries.collectAsState()
    val context = LocalContext.current

    val notificationData by notificationViewModel.currentNotification

    LaunchedEffect(Unit) {
        settingsViewModel.eventFlow.collect { event ->
            when (event) {
                is SettingsViewModel.SettingsEvent.ShowNotification -> {
                    notificationViewModel.showNotification(event.message, event.type)
                }
            }
        }
    }

    var selectedTinderTab by remember { mutableStateOf(0) } // 0 = Swiping, 1 = History

    // When a URL is shared from another app, jump to the import tab and fill the field
    LaunchedEffect(pendingSharedUrl) {
        val url = pendingSharedUrl ?: return@LaunchedEffect
        importViewModel.setBatchTab(false)   // ensure single-import tab is selected
        importViewModel.setUrl(url)
        importViewModel.consumePendingSharedUrl()
        pagerState.animateScrollToPage(4) // Import is the last tab
    }

    LaunchedEffect(allRecipes) {
        selectedRecipe?.let { current ->
            val updated = allRecipes.find { it.id == current.id }
            if (updated != null && updated != current) selectedRecipe = updated
        }
        // Give the cooking assistant access to the user's recipes
        assistantViewModel.setRecipes(allRecipes)
    }

    BackHandler(enabled = selectedRecipe != null || showSettings) {
        if (showSettings) showSettings = false
        else selectedRecipe = null
    }

    val backgroundBlur by animateDpAsState(
        targetValue = if (showAssistant) 20.dp else 0.dp,
        animationSpec = tween(500),
        label = "assistant_blur"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Main App Content
        AnimatedVisibility(
            visible = !showSettings && selectedRecipe == null,
            enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.95f, animationSpec = tween(300)),
            exit = fadeOut(tween(250)) + scaleOut(targetScale = 0.95f, animationSpec = tween(250))
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0),
                bottomBar = {
                    // Floating Aura Navigation - Themed Version
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                            .navigationBarsPadding(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Surface(
                            modifier = Modifier
                                .width(340.dp)
                                .height(76.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                            shape = CircleShape,
                            tonalElevation = 0.dp,
                            shadowElevation = 16.dp
                        ) {
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val density = LocalDensity.current
                                val tabW = constraints.maxWidth.toFloat() / navItems.size
                                val padPx = with(density) { 6.dp.toPx() }
                                val pillH = with(density) { 46.dp.toPx() }

                                // Leading edge: faster spring → arrives first when moving
                                val pillLeft by animateFloatAsState(
                                    targetValue = selectedTab * tabW + padPx,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "pill_left"
                                )
                                // Trailing edge: slower spring → lags behind, creating stretch
                                val pillRight by animateFloatAsState(
                                    targetValue = (selectedTab + 1) * tabW - padPx,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    label = "pill_right"
                                )
                                val pillColor = secondary.copy(alpha = 0.14f)

                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val top = (size.height - pillH) / 2f
                                    val w = (pillRight - pillLeft).coerceAtLeast(0f)
                                    drawRoundRect(
                                        color = pillColor,
                                        topLeft = Offset(pillLeft, top),
                                        size = Size(w, pillH),
                                        cornerRadius = CornerRadius(pillH / 2)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    navItems.forEachIndexed { index, item ->
                                        val isSelected = selectedTab == index

                                        val iconScale by animateFloatAsState(
                                            targetValue = if (isSelected) 1.2f else 1.0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            ),
                                            label = "icon_scale"
                                        )

                                        val rotationAnim = remember { Animatable(0f) }
                                        LaunchedEffect(isSelected) {
                                            if (isSelected) {
                                                rotationAnim.animateTo(10f, tween(80, easing = FastOutSlowInEasing))
                                                rotationAnim.animateTo(-10f, tween(80, easing = FastOutSlowInEasing))
                                                rotationAnim.animateTo(0f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMedium))
                                            } else {
                                                rotationAnim.snapTo(0f)
                                            }
                                        }

                                        val tint by animateColorAsState(
                                            targetValue = if (isSelected) secondary
                                                          else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            ),
                                            label = "icon_tint"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                                    contentDescription = item.label,
                                                    tint = tint,
                                                    modifier = Modifier
                                                        .size(26.dp)
                                                        .graphicsLayer {
                                                            scaleX = iconScale
                                                            scaleY = iconScale
                                                            rotationZ = rotationAnim.value
                                                        }
                                                )
                                                AnimatedVisibility(
                                                    visible = isSelected,
                                                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                                                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                                                ) {
                                                    Text(
                                                        text = item.label,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = secondary,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .padding(top = paddingValues.calculateTopPadding())
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .blur(backgroundBlur)
                        .background(
                            Brush.verticalGradient(
                                listOf(LocalAppColors.current.gradientTop.copy(alpha = 0.38f), Color.Transparent),
                                endY = 900f
                            )
                        )
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = pagerScrollEnabled
                    ) { page ->
                        when (page) {
                            0 -> RecipeOfDayScreen(
                                recipes = allRecipes.filter {
                                    val me = settingsViewModel.firebaseUser.value?.uid ?: ""
                                    it.ownerId.isBlank() || it.ownerId == me
                                },
                                userName = userName,
                                currentUserId = settingsViewModel.firebaseUser.value?.uid ?: "",
                                friendNames = settingsViewModel.friendNames.collectAsState().value,
                                friendNicknames = settingsViewModel.friendNicknames.collectAsState().value,
                                onRecipeClick = { r -> selectedRecipe = r; selectedRecipeIsPartner = false },
                                onOpenSettings = { showSettings = true },
                                onToggleAssistantBubble = { showAssistantBubble = !showAssistantBubble },
                                isAssistantVisible = showAssistantBubble,
                                onScrollLock = { pagerScrollEnabled = !it }
                            )
                            1 -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // ── Header (matches the other pages) ──────────────────
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .windowInsetsPadding(WindowInsets.statusBars)
                                            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.tab_tinder),
                                            style = MaterialTheme.typography.displaySmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f),
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        IconButton(onClick = { showAssistantBubble = !showAssistantBubble }) {
                                            Icon(
                                                imageVector = Icons.Filled.AutoAwesome,
                                                contentDescription = "Assistant",
                                                tint = if (showAssistantBubble) secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { showSettings = true }) {
                                            Icon(
                                                imageVector = Icons.Filled.Settings,
                                                contentDescription = stringResource(R.string.settings_title),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // ── Mode toggle (Swipe / Matches / Friends) - Expressive Design ──
                                    val secondaryColor = LocalAppColors.current.secondary
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
                                            // Sliding indicator
                                            val indicatorOffset by animateFloatAsState(
                                                targetValue = selectedTinderTab.toFloat(),
                                                animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
                                                label = "tab_indicator"
                                            )
                                            
                                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                                val indicatorWidth = maxWidth / 3
                                                Box(
                                                    modifier = Modifier
                                                        .offset(x = indicatorWidth * indicatorOffset)
                                                        .width(indicatorWidth)
                                                        .fillMaxHeight()
                                                        .padding(4.dp)
                                                        .background(secondaryColor, CircleShape)
                                                )
                                            }

                                            Row(modifier = Modifier.fillMaxSize()) {
                                                listOf(
                                                    stringResource(R.string.crave_tab_swipe),
                                                    stringResource(R.string.crave_tab_matches),
                                                    stringResource(R.string.crave_tab_friends)
                                                ).forEachIndexed { index, label ->
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .fillMaxHeight()
                                                            .clickable(
                                                                interactionSource = remember { MutableInteractionSource() },
                                                                indication = null
                                                            ) { selectedTinderTab = index },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = label,
                                                            style = MaterialTheme.typography.labelLarge,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (selectedTinderTab == index) Color.White 
                                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // ── Content ───────────────────────────────────────────
                                    val myUid = settingsViewModel.firebaseUser.value?.uid ?: ""
                                    val today = java.time.LocalDate.now().toString()
                                    val pairId = if (cravePartnerId.isNullOrBlank()) myUid
                                                 else listOf(myUid, cravePartnerId!!).sorted().joinToString("_")
                                    val craveEligible = remember(allRecipes, cravePartnerId, today) {
                                        val filtered = allRecipes.filter {
                                            it.ownerId == myUid || (!cravePartnerId.isNullOrBlank() && it.ownerId == cravePartnerId)
                                        }
                                        val seed = (today + pairId).hashCode().toLong()
                                        filtered.shuffled(java.util.Random(seed))
                                    }
                                    val allKnownTags = remember {
                                        TAG_GROUPS.flatMap { it.second }
                                    }
                                    val availableTags = remember(craveEligible) {
                                        val present = craveEligible.flatMap { recipe ->
                                            try {
                                                val gson = com.google.gson.Gson()
                                                val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                                                gson.fromJson<List<String>>(recipe.tags, type) ?: emptyList()
                                            } catch (_: Exception) { emptyList() }
                                        }.toSet()
                                        allKnownTags.filter { it in present }
                                    }
                                    var selectedCraveTags by remember { mutableStateOf<Set<String>>(emptySet()) }
                                    var showCraveFilterSheet by remember { mutableStateOf(false) }
                                    val craveSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                                    LaunchedEffect(availableTags) {
                                        selectedCraveTags = selectedCraveTags.filter { it in availableTags }.toSet()
                                    }

                                    // ── Partner picker + sync (only on the Swipe tab) ─────
                                    if (selectedTinderTab == 0) {
                                        CravePartnerBar(
                                            friends = friends,
                                            friendNames = friendNames,
                                            friendNicknames = friendNicknames,
                                            friendPhotoUrls = friendPhotoUrls,
                                            selectedId = cravePartnerId,
                                            syncing = craveSyncing,
                                            accent = orange,
                                            onSelect = { settingsViewModel.setCravePartner(it) },
                                            onSync = { settingsViewModel.syncCravePartner() },
                                            onFilterClick = { showCraveFilterSheet = true },
                                            filterActive = selectedCraveTags.isNotEmpty()
                                        )
                                    }

                                    // ── Tag filter sheet ──────────────────────────────────
                                    if (showCraveFilterSheet) {
                                        val sheetHeight = (LocalConfiguration.current.screenHeightDp * 0.70f).dp
                                        val dismiss = { coroutineScope.launch { craveSheetState.hide() }.invokeOnCompletion { showCraveFilterSheet = false } }
                                        ModalBottomSheet(
                                            onDismissRequest = { showCraveFilterSheet = false },
                                            sheetState = craveSheetState,
                                            containerColor = MaterialTheme.colorScheme.surface
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth().height(sheetHeight)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth()
                                                        .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Filter",
                                                        style = MaterialTheme.typography.titleLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    if (selectedCraveTags.isNotEmpty()) {
                                                        TextButton(onClick = { selectedCraveTags = emptySet() }) {
                                                            Text("Zurücksetzen", color = LocalAppColors.current.secondary)
                                                        }
                                                    }
                                                    Button(
                                                        onClick = { dismiss() },
                                                        shape = RoundedCornerShape(12.dp),
                                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = orange)
                                                    ) {
                                                        Text(
                                                            text = if (selectedCraveTags.isEmpty()) "Fertig" else "Fertig (${selectedCraveTags.size})",
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontSize = 14.sp
                                                        )
                                                    }
                                                }
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                                val filterScroll = rememberScrollState()
                                                Column(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .verticalScroll(filterScroll)
                                                        .padding(horizontal = 20.dp, vertical = 12.dp)
                                                        .windowInsetsPadding(WindowInsets.navigationBars)
                                                ) {
                                                    TAG_GROUPS.forEach { (groupName, tags) ->
                                                        Text(
                                                            text = groupName.uppercase(),
                                                            style = MaterialTheme.typography.labelLarge,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontWeight = FontWeight.SemiBold,
                                                            modifier = Modifier.padding(bottom = 8.dp)
                                                        )
                                                        FlowRow(
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                                            modifier = Modifier.padding(bottom = 16.dp)
                                                        ) {
                                                            tags.forEach { tag ->
                                                                val icon = TAG_ICONS[tag]
                                                                FilterChip(
                                                                    selected = tag in selectedCraveTags,
                                                                    onClick = {
                                                                        selectedCraveTags = if (tag in selectedCraveTags)
                                                                            selectedCraveTags - tag else selectedCraveTags + tag
                                                                    },
                                                                    label = { Text(tag) },
                                                                    leadingIcon = icon?.let { { Icon(it, null, Modifier.size(16.dp)) } },
                                                                    colors = FilterChipDefaults.filterChipColors(
                                                                        selectedContainerColor = LocalAppColors.current.secondary,
                                                                        selectedLabelColor = Color.White,
                                                                        selectedLeadingIconColor = Color.White
                                                                    )
                                                                )
                                                            }
                                                        }
                                                        HorizontalDivider(
                                                            modifier = Modifier.padding(bottom = 16.dp),
                                                            color = MaterialTheme.colorScheme.outlineVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    val craveFiltered = if (selectedCraveTags.isNotEmpty()) craveEligible.filter { recipe ->
                                        try {
                                            val gson = com.google.gson.Gson()
                                            val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                                            val recipeTags = gson.fromJson<List<String>>(recipe.tags, type) ?: emptyList()
                                            selectedCraveTags.all { it in recipeTags }
                                        } catch (_: Exception) { false }
                                    } else craveEligible
                                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                        when (selectedTinderTab) {
                                            0 -> RecipeTinderScreen(
                                                recipes = craveFiltered.filter { it.url !in swipedUrls },
                                                eligibleEmpty = craveFiltered.isEmpty(),
                                                currentUserId = myUid,
                                                friendNames = friendNames,
                                                friendNicknames = friendNicknames,
                                                newMatchFlow = settingsViewModel.newMatchFlow,
                                                onRecipeClick = { r -> selectedRecipe = r; selectedRecipeIsPartner = !(r.ownerId == myUid || r.ownerId.isBlank()) },
                                                onSwipe = { recipe, liked -> settingsViewModel.registerSwipe(recipe, liked) },
                                                onUndo = { recipe -> settingsViewModel.unswipe(recipe) },
                                                onReset = { settingsViewModel.resetSwipes() }
                                            )
                                            1 -> MatchHistoryScreen(
                                                matches = settingsViewModel.matches.collectAsState().value,
                                                onRecipeClick = { url: String ->
                                                    allRecipes.find { it.url == url }?.let { r -> selectedRecipe = r; selectedRecipeIsPartner = !(r.ownerId == myUid || r.ownerId.isBlank()) }
                                                },
                                                onClearMatches = { partnerId ->
                                                    if (partnerId == null) settingsViewModel.clearMatches()
                                                    else settingsViewModel.clearMatchesForPartner(partnerId)
                                                },
                                                onDeleteMatch = { match ->
                                                    settingsViewModel.clearMatch(match)
                                                },
                                                onDeleteMatches = { matches ->
                                                    settingsViewModel.clearSelectedMatches(matches)
                                                },
                                                friends = friends,
                                                friendNames = friendNames,
                                                friendNicknames = friendNicknames,
                                                friendPhotoUrls = friendPhotoUrls
                                            )
                                            else -> Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .verticalScroll(rememberScrollState())
                                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                            ) {
                                                FriendsConnectionCard(
                                                    currentUserId = myUid,
                                                    friends = friends,
                                                    activeFriends = activeFriends,
                                                    friendNames = friendNames,
                                                    friendNicknames = friendNicknames,
                                                    friendPhotoUrls = friendPhotoUrls,
                                                    pendingRequests = pendingRequests,
                                                    onAddFriend = {
                                                        val sent = settingsViewModel.sendFriendRequest(it)
                                                        android.widget.Toast.makeText(
                                                            context,
                                                            context.getString(if (sent) R.string.crave_request_sent else R.string.crave_request_invalid),
                                                            android.widget.Toast.LENGTH_SHORT
                                                        ).show()
                                                    },
                                                    onRemoveFriend = { settingsViewModel.removeFriend(it) },
                                                    onToggleActive = { settingsViewModel.toggleFriendActive(it) },
                                                    onUpdateNickname = { id, nick -> settingsViewModel.setFriendNickname(id, nick) },
                                                    onAcceptRequest = { settingsViewModel.acceptFriendRequest(it) },
                                                    onDeclineRequest = { settingsViewModel.declineFriendRequest(it) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            2 -> RecipesScreen(
                                recipes = recipes,
                                searchQuery = searchQuery,
                                selectedCategory = selectedCategory,
                                selectedTags = selectedTags,
                                searchMode = searchMode,
                                maxCookTime = maxCookTime,
                                minRating = minRating,
                                showFavoritesOnly = showFavoritesOnly,
                                filterOwnerId = filterOwnerId,
                                friends = friends,
                                onToggleFavoritesOnly = { recipeViewModel.setShowFavoritesOnly(it) },
                                onFilterOwnerChange = { recipeViewModel.setFilterOwnerId(it) },
                                currentUserId = settingsViewModel.firebaseUser.value?.uid ?: "",
                                currentUserName = userName,
                                currentUserPhotoUrl = settingsViewModel.profilePictureUrl.collectAsState().value,
                                friendNames = settingsViewModel.friendNames.collectAsState().value,
                                friendNicknames = settingsViewModel.friendNicknames.collectAsState().value,
                                friendPhotoUrls = settingsViewModel.friendPhotoUrls.collectAsState().value,
                                onSearchQueryChange = { recipeViewModel.setSearchQuery(it) },
                                onCategorySelect = { recipeViewModel.setCategory(it) },
                                onTagToggle = { recipeViewModel.toggleTag(it) },
                                onClearTags = { recipeViewModel.clearTags() },
                                onSearchModeChange = { recipeViewModel.setSearchMode(it) },
                                onMaxCookTimeChange = { recipeViewModel.setMaxCookTime(it) },
                                onMinRatingChange = { recipeViewModel.setMinRating(it) },
                                onRecipeClick = { r -> selectedRecipe = r; selectedRecipeIsPartner = false },
                                onRecipeDelete = { recipeViewModel.deleteRecipe(it) },
                                onBulkDelete = { ids -> recipeViewModel.deleteRecipes(ids) },
                                onAdoptRecipe = { recipeViewModel.adoptRecipe(it) },
                                onOpenSettings = { showSettings = true },
                                onToggleAssistantBubble = { showAssistantBubble = !showAssistantBubble },
                                isAssistantVisible = showAssistantBubble,
                                onNavigateToImport = { coroutineScope.launch { pagerState.animateScrollToPage(4) } },
                                isRefreshing = false,
                                onRefresh = { /* recipes sync via Firestore listener */ }
                            )
                            4 -> ImportScreen(
                                url = importUrl,
                                importState = importState,
                                batchState = batchState,
                                batchText = batchInputText,
                                isBatchTab = isBatchTab,
                                isManualTab = isManualTab,
                                onUrlChange = { importViewModel.setUrl(it) },
                                onImport = { importViewModel.importRecipe() },
                                onSave = { importViewModel.saveRecipe() },
                                onTitleChange = { importViewModel.updateTitle(it) },
                                onTagsChange = { importViewModel.updateTags(it) },
                                onServingsChange = { importViewModel.updateServings(it) },
                                onReset = { importViewModel.reset() },
                                onOpenSettings = { showSettings = true },
                                onToggleAssistantBubble = { showAssistantBubble = !showAssistantBubble },
                                isAssistantVisible = showAssistantBubble,
                                onBatchTabChange = { importViewModel.setBatchTab(it) },
                                onManualTabChange = { importViewModel.setManualTab(it) },
                                onBatchTextChange = { importViewModel.setBatchInputText(it) },
                                onFilterSaved = { tags ->
                                    recipeViewModel.clearTags()
                                    tags.forEach { recipeViewModel.toggleTag(it) }
                                    importViewModel.reset()
                                    coroutineScope.launch { pagerState.scrollToPage(2) }
                                },
                                onExport = {
                                    val urls = allRecipes.mapNotNull { it.url.takeIf { u -> u.isNotBlank() } }
                                    if (urls.isNotEmpty()) {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, urls.joinToString("\n"))
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, null))
                                    }
                                },
                                onBatchImport = { urls, mode -> importViewModel.importBatch(urls, mode) },
                                onBatchReset = { importViewModel.resetBatch() },
                                onSelectThumbnail = { importViewModel.selectThumbnail(it) },
                                onSkipInQueue = { importViewModel.skipCurrentInQueue() },
                                onManualSave = { title, tags, notes -> importViewModel.saveManualRecipe(title, tags, notes) },
                                onRecipeClick = { r -> selectedRecipe = r; selectedRecipeIsPartner = false },
                                events = importViewModel.events
                            )
                            3 -> PlanScreen(
                                friends = friends,
                                friendNames = friendNames,
                                friendNicknames = friendNicknames,
                                planPartnerId = planPartnerId,
                                planEntries = planEntries,
                                recipes = allRecipes.filter {
                                    val myUid = settingsViewModel.firebaseUser.value?.uid ?: ""
                                    it.ownerId == myUid || (planPartnerId != null && it.ownerId == planPartnerId)
                                },
                                currentUserId = settingsViewModel.firebaseUser.value?.uid ?: "",
                                onSelectPartner = { settingsViewModel.setPlanPartner(it) },
                                onAddToPlan = { day, recipe, mealType -> settingsViewModel.addToPlan(day, recipe, mealType) },
                                onRemoveFromPlan = { settingsViewModel.removeFromPlan(it) },
                                onRecipeClick = { r ->
                                    val myUid = settingsViewModel.firebaseUser.value?.uid ?: ""
                                    selectedRecipe = r
                                    selectedRecipeIsPartner = r.ownerId.isNotBlank() && r.ownerId != myUid
                                },
                                onOpenSettings = { showSettings = true },
                                onToggleAssistantBubble = { showAssistantBubble = !showAssistantBubble },
                                isAssistantVisible = showAssistantBubble
                            )
                        }
                    }

                    // --- Soft Bottom Gradient behind the Bar ---
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                    )

                    // Floating, draggable cooking-assistant bubble (over all pages)
                    AnimatedVisibility(
                        visible = showAssistantBubble,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        MovableAssistantBubble(
                            onPositionChange = { assistantBubblePosition = it },
                            onOpen = { showAssistant = true }
                        )
                    }
                }
            }
        }

        // Settings Screen with Ultra-Fast Horizontal Slide
        AnimatedVisibility(
            visible = showSettings,
            enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(150, easing = LinearOutSlowInEasing)) + fadeIn(tween(100)),
            exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(150, easing = FastOutLinearInEasing)) + fadeOut(tween(100))
        ) {
            settingsViewModel.clearNewFriendBadge()
            SettingsScreen(
                settingsViewModel = settingsViewModel,
                onBack = { showSettings = false },
                onLanguageChange = onLanguageChange
            )
        }

        // Keep the detail screen recipe live — reflect DB updates immediately
        val liveSelectedRecipe = selectedRecipe?.let { sel -> allRecipes.find { it.id == sel.id } ?: sel }

        AnimatedVisibility(
            visible = selectedRecipe != null,
            modifier = Modifier.fillMaxSize(),
            enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(150, easing = LinearOutSlowInEasing)) + fadeIn(tween(100)),
            exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(150, easing = FastOutLinearInEasing)) + fadeOut(tween(100))
        ) {
            liveSelectedRecipe?.let { recipe ->
                RecipeDetailScreen(
                    recipe = recipe,
                    onBack = { selectedRecipe = null },
                    onDelete = { r ->
                        recipeViewModel.deleteRecipe(r)
                        selectedRecipe = null
                    },
                    onTagClick = { tag ->
                        recipeViewModel.setSearchQuery("")
                        recipeViewModel.clearTags()
                        recipeViewModel.toggleTag(tag)
                        selectedRecipe = null
                        coroutineScope.launch { pagerState.scrollToPage(2) }
                    },
                    onUpdateTags = { r, newTags ->
                        val isVeg = newTags.any { it.lowercase() in listOf("vegetarisch", "vegetarian", "vegan") }
                        recipeViewModel.updateRecipe(r.copy(tags = Gson().toJson(newTags), isVegetarian = isVeg))
                    },
                    onUpdateTitle = { r, newTitle ->
                        recipeViewModel.updateRecipe(r.copy(title = newTitle))
                    },
                    onUpdateCookTime = { r, minutes ->
                        recipeViewModel.updateRecipe(r.copy(cookingTimeMinutes = minutes))
                    },
                    onUpdateServings = { r, count ->
                        recipeViewModel.updateRecipe(r.copy(servings = count))
                    },
                    onUpdateIngredients = { r, newIngredients ->
                        recipeViewModel.updateRecipe(r.copy(ingredients = Gson().toJson(newIngredients)))
                    },
                    onUpdateSteps = { r, newSteps ->
                        recipeViewModel.updateRecipe(r.copy(steps = Gson().toJson(newSteps)))
                    },
                    onUpdateRating = { r, stars ->
                        recipeViewModel.updateRecipe(r.copy(rating = stars))
                    },
                    onUpdateIsVegetarian = { r, isVeg ->
                        recipeViewModel.updateRecipe(r.copy(isVegetarian = isVeg))
                    },
                    onUpdateThumbnail = { r, path ->
                        recipeViewModel.updateRecipe(r.copy(thumbnailUrl = path))
                    },
                    onToggleFavorite = { r -> recipeViewModel.toggleFavorite(r) },
                    isOwnRecipe = !selectedRecipeIsPartner,
                    alreadyOwned = selectedRecipeIsPartner && recipes.any { it.url == recipe.url },
                    onAdopt = if (selectedRecipeIsPartner && recipes.none { it.url == recipe.url }) {
                        { r -> recipeViewModel.adoptRecipe(r) }
                    } else null
                )
            }
        }

        // Assistant UI (Window)
        val density = androidx.compose.ui.platform.LocalDensity.current
        val config = LocalConfiguration.current
        val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
        val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }
        
        AnimatedVisibility(
            visible = showAssistant,
            enter = fadeIn(tween(250)) + 
                    scaleIn(
                        initialScale = 0.01f,
                        transformOrigin = TransformOrigin(
                            assistantBubblePosition.x / screenWidthPx,
                            assistantBubblePosition.y / screenHeightPx
                        ),
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
            exit = fadeOut(tween(200)) + 
                   scaleOut(
                       targetScale = 0.01f,
                       transformOrigin = TransformOrigin(
                           assistantBubblePosition.x / screenWidthPx,
                           assistantBubblePosition.y / screenHeightPx
                       ),
                       animationSpec = spring(
                           dampingRatio = Spring.DampingRatioNoBouncy,
                           stiffness = Spring.StiffnessMedium
                       )
                   )
        ) {
            // Ripple Splash Effect when appearing
            Box(contentAlignment = Alignment.Center) {
                // Background ripple "burst"
                var startRipple by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { startRipple = true }
                val rippleScale by animateFloatAsState(
                    targetValue = if (startRipple) 4f else 0.1f,
                    animationSpec = tween(800, easing = FastOutSlowInEasing),
                    label = "splash_ripple"
                )
                val rippleAlpha by animateFloatAsState(
                    targetValue = if (startRipple) 0f else 0.4f,
                    animationSpec = tween(800, easing = LinearEasing),
                    label = "splash_alpha"
                )

                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .graphicsLayer {
                            scaleX = rippleScale
                            scaleY = rippleScale
                            alpha = rippleAlpha
                            translationX = assistantBubblePosition.x - (screenWidthPx / 2)
                            translationY = assistantBubblePosition.y - (screenHeightPx / 2)
                        }
                        .background(orange.copy(alpha = 0.5f), CircleShape)
                )

                AssistantFloatingWindow(
                    messages = assistantMessages,
                    isLoading = assistantLoading,
                    remaining = assistantRemaining,
                    onSend = { assistantViewModel.sendMessage(it) },
                    onClear = { assistantViewModel.clearChat() },
                    onDismiss = { showAssistant = false },
                    resolveRecipe = { id -> assistantViewModel.recipeFor(id) },
                    onOpenRecipe = { recipe ->
                        showAssistant = false
                        selectedRecipe = recipe
                    }
                )
            }
        }
    }
}

/**
 * Crave header bar: pick the single friend you want to match with + a sync
 * button that pulls both recipe collections so the swipe decks line up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CravePartnerBar(
    friends: List<String>,
    friendNames: Map<String, String>,
    friendNicknames: Map<String, String>,
    friendPhotoUrls: Map<String, String> = emptyMap(),
    selectedId: String?,
    syncing: Boolean,
    accent: Color,
    onSelect: (String) -> Unit,
    onSync: () -> Unit,
    onFilterClick: () -> Unit,
    filterActive: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = selectedId?.let { friendNicknames[it] ?: friendNames[it] ?: it }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            // Expressive pill-shaped trigger with avatar + chevron
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
                    PartnerAvatar(label = selectedLabel, accent = accent,
                        photoUrl = selectedId?.let { friendPhotoUrls[it] })
                    Text(
                        text = selectedLabel ?: stringResource(R.string.crave_pick_friend),
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        color = if (selectedLabel != null) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = accent)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                if (friends.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.crave_no_friends), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { expanded = false }
                    )
                } else {
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
                            leadingIcon = { PartnerAvatar(label = label, accent = accent, photoUrl = friendPhotoUrls[fid]) },
                            trailingIcon = {
                                if (isSelected) Icon(Icons.Filled.CheckCircle, null, tint = accent, modifier = Modifier.size(20.dp))
                            }
                        )
                    }
                }
            }
        }

        // Cuisine filter toggle button
        FilledTonalIconButton(
            onClick = onFilterClick,
            shape = RoundedCornerShape(16.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (filterActive) accent.copy(alpha = 0.22f) else accent.copy(alpha = 0.14f),
                contentColor = accent
            )
        ) {
            Icon(Icons.Filled.Tune, contentDescription = "Küchen-Filter", modifier = Modifier.size(20.dp))
        }

        // Sync action — filled, rounded, expressive
        Button(
            onClick = onSync,
            enabled = !syncing && selectedId != null,
            shape = RoundedCornerShape(22.dp),
            contentPadding = PaddingValues(horizontal = 18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)
        ) {
            if (syncing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.crave_sync), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun cuisineEmoji(cuisine: String): String = when (cuisine) {
    "Italienisch"   -> "🍕"
    "Asiatisch"     -> "🍜"
    "Chinesisch"    -> "🥡"
    "Japanisch"     -> "🍱"
    "Thailändisch"  -> "🌶️"
    "Koreanisch"    -> "🍲"
    "Vietnamesisch" -> "🍜"
    "Mexikanisch"   -> "🌮"
    "Indisch"       -> "🍛"
    "Mediterran"    -> "🫒"
    "Französisch"   -> "🥐"
    "Griechisch"    -> "🫒"
    "Türkisch"      -> "🥙"
    "Amerikanisch"  -> "🍔"
    "Deutsch"       -> "🥨"
    "Spanisch"      -> "🥘"
    "Vegetarisch"   -> "🥗"
    "Vegan"         -> "🌱"
    else            -> "🍽️"
}
