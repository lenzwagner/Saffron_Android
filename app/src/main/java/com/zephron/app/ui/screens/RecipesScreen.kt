package com.zephron.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.zephron.app.ui.lazyGridScrollbar
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiFoodBeverage
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.zephron.app.R
import com.zephron.app.data.Recipe
import com.zephron.app.ui.ExpressiveCheckbox
import com.zephron.app.ui.INGREDIENT_FILTER_GROUPS
import com.zephron.app.ui.PartnerAvatar
import com.zephron.app.ui.TAG_GROUPS
import com.zephron.app.ui.TAG_ICONS
import com.zephron.app.ui.theme.LocalAppColors
import com.zephron.app.ui.verticalScrollbar
import com.zephron.app.viewmodel.SearchMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipesScreen(
    recipes: List<Recipe>,
    searchQuery: String,
    selectedCategory: String,
    selectedTags: Set<String>,
    searchMode: SearchMode,
    maxCookTime: Int?,
    minRating: Int,
    showFavoritesOnly: Boolean = false,
    filterOwnerId: String? = null,
    friends: List<String> = emptyList(),
    onToggleFavoritesOnly: (Boolean) -> Unit = {},
    onFilterOwnerChange: (String?) -> Unit = {},
    currentUserId: String = "",
    currentUserName: String = "",
    currentUserPhotoUrl: String = "",
    friendNames: Map<String, String> = emptyMap(),
    friendNicknames: Map<String, String> = emptyMap(),
    friendPhotoUrls: Map<String, String> = emptyMap(),
    onSearchQueryChange: (String) -> Unit,
    onCategorySelect: (String) -> Unit,
    onTagToggle: (String) -> Unit,
    onClearTags: () -> Unit,
    onSearchModeChange: (SearchMode) -> Unit,
    onMaxCookTimeChange: (Int?) -> Unit,
    onMinRatingChange: (Int) -> Unit,
    onRecipeClick: (Recipe) -> Unit,
    onRecipeDelete: (Recipe) -> Unit,
    onBulkDelete: (Set<Int>) -> Unit,
    onAdoptRecipe: (Recipe) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onToggleAssistantBubble: () -> Unit = {},
    isAssistantVisible: Boolean = false,
    onNavigateToImport: () -> Unit = {},
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    val orange = LocalAppColors.current.accent
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(emptySet<Int>()) }
    var adoptedIds by remember { mutableStateOf(emptySet<Int>()) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val activeFilterCount = selectedTags.size + (if (maxCookTime != null) 1 else 0) + (if (minRating > 0) 1 else 0)

    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedIds = emptySet()
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        // ── Top bar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Text(
                    text = stringResource(R.string.recipes_selected, selectedIds.size),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    selectedIds = if (selectedIds.size == recipes.size) emptySet()
                    else recipes.map { it.id }.toSet()
                }) {
                    Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.recipes_select_all))
                }
                IconButton(
                    onClick = { if (selectedIds.isNotEmpty()) showBulkDeleteDialog = true },
                    enabled = selectedIds.isNotEmpty()
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.recipes_delete_selected),
                        tint = if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
                IconButton(onClick = { selectionMode = false; selectedIds = emptySet() }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.recipes_cancel))
                }
            } else {
                Text(
                    text = stringResource(R.string.recipes_title),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // Friend picker
                Box {
                    var expanded by remember { mutableStateOf(false) }
                    val label = if (filterOwnerId == null) (currentUserName.ifBlank { "Ich" }) 
                                else (friendNicknames[filterOwnerId] ?: friendNames[filterOwnerId] ?: filterOwnerId)
                    val photoUrl = if (filterOwnerId == null) currentUserPhotoUrl 
                                   else friendPhotoUrls[filterOwnerId]
                    
                    IconButton(onClick = { expanded = true }) {
                        PartnerAvatar(label = label, accent = orange, size = 28.dp, photoUrl = photoUrl)
                    }

                    androidx.compose.material3.DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Alle (Meine)", fontWeight = if (filterOwnerId == null) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { onFilterOwnerChange(null); expanded = false },
                            leadingIcon = { PartnerAvatar(label = currentUserName.ifBlank { "Ich" }, accent = orange, size = 24.dp, photoUrl = currentUserPhotoUrl) }
                        )
                        friends.forEach { fid ->
                            val fLabel = friendNicknames[fid] ?: friendNames[fid] ?: fid
                            val isSelected = fid == filterOwnerId
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(fLabel, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { onFilterOwnerChange(fid); expanded = false },
                                leadingIcon = { PartnerAvatar(label = fLabel, accent = orange, size = 24.dp, photoUrl = friendPhotoUrls[fid]) }
                            )
                        }
                    }
                }

                // Favorites-only toggle
                IconButton(onClick = { onToggleFavoritesOnly(!showFavoritesOnly) }) {
                    Icon(
                        imageVector = if (showFavoritesOnly) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = stringResource(R.string.recipes_favorites_only),
                        tint = if (showFavoritesOnly) orange else MaterialTheme.colorScheme.onSurface
                    )
                }
                // Filter button with badge showing active count
                BadgedBox(
                    badge = {
                        if (activeFilterCount > 0) {
                            Badge(containerColor = orange) {
                                Text(
                                    "$activeFilterCount",
                                    color = Color.White,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                ) {
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(
                            Icons.Filled.FilterList,
                            contentDescription = stringResource(R.string.recipes_filter_label),
                            tint = if (activeFilterCount > 0) orange
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                IconButton(onClick = onToggleAssistantBubble) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = "Assistant",
                        tint = if (isAssistantVisible) LocalAppColors.current.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.settings_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Search bar + mode toggle
        AnimatedVisibility(visible = !selectionMode, enter = fadeIn(), exit = fadeOut()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                SearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = searchQuery,
                            onQueryChange = onSearchQueryChange,
                            onSearch = {},
                            expanded = false,
                            onExpandedChange = {},
                            placeholder = {
                                val placeholderRes = when (searchMode) {
                                    SearchMode.TITLE -> R.string.recipes_search_title
                                    SearchMode.INGREDIENTS -> R.string.recipes_search_ingredients
                                    SearchMode.CUISINE -> R.string.recipes_search_cuisine
                                }
                                Text(stringResource(placeholderRes))
                            },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchQueryChange("") }) {
                                        Icon(Icons.Filled.Close, contentDescription = null)
                                    }
                                }
                            }
                        )
                    },
                    expanded = false,
                    onExpandedChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    windowInsets = WindowInsets(0),
                ) {}
                Spacer(modifier = Modifier.height(6.dp))
                // Mode toggle (Sliding indicator style)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val modes = listOf(
                            Triple(SearchMode.TITLE, R.string.recipes_filter_title_mode, Icons.AutoMirrored.Filled.MenuBook),
                            Triple(SearchMode.INGREDIENTS, R.string.recipes_filter_ingredients_mode, Icons.Filled.Kitchen),
                            Triple(SearchMode.CUISINE, R.string.recipes_filter_cuisine_mode, Icons.Filled.Public)
                        )
                        
                        val selectedIndex = modes.indexOfFirst { it.first == searchMode }.coerceAtLeast(0)
                        
                        val indicatorOffset by animateFloatAsState(
                            targetValue = selectedIndex.toFloat(),
                            animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "search_mode_indicator"
                        )

                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val indicatorWidth = maxWidth / 3
                            Box(
                                modifier = Modifier
                                    .offset(x = indicatorWidth * indicatorOffset)
                                    .width(indicatorWidth)
                                    .fillMaxHeight()
                                    .padding(4.dp)
                                    .background(LocalAppColors.current.secondary, CircleShape)
                            )
                        }

                        Row(modifier = Modifier.fillMaxSize()) {
                            modes.forEachIndexed { index, (mode, labelRes, icon) ->
                                val isSelected = searchMode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { onSearchModeChange(mode) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(labelRes),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active filter pills — tags + cook time
        AnimatedVisibility(
            visible = activeFilterCount > 0 && !selectionMode,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 6.dp, top = 2.dp)
            ) {
                items(selectedTags.toList()) { tag ->
                    val tagIcon = TAG_ICONS[tag]
                    ActiveFilterPill(
                        label = tag,
                        icon = tagIcon,
                        onRemove = { onTagToggle(tag) }
                    )
                }
                if (maxCookTime != null) {
                    item {
                        ActiveFilterPill(
                            label = stringResource(R.string.recipes_cook_time_max, maxCookTime),
                            icon = null,
                            onRemove = { onMaxCookTimeChange(null) }
                        )
                    }
                }
                if (minRating > 0) {
                    item {
                        ActiveFilterPill(
                            label = "≥ ${"★".repeat(minRating)}",
                            icon = null,
                            onRemove = { onMinRatingChange(0) }
                        )
                    }
                }
                item {
                    TextButton(onClick = {
                        onClearTags()
                        onMaxCookTimeChange(null)
                        onMinRatingChange(0)
                    }) {
                        Text(stringResource(R.string.recipes_clear_all), color = LocalAppColors.current.secondary, fontSize = 13.sp)
                    }
                }
            }
        }

        // ── Recipe grid ──────────────────────────────────────────────────────
        AnimatedContent(
            targetState = recipes.isEmpty(),
            transitionSpec = {
                fadeIn(tween(300)) togetherWith fadeOut(tween(250))
            },
            label = "recipes_list_transition",
            modifier = Modifier.weight(1f)
        ) { isEmpty ->
            if (isEmpty) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.EmojiFoodBeverage,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = orange
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (activeFilterCount > 0) stringResource(R.string.recipes_no_match)
                            else stringResource(R.string.recipes_no_recipes),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (activeFilterCount > 0) {
                            TextButton(onClick = {
                                onClearTags()
                                onMaxCookTimeChange(null)
                                onMinRatingChange(0)
                            }) {
                                Text(stringResource(R.string.recipes_clear_filters), color = LocalAppColors.current.secondary)
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.recipes_import_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                val gridState = rememberLazyGridState()
                val accentColor = LocalAppColors.current.accent
                val pullState = rememberPullToRefreshState()
                // Hide FAB when scrolling down
                val showFab by remember { derivedStateOf { gridState.firstVisibleItemIndex == 0 } }

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    state = pullState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        modifier = Modifier
                            .fillMaxSize()
                            .lazyGridScrollbar(gridState, color = accentColor.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 140.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(recipes, key = { it.id }) { recipe ->
                            val isSelected = recipe.id in selectedIds
                            val isMine = recipe.ownerId.isEmpty() || recipe.ownerId == currentUserId
                            val alreadyAdopted = recipe.id in adoptedIds
                            RecipeGridCard(
                                modifier = Modifier.animateItem(),
                                recipe = recipe,
                                selectionMode = selectionMode,
                                isSelected = isSelected,
                                currentUserId = currentUserId,
                                friendNames = friendNames,
                                friendNicknames = friendNicknames,
                                isMine = isMine,
                                alreadyAdopted = alreadyAdopted,
                                onClick = {
                                    if (selectionMode) {
                                        selectedIds = if (isSelected) selectedIds - recipe.id
                                        else selectedIds + recipe.id
                                    } else {
                                        onRecipeClick(recipe)
                                    }
                                },
                                onLongPress = {
                                    selectionMode = true
                                    selectedIds = setOf(recipe.id)
                                },
                                onAdopt = {
                                    onAdoptRecipe(recipe)
                                    adoptedIds = adoptedIds + recipe.id
                                }
                            )
                        }
                    }

                    // ── Extended FAB (Import) ─────────────────────────────────
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(end = 20.dp, bottom = 88.dp)
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showFab && !selectionMode,
                            enter = fadeIn() + androidx.compose.animation.slideInVertically { it },
                            exit = fadeOut() + androidx.compose.animation.slideOutVertically { it }
                        ) {
                            ExtendedFloatingActionButton(
                                onClick = onNavigateToImport,
                                icon = {
                                    Icon(
                                        Icons.Filled.Public,
                                        contentDescription = null
                                    )
                                },
                                text = { Text("Importieren", fontWeight = FontWeight.SemiBold) },
                                containerColor = accentColor,
                                contentColor = Color.White,
                                elevation = FloatingActionButtonDefaults.elevation(6.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Bulk delete dialog ────────────────────────────────────────────────────
    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text(pluralStringResource(R.plurals.recipes_delete_count, selectedIds.size, selectedIds.size)) },
            text = { Text(stringResource(R.string.recipes_this_cannot_undone)) },
            confirmButton = {
                TextButton(onClick = {
                    onBulkDelete(selectedIds)
                    showBulkDeleteDialog = false
                    selectionMode = false
                    selectedIds = emptySet()
                }) { Text(stringResource(R.string.recipes_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) { Text(stringResource(R.string.recipes_cancel)) }
            }
        )
    } // Column

    // ── Scrim overlay (replaces expensive blur) ───────────────────────────────
    androidx.compose.animation.AnimatedVisibility(
        visible = showFilterSheet,
        enter = fadeIn(androidx.compose.animation.core.tween(200)),
        exit = fadeOut(androidx.compose.animation.core.tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
        )
    }

    } // Box

    // ── Filter bottom sheet ───────────────────────────────────────────────────
    if (showFilterSheet) {
        val sheetHeight = (LocalConfiguration.current.screenHeightDp * 0.70f).dp
        val dismiss = { scope.launch { sheetState.hide() }.invokeOnCompletion { showFilterSheet = false } }

        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetHeight)
            ) {
                // ── Fixed header ──────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.recipes_filter_label),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (activeFilterCount > 0) {
                        TextButton(onClick = {
                            onClearTags()
                            onMaxCookTimeChange(null)
                            onMinRatingChange(0)
                        }) {
                            Text(stringResource(R.string.recipes_filter_clear), color = LocalAppColors.current.secondary)
                        }
                    }
                    Button(
                        onClick = { dismiss() },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp, vertical = 8.dp
                        ),
                        colors = ButtonDefaults.buttonColors(containerColor = orange)
                    ) {
                        Text(
                            text = if (activeFilterCount == 0) stringResource(R.string.recipes_filter_done) else stringResource(R.string.recipes_filter_done_count, activeFilterCount),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // ── Scrollable content ────────────────────────────────────────
                val filterScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScrollbar(filterScrollState, color = LocalAppColors.current.secondary.copy(alpha = 0.5f))
                        .verticalScroll(filterScrollState)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    // Which filter sections are shown depends on the active search
                    // mode (the Name / Zutaten / Küche tab):
                    //  • Küche   → only the cuisine group
                    //  • Zutaten → rating, cook time + ingredient-related groups
                    //  • Name    → everything
                    val visibleGroups = when (searchMode) {
                        SearchMode.CUISINE -> TAG_GROUPS.filter { it.first == "Küche" }
                        SearchMode.INGREDIENTS -> TAG_GROUPS.filter { it.first in INGREDIENT_FILTER_GROUPS }
                        SearchMode.TITLE -> TAG_GROUPS
                    }
                    val showRatingAndCookTime = searchMode != SearchMode.CUISINE

                    if (showRatingAndCookTime) {
                    // ── Rating section ────────────────────────────────────────
                    Text(
                        text = stringResource(R.string.recipes_min_rating),
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
                        // "Alle" chip
                        FilterChip(
                            selected = minRating == 0,
                            onClick = { onMinRatingChange(0) },
                            label = { Text(stringResource(R.string.recipes_all)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LocalAppColors.current.secondary,
                                selectedLabelColor = Color.White
                            )
                        )
                        (1..5).forEach { stars ->
                            FilterChip(
                                selected = minRating == stars,
                                onClick = { onMinRatingChange(stars) },
                                label = {
                                    Text(
                                        "★".repeat(stars),
                                        fontWeight = FontWeight.Bold,
                                        color = if (minRating == stars) Color.White else Color(0xFFFFBB00)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LocalAppColors.current.secondary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // ── Cook time section ─────────────────────────────────────
                    Text(
                        text = stringResource(R.string.recipes_cook_time),
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
                        listOf(
                            null to stringResource(R.string.recipes_cook_time_any),
                            15 to stringResource(R.string.recipes_cook_time_15),
                            30 to stringResource(R.string.recipes_cook_time_30),
                            60 to stringResource(R.string.recipes_cook_time_60)
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = maxCookTime == value,
                                onClick = { onMaxCookTimeChange(value) },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LocalAppColors.current.secondary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    } // showRatingAndCookTime

                    visibleGroups.forEach { (groupName, tags) ->
                        Text(
                            text = groupName,
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
                                    selected = tag in selectedTags,
                                    onClick = { onTagToggle(tag) },
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
}

@Composable
private fun ActiveFilterPill(
    label: String,
    icon: ImageVector?,
    onRemove: () -> Unit
) {
    val secondary = LocalAppColors.current.secondary
    Surface(shape = RoundedCornerShape(20.dp), color = secondary) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
        ) {
            if (icon != null) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun RecipeGridCard(
    modifier: Modifier = Modifier,
    recipe: Recipe,
    selectionMode: Boolean,
    isSelected: Boolean,
    currentUserId: String = "",
    friendNames: Map<String, String> = emptyMap(),
    friendNicknames: Map<String, String> = emptyMap(),
    isMine: Boolean = true,
    alreadyAdopted: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onAdopt: () -> Unit = {}
) {
    val orange = LocalAppColors.current.accent
    var showContextMenu by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        // Expressive bouncy press feedback.
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "scale"
    )

    val expressiveShape = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 12.dp,
        bottomStart = 12.dp,
        bottomEnd = 28.dp
    )

    // MD3 surface container: tonal layering instead of shadow + border
    val containerColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.secondaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = tween(200),
        label = "container"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    if (!isMine && !alreadyAdopted) showContextMenu = true
                    else onLongPress()
                }
            ),
        shape = expressiveShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        val columnScope = this
        Box {
            Column {
                if (recipe.thumbnailUrl.isNotBlank()) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(ctx)
                            .data(recipe.thumbnailUrl)
                            .memoryCacheKey(recipe.thumbnailUrl)
                            .crossfade(false)
                            .build(),
                        contentDescription = recipe.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(expressiveShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clip(expressiveShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EmojiFoodBeverage,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .height(52.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = recipe.title,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val ownerName = if (recipe.ownerId.isNotEmpty() && recipe.ownerId != currentUserId) {
                        friendNicknames[recipe.ownerId] ?: friendNames[recipe.ownerId]
                    } else null
                    if (ownerName != null) {
                        Text(
                            text = "Von $ownerName",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Bookmark badge (top-right of image, when favorited)
            if (!selectionMode && recipe.isFavorite) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = null,
                        tint = orange,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(14.dp)
                    )
                }
            }

            // Star badge (top-left of image, only when rated)
            if (!selectionMode && recipe.rating > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFBB00),
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "${recipe.rating}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            columnScope.AnimatedVisibility(
                visible = selectionMode,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                val blue = Color(0xFF2196F3)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    ExpressiveCheckbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        color = blue
                    )
                }
            }

            // Friend badge (bottom-end of image) — shown when recipe belongs to a friend
            if (!isMine) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Icon(
                        imageVector = if (alreadyAdopted) Icons.Filled.Check else Icons.Filled.Person,
                        contentDescription = if (alreadyAdopted) "Übernommen" else "Vom Freund",
                        tint = if (alreadyAdopted) orange else Color.White,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(14.dp)
                    )
                }
            }

            // Context menu for adopting friend recipes
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Als eigenes übernehmen") },
                    leadingIcon = { Icon(Icons.Filled.SaveAlt, contentDescription = null) },
                    onClick = {
                        showContextMenu = false
                        onAdopt()
                    }
                )
            }
        }
    }
}
