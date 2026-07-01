package com.zephron.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Link
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collect
import coil.compose.AsyncImage
import com.zephron.app.R
import com.zephron.app.ui.AppLogo
import com.zephron.app.ui.ExpressiveProgressBar
import com.zephron.app.network.RecipeMetadata
import com.zephron.app.ui.TAG_GROUPS
import com.zephron.app.ui.TAG_ICONS
import com.zephron.app.ui.theme.LocalAppColors
import com.zephron.app.viewmodel.BatchImportState
import com.zephron.app.viewmodel.BatchMode
import com.zephron.app.viewmodel.ImportState

@Composable
private fun ImportBrandingIcon(modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Link + Plus Icon with Gradient
        val gradient = Brush.linearGradient(listOf(colors.secondary, colors.accent))
        
        Box(contentAlignment = Alignment.BottomEnd) {
            Icon(
                imageVector = Icons.Filled.Link,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer(alpha = 0.99f)
                    .drawWithCache {
                        onDrawWithContent {
                            drawContent()
                            drawRect(gradient, blendMode = BlendMode.SrcAtop)
                        }
                    },
                tint = Color.White
            )
            Surface(
                modifier = Modifier
                    .size(32.dp)
                    .offset(x = 4.dp, y = 4.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer(alpha = 0.99f)
                            .drawWithCache {
                                onDrawWithContent {
                                    drawContent()
                                    drawRect(gradient, blendMode = BlendMode.SrcAtop)
                                }
                            },
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    url: String,
    importState: ImportState,
    batchState: BatchImportState,
    batchText: String,
    isBatchTab: Boolean,
    isManualTab: Boolean,
    onUrlChange: (String) -> Unit,
    onImport: () -> Unit,
    onSave: () -> Unit,
    onTitleChange: (String) -> Unit,
    onTagsChange: (List<String>) -> Unit,
    onServingsChange: (Int) -> Unit,
    onReset: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleAssistantBubble: () -> Unit = {},
    isAssistantVisible: Boolean = false,
    onBatchTabChange: (Boolean) -> Unit,
    onManualTabChange: (Boolean) -> Unit,
    onBatchTextChange: (String) -> Unit,
    onFilterSaved: (List<String>) -> Unit,
    onExport: () -> Unit,
    onBatchImport: (List<String>, BatchMode) -> Unit,
    onBatchReset: () -> Unit,
    onSelectThumbnail: (String) -> Unit,
    onSkipInQueue: () -> Unit,
    onManualSave: (String, List<String>, String) -> Unit = { _, _, _ -> },
    onRecipeClick: (com.zephron.app.data.Recipe) -> Unit = {},
    events: kotlinx.coroutines.flow.SharedFlow<com.zephron.app.viewmodel.ImportEvent> = kotlinx.coroutines.flow.MutableSharedFlow()
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        events.collect { event ->
            when (event) {
                is com.zephron.app.viewmodel.ImportEvent.ShowToast -> {
                    android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Only show mode toggle when both sides are idle
    val showToggle = batchState is BatchImportState.Idle
            && importState !is ImportState.Saved
            && importState !is ImportState.AlreadyExists

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
                text = stringResource(R.string.import_title),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(onClick = onExport) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = stringResource(R.string.import_export_links),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggleAssistantBubble) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = "Assistant",
                    tint = if (isAssistantVisible) LocalAppColors.current.accent else MaterialTheme.colorScheme.onSurfaceVariant
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

        // ── Mode toggle (Link / Mehrere / Manuell) ───────────────────────────
        if (showToggle) {
            val accent = LocalAppColors.current.accent
            // 0 = single link, 1 = batch, 2 = manual
            val tabIndex = if (isManualTab) 2 else if (isBatchTab) 1 else 0
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
                        targetValue = tabIndex.toFloat(),
                        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "import_tab_indicator"
                    )
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val indicatorWidth = maxWidth / 3
                        Box(
                            modifier = Modifier
                                .offset(x = indicatorWidth * indicatorOffset)
                                .width(indicatorWidth)
                                .fillMaxHeight()
                                .padding(4.dp)
                                .background(accent, CircleShape)
                        )
                    }
                    Row(modifier = Modifier.fillMaxSize()) {
                        listOf("Link", "Mehrere", "Manuell").forEachIndexed { idx, label ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        onManualTabChange(idx == 2)
                                        onBatchTabChange(idx == 1)
                                        onReset()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (tabIndex == idx) Color.White
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Content ───────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Manual entry tab ──────────────────────────────────────────────
            if (isManualTab && importState !is ImportState.Saved) {
                ManualRecipeEntry(onSave = onManualSave, onReset = onReset)
                return@Column
            }
            AnimatedContent(
                targetState = Triple(batchState, importState, isBatchTab),
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(250))
                },
                label = "import_state"
            ) { (bState, iState, isBatch) ->
                val showBatchLocal = (isBatch || bState !is BatchImportState.Idle) && iState !is ImportState.Saved
                
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when {
                        // ── Queue mode: one-by-one review ──────────────────────────
                        bState is BatchImportState.QueueActive -> {
                            QueueProgressBanner(
                                batchState = bState,
                                onCancel = { onBatchReset() }
                            )
                            when (iState) {
                                is ImportState.Loading -> QueueLoadingCard(bState)
                                is ImportState.Error   -> QueueErrorCard(
                                    error = iState,
                                    onSkip = onSkipInQueue,
                                    onOpenSettings = onOpenSettings
                                )
                                is ImportState.Success -> QueueReviewCard(
                                    metadata = iState.metadata,
                                    batchState = bState,
                                    isSaving = iState.isSaving,
                                    onSave = onSave,
                                    onSkip = onSkipInQueue,
                                    onTitleChange = onTitleChange,
                                    onTagsChange = onTagsChange,
                                    onServingsChange = onServingsChange,
                                    onSelectThumbnail = onSelectThumbnail
                                )
                                else -> {}
                            }
                        }

                        // ── Batch input / Done ─────────────────────────────────────
                        showBatchLocal -> BatchImportCard(
                            batchText = batchText,
                            batchState = bState,
                            onBatchTextChange = onBatchTextChange,
                            onImport = { mode -> onBatchImport(listOf(batchText), mode) },
                            onReset = { onBatchReset() },
                            onRecipeClick = onRecipeClick
                        )

                        // ── Single import ──────────────────────────────────────────
                        iState is ImportState.AlreadyExists -> AlreadyExistsCard(
                            recipe = iState.recipe,
                            onRecipeClick = onRecipeClick,
                            onReset = onReset
                        )
                        iState is ImportState.Saved -> SavedConfirmation(
                            savedTags = iState.tags,
                            savedRecipe = iState.savedRecipe,
                            onReset = onReset,
                            onFilterByTags = onFilterSaved,
                            onRecipeClick = onRecipeClick
                        )
                        else -> ImportCard(
                            url = url,
                            importState = iState,
                            onUrlChange = onUrlChange,
                            onImport = onImport,
                            onSave = onSave,
                            onDiscard = onReset,
                            onTitleChange = onTitleChange,
                            onTagsChange = onTagsChange,
                            onServingsChange = onServingsChange,
                            onOpenSettings = onOpenSettings,
                            onSelectThumbnail = onSelectThumbnail
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(120.dp)) // Added spacing for floating bar
    }
}

// ── Batch import card ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
/**
 * Extracts all URLs from raw text — handles URLs separated by spaces, newlines,
 * or concatenated directly (e.g. "https://...https://...").
 */
private fun extractUrls(text: String): List<String> {
    val regex = Regex("""https?://[^\s,;"'<>]+""")
    // First split on "https://" boundaries so concatenated URLs are handled
    val normalized = text.replace(Regex("(?<!^)(https?://)"), "\n$1")
    return regex.findAll(normalized)
        .map { it.value.trimEnd('.', ',', ')') }
        .filter { it.length > 10 }
        .distinct()
        .toList()
}

@Composable
private fun BatchImportCard(
    batchText: String,
    batchState: BatchImportState,
    onBatchTextChange: (String) -> Unit,
    onImport: (BatchMode) -> Unit,
    onReset: () -> Unit,
    onRecipeClick: (com.zephron.app.data.Recipe) -> Unit = {}
) {
    val secondary = LocalAppColors.current.secondary
    val accent = LocalAppColors.current.accent
    var selectedMode by remember { mutableStateOf(BatchMode.MANUAL) }
    val urlCount = extractUrls(batchText).size

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (batchState) {
                is BatchImportState.QueueActive -> { /* handled above, never reaches here */ }
                is BatchImportState.Idle -> {
                    // ── Icon orb ──────────────────────────────────────────
                    ImportBrandingIcon(modifier = Modifier.size(72.dp))

                    Text(
                        text = stringResource(R.string.import_batch_heading),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.import_batch_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    OutlinedTextField(
                        value = batchText,
                        onValueChange = onBatchTextChange,
                        label = { Text(stringResource(R.string.import_batch_label)) },
                        leadingIcon = { Icon(Icons.Filled.Link, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingIcon = {
                            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                            if (batchText.isNotEmpty()) {
                                IconButton(onClick = { onBatchTextChange("") }) { Icon(Icons.Filled.Clear, null) }
                            } else {
                                IconButton(onClick = { clipboard.getText()?.text?.trim()?.let { if (it.isNotBlank()) onBatchTextChange(it) } }) {
                                    Icon(Icons.Filled.ContentPaste, null)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp),
                        minLines = 6,
                        shape = RoundedCornerShape(20.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF2196F3), 
                            fontWeight = FontWeight.SemiBold
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = secondary.copy(alpha = 0.5f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    // ── Auto / Manual mode selector (Sliding style) ──────────────────────────
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val indicatorOffset by animateFloatAsState(
                                targetValue = if (selectedMode == BatchMode.AUTO) 1f else 0f,
                                animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "batch_mode_toggle"
                            )
                            
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val indicatorWidth = maxWidth / 2
                                Box(
                                    modifier = Modifier
                                        .offset(x = indicatorWidth * indicatorOffset)
                                        .width(indicatorWidth)
                                        .fillMaxHeight()
                                        .padding(4.dp)
                                        .background(accent, CircleShape)
                                )
                            }

                            Row(modifier = Modifier.fillMaxSize()) {
                                listOf(
                                    stringResource(R.string.import_mode_manual) to BatchMode.MANUAL,
                                    stringResource(R.string.import_mode_auto) to BatchMode.AUTO
                                ).forEach { (label, mode) ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) { selectedMode = mode },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selectedMode == mode) Color.White 
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (urlCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = secondary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = stringResource(R.string.import_batch_count, urlCount),
                                color = secondary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Button(
                        onClick = { onImport(selectedMode) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = urlCount > 0,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = secondary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Filled.Download, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.import_batch_button),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                is BatchImportState.Done -> {
                    val clipboardManager = LocalClipboardManager.current
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .shadow(
                                elevation = 14.dp,
                                shape = CircleShape,
                                spotColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
                                ambientColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                            )
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    Text(
                        text = stringResource(R.string.import_batch_done),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Summary row
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (batchState.imported > 0) {
                            Text(
                                text = stringResource(
                                    R.string.import_batch_imported,
                                    batchState.imported
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (batchState.skipped > 0) {
                            Text(
                                text = stringResource(
                                    R.string.import_batch_skipped,
                                    batchState.skipped
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (batchState.failed > 0) {
                            Text(
                                text = stringResource(
                                    R.string.import_batch_failed,
                                    batchState.failed
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // ── Already imported — clickable recipe list ───────────
                    if (batchState.skippedRecipes.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Bereits in deiner Bibliothek:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            batchState.skippedRecipes.forEach { recipe ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onRecipeClick(recipe) },
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.tertiary
                                        )
                                        Text(
                                            text = recipe.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Icon(
                                            Icons.Filled.ChevronRight,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Failed URLs — copyable box ─────────────────────────
                    if (batchState.failedUrls.isNotEmpty()) {
                        val failedText = batchState.failedUrls.joinToString("\n")
                        var copied by remember { mutableStateOf(false) }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.import_not_imported, batchState.failedUrls.size),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                            OutlinedTextField(
                                value = failedText,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 80.dp, max = 180.dp),
                                shape = RoundedCornerShape(12.dp),
                                textStyle = MaterialTheme.typography.bodySmall,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                                    focusedBorderColor = MaterialTheme.colorScheme.error
                                )
                            )
                            OutlinedButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(failedText))
                                    copied = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    if (copied) stringResource(R.string.import_copied) else stringResource(R.string.import_copy_urls),
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (copied) MaterialTheme.colorScheme.tertiary
                                            else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onReset,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = secondary,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            stringResource(R.string.import_done),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ── Single import card ────────────────────────────────────────────────────────

@Composable
private fun ImportCard(
    url: String,
    importState: ImportState,
    onUrlChange: (String) -> Unit,
    onImport: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onTitleChange: (String) -> Unit,
    onTagsChange: (List<String>) -> Unit,
    onServingsChange: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onSelectThumbnail: (String) -> Unit
) {
    val secondary = LocalAppColors.current.secondary
    val accent = LocalAppColors.current.accent
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 3D orb
            ImportBrandingIcon(modifier = Modifier.size(80.dp))

            Text(
                text = stringResource(R.string.import_recipe_heading),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.import_recipe_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                placeholder = { Text("https://www.tiktok.com/...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                label = { Text(stringResource(R.string.import_url_label)) },
                leadingIcon = { Icon(Icons.Filled.Link, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = {
                    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                    if (url.isNotEmpty()) {
                        IconButton(onClick = { onUrlChange("") }) { Icon(Icons.Filled.Clear, null) }
                    } else {
                        IconButton(onClick = { clipboard.getText()?.text?.trim()?.let { if (it.isNotBlank()) onUrlChange(it) } }) {
                            Icon(Icons.Filled.ContentPaste, null)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                singleLine = true,
                enabled = importState !is ImportState.Loading,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF2196F3), 
                    fontWeight = FontWeight.SemiBold
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            if (importState is ImportState.Error) {
                if (importState.sessionExpired) {
                    SessionExpiredBanner(onOpenSettings = onOpenSettings)
                } else {
                    val clipManager = androidx.compose.ui.platform.LocalClipboardManager.current
                    val errMsg = importState.message
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = errMsg,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { clipManager.setText(androidx.compose.ui.text.AnnotatedString(errMsg)) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = "Kopieren",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onImport,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                enabled = importState !is ImportState.Loading,
                colors = ButtonDefaults.buttonColors(containerColor = secondary, contentColor = Color.White)
            ) {
                if (importState is ImportState.Loading) {
                    com.zephron.app.ui.ExpressiveLoader(color = Color.White, size = 22.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.import_fetching))
                } else {
                    Text(stringResource(R.string.import_button), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            if (importState is ImportState.Success) {
                PreviewCard(
                    metadata = importState.metadata,
                    isSaving = importState.isSaving,
                    onSave = onSave,
                    onDiscard = onDiscard,
                    onTitleChange = onTitleChange,
                    onTagsChange = onTagsChange,
                    onServingsChange = onServingsChange,
                    onSelectThumbnail = onSelectThumbnail
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewCard(
    metadata: RecipeMetadata,
    isSaving: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onTitleChange: (String) -> Unit,
    onTagsChange: (List<String>) -> Unit,
    onServingsChange: (Int) -> Unit,
    onSelectThumbnail: (String) -> Unit
) {
    val accent = LocalAppColors.current.accent
    val secondary = LocalAppColors.current.secondary
    var showTagPicker by remember { mutableStateOf(false) }
    var titleDraft by remember { mutableStateOf(metadata.title) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            when {
                metadata.slideImages.size > 1 -> {
                    // ── Slideshow gallery ──────────────────────────────────
                    SlideshowPager(
                        images = metadata.slideImages,
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                        onSelectCover = onSelectThumbnail
                    )
                }
                metadata.googleImageUrl.isNotBlank() -> {
                    // ── Two-option thumbnail picker ────────────────────────────
                    ThumbnailPicker(
                        originalUrl = metadata.originalThumbnailUrl,
                        googleUrl = metadata.googleImageUrl,
                        selectedUrl = metadata.thumbnailUrl,
                        onSelect = onSelectThumbnail,
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    )
                }
                metadata.thumbnailUrl.isNotBlank() -> {
                    AsyncImage(
                        model = metadata.thumbnailUrl,
                        contentDescription = metadata.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.EmojiFoodBeverage, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = titleDraft,
                    onValueChange = { titleDraft = it },
                    label = { Text(stringResource(R.string.import_title_label)) },
                    leadingIcon = { Icon(Icons.Filled.LocalPizza, null, tint = accent.copy(alpha = 0.6f)) },
                    trailingIcon = { if (titleDraft.isNotEmpty()) IconButton(onClick = { titleDraft = ""; onTitleChange("") }) { Icon(Icons.Filled.Clear, null) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (!it.isFocused) onTitleChange(titleDraft) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = false,
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        focusedLabelColor = accent,
                        unfocusedBorderColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Category, null, modifier = Modifier.size(16.dp), tint = secondary.copy(alpha = 0.6f))
                    Text(text = metadata.category, style = MaterialTheme.typography.labelMedium, color = secondary)
                    if (metadata.isVegetarian) {
                        Spacer(Modifier.width(8.dp))
                        Text(text = "🌿", fontSize = 14.sp)
                        Text(text = stringResource(R.string.import_vegetarian), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Restaurant, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.import_servings),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onServingsChange(metadata.servings - 1) },
                        enabled = metadata.servings > 0,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.Remove, null, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = if (metadata.servings == 0) "—" else "${metadata.servings}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.Center,
                        color = if (metadata.servings > 0) secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = { onServingsChange(metadata.servings + 1) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                    }
                }

                if (metadata.ingredients.isNotEmpty() &&
                    !(metadata.ingredients.size == 1 && metadata.ingredients[0] == "Recipe in the video")) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    var showIngredients by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.EmojiFoodBeverage, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.import_ingredients_count, metadata.ingredients.size) +
                                if (metadata.geminiSteps.isNotEmpty()) stringResource(R.string.import_steps_count, metadata.geminiSteps.size) else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showIngredients = !showIngredients }) {
                            Text(if (showIngredients) stringResource(R.string.import_done) else "Anzeigen", color = secondary, fontSize = 13.sp)
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                if (showIngredients) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                null, tint = secondary, modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    AnimatedVisibility(visible = showIngredients, enter = expandVertically(), exit = shrinkVertically()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            metadata.ingredients.forEach { ingredient ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Text("·", color = secondary, fontWeight = FontWeight.Bold, modifier = Modifier.width(16.dp))
                                    Text(ingredient, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.import_tags),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showTagPicker = !showTagPicker }) {
                        Text(if (showTagPicker) stringResource(R.string.import_done) else stringResource(R.string.import_edit), color = secondary, fontSize = 13.sp)
                        Spacer(Modifier.width(4.dp))
                        Icon(if (showTagPicker) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = secondary, modifier = Modifier.size(16.dp))
                    }
                }

                if (metadata.tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        metadata.tags.forEach { tag ->
                            val icon = TAG_ICONS[tag]
                            FilterChip(
                                selected = true,
                                onClick = { onTagsChange(metadata.tags - tag) },
                                label = { Text(tag, fontSize = 12.sp) },
                                leadingIcon = icon?.let { { Icon(it, null, Modifier.size(16.dp)) } },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = secondary, selectedLabelColor = Color.White, selectedLeadingIconColor = Color.White)
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = showTagPicker, enter = expandVertically(), exit = shrinkVertically()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TAG_GROUPS.forEach { (groupName, groupTags) ->
                            Text(groupName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                groupTags.forEach { tag ->
                                    val selected = tag in metadata.tags
                                    val icon = TAG_ICONS[tag]
                                    FilterChip(
                                        selected = selected,
                                        onClick = { onTagsChange(if (selected) metadata.tags - tag else metadata.tags + tag) },
                                        label = { Text(tag, fontSize = 12.sp) },
                                        leadingIcon = icon?.let { { Icon(it, null, Modifier.size(16.dp)) } },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = secondary, selectedLabelColor = Color.White, selectedLeadingIconColor = Color.White)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDiscard,
                        modifier = Modifier.weight(1f).height(52.dp),
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.import_discard), maxLines = 1)
                    }
                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f).height(52.dp),
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)
                    ) {
                        if (isSaving) {
                            com.zephron.app.ui.ExpressiveLoader(color = Color.White, size = 22.dp)
                        } else {
                            Icon(Icons.Filled.Save, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.import_save), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Swipeable gallery for TikTok slideshow images shown during import preview.
 * Shows all slides, page indicator dots, and a "Als Cover" tap action per slide.
 */
@Composable
private fun SlideshowPager(
    images: List<String>,
    shape: androidx.compose.ui.graphics.Shape,
    onSelectCover: (String) -> Unit
) {
    val secondary = LocalAppColors.current.secondary
    val pagerState = rememberPagerState { images.size }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = images[page],
                        contentDescription = "Slide ${page + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // "Als Cover" tap overlay
                    Surface(
                        onClick = { onSelectCover(images[page]) },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.55f)
                    ) {
                        Text(
                            text = "Als Cover",
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    // Slideshow badge top-right
                    if (page == 0) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.65f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Filled.Collections, null, modifier = Modifier.size(12.dp), tint = Color.White)
                                Text("Slideshow", color = Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
        // Page indicator dots + slide counter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            images.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (index == pagerState.currentPage) 7.dp else 5.dp)
                        .clip(CircleShape)
                        .background(if (index == pagerState.currentPage) secondary else secondary.copy(alpha = 0.3f))
                )
            }
        }
    }
}

/**
 * Side-by-side image picker: shows the original post thumbnail and a Google Search result.
 * The selected one gets a secondary color border + label; tapping the other switches selection.
 */
@Composable
private fun ThumbnailPicker(
    originalUrl: String,
    googleUrl: String,
    selectedUrl: String,
    onSelect: (String) -> Unit,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
) {
    val secondary = LocalAppColors.current.secondary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            listOf(
                "Original" to originalUrl,
                "Google" to googleUrl
            ).forEach { (label, url) ->
                val isSelected = selectedUrl == url
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    AsyncImage(
                        model = url,
                        contentDescription = label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Selection overlay
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(secondary.copy(alpha = 0.15f))
                        )
                    }
                    // Tap target
                    Surface(
                        onClick = { if (!isSelected) onSelect(url) },
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent
                    ) {}
                    // Label chip
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) secondary else Color.Black.copy(alpha = 0.55f)
                    ) {
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    // Selected checkmark
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = secondary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(22.dp)
                        )
                    }
                }
            }
        }
        // Hint text
        Text(
            text = stringResource(R.string.import_pick_image_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            textAlign = TextAlign.Center
        )
    }
}

// ── Queue composables ─────────────────────────────────────────────────────────

/** Compact progress banner: shows "Rezept X von Y" + counters + Cancel. */
@Composable
private fun QueueProgressBanner(
    batchState: BatchImportState.QueueActive,
    onCancel: () -> Unit
) {
    val secondary = LocalAppColors.current.secondary
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Determinate circular progress (M3 spec)
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { batchState.current.toFloat() / batchState.total.toFloat() },
                    modifier = Modifier.size(52.dp),
                    color = secondary,
                    trackColor = secondary.copy(alpha = 0.18f),
                    strokeWidth = 4.dp
                )
                Text(
                    text = "${batchState.current}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = secondary
                )
            }
            // Labels + counters
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.import_queue_progress, batchState.current, batchState.total),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (batchState.imported > 0)
                        Text(stringResource(R.string.import_batch_imported, batchState.imported),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)
                    if (batchState.skipped > 0)
                        Text(stringResource(R.string.import_batch_skipped, batchState.skipped),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (batchState.failed > 0)
                        Text(stringResource(R.string.import_batch_failed, batchState.failed),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error)
                }
            }
            // Cancel
            TextButton(onClick = onCancel, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(R.string.import_queue_cancel), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        }
    }
}

/** Shown while the next URL is being fetched. */
@Composable
private fun QueueLoadingCard(batchState: BatchImportState.QueueActive) {
    val secondary = LocalAppColors.current.secondary
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            com.zephron.app.ui.ExpressiveLoader(color = secondary, size = 56.dp)
            Text(
                text = stringResource(R.string.import_queue_fetching, batchState.current, batchState.total),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Shown when a fetch failed — user can skip or open settings (if session expired). */
@Composable
private fun QueueErrorCard(
    error: ImportState.Error,
    onSkip: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val secondary = LocalAppColors.current.secondary
    val clipManager = androidx.compose.ui.platform.LocalClipboardManager.current
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = error.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { clipManager.setText(androidx.compose.ui.text.AnnotatedString(error.message)) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.ContentCopy, null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (error.sessionExpired) {
                    OutlinedButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(12.dp)
                    ) { 
                        Icon(Icons.Filled.Settings, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Einstellungen", fontSize = 13.sp) 
                    }
                }
                Button(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = secondary, contentColor = Color.White)
                ) { 
                    Icon(Icons.Filled.SkipNext, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.import_queue_skip), fontWeight = FontWeight.SemiBold) 
                }
            }
        }
    }
}

/**
 * Full recipe preview card shown during queue mode.
 * Identical to the single-import PreviewCard but with "Save & Next" + "Skip" buttons.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QueueReviewCard(
    metadata: com.zephron.app.network.RecipeMetadata,
    batchState: BatchImportState.QueueActive,
    isSaving: Boolean,
    onSave: () -> Unit,
    onSkip: () -> Unit,
    onTitleChange: (String) -> Unit,
    onTagsChange: (List<String>) -> Unit,
    onServingsChange: (Int) -> Unit,
    onSelectThumbnail: (String) -> Unit
) {
    val accent = LocalAppColors.current.accent
    val secondary = LocalAppColors.current.secondary
    var showTagPicker by remember { mutableStateOf(false) }
    var titleDraft by remember { mutableStateOf(metadata.title) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            // ── Thumbnail / Picker ────────────────────────────────────────
            when {
                metadata.slideImages.size > 1 -> SlideshowPager(
                    images = metadata.slideImages,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    onSelectCover = onSelectThumbnail
                )
                metadata.googleImageUrl.isNotBlank() -> ThumbnailPicker(
                    originalUrl = metadata.originalThumbnailUrl,
                    googleUrl = metadata.googleImageUrl,
                    selectedUrl = metadata.thumbnailUrl,
                    onSelect = onSelectThumbnail,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
                metadata.thumbnailUrl.isNotBlank() -> AsyncImage(
                    model = metadata.thumbnailUrl,
                    contentDescription = metadata.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                )
                else -> Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.EmojiFoodBeverage, null, modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Title
                OutlinedTextField(
                    value = titleDraft,
                    onValueChange = { titleDraft = it },
                    label = { Text(stringResource(R.string.import_title_label)) },
                    trailingIcon = { if (titleDraft.isNotEmpty()) IconButton(onClick = { titleDraft = ""; onTitleChange("") }) { Icon(Icons.Filled.Clear, null) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (!it.isFocused) onTitleChange(titleDraft) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = false, maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent, focusedLabelColor = accent,
                        unfocusedBorderColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(metadata.category, style = MaterialTheme.typography.labelMedium, color = secondary)
                    if (metadata.isVegetarian)
                        Text(stringResource(R.string.import_vegetarian), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                }

                // Servings
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Restaurant, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.import_servings), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onServingsChange(metadata.servings - 1) },
                        enabled = metadata.servings > 0, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Remove, null, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = if (metadata.servings == 0) "—" else "${metadata.servings}",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(36.dp), textAlign = TextAlign.Center,
                        color = if (metadata.servings > 0) secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = { onServingsChange(metadata.servings + 1) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                    }
                }

                // Ingredients (collapsed by default)
                if (metadata.ingredients.isNotEmpty() &&
                    !(metadata.ingredients.size == 1 && metadata.ingredients[0] == "Recipe in the video")) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    var showIngredients by remember { mutableStateOf(false) }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.EmojiFoodBeverage, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.import_ingredients_count, metadata.ingredients.size) +
                                if (metadata.geminiSteps.isNotEmpty()) stringResource(R.string.import_steps_count, metadata.geminiSteps.size) else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showIngredients = !showIngredients }) {
                            Text(if (showIngredients) stringResource(R.string.import_done) else "Anzeigen", color = secondary, fontSize = 13.sp)
                            Spacer(Modifier.width(4.dp))
                            Icon(if (showIngredients) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                null, tint = secondary, modifier = Modifier.size(16.dp))
                        }
                    }
                    AnimatedVisibility(visible = showIngredients, enter = expandVertically(), exit = shrinkVertically()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            metadata.ingredients.forEach { ingredient ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Text("·", color = secondary, fontWeight = FontWeight.Bold, modifier = Modifier.width(16.dp))
                                    Text(ingredient, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // Tags
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.import_tags), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showTagPicker = !showTagPicker }) {
                        Text(if (showTagPicker) stringResource(R.string.import_done) else stringResource(R.string.import_edit), color = secondary, fontSize = 13.sp)
                        Spacer(Modifier.width(4.dp))
                        Icon(if (showTagPicker) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            null, tint = secondary, modifier = Modifier.size(16.dp))
                    }
                }
                if (metadata.tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        metadata.tags.forEach { tag ->
                            val icon = TAG_ICONS[tag]
                            FilterChip(selected = true, onClick = { onTagsChange(metadata.tags - tag) },
                                label = { Text(tag, fontSize = 12.sp) },
                                leadingIcon = icon?.let { { Icon(it, null, Modifier.size(16.dp)) } },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = secondary, selectedLabelColor = Color.White, selectedLeadingIconColor = Color.White))
                        }
                    }
                }
                AnimatedVisibility(visible = showTagPicker, enter = expandVertically(), exit = shrinkVertically()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TAG_GROUPS.forEach { (groupName, groupTags) ->
                            Text(groupName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                groupTags.forEach { tag ->
                                    val selected = tag in metadata.tags
                                    val icon = TAG_ICONS[tag]
                                    FilterChip(selected = selected, onClick = { onTagsChange(if (selected) metadata.tags - tag else metadata.tags + tag) },
                                        label = { Text(tag, fontSize = 12.sp) },
                                        leadingIcon = icon?.let { { Icon(it, null, Modifier.size(14.dp)) } },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = secondary, selectedLabelColor = Color.White, selectedLeadingIconColor = Color.White))
                                }
                            }
                        }
                    }
                }

                // ── Save & Next / Skip buttons ────────────────────────────
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onSkip,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.SkipNext, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.import_queue_skip), fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)
                    ) {
                        if (isSaving) {
                            com.zephron.app.ui.ExpressiveLoader(color = Color.White, size = 22.dp)
                        } else {
                            val label = if (batchState.current < batchState.total)
                                stringResource(R.string.import_queue_save_next)
                            else
                                stringResource(R.string.import_save)
                            
                            Icon(Icons.Filled.Save, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(text = label, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionExpiredBanner(onOpenSettings: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Instagram Session abgelaufen", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                Text("Bitte Session-ID in den Einstellungen aktualisieren.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onOpenSettings) {
                Text("Einstellungen", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SavedConfirmation(
    savedTags: List<String>,
    savedRecipe: com.zephron.app.data.Recipe?,
    onReset: () -> Unit,
    onFilterByTags: (List<String>) -> Unit,
    onRecipeClick: (com.zephron.app.data.Recipe) -> Unit = {}
) {
    val accent = LocalAppColors.current.accent
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .shadow(elevation = 14.dp, shape = CircleShape, spotColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f), ambientColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f))
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.tertiary)
            }
            Text(stringResource(R.string.import_saved_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

            // Summary stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SummaryChip("1 importiert", MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                SummaryChip("0 fehlgeschlagen", MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                SummaryChip("0 vorhanden", MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Clickable recipe title
            if (savedRecipe != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onRecipeClick(savedRecipe) },
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Text(
                            text = savedRecipe.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(Icons.Filled.ChevronRight, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (savedTags.isNotEmpty()) {
                Button(
                    onClick = { onFilterByTags(savedTags) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)
                ) {
                    Icon(Icons.Filled.Restaurant, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.import_show_similar), fontWeight = FontWeight.SemiBold)
                }
            }

            TextButton(onClick = onReset) {
                Text(stringResource(R.string.import_another), color = accent)
            }
        }
    }
}

@Composable
private fun AlreadyExistsCard(
    recipe: com.zephron.app.data.Recipe,
    onRecipeClick: (com.zephron.app.data.Recipe) -> Unit,
    onReset: () -> Unit
) {
    val accent = LocalAppColors.current.accent
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.LibraryAddCheck, null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.secondary)
            }
            Text("Bereits importiert", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Dieses Rezept befindet sich bereits in deiner Bibliothek.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

            // Summary stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                SummaryChip("0 importiert", MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                SummaryChip("0 fehlgeschlagen", MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                SummaryChip("1 vorhanden", MaterialTheme.colorScheme.secondary)
            }

            // Clickable recipe title
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onRecipeClick(recipe) },
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.LibraryAddCheck, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                    Text(
                        text = recipe.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(Icons.Filled.ChevronRight, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            TextButton(onClick = onReset) {
                Text(stringResource(R.string.import_another), color = accent)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ManualRecipeEntry(
    onSave: (String, List<String>, String) -> Unit,
    onReset: () -> Unit
) {
    val colors = LocalAppColors.current
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var showTagPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Icon + heading
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Filled.EditNote,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = colors.accent
                )
                Text("Rezept manuell anlegen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Ohne Link — gib einfach Namen und Kategorie ein.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Rezeptname *") },
            leadingIcon = { Icon(Icons.Filled.Restaurant, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notizen (optional)") },
            leadingIcon = { Icon(Icons.Filled.Notes, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )

        // Tag picker
        OutlinedButton(
            onClick = { showTagPicker = true },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, if (selectedTags.isEmpty()) MaterialTheme.colorScheme.outline else colors.accent)
        ) {
            Icon(Icons.Filled.Label, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (selectedTags.isEmpty()) "Tags auswählen" else selectedTags.joinToString(", "))
        }

        if (selectedTags.isNotEmpty()) {
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(selectedTags) { tag ->
                    AssistChip(
                        onClick = { selectedTags = selectedTags - tag },
                        label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = { Icon(Icons.Filled.Close, null, modifier = Modifier.size(14.dp)) }
                    )
                }
            }
        }

        Button(
            onClick = { if (title.isNotBlank()) onSave(title, selectedTags, notes) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = title.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
        ) {
            Icon(Icons.Filled.Save, null)
            Spacer(Modifier.width(8.dp))
            Text("Speichern", fontWeight = FontWeight.Bold)
        }
    }

    if (showTagPicker) {
        ModalBottomSheet(onDismissRequest = { showTagPicker = false }) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text("Tags", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                com.zephron.app.ui.TAG_GROUPS.forEach { (groupName, groupTags) ->
                    Text(groupName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        groupTags.forEach { tag ->
                            FilterChip(
                                selected = tag in selectedTags,
                                onClick = { selectedTags = if (tag in selectedTags) selectedTags - tag else selectedTags + tag },
                                label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = { showTagPicker = false }, modifier = Modifier.fillMaxWidth()) { Text("Fertig") }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SummaryChip(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}
