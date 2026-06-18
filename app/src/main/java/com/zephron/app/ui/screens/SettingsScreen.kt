package com.zephron.app.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.zephron.app.utils.LogStorage
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.*
import android.graphics.Color as AndroidColor
import android.graphics.SweepGradient

import kotlin.math.roundToInt

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.zephron.app.R
import kotlinx.coroutines.launch
import com.zephron.app.ui.PartnerAvatar
import com.zephron.app.ui.theme.LocalAppColors
import com.zephron.app.viewmodel.DarkModePreference
import com.zephron.app.viewmodel.LanguagePreference
import com.zephron.app.viewmodel.SettingsViewModel

private data class DarkModeOption(
    val pref: DarkModePreference,
    @StringRes val labelRes: Int,
    val icon: ImageVector
)

private val DARK_MODE_OPTIONS = listOf(
    DarkModeOption(DarkModePreference.SYSTEM, R.string.settings_mode_system, Icons.Filled.PhoneAndroid),
    DarkModeOption(DarkModePreference.LIGHT, R.string.settings_mode_light, Icons.Filled.LightMode),
    DarkModeOption(DarkModePreference.DARK, R.string.settings_mode_dark, Icons.Filled.DarkMode),
)

private data class LanguageOption(
    val pref: LanguagePreference,
    @StringRes val labelRes: Int
)

private val LANGUAGE_OPTIONS = listOf(
    LanguageOption(LanguagePreference.SYSTEM, R.string.settings_language_system),
    LanguageOption(LanguagePreference.ENGLISH, R.string.settings_language_english),
    LanguageOption(LanguagePreference.GERMAN, R.string.settings_language_german),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
    onLanguageChange: () -> Unit = {}
) {
    val orange = LocalAppColors.current.accent
    val userName by settingsViewModel.userName.collectAsState()
    val darkMode by settingsViewModel.darkMode.collectAsState()
    val language by settingsViewModel.language.collectAsState()
    val accentColor by settingsViewModel.accentColor.collectAsState()
    val secondaryColor by settingsViewModel.secondaryColor.collectAsState()
    val syncProgress by settingsViewModel.syncProgress.collectAsState()
    val profilePictureUrl by settingsViewModel.profilePictureUrl.collectAsState()
    val firebaseUser by settingsViewModel.firebaseUser.collectAsState()
    val friends by settingsViewModel.friends.collectAsState()
    val activeFriends by settingsViewModel.activeFriends.collectAsState()
    val friendNames by settingsViewModel.friendNames.collectAsState()
    val friendNicknames by settingsViewModel.friendNicknames.collectAsState()
    val pendingRequests by settingsViewModel.pendingRequests.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var signInError by remember { mutableStateOf<String?>(null) }
    var isSigningIn by remember { mutableStateOf(false) }

    var nameDraft by remember(userName) { mutableStateOf(userName) }
    val isNameDirty = nameDraft != userName

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let { settingsViewModel.uploadProfilePicture(it) }
        }
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(LocalAppColors.current.gradientTop.copy(alpha = 0.38f), Color.Transparent),
                        endY = 900f
                    )
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
        // ── Top bar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back)
                )
            }
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // ── Profile & Account card ───────────────────────────────────────
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                if (profilePictureUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = profilePictureUrl,
                                        contentDescription = "Profile Picture",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else if (firebaseUser != null) {
                                    Text(
                                        text = (firebaseUser!!.displayName?.firstOrNull() ?: firebaseUser!!.email?.firstOrNull() ?: 'G').uppercaseChar().toString(),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                } else {
                                    Text(
                                        text = if (userName.isNotBlank()) userName.first().uppercaseChar().toString() else "?",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            
                            // Edit icon badge bottom-right
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(orange)
                                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(14.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (userName.isNotBlank()) stringResource(R.string.settings_hi_name, userName) else stringResource(R.string.settings_add_name),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (firebaseUser != null) {
                                Text(
                                    text = firebaseUser!!.email ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = "Gast-Modus",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it },
                        label = { Text(stringResource(R.string.settings_your_name)) },
                        placeholder = { Text(stringResource(R.string.settings_name_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = orange,
                            focusedLabelColor = orange
                        )
                    )

                    if (isNameDirty) {
                        Button(
                            onClick = { settingsViewModel.setUserName(nameDraft) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = orange, contentColor = Color.White)
                        ) {
                            Text("Profil speichern", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (firebaseUser != null) {
                        OutlinedButton(
                            onClick = { settingsViewModel.signOut() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Logout, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Abmelden")
                        }
                    } else {
                        if (signInError != null) {
                            Text(signInError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Button(
                            onClick = {
                                if (!isSigningIn) {
                                    isSigningIn = true
                                    signInError = null
                                    LogStorage.logToFile(context, "Google Sign-In started from Settings")
                                    scope.launch {
                                        val result = settingsViewModel.signInWithGoogle(context)
                                        isSigningIn = false
                                        result.onSuccess {
                                            LogStorage.logToFile(context, "Google Sign-In success in Settings")
                                            Toast.makeText(context, "Anmeldung erfolgreich!", Toast.LENGTH_SHORT).show()
                                        }
                                        result.onFailure { e ->
                                            isSigningIn = false
                                            LogStorage.logToFile(context, "Google Sign-In failure in Settings: ${e.message}", e)
                                            if (e.message != "Abgebrochen") {
                                                signInError = e.message
                                                Toast.makeText(context, "Anmeldefehler: ${e.localizedMessage ?: e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = orange, contentColor = Color.White),
                            enabled = !isSigningIn
                        ) {
                            if (isSigningIn) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (isSigningIn) "Wird angemeldet…" else "Mit Google anmelden", fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            "Für geräteübergreifende Synchronisierung",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Appearance card ───────────────────────────────────────────────
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_appearance),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DARK_MODE_OPTIONS.forEach { option ->
                            FilterChip(
                                selected = darkMode == option.pref,
                                onClick = { settingsViewModel.setDarkMode(option.pref) },
                                label = { Text(stringResource(option.labelRes), fontSize = 13.sp) },
                                modifier = Modifier.weight(1f),
                                leadingIcon = {
                                    Icon(
                                        imageVector = option.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }

            // ── Language card ─────────────────────────────────────────────────
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_language),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LANGUAGE_OPTIONS.forEach { option ->
                            FilterChip(
                                selected = language == option.pref,
                                onClick = {
                                    if (language != option.pref) {
                                        settingsViewModel.setLanguage(option.pref)
                                        onLanguageChange()
                                    }
                                },
                                label = { Text(stringResource(option.labelRes), fontSize = 13.sp) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }

            // Friends connection moved to the Crave page → "Freunde" tab.

            // ── Color picker card ─────────────────────────────────────────────
            ColorPickerCard(
                accentColor    = accentColor,
                secondaryColor = secondaryColor,
                onAccentChange    = { settingsViewModel.setAccentColor(it) },
                onSecondaryChange = { settingsViewModel.setSecondaryColor(it) }
            )

            // ── Image sync card ───────────────────────────────────────────────
            ImageSyncCard(progress = syncProgress, onSync = { settingsViewModel.syncImages() })

            // ── Release Notes card ────────────────────────────────────────────
            ReleaseNotesCard()

            // ── Upcoming features card ────────────────────────────────────────
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Kommende Funktionen",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    listOf(
                        Triple(Icons.Filled.Widgets, "Widget", "Rezepte direkt auf dem Homescreen"),
                        Triple(Icons.Outlined.Favorite, "Crave", "Swipe dich durch neue Rezeptideen"),
                        Triple(Icons.Filled.MoreHoriz, "...", "Weitere Features in Planung")
                    ).forEachIndexed { index, (icon, title, subtitle) ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            LocalAppColors.current.gradient
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ── Debug Logs ──────────────────────────────────────────────────
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Fehlersuche (Debug)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Sende die internen App-Logs zur Fehleranalyse.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { LogStorage.shareLogs(context) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("App-Logs teilen")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    } } }
}

@Composable
fun FriendsConnectionCard(
    currentUserId: String,
    friends: List<String>,
    activeFriends: Set<String>,
    friendNames: Map<String, String>,
    friendNicknames: Map<String, String>,
    friendPhotoUrls: Map<String, String> = emptyMap(),
    pendingRequests: List<com.zephron.app.viewmodel.FriendRequest>,
    onAddFriend: (String) -> Unit,
    onRemoveFriend: (String) -> Unit,
    onToggleActive: (String) -> Unit,
    onUpdateNickname: (String, String) -> Unit,
    onAcceptRequest: (com.zephron.app.viewmodel.FriendRequest) -> Unit,
    onDeclineRequest: (com.zephron.app.viewmodel.FriendRequest) -> Unit
) {
    val orange = LocalAppColors.current.accent
    var friendIdInput by remember { mutableStateOf("") }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = LocalContext.current

    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.verticalGradient(LocalAppColors.current.gradient)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Favorite,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.friends_connect_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.friends_connect_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Pending Requests
            if (pendingRequests.isNotEmpty()) {
                Text(stringResource(R.string.friends_open_requests), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = orange)
                pendingRequests.forEach { request ->
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = orange.copy(alpha = 0.05f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(request.fromName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.friends_wants_connect), style = MaterialTheme.typography.labelSmall)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(
                                    onClick = { onDeclineRequest(request) },
                                    modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.surface, CircleShape).border(1.dp, Color.LightGray, CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp), tint = Color.Red)
                                }
                                IconButton(
                                    onClick = { onAcceptRequest(request) },
                                    modifier = Modifier.size(32.dp).background(orange, CircleShape)
                                ) {
                                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp), tint = Color.White)
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            Text(
                text = stringResource(R.string.friends_share_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // My ID Box
            OutlinedCard(
                onClick = {
                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(currentUserId))
                    Toast.makeText(context, context.getString(R.string.friends_id_copied), Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.friends_your_id), style = MaterialTheme.typography.labelSmall, color = orange)
                        Text(currentUserId, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                    IconButton(onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                context.getString(R.string.friends_share_text, currentUserId)
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.friends_share_id)))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.friends_share_id), modifier = Modifier.size(16.dp), tint = orange)
                    }
                }
            }

            // Add Friend Input
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = friendIdInput,
                    onValueChange = { friendIdInput = it },
                    label = { Text(stringResource(R.string.friends_friend_id)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = orange,
                        focusedLabelColor = orange
                    )
                )
                Button(
                    onClick = { 
                        onAddFriend(friendIdInput.trim())
                        friendIdInput = ""
                    },
                    enabled = friendIdInput.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = orange, contentColor = Color.White)
                ) {
                    Text(stringResource(R.string.friends_request_button))
                }
            }

            // Friends List
            if (friends.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(stringResource(R.string.friends_connected), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                friends.forEach { friendId ->
                    val isActive = activeFriends.contains(friendId)
                    // Show ID if the synced name is missing or blank
                    val realName = friendNames[friendId]?.takeIf { it.isNotBlank() } ?: friendId
                    val nickname = friendNicknames[friendId] ?: ""
                    
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.Switch(
                                checked = isActive,
                                onCheckedChange = { onToggleActive(friendId) },
                                modifier = Modifier.scale(0.7f),
                                colors = androidx.compose.material3.SwitchDefaults.colors(
                                    checkedThumbColor = orange,
                                    checkedTrackColor = orange.copy(alpha = 0.5f)
                                )
                            )

                            PartnerAvatar(
                                label = realName,
                                accent = orange,
                                size = 38.dp,
                                photoUrl = friendPhotoUrls[friendId]
                            )
                            Spacer(Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                var isEditingNickname by remember { mutableStateOf(false) }
                                var nicknameDraft by remember(nickname) { mutableStateOf(nickname) }

                                if (isEditingNickname) {
                                    OutlinedTextField(
                                        value = nicknameDraft,
                                        onValueChange = { nicknameDraft = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true,
                                        trailingIcon = {
                                            IconButton(onClick = { 
                                                onUpdateNickname(friendId, nicknameDraft)
                                                isEditingNickname = false
                                            }) {
                                                Icon(Icons.Default.CheckCircle, null, tint = orange, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    )
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = when {
                                                nickname.isNotBlank() && realName.isNotBlank() -> "$nickname ($realName)"
                                                nickname.isNotBlank() -> nickname
                                                else -> realName
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        IconButton(onClick = { isEditingNickname = true }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                                Text(
                                    text = friendId,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }

                            IconButton(onClick = { onRemoveFriend(friendId) }) {
                                Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorPickerCard(
    accentColor: androidx.compose.ui.graphics.Color,
    secondaryColor: androidx.compose.ui.graphics.Color,
    onAccentChange: (androidx.compose.ui.graphics.Color) -> Unit,
    onSecondaryChange: (androidx.compose.ui.graphics.Color) -> Unit
) {
    val orange = LocalAppColors.current.accent
    val activity = LocalContext.current as? android.app.Activity
    var pendingRestart by remember { mutableStateOf(false) }

    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // ── Header ──────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.verticalGradient(LocalAppColors.current.gradient)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreHoriz,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.settings_colors),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_colors_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Akzentfarbe ─────────────────────────────────────────────────
            ColorSection(
                label      = stringResource(R.string.settings_accent_color),
                sublabel   = stringResource(R.string.settings_accent_sub),
                selected   = accentColor,
                onSelect   = { onAccentChange(it); pendingRestart = true }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Sekundärfarbe ───────────────────────────────────────────────
            ColorSection(
                label      = stringResource(R.string.settings_secondary_color),
                sublabel   = stringResource(R.string.settings_secondary_sub),
                selected   = secondaryColor,
                onSelect   = { onSecondaryChange(it); pendingRestart = true }
            )

            // ── Neustart-Button ─────────────────────────────────────────────
            if (pendingRestart) {
                androidx.compose.material3.Button(
                    onClick = { activity?.recreate() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = orange,
                        contentColor   = Color.White
                    )
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 0.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_restart_app), fontWeight = FontWeight.SemiBold)
                }
                Text(
                    text = stringResource(R.string.settings_restart_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ColorSection(
    label: String,
    sublabel: String,
    selected: androidx.compose.ui.graphics.Color,
    onSelect: (androidx.compose.ui.graphics.Color) -> Unit
) {
    val orange = LocalAppColors.current.accent
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(selected)
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(sublabel, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // The new Color Wheel Picker
                ColorWheelPicker(
                    initialColor = selected,
                    onColorChange = onSelect
                )
                
                val hexString = String.format("#%06X", (0xFFFFFF and selected.toArgb()))
                Text(
                    text = hexString,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ColorWheelPicker(
    initialColor: Color,
    onColorChange: (Color) -> Unit
) {
    var hsv by remember(initialColor) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(initialColor.toArgb(), hsv)
        mutableStateOf(hsv)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── The Color Wheel (Hue & Saturation) ──
        Box(
            modifier = Modifier
                .size(180.dp)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            
                            if (change.pressed) {
                                val w = size.width.toFloat()
                                val center = w / 2
                                val x = change.position.x - center
                                val y = change.position.y - center
                                
                                val radius = sqrt(x * x + y * y)
                                var angle = atan2(y, x) * 180 / PI
                                if (angle < 0) angle += 360
                                
                                val saturation = (radius / center).coerceIn(0f, 1f)
                                val hue = angle.toFloat().coerceIn(0f, 360f)
                                
                                val newHsv = floatArrayOf(hue, saturation, hsv[2])
                                hsv = newHsv
                                onColorChange(Color(AndroidColor.HSVToColor(newHsv)))
                                change.consume()
                            } else if (change.previousPressed && !change.pressed) {
                                // Released
                                break
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    // This second pointerInput ensures that the logic above
                    // is triggered for every new touch sequence (tap or drag start)
                    detectDragGestures(
                        onDragStart = { offset ->
                            val w = size.width.toFloat()
                            val center = w / 2
                            val x = offset.x - center
                            val y = offset.y - center
                            val radius = sqrt(x * x + y * y)
                            var angle = atan2(y, x) * 180 / PI
                            if (angle < 0) angle += 360
                            val saturation = (radius / center).coerceIn(0f, 1f)
                            val hue = angle.toFloat().coerceIn(0f, 360f)
                            val newHsv = floatArrayOf(hue, saturation, hsv[2])
                            hsv = newHsv
                            onColorChange(Color(AndroidColor.HSVToColor(newHsv)))
                        },
                        onDrag = { change, _ ->
                            val w = size.width.toFloat()
                            val center = w / 2
                            val x = change.position.x - center
                            val y = change.position.y - center
                            val radius = sqrt(x * x + y * y)
                            var angle = atan2(y, x) * 180 / PI
                            if (angle < 0) angle += 360
                            val saturation = (radius / center).coerceIn(0f, 1f)
                            val hue = angle.toFloat().coerceIn(0f, 360f)
                            val newHsv = floatArrayOf(hue, saturation, hsv[2])
                            hsv = newHsv
                            onColorChange(Color(AndroidColor.HSVToColor(newHsv)))
                            change.consume()
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val centerX = canvasWidth / 2
                val centerY = canvasHeight / 2
                val radius = canvasWidth / 2

                // Draw Hue Gradient using nativeCanvas for SweepGradient
                drawContext.canvas.nativeCanvas.apply {
                    val colors = intArrayOf(
                        AndroidColor.RED, AndroidColor.YELLOW, AndroidColor.GREEN,
                        AndroidColor.CYAN, AndroidColor.BLUE, AndroidColor.MAGENTA, AndroidColor.RED
                    )
                    val gradient = SweepGradient(centerX, centerY, colors, null)
                    val paint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        shader = gradient
                        style = android.graphics.Paint.Style.FILL
                    }
                    
                    // Saturation gradient (white center)
                    val satGradient = android.graphics.RadialGradient(
                        centerX, centerY, radius,
                        AndroidColor.WHITE, AndroidColor.TRANSPARENT,
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    val satPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        shader = satGradient
                    }

                    drawCircle(centerX, centerY, radius, paint)
                    drawCircle(centerX, centerY, radius, satPaint)
                }

                // Draw Selector Crosshair
                val selectorAngle = hsv[0] * PI / 180
                val selectorRadius = hsv[1] * radius
                val selectorOffset = Offset(
                    centerX + (selectorRadius * cos(selectorAngle)).toFloat(),
                    centerY + (selectorRadius * sin(selectorAngle)).toFloat()
                )

                drawCircle(
                    color = Color.Black,
                    radius = 8.dp.toPx(),
                    center = selectorOffset,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = Color.White,
                    radius = 7.dp.toPx(),
                    center = selectorOffset,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // ── Brightness Slider (Value) ──
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                text = "Helligkeit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = hsv[2],
                onValueChange = { 
                    val newHsv = floatArrayOf(hsv[0], hsv[1], it)
                    hsv = newHsv
                    onColorChange(Color(AndroidColor.HSVToColor(newHsv)))
                },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(AndroidColor.HSVToColor(floatArrayOf(hsv[0], hsv[1], hsv[2]))),
                    activeTrackColor = Color(AndroidColor.HSVToColor(floatArrayOf(hsv[0], hsv[1], 1f))).copy(alpha = 0.5f)
                )
            )
        }
    }
}

private data class ReleaseEntry(
    val version: String,
    val date: String,
    val changes: List<String>
)

private val RELEASE_NOTES = listOf(
    ReleaseEntry(
        version = "4.6",
        date = "Juni 2026",
        changes = listOf(
            "🔍 Import-Button prüft jetzt ob Rezept bereits in eigener Galerie vorhanden ist",
            "📋 Rezeptansicht zeigt überall korrekt Bearbeiten/Löschen (eigene) oder Import (Partner)",
            "📅 Planer: Rezepte von Freunden korrekt als Partner-Rezepte erkannt",
            "🛒 Einkaufsliste: Freundesauswahl im Tab verfügbar"
        )
    ),
    ReleaseEntry(
        version = "4.5",
        date = "Juni 2026",
        changes = listOf(
            "📥 Partnerrezepte importieren: Im Crave-Modus Rezepte direkt in die eigene Galerie übernehmen",
            "🛒 Einkaufsliste zeigt alle Rezepte der Woche (nicht nur heute)",
            "✏️ Crave-Rezeptansicht zeigt nur noch Import-Button (kein Bearbeiten/Löschen)"
        )
    ),
    ReleaseEntry(
        version = "4.4",
        date = "Juni 2026",
        changes = listOf(
            "📊 Bilder-Sync mit Fortschrittsanzeige: Circular Progress & Prozentanzeige",
            "🔄 Sync-Button in den Einstellungen zeigt genauen Upload-Status"
        )
    ),
    ReleaseEntry(
        version = "4.3",
        date = "Juni 2026",
        changes = listOf(
            "🖼️ Bilder-Sync: Rezeptbilder von Android sind jetzt auch auf iOS sichtbar",
            "🔧 Bestehende Bilder werden beim nächsten Start automatisch synchronisiert",
            "📏 Heute-Tab: Carousel-Karten verdecken keine Abschnitts-Überschriften mehr"
        )
    ),
    ReleaseEntry(
        version = "4.2",
        date = "Juni 2026",
        changes = listOf(
            "🔝 Buttons (Zurück, Lesezeichen, Löschen) wieder oben auf dem Hero-Bild",
            "🎨 Zutaten-Checkboxen in der Primärfarbe der App"
        )
    ),
    ReleaseEntry(
        version = "4.0",
        date = "Juni 2026",
        changes = listOf(
            "👥 Freunde-Rezepte: Rezepte von Freunden ansehen & als eigene übernehmen",
            "❤️ Täglicher Swipe-Reset: Ja/Nein-Entscheidungen gelten nur noch für den aktuellen Tag",
            "🖼️ Bildercaching: Bilder von Freunden werden dauerhaft gespeichert & laden sofort",
            "⭐ Bewertungsfilter: Sterne-Chips in einer Zeile, nie mehr abgeschnitten",
            "🗑️ Matches löschen: Einzelne Matches per Swipe oder Mehrfachauswahl löschen",
            "🏷️ Tags überarbeitet: Diät-Tag separat, Bearbeitung über Stift-Icon",
            "🔢 Portionen speichern: +/- Buttons skalieren Zutaten & werden dauerhaft gespeichert"
        )
    ),
    ReleaseEntry(
        version = "1.1",
        date = "Juni 2026",
        changes = listOf(
            "✨ Koch-Assistent: schwebender Gemini-Chat (20 Fragen/Tag), frei verschiebbar & einklappbar",
            "📅 Wochenplan: gemeinsamer Essensplan pro Freund (neuer Tab)",
            "🔖 Favoriten: Rezepte merken – getrennt von der Bewertung",
            "🍽️ Portionen skalieren: Zutatenmengen rechnen automatisch um",
            "💚 Crave überarbeitet: Partner wählen, Sync, Match-Animation & ‚It's a Match'",
            "🧹 Match-Historie: nach Freund filtern & leeren",
            "🌍 Mehr Küchen-Filter + durchgehend Deutsch/Englisch",
            "🔁 Swipes pro Freund-Paar + Rezept-Änderungen werden beim Sync übernommen"
        )
    ),
    ReleaseEntry(
        version = "1.0.5",
        date = "Juni 2025",
        changes = listOf(
            "🔥 Match-System: Finde Rezepte, die deine Freunde auch mögen",
            "🤝 Einladungs-System: Freunde annehmen oder ablehnen",
            "🔔 Push-Benachrichtigungen für neue Matches & Anfragen",
            "📜 Match-Historie: Sieh dir alle gemeinsamen Treffer an",
            "👥 Freunde-System: Verbinde dich mit anderen Köchen",
            "🏷️ Individuelle Spitznamen & selektive Synchronisierung",
            "🔑 Google Login & Cloud-Sync Optimierungen"
        )
    ),
    ReleaseEntry(
        version = "1.0.4",
        date = "Juni 2025",
        changes = listOf(
            "🦆 Ente als eigener Protein-Tag hinzugefügt",
            "✨ Konsistentes App-Icon im gesamten System",
            "🎨 Dynamischer Fokus & Blur Effekt im Carousel",
            "🚀 Performance-Optimierungen beim Bild-Loading"
        )
    ),
    ReleaseEntry(
        version = "1.0.3",
        date = "Juni 2025",
        changes = listOf(
            "Rezeptbild nachträglich aus Galerie ersetzen",
            "TikTok Slideshows: 3 Fallback-Strategien für zuverlässige Bilderkennung",
            "Lokale Bilder werden dauerhaft im App-Speicher gespeichert"
        )
    ),
    ReleaseEntry(
        version = "1.0.2",
        date = "Juni 2025",
        changes = listOf(
            "TikTok/Instagram-Link teilen → landet direkt im Importfeld",
            "Verwerfen-Button beim Einzel-Import",
            "Import-Felder bleiben beim Tab-Wechsel erhalten",
            "Multi-Import: Auto-Modus importiert alles ohne Bestätigung",
            "TikTok Slideshows: alle Bilder gespeichert & wischbar",
            "Gemini analysiert Slideshow-Bilder direkt (Vision)",
            "Fehlgeschlagene URLs nach Import kopierbar"
        )
    ),
    ReleaseEntry(
        version = "1.0.1",
        date = "Juni 2025",
        changes = listOf(
            "Neue Gang-Kategorie: Vorspeise, Hauptgang, Dessert, Getränk",
            "Pute als eigener Filter-Tag (getrennt von Hähnchen)",
            "Farbauswahl für Akzent- & Sekundärfarbe in den Einstellungen",
            "Swipe-Navigation zwischen den Tabs",
            "Alternatives Pexels-Bild beim Import auswählbar",
            "Batch-Import: Rezepte einzeln prüfen & speichern",
            "App-Icon stimmt jetzt mit Splash-Screen überein"
        )
    ),
    ReleaseEntry(
        version = "1.0",
        date = "Mai 2025",
        changes = listOf(
            "Erstveröffentlichung",
            "Rezepte von Instagram, TikTok & Websites importieren",
            "Gemini-KI extrahiert Zutaten & Schritte",
            "Rezept des Tages mit täglicher Benachrichtigung",
            "Filter nach Tags, Kochzeit & Bewertung",
            "Dark Mode & Sprachauswahl"
        )
    )
)

@Composable
private fun ImageSyncCard(
    progress: SettingsViewModel.ImageSyncProgress,
    onSync: () -> Unit
) {
    val orange = LocalAppColors.current.accent
    val green  = Color(0xFF34693A)
    val animatedFraction by animateFloatAsState(
        targetValue = progress.fraction,
        animationSpec = tween(durationMillis = 400),
        label = "syncProgress"
    )

    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.verticalGradient(LocalAppColors.current.gradient)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.CloudUpload, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Bilder synchronisieren", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = when (progress.state) {
                            SettingsViewModel.ImageSyncState.RUNNING ->
                                if (progress.total > 0) "${progress.done} / ${progress.total} hochgeladen…"
                                else "Lädt hoch…"
                            SettingsViewModel.ImageSyncState.DONE ->
                                if (progress.total == 0) "Alle Bilder bereits synchronisiert ✓"
                                else buildString {
                                    append("${progress.done} von ${progress.total} synchronisiert")
                                    if (progress.failed > 0) append(" · ${progress.failed} fehlgeschlagen")
                                }
                            SettingsViewModel.ImageSyncState.ERROR -> "Fehler beim Sync"
                            else -> "Rezeptbilder für Freunde sichtbar machen"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (progress.state) {
                            SettingsViewModel.ImageSyncState.DONE  -> green
                            SettingsViewModel.ImageSyncState.ERROR -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                when (progress.state) {
                    SettingsViewModel.ImageSyncState.RUNNING -> {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                            CircularProgressIndicator(
                                progress = { animatedFraction },
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 4.dp,
                                color = orange,
                                trackColor = orange.copy(alpha = 0.15f)
                            )
                            if (progress.total > 0) {
                                Text(
                                    text = "${(animatedFraction * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = orange
                                )
                            }
                        }
                    }
                    SettingsViewModel.ImageSyncState.DONE -> {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                            CircularProgressIndicator(
                                progress = { 1f },
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 4.dp,
                                color = green,
                                trackColor = green.copy(alpha = 0.15f)
                            )
                            Icon(Icons.Filled.CheckCircle, null, tint = green, modifier = Modifier.size(20.dp))
                        }
                    }
                    else -> {
                        FilledTonalButton(
                            onClick = onSync,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Sync", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseNotesCard() {
    val orange = LocalAppColors.current.accent
    var showHistory by remember { mutableStateOf(false) }
    val latest = RELEASE_NOTES.first()

    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            // ── Header ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.verticalGradient(LocalAppColors.current.gradient)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.NewReleases, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Was ist neu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Version ${latest.version}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Latest Changes Highlights ──
            latest.changes.take(3).forEach { change ->
                ChangeRow(change, orange)
            }
            if (latest.changes.size > 3) {
                Text(
                    text = "... und ${latest.changes.size - 3} weitere Änderungen",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── History Toggle ──
            OutlinedButton(
                onClick = { showHistory = !showHistory },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(if (showHistory) "Verlauf schließen" else "Vollständiger Versionsverlauf", fontSize = 13.sp)
                Icon(
                    imageVector = if (showHistory) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).padding(start = 4.dp)
                )
            }

            AnimatedVisibility(visible = showHistory) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    RELEASE_NOTES.forEachIndexed { index, entry ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 10.dp))
                        
                        var expanded by remember { mutableStateOf(index == 0 && latest.changes.size > 3) } // Auto-expand latest if it has more changes
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(entry.version, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = orange, modifier = Modifier.width(48.dp))
                                Text(entry.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            AnimatedVisibility(visible = expanded) {
                                Column(modifier = Modifier.padding(start = 4.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    entry.changes.forEach { ChangeRow(it, orange) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangeRow(text: String, bulletColor: Color) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(Icons.Filled.CheckCircle, null, tint = bulletColor.copy(alpha = 0.7f), modifier = Modifier.size(14.dp).padding(top = 2.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box(modifier = Modifier.padding(20.dp)) {
            content()
        }
    }
}
