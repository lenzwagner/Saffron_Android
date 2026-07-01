package com.zephron.app.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.zephron.app.data.Match
import com.zephron.app.data.RecipeDatabase
import com.zephron.app.data.RecipeRepository
import com.zephron.app.data.Recipe
import com.zephron.app.utils.LogStorage
import com.zephron.app.utils.NotificationHelper
import com.zephron.app.utils.ThumbnailStore
import com.zephron.app.viewmodel.NotificationType
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class DarkModePreference { SYSTEM, LIGHT, DARK }
enum class LanguagePreference(val tag: String) { SYSTEM("system"), ENGLISH("en"), GERMAN("de") }
enum class AuthMode { NONE, GUEST, GOOGLE }

data class FriendRequest(
    val fromId: String,
    val fromName: String,
    val requestId: String
)

data class PlanEntry(
    val id: String,
    val day: String,            // MON, TUE, …, SUN
    val mealType: String = "DINNER", // BREAKFAST, LUNCH, DINNER
    val recipeUrl: String,
    val title: String,
    val thumbnailUrl: String,
    val addedBy: String
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("saffron_prefs", Context.MODE_PRIVATE)
    private val firestore = Firebase.firestore
    private val auth = Firebase.auth
    private val storage = Firebase.storage
    private val tag = "SettingsViewModel"

    // ── StateFlows (Initialize BEFORE init block) ──────────────────────────────
    private val _onboarded = MutableStateFlow(prefs?.getBoolean("onboarded", false) ?: false)
    val onboarded: StateFlow<Boolean> = _onboarded

    private val _userName = MutableStateFlow(prefs?.getString("user_name", "") ?: "")
    val userName: StateFlow<String> = _userName

    private val _darkMode = MutableStateFlow(
        try {
            DarkModePreference.valueOf(
                prefs?.getString("dark_mode", DarkModePreference.SYSTEM.name) ?: DarkModePreference.SYSTEM.name
            )
        } catch (_: Exception) {
            DarkModePreference.SYSTEM
        }
    )
    val darkMode: StateFlow<DarkModePreference> = _darkMode

    private val _language = MutableStateFlow(
        LanguagePreference.entries.find {
            it.tag == prefs?.getString("language", LanguagePreference.SYSTEM.tag)
        } ?: LanguagePreference.SYSTEM
    )
    val language: StateFlow<LanguagePreference> = _language

    private val _authMode = MutableStateFlow(
        try {
            AuthMode.valueOf(prefs?.getString("auth_mode", AuthMode.NONE.name) ?: AuthMode.NONE.name)
        } catch (_: Exception) {
            AuthMode.NONE
        }
    )
    val authMode: StateFlow<AuthMode> = _authMode

    private val _accentColor = MutableStateFlow(
        Color(prefs?.getInt("accent_color", Color(0xFFFF6B35).toArgb()) ?: Color(0xFFFF6B35).toArgb())
    )
    val accentColor: StateFlow<Color> = _accentColor

    private val _secondaryColor = MutableStateFlow(
        Color(prefs?.getInt("secondary_color", Color(0xFF34693A).toArgb()) ?: Color(0xFF34693A).toArgb())
    )
    val secondaryColor: StateFlow<Color> = _secondaryColor

    private val _profilePictureUrl = MutableStateFlow(prefs?.getString("profile_picture_url", "") ?: "")
    val profilePictureUrl: StateFlow<String> = _profilePictureUrl


    private val _friends = MutableStateFlow<List<String>>(
        prefs?.getStringSet("cached_friends", emptySet())?.toList() ?: emptyList()
    )
    val friends: StateFlow<List<String>> = _friends

    private val _friendNames = MutableStateFlow<Map<String, String>>(
        prefs?.getStringSet("cached_friend_names", emptySet())?.associate {
            val parts = it.split("|", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else "" to ""
        }?.filter { it.key.isNotBlank() } ?: emptyMap()
    )
    val friendNames: StateFlow<Map<String, String>> = _friendNames

    private val _friendNicknames = MutableStateFlow<Map<String, String>>(
        prefs?.getStringSet("cached_friend_nicknames", emptySet())?.associate {
            val parts = it.split("|", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else "" to ""
        }?.filter { it.key.isNotBlank() } ?: emptyMap()
    )
    val friendNicknames: StateFlow<Map<String, String>> = _friendNicknames

    private val _friendPhotoUrls = MutableStateFlow<Map<String, String>>(
        prefs?.getStringSet("cached_friend_photos", emptySet())?.associate {
            val parts = it.split("|", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else "" to ""
        }?.filter { it.key.isNotBlank() } ?: emptyMap()
    )
    val friendPhotoUrls: StateFlow<Map<String, String>> = _friendPhotoUrls

    private val _pendingRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val pendingRequests: StateFlow<List<FriendRequest>> = _pendingRequests

    private val _matches = MutableStateFlow<List<Match>>(emptyList())
    val matches: StateFlow<List<Match>> = _matches

    private val _newMatchFlow = kotlinx.coroutines.flow.MutableSharedFlow<Recipe>()
    val newMatchFlow = _newMatchFlow.asSharedFlow()

    private val _hasNewFriend = MutableStateFlow(false)
    val hasNewFriend: StateFlow<Boolean> = _hasNewFriend

    private val _eventFlow = kotlinx.coroutines.flow.MutableSharedFlow<SettingsEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    sealed class SettingsEvent {
        data class ShowNotification(val message: String, val type: NotificationType = NotificationType.INFO) : SettingsEvent()
    }

    private val _activeFriends = MutableStateFlow<Set<String>>(
        prefs?.getStringSet("cached_active_friends", emptySet()) ?: emptySet()
    )
    val activeFriends: StateFlow<Set<String>> = _activeFriends

    // ── Crave: the single friend you currently want to match with ──────────────
    private val _cravePartnerId = MutableStateFlow(
        prefs?.getString("crave_partner_id", null)?.ifBlank { null }
    )
    val cravePartnerId: StateFlow<String?> = _cravePartnerId

    private val _craveSyncing = MutableStateFlow(false)
    val craveSyncing: StateFlow<Boolean> = _craveSyncing

    // ── Weekly meal plan, shared with one friend ───────────────────────────────
    private val _planPartnerId = MutableStateFlow(
        prefs?.getString("plan_partner_id", null)?.ifBlank { null }
    )
    val planPartnerId: StateFlow<String?> = _planPartnerId

    private val _planEntries = MutableStateFlow<List<PlanEntry>>(emptyList())
    val planEntries: StateFlow<List<PlanEntry>> = _planEntries

    private val _planDebugInfo = MutableStateFlow("")
    val planDebugInfo: StateFlow<String> = _planDebugInfo

    /** Publicly exposed for the debug indicator in PlanScreen. */
    val currentPlanPairId: String? get() = planPairId()

    private var planListener: com.google.firebase.firestore.ListenerRegistration? = null

    // URLs of recipes I've already swiped — SCOPED to the currently selected
    // Crave partner (per friend-pair). Persisted locally + in Firestore so the
    // deck doesn't re-show them after a restart, until the user resets.
    private val _swipedUrls = MutableStateFlow<Set<String>>(emptySet())
    val swipedUrls: StateFlow<Set<String>> = _swipedUrls

    private val _firebaseUser = MutableStateFlow<FirebaseUser?>(Firebase.auth.currentUser)
    val firebaseUser: StateFlow<FirebaseUser?> = _firebaseUser

    private val dao = RecipeDatabase.getDatabase(application).recipeDao()
    private val repository = RecipeRepository(dao)

    private var inboxListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var isInitialInboxLoad = true

    // ── Computed Props ───────────────────────────────────────────────────────
    val isOnboarded: Boolean
        get() = onboarded.value

    val isLoginComplete: Boolean
        get() = prefs?.getBoolean("login_complete", onboarded.value) ?: false

    private val _loginComplete = MutableStateFlow(isLoginComplete)
    val loginCompleteFlow: StateFlow<Boolean> = _loginComplete

    private val ONBOARDING_VERSION = 2
    init {
        Log.d(tag, "SettingsViewModel init. Current User: ${auth.currentUser?.email}")

        // Load matches from DB
        viewModelScope.launch {
            dao.getAllMatches().collect {
                _matches.value = it
            }
        }

        val savedVersion = prefs?.getInt("onboarding_version", 1) ?: 1
        if (savedVersion < ONBOARDING_VERSION) {
            prefs?.edit()
                ?.putBoolean("onboarded", false)
                ?.putBoolean("login_complete", false)
                ?.remove("user_name")
                ?.putInt("onboarding_version", ONBOARDING_VERSION)
                ?.apply()
            
            _onboarded.value = false
            _loginComplete.value = false
        }

        // Trigger sync if already logged in
        auth.currentUser?.uid?.let { uid ->
            reloadSwipesForCurrentPair()
            syncAll(uid)
            startPlanListener()
        }
    }

    // ── Setters ──────────────────────────────────────────────────────────────
    fun setAccentColor(color: Color, sync: Boolean = true) {
        _accentColor.value = color
        prefs?.edit()?.putInt("accent_color", color.toArgb())?.apply()
        if (sync) syncSettingsToCloud()
    }

    fun setSecondaryColor(color: Color, sync: Boolean = true) {
        _secondaryColor.value = color
        prefs?.edit()?.putInt("secondary_color", color.toArgb())?.apply()
        if (sync) syncSettingsToCloud()
    }

    fun setUserName(name: String, sync: Boolean = true) {
        _userName.value = name
        prefs?.edit()?.putString("user_name", name)?.apply()
        if (sync) syncSettingsToCloud()
    }

    fun setDarkMode(mode: DarkModePreference, sync: Boolean = true) {
        _darkMode.value = mode
        prefs?.edit()?.putString("dark_mode", mode.name)?.apply()
        if (sync) syncSettingsToCloud()
    }

    fun setLanguage(lang: LanguagePreference, sync: Boolean = true) {
        _language.value = lang
        prefs?.edit()?.putString("language", lang.tag)?.apply()
        if (sync) syncSettingsToCloud()
    }

    fun uploadProfilePicture(uri: android.net.Uri) {
        val context = getApplication<Application>().applicationContext
        val uid = auth.currentUser?.uid ?: run {
            LogStorage.logToFile(context, "Upload failed: No user logged in")
            return
        }
        
        LogStorage.logToFile(context, "Starting profile picture upload for UID: $uid")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ref = storage.reference.child("users/$uid/profile.jpg")
                LogStorage.logToFile(context, "Uploading file to path: ${ref.path}")
                
                ref.putFile(uri).await()
                LogStorage.logToFile(context, "Upload successful, fetching download URL...")
                
                val downloadUrl = ref.downloadUrl.await().toString()
                LogStorage.logToFile(context, "New Profile URL: $downloadUrl")
                
                _profilePictureUrl.value = downloadUrl
                prefs?.edit()?.putString("profile_picture_url", downloadUrl)?.apply()
                syncSettingsToCloud()
            } catch (e: Exception) {
                Log.e(tag, "Error uploading profile picture", e)
                LogStorage.logToFile(context, "Error uploading profile picture: ${e.message}", e)
            }
        }
    }

    /**
     * Sends a friend request to [friendId]. We do NOT add them locally yet —
     * the friendship only becomes real once they accept (handled in the inbox
     * listener via the "request_accepted" message). Returns false if the input
     * is invalid or we are already friends.
     */
    fun sendFriendRequest(friendId: String): Boolean {
        val myUid = auth.currentUser?.uid ?: return false
        if (friendId.isBlank() || friendId == myUid) return false
        // A Firebase UID is a short token: no slashes, no whitespace/line breaks.
        // This guards against pasting arbitrary text (e.g. the whole rules file)
        // into the ID field, which would otherwise build an invalid Firestore path.
        if (friendId.length > 128 || friendId.any { it == '/' || it.isWhitespace() }) return false
        if (_friends.value.contains(friendId)) return false

        val myName = _userName.value.ifBlank { auth.currentUser?.displayName ?: "Ein Freund" }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                firestore.collection("users").document(friendId)
                    .collection("inbox").add(mapOf(
                        "type" to "friend_request",
                        "fromName" to myName,
                        "fromId" to myUid,
                        "timestamp" to com.google.firebase.Timestamp.now()
                    ))
            } catch (e: Exception) {
                Log.e(tag, "Error sending friend request", e)
            }
        }
        return true
    }

    fun removeFriend(friendId: String) {
        _friends.value = _friends.value.filter { it != friendId }
        _activeFriends.value = _activeFriends.value - friendId
        _friendNicknames.value = _friendNicknames.value - friendId
        syncSettingsToCloud()
    }

    fun toggleFriendActive(friendId: String) {
        if (_activeFriends.value.contains(friendId)) {
            _activeFriends.value = _activeFriends.value - friendId
        } else {
            _activeFriends.value = _activeFriends.value + friendId
        }
        syncSettingsToCloud()
        // Trigger a re-sync or a filter refresh? For now, re-syncing works
        syncAll(auth.currentUser?.uid ?: "")
    }

    /** Picks the friend to match with in Crave (null = none). */
    fun setCravePartner(friendId: String?) {
        val clean = friendId?.ifBlank { null }
        _cravePartnerId.value = clean
        prefs?.edit()?.putString("crave_partner_id", clean ?: "")?.apply()
        // Swipes are per friend-pair → load this pair's swiped set.
        reloadSwipesForCurrentPair()
    }

    /** Pulls my recipes + the selected partner's recipes so both decks line up. */
    fun syncCravePartner() {
        val myUid = auth.currentUser?.uid ?: return
        val partnerId = _cravePartnerId.value
        viewModelScope.launch(Dispatchers.IO) {
            _craveSyncing.value = true
            try {
                repository.syncFromCloud(myUid)
                if (!partnerId.isNullOrBlank()) {
                    repository.syncFromCloud(partnerId)
                    // Pre-warm Coil disk cache for partner thumbnails so they
                    // load instantly in the swipe deck.
                    val context = getApplication<Application>()
                    repository.getAllRecipesOnce()
                        .filter { it.ownerId == partnerId && it.thumbnailUrl.isNotBlank() }
                        .forEach { recipe ->
                            val req = ImageRequest.Builder(context)
                                .data(recipe.thumbnailUrl)
                                .build()
                            context.imageLoader.enqueue(req)
                        }
                }
                reloadSwipesForCurrentPair()
            } catch (e: Exception) {
                Log.e(tag, "Error syncing crave partner", e)
            } finally {
                _craveSyncing.value = false
            }
        }
    }

    // ── Weekly plan ────────────────────────────────────────────────────────────
    /**
     * Solo  → myUID alone
     * Pair  → sorted([me, partner]).joinToString("_")
     */
    private fun planPairId(): String? {
        val me = auth.currentUser?.uid ?: return null
        val partner = _planPartnerId.value
        return if (partner.isNullOrBlank()) me
               else listOf(me, partner).sorted().joinToString("_")
    }

    /** null = Solo mode (plan stored under own UID). */
    fun setPlanPartner(friendId: String?) {
        val clean = friendId?.ifBlank { null }
        _planPartnerId.value = clean
        prefs?.edit()?.putString("plan_partner_id", clean ?: "")?.apply()
        startPlanListener()
    }

    private fun startPlanListener() {
        planListener?.remove()
        _planEntries.value = emptyList()
        val pairId = planPairId() ?: return
        Log.d(tag, "🗓 Plan listener START: mealPlans/$pairId/entries (partner=${_planPartnerId.value ?: "solo"})")
        planListener = firestore.collection("mealPlans").document(pairId)
            .collection("entries")
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snap, e ->
                if (e != null) {
                    Log.e(tag, "❌ Plan listener error", e)
                    return@addSnapshotListener
                }
                val fromCache = snap?.metadata?.isFromCache ?: true
                val count = snap?.documents?.size ?: 0
                Log.d(tag, "📥 Plan snapshot: $count docs, fromCache=$fromCache, hasPendingWrites=${snap?.metadata?.hasPendingWrites()}")
                _planDebugInfo.value = "$count Einträge • ${if (fromCache) "Cache" else "Server"}"
                _planEntries.value = snap?.documents?.map { d ->
                    PlanEntry(
                        id = d.id,
                        day = d.getString("day") ?: "",
                        mealType = d.getString("mealType") ?: "DINNER",
                        recipeUrl = d.getString("recipeUrl") ?: "",
                        title = d.getString("title") ?: "",
                        thumbnailUrl = d.getString("thumbnailUrl") ?: "",
                        addedBy = d.getString("addedBy") ?: ""
                    )
                } ?: emptyList()
            }
    }

    fun forceFetchPlanFromServer() {
        val pairId = planPairId() ?: run { _planDebugInfo.value = "Kein pairId!"; return }
        _planDebugInfo.value = "Lade vom Server…"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val snap = firestore.collection("mealPlans").document(pairId)
                    .collection("entries")
                    .get(com.google.firebase.firestore.Source.SERVER)
                    .await()
                Log.d(tag, "🔄 Force-fetch: ${snap.documents.size} docs from server at mealPlans/$pairId/entries")
                _planDebugInfo.value = "Server: ${snap.documents.size} Einträge"
                _planEntries.value = snap.documents.map { d ->
                    PlanEntry(
                        id = d.id,
                        day = d.getString("day") ?: "",
                        mealType = d.getString("mealType") ?: "DINNER",
                        recipeUrl = d.getString("recipeUrl") ?: "",
                        title = d.getString("title") ?: "",
                        thumbnailUrl = d.getString("thumbnailUrl") ?: "",
                        addedBy = d.getString("addedBy") ?: ""
                    )
                }
            } catch (e: Exception) {
                Log.e(tag, "Force-fetch error", e)
                _planDebugInfo.value = "Fehler: ${e.message}"
            }
        }
    }

    fun addToPlan(day: String, recipe: Recipe, mealType: String = "DINNER") {
        val me = auth.currentUser?.uid ?: return
        val pairId = planPairId() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val col = firestore.collection("mealPlans").document(pairId).collection("entries")
                // Remove any existing entry for the same day+mealType slot (max 1 per slot).
                val existing = _planEntries.value.filter { it.day == day && it.mealType == mealType }
                existing.forEach { col.document(it.id).delete() }

                col.add(
                    mapOf(
                        "day" to day,
                        "mealType" to mealType,
                        "recipeUrl" to recipe.url,
                        "title" to recipe.title,
                        "thumbnailUrl" to recipe.thumbnailUrl,
                        "addedBy" to me,
                        "createdAt" to com.google.firebase.Timestamp.now()
                    )
                )
            } catch (e: Exception) {
                Log.e(tag, "Error adding to plan", e)
            }
        }
    }

    fun removeFromPlan(entryId: String) {
        val pairId = planPairId() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                firestore.collection("mealPlans").document(pairId)
                    .collection("entries").document(entryId).delete()
            } catch (e: Exception) {
                Log.e(tag, "Error removing from plan", e)
            }
        }
    }

    /** Deterministic id for the current Crave pair (sorted uids, or solo = me). */
    private fun cravePairId(): String? {
        val me = auth.currentUser?.uid ?: return null
        val partner = _cravePartnerId.value
        return if (partner.isNullOrBlank()) me else listOf(me, partner).sorted().joinToString("_")
    }

    private fun swipedPrefsKey(pairId: String) = "cswipes_$pairId"

    private fun persistSwiped() {
        val pairId = cravePairId() ?: return
        prefs?.edit()
            ?.putStringSet(swipedPrefsKey(pairId), _swipedUrls.value)
            ?.putString("${swipedPrefsKey(pairId)}_date", todayString)
            ?.apply()
    }

    /** Today's date as "yyyy-MM-dd" string — used to scope swipes to a single day. */
    private val todayString: String
        get() {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            return sdf.format(java.util.Date())
        }

    /** Loads the swiped set for the current pair: instantly from prefs (today only), then refreshed from Firestore. */
    private fun reloadSwipesForCurrentPair() {
        val pairId = cravePairId() ?: run { _swipedUrls.value = emptySet(); return }
        val today = todayString

        // Instant from cache — but only if the cached date matches today
        val cachedDate = prefs?.getString("${swipedPrefsKey(pairId)}_date", "") ?: ""
        _swipedUrls.value = if (cachedDate == today) {
            prefs?.getStringSet(swipedPrefsKey(pairId), emptySet()) ?: emptySet()
        } else {
            emptySet()  // stale cache from a previous day → ignore
        }

        // Refresh from cloud (today only)
        val me = auth.currentUser?.uid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val snap = firestore.collection("users").document(me)
                    .collection("craveSwipes")
                    .whereEqualTo("pairId", pairId)
                    .whereEqualTo("date", today)
                    .get().await()
                val urls = snap.documents.mapNotNull { it.getString("url") }.toSet()
                _swipedUrls.value = urls
                persistSwiped()
            } catch (e: Exception) {
                Log.e(tag, "Error loading swipes", e)
            }
        }
    }

    // ── Image sync ────────────────────────────────────────────────────────────

    enum class ImageSyncState { IDLE, RUNNING, DONE, ERROR }

    data class ImageSyncProgress(
        val state: ImageSyncState = ImageSyncState.IDLE,
        val done: Int = 0,
        val total: Int = 0,
        val failed: Int = 0
    ) {
        val fraction: Float get() = if (total > 0) done.toFloat() / total else 0f
        val isRunning get() = state == ImageSyncState.RUNNING
    }

    private val _syncProgress = MutableStateFlow(ImageSyncProgress())
    val syncProgress: StateFlow<ImageSyncProgress> = _syncProgress

    /** Manually upload local thumbnails AND download remote ones for offline use. */
    fun syncImages() {
        if (_syncProgress.value.isRunning) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                syncAllImagesInternal(dao)
            } catch (e: Exception) {
                Log.e(tag, "Manual image sync failed", e)
                _syncProgress.value = _syncProgress.value.copy(state = ImageSyncState.ERROR)
            }
        }
    }

    /**
     * Combined sync:
     * 1. Uploads local file:// thumbnails of MY recipes to Firebase (to share).
     * 2. Downloads remote https:// thumbnails of ANY recipe to local cache (for performance/offline).
     */
    private suspend fun syncAllImagesInternal(dao: com.zephron.app.data.RecipeDao, allRecipes: List<Recipe>? = null) {
        val myUid = auth.currentUser?.uid ?: return
        val ctx = getApplication<Application>().applicationContext
        val all = allRecipes ?: dao.getAllRecipesOnce()

        // Phase A: Upload local thumbnails
        val toUpload = all.filter { it.ownerId == myUid && it.thumbnailUrl.startsWith("file://") }
        // Phase B: Download remote thumbnails that aren't cached yet
        val toDownload = all.filter { r ->
            r.thumbnailUrl.isNotBlank() && 
            !r.thumbnailUrl.startsWith("file://") &&
            ThumbnailStore.localPath(ctx, ThumbnailStore.docIdFor(r)) == null
        }

        val total = toUpload.size + toDownload.size
        if (total == 0) {
            _syncProgress.value = ImageSyncProgress(state = ImageSyncState.DONE, done = 0, total = 0)
            return
        }

        Log.d(tag, "Syncing images: ${toUpload.size} to upload, ${toDownload.size} to download")
        _syncProgress.value = ImageSyncProgress(state = ImageSyncState.RUNNING, done = 0, total = total)
        var done = 0; var failed = 0

        // 1. Uploads
        toUpload.forEach { recipe ->
            try {
                val docId = java.util.Base64.getUrlEncoder().encodeToString(recipe.url.toByteArray())
                val file = java.io.File(java.net.URI(recipe.thumbnailUrl))
                if (!file.exists()) { failed++; return@forEach }
                val bytes = file.readBytes()
                val ref = storage.reference.child("users/$myUid/recipes/$docId.jpg")
                ref.putBytes(bytes).await()
                val downloadUrl = ref.downloadUrl.await().toString()
                repository.update(recipe.copy(thumbnailUrl = downloadUrl))
                done++
                _syncProgress.value = ImageSyncProgress(ImageSyncState.RUNNING, done, total, failed)
            } catch (e: Exception) {
                failed++; Log.w(tag, "Upload failed for ${recipe.title}", e)
                _syncProgress.value = ImageSyncProgress(ImageSyncState.RUNNING, done, total, failed)
            }
        }

        // 2. Downloads
        toDownload.forEach { recipe ->
            try {
                val docId = ThumbnailStore.docIdFor(recipe)
                ThumbnailStore.download(ctx, recipe.thumbnailUrl, docId)
                done++
                _syncProgress.value = ImageSyncProgress(ImageSyncState.RUNNING, done, total, failed)
            } catch (e: Exception) {
                failed++; Log.w(tag, "Download failed for ${recipe.title}", e)
                _syncProgress.value = ImageSyncProgress(ImageSyncState.RUNNING, done, total, failed)
            }
        }

        _syncProgress.value = ImageSyncProgress(ImageSyncState.DONE, done, total, failed)
        Log.d(tag, "Image sync complete. $done ok, $failed failed.")
    }

    /**
     * Migrates any remote thumbnail URLs still stored in Room to local files,
     * then pre-warms Coil's memory cache from those local files.
     * New imports already go through ThumbnailStore during save, so over time
     * this list of "still remote" recipes will shrink to zero.
     */
    private fun preloadAllThumbnails(dao: com.zephron.app.data.RecipeDao) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>().applicationContext
                val all = dao.getAllRecipesOnce()
                ThumbnailStore.migrateExisting(ctx, dao, all)
                Log.d(tag, "Thumbnail migration complete")
                // Start background sync for anything missed
                syncAllImagesInternal(dao, all)
            } catch (e: Exception) {
                Log.e(tag, "Error migrating thumbnails", e)
            }
        }
    }


    /** Undo: bring a swiped recipe back into the deck (removes its swipe record). */
    fun unswipe(recipe: Recipe) {
        _swipedUrls.value = _swipedUrls.value - recipe.url
        persistSwiped()
        val myUid = auth.currentUser?.uid ?: return
        val pairId = cravePairId() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val docId = java.util.Base64.getUrlEncoder().encodeToString(recipe.url.toByteArray())
                firestore.collection("users").document(myUid)
                    .collection("craveSwipes").document("${pairId}__$docId").delete()
            } catch (e: Exception) {
                Log.e(tag, "Error undoing swipe", e)
            }
        }
    }

    /** Resets the swipe decisions for the CURRENT pair so its deck can be swiped again. */
    fun resetSwipes() {
        _swipedUrls.value = emptySet()
        persistSwiped()
        val myUid = auth.currentUser?.uid ?: return
        val pairId = cravePairId() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val snap = firestore.collection("users").document(myUid)
                    .collection("craveSwipes")
                    .whereEqualTo("pairId", pairId)
                    .get().await()
                for (d in snap.documents) d.reference.delete()
            } catch (e: Exception) {
                Log.e(tag, "Error resetting swipes", e)
            }
        }
    }

    /** Deletes a single match locally and notifies the partner so they remove it too. */
    private suspend fun insertMatchIfNotToday(match: Match) {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val dayStart = cal.timeInMillis
        if (dao.countMatchesToday(match.partnerId, match.recipeUrl, dayStart) == 0) {
            dao.insertMatch(match)
        }
    }

    fun clearMatch(match: Match) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteMatch(match)
            notifyPartnerMatchDeleted(match.partnerId, match.recipeUrl)
        }
    }

    /** Deletes multiple selected matches locally (no partner notification for bulk). */
    fun clearSelectedMatches(matches: List<Match>) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteMatches(matches)
        }
    }

    /** Clears the local match history and notifies each affected partner. */
    fun clearMatches() {
        viewModelScope.launch(Dispatchers.IO) {
            val all = dao.getMatchesOnce()
            all.groupBy { it.partnerId }.forEach { (partnerId, _) ->
                notifyPartnerMatchDeleted(partnerId, recipeUrl = null)
            }
            dao.deleteAllMatches()
        }
    }

    /** Clears the match history with one specific friend and notifies them. */
    fun clearMatchesForPartner(partnerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            notifyPartnerMatchDeleted(partnerId, recipeUrl = null)
            dao.deleteMatchesByPartner(partnerId)
        }
    }

    /**
     * Sends an inbox message to [partnerId] so they remove the match on their device.
     * [recipeUrl] = specific match to remove; null = remove all matches with us.
     */
    private fun notifyPartnerMatchDeleted(partnerId: String, recipeUrl: String?) {
        val myUid = auth.currentUser?.uid ?: return
        val data = mutableMapOf<String, Any>(
            "type" to "match_deleted",
            "fromId" to myUid,
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        if (recipeUrl != null) data["recipeUrl"] = recipeUrl
        firestore.collection("users").document(partnerId)
            .collection("inbox").add(data)
    }

    /**
     * Makes sure the matched recipe lives in MY OWN collection (ownerId = me) so
     * it shows in my Rezepte tab and stays clickable from the match history even
     * if the friend is later removed. Must be called from a coroutine.
     */
    private suspend fun adoptRecipeAsOwn(url: String, fallbackTitle: String, fallbackThumb: String) {
        val myUid = auth.currentUser?.uid ?: return
        if (url.isBlank()) return
        val ctx = getApplication<Application>().applicationContext
        val existing = dao.getByUrl(url)
        if (existing != null) {
            if (existing.ownerId != myUid) {
                repository.update(existing.copy(ownerId = myUid))
            }
            // If the existing entry still has a remote URL, localise it now
            if (existing.thumbnailUrl.isNotBlank() && !existing.thumbnailUrl.startsWith("file://")) {
                val docId = java.util.Base64.getUrlEncoder().encodeToString(url.trim().toByteArray())
                val localUri = ThumbnailStore.download(ctx, existing.thumbnailUrl, docId)
                if (localUri.startsWith("file://")) {
                    dao.update(existing.copy(ownerId = myUid, thumbnailUrl = localUri))
                }
            }
        } else {
            // Fallback: we don't have the full recipe locally — store a stub so the
            // history entry is at least clickable. Download the partner's thumbnail once.
            val docId = java.util.Base64.getUrlEncoder().encodeToString(url.trim().toByteArray())
            val localThumb = if (fallbackThumb.isNotBlank())
                ThumbnailStore.download(ctx, fallbackThumb, docId)
            else fallbackThumb
            repository.insert(
                Recipe(
                    title = fallbackTitle,
                    url = url,
                    thumbnailUrl = localThumb,
                    category = "",
                    ingredients = "[]",
                    isVegetarian = false,
                    ownerId = myUid
                )
            )
        }
    }

    fun setFriendNickname(friendId: String, nickname: String) {
        _friendNicknames.value = _friendNicknames.value + (friendId to nickname)
        saveFriendDataLocally()
        syncSettingsToCloud()
    }

    private fun saveFriendDataLocally() {
        val nameSet = _friendNames.value.map { "${it.key}|${it.value}" }.toSet()
        val nickSet = _friendNicknames.value.map { "${it.key}|${it.value}" }.toSet()
        val photoSet = _friendPhotoUrls.value.map { "${it.key}|${it.value}" }.toSet()
        prefs?.edit()
            ?.putStringSet("cached_friends", _friends.value.toSet())
            ?.putStringSet("cached_active_friends", _activeFriends.value)
            ?.putStringSet("cached_friend_names", nameSet)
            ?.putStringSet("cached_friend_nicknames", nickSet)
            ?.putStringSet("cached_friend_photos", photoSet)
            ?.apply()
    }

    fun clearNewFriendBadge() {
        _hasNewFriend.value = false
    }

    fun completeOnboarding(name: String, mode: DarkModePreference, accent: Color, secondary: Color) {
        setUserName(name)
        setDarkMode(mode)
        setAccentColor(accent)
        setSecondaryColor(secondary)
        prefs?.edit()?.putBoolean("onboarded", true)?.apply()
        _onboarded.value = true
        syncSettingsToCloud()
    }

    fun resetOnboarding() {
        prefs?.edit()
            ?.putBoolean("onboarded", false)
            ?.putBoolean("login_complete", false)
            ?.remove("user_name")
            ?.apply()
        _userName.value = ""
        _onboarded.value = false
    }

    fun setGuestMode() {
        Log.d(tag, "setGuestMode called")
        _authMode.value = AuthMode.GUEST
        prefs?.edit()
            ?.putString("auth_mode", AuthMode.GUEST.name)
            ?.putBoolean("login_complete", true)
            ?.apply()
        _loginComplete.value = true
    }

    /** Signs in with Google via Credential Manager + Firebase Auth. Call from a Composable scope. */
    suspend fun signInWithGoogle(context: Context): Result<FirebaseUser> {
        Log.d(tag, "signInWithGoogle started")
        return try {
            val credentialManager = CredentialManager.create(context)
            val option = GetSignInWithGoogleOption.Builder(
                "199105728544-9ahkrorkci7ue69ge7b3lihib1hu9k32.apps.googleusercontent.com"
            ).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            Log.d(tag, "Requesting credentials...")
            val result = credentialManager.getCredential(context, request)
            Log.d(tag, "Got credential, creating Firebase credential")
            val googleCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
            Log.d(tag, "Signing into Firebase...")
            val authResult = Firebase.auth.signInWithCredential(firebaseCredential).await()
            val user = authResult.user ?: return Result.failure(Exception("Login fehlgeschlagen"))
            Log.d(tag, "Firebase login success for UID: ${user.uid}")
            _firebaseUser.value = user
            completeGoogleSignIn(user.displayName ?: "", user.email ?: "")
            syncAll(user.uid)
            startPlanListener()
            Result.success(user)
        } catch (e: GetCredentialCancellationException) {
            Log.d(tag, "Google Sign-In cancelled")
            Result.failure(Exception("Abgebrochen"))
        } catch (e: Exception) {
            Log.e(tag, "Detailed Sign-In error", e)
            Result.failure(e)
        }
    }

    fun signOut() {
        Firebase.auth.signOut()
        _firebaseUser.value = null
        _authMode.value = AuthMode.GUEST
        prefs?.edit()?.putString("auth_mode", AuthMode.GUEST.name)?.apply()
    }

    fun completeGoogleSignIn(name: String, email: String) {
        Log.d(tag, "completeGoogleSignIn for $name ($email)")
        _userName.value = name
        prefs?.edit()?.putString("user_name", name)?.apply()
        
        _authMode.value = AuthMode.GOOGLE
        prefs?.edit()
            ?.putString("auth_mode", AuthMode.GOOGLE.name)
            ?.putBoolean("login_complete", true)
            ?.putString("google_email", email)
            ?.apply()
        _loginComplete.value = true
        Log.d(tag, "loginComplete set to true in completeGoogleSignIn")
    }

    private fun syncSettingsToCloud() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settings = mapOf(
                    "user_name" to _userName.value,
                    "dark_mode" to _darkMode.value.name,
                    "language" to _language.value.tag,
                    "accent_color" to _accentColor.value.toArgb(),
                    "secondary_color" to _secondaryColor.value.toArgb(),
                    "profile_picture_url" to _profilePictureUrl.value,
                    "onboarded" to _onboarded.value,
                    "friends" to _friends.value,
                    "active_friends" to _activeFriends.value.toList(),
                    "friend_nicknames" to _friendNicknames.value
                )
                firestore.collection("users").document(uid)
                    .collection("config").document("settings")
                    .set(settings, SetOptions.merge())
                    .addOnFailureListener { e -> Log.e(tag, "Error syncing settings to cloud", e) }
            } catch (e: Exception) {
                Log.e(tag, "Error syncing settings to cloud", e)
            }
        }
    }

    private fun syncAll(uid: String) {
        Log.d(tag, "syncAll started for UID: $uid")

        // Start listening for notifications/inbox
        startInboxListener(uid)
        fetchCloudData(uid)
        registerFcmToken(uid)
    }

    /** Fetches this device's FCM token and stores it so the Cloud Function can
     *  push notifications (friend requests, matches) even when the app is closed. */
    private fun registerFcmToken(uid: String) {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                firestore.collection("users").document(uid)
                    .collection("fcmTokens").document(token)
                    .set(
                        mapOf(
                            "token" to token,
                            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                    )
                    .addOnFailureListener { e -> Log.e(tag, "Failed to store FCM token", e) }
            }
            .addOnFailureListener { e -> Log.e(tag, "Failed to get FCM token", e) }
    }

    /**
     * Pulls settings, friends and their recipes from Firestore.
     * Does NOT (re)attach the inbox listener, so it is safe to call from within
     * the listener itself without making it re-register.
     */
    private fun fetchCloudData(uid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settingsDoc = firestore.collection("users").document(uid)
                    .collection("config").document("settings")
                    .get()
                    .await()

                if (settingsDoc.exists()) {
                    settingsDoc.getString("user_name")?.let { setUserName(it, sync = false) }
                    settingsDoc.getString("dark_mode")?.let {
                        try { setDarkMode(DarkModePreference.valueOf(it), sync = false) } catch(_: Exception) {}
                    }
                    settingsDoc.getString("language")?.let { langTag ->
                        LanguagePreference.entries.find { it.tag == langTag }?.let { setLanguage(it, sync = false) }
                    }
                    settingsDoc.getLong("accent_color")?.let { setAccentColor(Color(it.toInt()), sync = false) }
                    settingsDoc.getLong("secondary_color")?.let { setSecondaryColor(Color(it.toInt()), sync = false) }
                    settingsDoc.getString("profile_picture_url")?.let {
                        _profilePictureUrl.value = it
                        prefs?.edit()?.putString("profile_picture_url", it)?.apply()
                    }
                    settingsDoc.get("friends")?.let {
                        if (it is List<*>) {
                            val list = it.filterIsInstance<String>()
                            _friends.value = list
                            // Initially, all friends are active if not specified
                            if (_activeFriends.value.isEmpty()) {
                                _activeFriends.value = list.toSet()
                            }
                        }
                    }
                    settingsDoc.get("active_friends")?.let {
                        if (it is List<*>) {
                            _activeFriends.value = it.filterIsInstance<String>().toSet()
                        }
                    }
                    settingsDoc.get("friend_nicknames")?.let {
                        if (it is Map<*, *>) {
                            _friendNicknames.value = it.filter { (k, v) -> k is String && v is String } as Map<String, String>
                        }
                    }
                    saveFriendDataLocally()
                    settingsDoc.getBoolean("onboarded")?.let { 
                        _onboarded.value = it
                        prefs?.edit()?.putBoolean("onboarded", it)?.apply()
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error fetching settings from cloud", e)
            }

            try {
                val dao = RecipeDatabase.getDatabase(getApplication()).recipeDao()
                val repository = RecipeRepository(dao)
                
                // Sync own recipes
                repository.syncFromCloud(uid)
                
                // Sync friends' recipes
                _friends.value.forEach { friendId ->
                    Log.d(tag, "Syncing recipes for friend: $friendId")
                    repository.syncFromCloud(friendId)

                    // Also fetch friend's name + profile picture for the UI
                    try {
                        val friendDoc = firestore.collection("users").document(friendId)
                            .collection("config").document("settings")
                            .get()
                            .await()
                        friendDoc.getString("user_name")?.let { name ->
                            if (!_friendNames.value.containsKey(friendId)) {
                                _hasNewFriend.value = true
                            }
                            _friendNames.value = _friendNames.value + (friendId to name)
                        }
                        friendDoc.getString("profile_picture_url")
                            ?.takeIf { it.isNotBlank() }
                            ?.let { photoUrl ->
                                _friendPhotoUrls.value = _friendPhotoUrls.value + (friendId to photoUrl)
                            }
                        saveFriendDataLocally()
                    } catch (e: Exception) {
                        Log.e(tag, "Error fetching friend name", e)
                    }
                }

                // Load my swipe history (current pair) so already-swiped recipes stay hidden
                preloadAllThumbnails(dao)
                reloadSwipesForCurrentPair()
            } catch (e: Exception) {
                Log.e(tag, "Error syncing recipes from cloud", e)
            }
        }
    }

    fun acceptFriendRequest(request: FriendRequest) {
        val myUid = auth.currentUser?.uid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Add to my friends
                if (!_friends.value.contains(request.fromId)) {
                    _friends.value = _friends.value + request.fromId
                    _activeFriends.value = _activeFriends.value + request.fromId
                }
                
                // 2. Remove the request from inbox
                firestore.collection("users").document(myUid)
                    .collection("inbox").document(request.requestId)
                    .delete()
                
                // 3. Notify the sender that I accepted
                firestore.collection("users").document(request.fromId)
                    .collection("inbox").add(mapOf(
                        "type" to "request_accepted",
                        "fromName" to _userName.value,
                        "fromId" to myUid,
                        "timestamp" to com.google.firebase.Timestamp.now()
                    ))

                // 4. Update local and cloud settings (no listener re-attach)
                saveFriendDataLocally()
                syncSettingsToCloud()
                fetchCloudData(myUid)
            } catch (e: Exception) {
                Log.e(tag, "Error accepting friend request", e)
            }
        }
    }

    fun declineFriendRequest(request: FriendRequest) {
        val myUid = auth.currentUser?.uid ?: return
        val myName = _userName.value.ifBlank { auth.currentUser?.displayName ?: "Ein Freund" }
        // Optimistically drop it from the UI right away.
        _pendingRequests.value = _pendingRequests.value.filter { it.requestId != request.requestId }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Remove the request from my inbox …
                firestore.collection("users").document(myUid)
                    .collection("inbox").document(request.requestId)
                    .delete()
                // … and let the sender know it was declined.
                firestore.collection("users").document(request.fromId)
                    .collection("inbox").add(mapOf(
                        "type" to "request_declined",
                        "fromName" to myName,
                        "fromId" to myUid,
                        "timestamp" to com.google.firebase.Timestamp.now()
                    ))
            } catch (e: Exception) {
                Log.e(tag, "Error declining friend request", e)
            }
        }
    }

    fun registerSwipe(recipe: Recipe, liked: Boolean) {
        // Mark as swiped immediately so the deck updates and the decision
        // survives an app restart.
        _swipedUrls.value = _swipedUrls.value + recipe.url
        persistSwiped()

        val myUid = auth.currentUser?.uid ?: return
        val myName = _userName.value.ifBlank { auth.currentUser?.displayName ?: "Ein Freund" }

        val pairId = cravePairId() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val docId = java.util.Base64.getUrlEncoder().encodeToString(recipe.url.toByteArray())
                val composite = "${pairId}__$docId"

                // 1. Record my swipe (scoped to this friend-pair)
                firestore.collection("users").document(myUid)
                    .collection("craveSwipes").document(composite)
                    .set(mapOf(
                        "liked" to liked,
                        "pairId" to pairId,
                        "timestamp" to com.google.firebase.Timestamp.now(),
                        "date" to todayString,   // enables daily reset filter
                        "title" to recipe.title,
                        "thumbnailUrl" to recipe.thumbnailUrl,
                        "url" to recipe.url
                    ))

                if (liked) {
                    // 2. Only match against the friend currently selected in Crave
                    val friendId = _cravePartnerId.value
                    if (!friendId.isNullOrBlank()) {
                        val friendSwipe = firestore.collection("users").document(friendId)
                            .collection("craveSwipes").document(composite)
                            .get()
                            .await()

                        if (friendSwipe.exists() &&
                            friendSwipe.getBoolean("liked") == true &&
                            friendSwipe.getString("date") == todayString) {
                            // MATCH!
                            val friendName = _friendNicknames.value[friendId] ?: _friendNames.value[friendId] ?: "Dein Freund"

                            // Send notification to friend
                            firestore.collection("users").document(friendId)
                                .collection("inbox").add(mapOf(
                                    "type" to "match",
                                    "recipeTitle" to recipe.title,
                                    "recipeThumbnailUrl" to recipe.thumbnailUrl,
                                    "recipeUrl" to recipe.url,
                                    "recipeOwnerId" to recipe.ownerId,
                                    "fromName" to myName,
                                    "fromId" to myUid,
                                    "timestamp" to com.google.firebase.Timestamp.now()
                                ))

                            // Save match locally (once per day per recipe+partner)
                            val match = Match(
                                recipeTitle = recipe.title,
                                recipeThumbnailUrl = recipe.thumbnailUrl,
                                partnerId = friendId,
                                partnerName = friendName,
                                recipeUrl = recipe.url
                            )
                            insertMatchIfNotToday(match)

                            // Adopt the recipe into my own collection (clickable forever)
                            adoptRecipeAsOwn(recipe.url, recipe.title, recipe.thumbnailUrl)

                            // Notify local UI
                            _newMatchFlow.emit(recipe)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error registering swipe", e)
            }
        }
    }

    private fun startInboxListener(uid: String) {
        inboxListener?.remove()
        isInitialInboxLoad = true
        inboxListener = firestore.collection("users").document(uid)
            .collection("inbox")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(tag, "Inbox listener error", e)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                // ── Rebuild the list of pending incoming friend requests ──────
                // These persist until the user accepts/declines, so we always
                // derive them from the full snapshot.
                _pendingRequests.value = snapshot.documents.mapNotNull { doc ->
                    if (doc.getString("type") == "friend_request") {
                        FriendRequest(
                            fromId = doc.getString("fromId") ?: "",
                            fromName = doc.getString("fromName") ?: "Unbekannt",
                            requestId = doc.id
                        )
                    } else null
                }

                // ── React to incoming messages ────────────────────────────────
                // On the first snapshot every existing doc is reported as ADDED,
                // so accept/decline/match messages that arrived while the app was
                // closed still get handled. We only suppress the *friend_request*
                // notification on first load (otherwise it would re-fire on every
                // launch while a request is still pending). One-shot messages are
                // deleted after handling so they never get processed twice.
                snapshot.documentChanges.forEach { change ->
                    if (change.type != com.google.firebase.firestore.DocumentChange.Type.ADDED) return@forEach
                    val doc = change.document
                    when (doc.getString("type")) {
                        "friend_request" -> {
                            if (!isInitialInboxLoad) {
                                viewModelScope.launch {
                                    _eventFlow.emit(SettingsEvent.ShowNotification(
                                        "${doc.getString("fromName") ?: "Jemand"} möchte sich mit dir verbinden.",
                                        NotificationType.INFO
                                    ))
                                }
                            }
                        }
                        "request_accepted" -> {
                            val fromId = doc.getString("fromId") ?: ""
                            val fromName = doc.getString("fromName") ?: "Dein Freund"
                            // They accepted — add them on our side too.
                            if (fromId.isNotBlank() && !_friends.value.contains(fromId)) {
                                _friends.value = _friends.value + fromId
                                _activeFriends.value = _activeFriends.value + fromId
                                saveFriendDataLocally()
                                syncSettingsToCloud()
                            }
                            viewModelScope.launch {
                                _eventFlow.emit(SettingsEvent.ShowNotification(
                                    "$fromName hat deine Freundschaftsanfrage angenommen. ✅",
                                    NotificationType.SUCCESS
                                ))
                            }
                            fetchCloudData(uid)
                            deleteInboxMessage(uid, doc.id)
                        }
                        "request_declined" -> {
                            val fromName = doc.getString("fromName") ?: "Dein Freund"
                            viewModelScope.launch {
                                _eventFlow.emit(SettingsEvent.ShowNotification(
                                    "$fromName hat deine Freundschaftsanfrage abgelehnt.",
                                    NotificationType.ERROR
                                ))
                            }
                            deleteInboxMessage(uid, doc.id)
                        }
                        "match" -> {
                            handleRemoteMatch(doc)
                            val fromName = doc.getString("fromName") ?: "Jemand"
                            val title = doc.getString("recipeTitle") ?: "ein Rezept"
                            viewModelScope.launch {
                                _eventFlow.emit(SettingsEvent.ShowNotification(
                                    "$fromName und du wollt beide $title kochen! ❤️",
                                    NotificationType.SUCCESS
                                ))
                            }
                            deleteInboxMessage(uid, doc.id)
                        }
                        "match_deleted" -> {
                            val fromId = doc.getString("fromId") ?: ""
                            val recipeUrl = doc.getString("recipeUrl") // null = delete all with this partner
                            viewModelScope.launch(Dispatchers.IO) {
                                if (recipeUrl != null) {
                                    dao.deleteMatchByPartnerAndUrl(fromId, recipeUrl)
                                } else {
                                    dao.deleteMatchesByPartner(fromId)
                                }
                            }
                            deleteInboxMessage(uid, doc.id)
                        }
                    }
                }

                isInitialInboxLoad = false
            }
    }

    /** Deletes a one-shot inbox message after it has been handled. */
    private fun deleteInboxMessage(uid: String, docId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                firestore.collection("users").document(uid)
                    .collection("inbox").document(docId).delete()
            } catch (e: Exception) {
                Log.e(tag, "Error deleting inbox message $docId", e)
            }
        }
    }

    private fun handleRemoteMatch(doc: com.google.firebase.firestore.DocumentSnapshot) {
        val fromId = doc.getString("fromId") ?: return
        // Prefer local nickname > local display name > name sent by the partner (their Google name)
        val fromName = _friendNicknames.value[fromId]
            ?: _friendNames.value[fromId]
            ?: doc.getString("fromName")
            ?: "Dein Freund"
        val title = doc.getString("recipeTitle") ?: ""
        val thumb = doc.getString("recipeThumbnailUrl") ?: ""
        val url = doc.getString("recipeUrl") ?: ""

        viewModelScope.launch(Dispatchers.IO) {
            val match = Match(
                recipeTitle = title,
                recipeThumbnailUrl = thumb,
                partnerId = fromId,
                partnerName = fromName,
                recipeUrl = url
            )
            insertMatchIfNotToday(match)
            // Adopt the matched recipe into my own collection so it stays clickable
            adoptRecipeAsOwn(url, title, thumb)
            
            // Notify local UI so this user also sees the match overlay
            _newMatchFlow.emit(Recipe(
                title = title,
                thumbnailUrl = thumb,
                url = url,
                ownerId = fromId,
                category = "",
                ingredients = "[]",
                isVegetarian = false
            ))
        }
    }

    override fun onCleared() {
        super.onCleared()
        inboxListener?.remove()
        planListener?.remove()
    }
}
