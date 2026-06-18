package com.zephron.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zephron.app.R
import com.zephron.app.data.Match
import com.zephron.app.ui.theme.LocalAppColors
import java.text.SimpleDateFormat
import java.util.*

/** One section entry: a calendar day with its matches. */
private data class DayGroup(
    val label: String,
    val key: String,
    val matches: List<Match>
)

// Stable key for a Match used in selection sets
private fun Match.selectionKey() = "${recipeUrl}_${partnerId}_${matchedAt}"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MatchHistoryScreen(
    matches: List<Match>,
    onRecipeClick: (String) -> Unit,
    onClearMatches: (String?) -> Unit = {},
    onDeleteMatch: (Match) -> Unit = {},
    onDeleteMatches: (List<Match>) -> Unit = {},
    friends: List<String> = emptyList(),
    friendNames: Map<String, String> = emptyMap(),
    friendNicknames: Map<String, String> = emptyMap(),
    friendPhotoUrls: Map<String, String> = emptyMap()
) {
    if (matches.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.matches_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val accent = LocalAppColors.current.accent
    var selectedPartnerId by rememberSaveable { mutableStateOf<String?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var searchText by rememberSaveable { mutableStateOf("") }
    val collapsedDays = remember { mutableStateMapOf<String, Boolean>() }

    // ── Selection mode ────────────────────────────────────────────────────────
    var selectionMode by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf(emptySet<String>()) }

    val dayFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
    val today = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
    }

    // Resolve display name: prefer in-app nickname > friendNames passed in > stored name
    fun resolvedName(match: Match): String =
        friendNicknames[match.partnerId]
            ?: friendNames[match.partnerId]
            ?: match.partnerName

    val partners = remember(matches, friendNicknames, friendNames) {
        matches.map { it.partnerId to resolvedName(it) }.distinctBy { it.first }
    }
    val effectivePartner = selectedPartnerId?.takeIf { id -> partners.any { it.first == id } }
    val partnerFiltered = if (effectivePartner == null) matches
                         else matches.filter { it.partnerId == effectivePartner }

    val allDayGroups: List<DayGroup> = remember(partnerFiltered) {
        val cal = Calendar.getInstance()
        val todayCal = Calendar.getInstance()
        val yesterdayCal = Calendar.getInstance().also { it.add(Calendar.DAY_OF_YEAR, -1) }

        val dict = mutableMapOf<String, MutableList<Match>>()
        for (m in partnerFiltered) {
            val d = Date(m.matchedAt)
            cal.time = d
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            if (cal.time.after(today)) continue
            val key = dayFormat.format(d)
            dict.getOrPut(key) { mutableListOf() }.add(m)
        }
        dict.entries
            .sortedByDescending { dayFormat.parse(it.key) }
            .map { (key, ms) ->
                val date = dayFormat.parse(key) ?: Date()
                cal.time = date
                val label = when {
                    cal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                    cal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR) -> "Heute · $key"
                    cal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
                    cal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR) -> "Gestern · $key"
                    else -> key
                }
                DayGroup(label = label, key = key, matches = ms)
            }
    }

    val visibleGroups = remember(allDayGroups, searchText) {
        if (searchText.isBlank()) allDayGroups
        else allDayGroups.filter { it.key.contains(searchText.trim()) }
    }

    val allVisibleKeys = remember(visibleGroups) {
        visibleGroups.flatMap { g -> g.matches.map { it.selectionKey() } }.toSet()
    }
    val allSelected = selectionMode && selectedKeys.containsAll(allVisibleKeys) && allVisibleKeys.isNotEmpty()

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Selection mode top bar ────────────────────────────────────────────
        AnimatedVisibility(visible = selectionMode) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        selectionMode = false
                        selectedKeys = emptySet()
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Abbrechen")
                    }
                    Text(
                        text = "${selectedKeys.size} ausgewählt",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    // Select all / deselect all
                    IconButton(onClick = {
                        selectedKeys = if (allSelected) emptySet() else allVisibleKeys
                    }) {
                        Icon(
                            Icons.Filled.SelectAll,
                            contentDescription = if (allSelected) "Alle abwählen" else "Alle auswählen",
                            tint = if (allSelected) accent else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    // Delete selected
                    IconButton(
                        onClick = { if (selectedKeys.isNotEmpty()) showBulkDeleteDialog = true },
                        enabled = selectedKeys.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Löschen",
                            tint = if (selectedKeys.isNotEmpty()) MaterialTheme.colorScheme.error
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ── Partner dropdown + clear button ───────────────────────────────────
        AnimatedVisibility(visible = !selectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Expressive pill dropdown — same style as CravePartnerBar
                var dropdownExpanded by remember { mutableStateOf(false) }
                val selectedLabel = effectivePartner?.let {
                    friendNicknames[it] ?: friendNames[it] ?: partners.find { p -> p.first == it }?.second ?: it
                }
                Box(modifier = Modifier.weight(1f)) {
                    Surface(
                        onClick = { dropdownExpanded = true },
                        shape = RoundedCornerShape(22.dp),
                        color = accent.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Avatar / icon
                            if (effectivePartner != null && friendPhotoUrls[effectivePartner] != null) {
                                AsyncImage(
                                    model = friendPhotoUrls[effectivePartner],
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(accent.copy(alpha = 0.22f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.People, null,
                                        modifier = Modifier.size(16.dp), tint = accent
                                    )
                                }
                            }
                            Text(
                                text = selectedLabel ?: stringResource(R.string.matches_filter_all),
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.SemiBold,
                                color = if (effectivePartner != null) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(Icons.Filled.ArrowDropDown, null, tint = accent)
                        }
                    }
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        // "Alle" option
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.matches_filter_all),
                                    fontWeight = if (effectivePartner == null) FontWeight.Bold else FontWeight.Normal,
                                    color = if (effectivePartner == null) accent else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = { selectedPartnerId = null; dropdownExpanded = false },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(accent.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.People, null, modifier = Modifier.size(16.dp), tint = accent)
                                }
                            },
                            trailingIcon = {
                                if (effectivePartner == null)
                                    Icon(Icons.Filled.CheckCircle, null, tint = accent, modifier = Modifier.size(20.dp))
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        partners.forEach { (id, _) ->
                            val label = friendNicknames[id] ?: friendNames[id] ?: id
                            val isSelected = id == effectivePartner
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = { selectedPartnerId = id; dropdownExpanded = false },
                                leadingIcon = {
                                    val photoUrl = friendPhotoUrls[id]
                                    if (photoUrl != null) {
                                        AsyncImage(
                                            model = photoUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(accent.copy(alpha = 0.18f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                label.take(1).uppercase(),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = accent,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                },
                                trailingIcon = {
                                    if (isSelected)
                                        Icon(Icons.Filled.CheckCircle, null, tint = accent, modifier = Modifier.size(20.dp))
                                }
                            )
                        }
                    }
                }

                // Clear all button
                FilledTonalIconButton(
                    onClick = { showClearDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Alle löschen", modifier = Modifier.size(20.dp))
                }
            }
        }

        // ── Date search bar ───────────────────────────────────────────────────
        AnimatedVisibility(visible = !selectionMode) {
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = searchText,
                        onQueryChange = { searchText = it },
                        onSearch = {},
                        expanded = false,
                        onExpandedChange = {},
                        placeholder = { Text("Tag suchen (z. B. 13.06.)") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchText.isNotEmpty()) {
                                IconButton(onClick = { searchText = "" }) {
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
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                windowInsets = WindowInsets(0),
            ) {}
        }

        // ── Day-grouped list ──────────────────────────────────────────────────
        if (visibleGroups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Kein Tag gefunden", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                visibleGroups.forEach { group ->
                    // Section header
                    item(key = "header_${group.key}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!selectionMode) {
                                        if (collapsedDays.containsKey(group.key)) collapsedDays.remove(group.key)
                                        else collapsedDays[group.key] = true
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Colored banner pill — same style as RecipeOfDay carousel label
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = accent.copy(alpha = 0.13f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = group.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = accent,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${group.matches.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accent.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (!selectionMode) {
                                        Icon(
                                            imageVector = if (collapsedDays.containsKey(group.key))
                                                Icons.Filled.KeyboardArrowRight
                                            else
                                                Icons.Filled.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Match cards
                    items(
                        group.matches,
                        key = { "${it.recipeUrl}_${it.partnerId}_${it.matchedAt}" }
                    ) { match ->
                        val matchKey = match.selectionKey()
                        val isSelected = matchKey in selectedKeys

                        AnimatedVisibility(
                            visible = !collapsedDays.containsKey(group.key),
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            if (selectionMode) {
                                // ── Selection mode: checkbox item (no swipe) ──
                                val bgColor by animateColorAsState(
                                    if (isSelected) accent.copy(alpha = 0.12f)
                                    else Color.Transparent,
                                    label = "matchSelectBg"
                                )
                                Row(
                                    modifier = Modifier
                                        .animateItem()
                                        .fillMaxWidth()
                                        .background(bgColor)
                                        .combinedClickable(
                                            onClick = {
                                                selectedKeys = if (isSelected)
                                                    selectedKeys - matchKey
                                                else
                                                    selectedKeys + matchKey
                                            }
                                        )
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isSelected)
                                            Icons.Filled.CheckCircle
                                        else
                                            Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = if (isSelected) accent
                                               else MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .padding(end = 0.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Box(modifier = Modifier.weight(1f)) {
                                        MatchItem(
                                            match = match,
                                            partnerDisplayName = resolvedName(match),
                                            showPartnerName = effectivePartner == null,
                                            onClick = {
                                                selectedKeys = if (isSelected)
                                                    selectedKeys - matchKey
                                                else
                                                    selectedKeys + matchKey
                                            }
                                        )
                                    }
                                }
                            } else {
                                // ── Normal mode: swipe-to-delete ──────────────
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        if (value == SwipeToDismissBoxValue.EndToStart) {
                                            onDeleteMatch(match); true
                                        } else false
                                    },
                                    positionalThreshold = { it * 0.4f }
                                )
                                SwipeToDismissBox(
                                    state = dismissState,
                                    modifier = Modifier
                                        .animateItem()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    enableDismissFromStartToEnd = false,
                                    backgroundContent = {
                                        val color by animateColorAsState(
                                            if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart)
                                                MaterialTheme.colorScheme.errorContainer
                                            else Color.Transparent,
                                            label = "swipeBg"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(color),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(end = 20.dp)
                                            )
                                        }
                                    }
                                ) {
                                    MatchItem(
                                        match = match,
                                        partnerDisplayName = resolvedName(match),
                                        showPartnerName = effectivePartner == null,
                                        onClick = { onRecipeClick(match.recipeUrl) },
                                        onLongPress = {
                                            selectionMode = true
                                            selectedKeys = setOf(matchKey)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Clear all dialog ──────────────────────────────────────────────────────
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.matches_clear_title)) },
            text = { Text(stringResource(R.string.recipes_this_cannot_undone)) },
            confirmButton = {
                TextButton(onClick = {
                    onClearMatches(effectivePartner)
                    selectedPartnerId = null
                    showClearDialog = false
                }) {
                    Text(stringResource(R.string.recipes_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.recipes_cancel))
                }
            }
        )
    }

    // ── Bulk delete dialog ────────────────────────────────────────────────────
    if (showBulkDeleteDialog) {
        val count = selectedKeys.size
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text("$count Match${if (count == 1) "" else "es"} löschen") },
            text = { Text(stringResource(R.string.recipes_this_cannot_undone)) },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = partnerFiltered.filter { it.selectionKey() in selectedKeys }
                    onDeleteMatches(toDelete)
                    selectedKeys = emptySet()
                    selectionMode = false
                    showBulkDeleteDialog = false
                }) {
                    Text(stringResource(R.string.recipes_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) {
                    Text(stringResource(R.string.recipes_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MatchItem(
    match: Match,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    showPartnerName: Boolean = false,
    partnerDisplayName: String = match.partnerName
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val accent = LocalAppColors.current.accent

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            ),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(16.dp)
    ) {
        ListItem(
            headlineContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = match.recipeTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // Partner name badge — only shown in "Alle" mode
                    if (showPartnerName) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = accent.copy(alpha = 0.13f)
                        ) {
                            Text(
                                text = partnerDisplayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.match_with, partnerDisplayName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = timeFormat.format(Date(match.matchedAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            leadingContent = {
                AsyncImage(
                    model = match.recipeThumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}
