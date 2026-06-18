package com.zephron.app.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.google.gson.Gson
import com.zephron.app.data.Recipe
import com.zephron.app.data.RecipeDatabase
import com.zephron.app.data.RecipeRepository
import com.zephron.app.data.RecipeStep
import com.zephron.app.network.MetadataFetcher
import com.zephron.app.network.RecipeMetadata
import com.zephron.app.utils.ThumbnailStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.URL

enum class BatchMode { MANUAL, AUTO }

sealed class ImportEvent {
    data class ShowToast(val message: String) : ImportEvent()
}

sealed class BatchImportState {
    object Idle : BatchImportState()
    /** Queue is running — either MANUAL (user reviews each) or AUTO (silent). */
    data class QueueActive(
        val current: Int,   // 1-based index of the item currently being processed
        val total: Int,
        val imported: Int,
        val skipped: Int,
        val failed: Int,
        val mode: BatchMode = BatchMode.MANUAL
    ) : BatchImportState()
    data class Done(
        val imported: Int,
        val skipped: Int,
        val failed: Int,
        val failedUrls: List<String> = emptyList(),
        val skippedRecipes: List<com.zephron.app.data.Recipe> = emptyList()
    ) : BatchImportState()
}

sealed class ImportState {
    object Idle : ImportState()
    object Loading : ImportState()
    data class Success(val url: String, val metadata: RecipeMetadata, val isSaving: Boolean = false) : ImportState()
    data class Error(val message: String, val sessionExpired: Boolean = false) : ImportState()
    data class Saved(val tags: List<String> = emptyList(), val savedRecipe: com.zephron.app.data.Recipe? = null) : ImportState()
    /** The URL was already in the library — recipe is the existing entry. */
    data class AlreadyExists(val recipe: com.zephron.app.data.Recipe) : ImportState()
}

class ImportViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: RecipeRepository
    private val gson = Gson()
    private val storage = Firebase.storage
    private val auth = Firebase.auth

    /**
     * Downloads [thumbnailUrl], compresses + saves it permanently in local storage
     * (filesDir/thumbnails/{docId}.jpg) and — in the background — uploads to
     * Firebase Storage and writes the download URL back to Room + Firestore so
     * iOS and other clients can load the image.
     *
     * Returns the local "file://…" URI so Room always loads from disk instantly.
     * Falls back to the original URL if local save fails.
     */
    /**
     * Downloads [thumbnailUrl], saves it locally, uploads to Firebase Storage,
     * and returns the https:// download URL so Room + Firestore always store a
     * cloud-accessible URL (never a local file:// path).
     *
     * Awaiting the upload here means the recipe is only marked "saved" once the
     * image is visible to all clients (iOS, partner devices, etc.).
     * Falls back to the original URL on any error.
     */
    private suspend fun mirrorThumbnail(thumbnailUrl: String, docId: String): String {
        if (thumbnailUrl.isBlank()) return thumbnailUrl
        val ctx = getApplication<Application>().applicationContext
        val uid = auth.currentUser?.uid ?: return thumbnailUrl

        return try {
            val bytes = withContext(Dispatchers.IO) {
                URL(thumbnailUrl).openStream().use { it.readBytes() }
            }

            // Save locally so Coil can load from disk on this device (fast, offline).
            ThumbnailStore.save(ctx, bytes, docId)

            // Upload to Firebase Storage and return the https:// URL.
            // Awaiting here blocks insert() until the image is cloud-accessible.
            val ref = storage.reference.child("users/$uid/recipes/$docId.jpg")
            ref.putBytes(bytes).await()
            ref.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.w("ImportViewModel", "mirrorThumbnail failed, keeping original URL: ${e.message}")
            thumbnailUrl
        }
    }

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState

    private val _events = MutableSharedFlow<ImportEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ImportEvent> = _events

    private val _batchState = MutableStateFlow<BatchImportState>(BatchImportState.Idle)
    val batchState: StateFlow<BatchImportState> = _batchState

    /** URL shared into the app from an external app (e.g. TikTok). Consumed once by MainScreen. */
    private val _pendingSharedUrl = MutableStateFlow<String?>(null)
    val pendingSharedUrl: StateFlow<String?> = _pendingSharedUrl

    /** URL field for the single-import tab — persists across tab switches. */
    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url

    /** Text field content for the multi-import tab — persists across tab switches. */
    private val _batchInputText = MutableStateFlow("")
    val batchInputText: StateFlow<String> = _batchInputText

    /** Whether the multi-import tab is selected — persists across tab switches. */
    private val _isBatchTab = MutableStateFlow(false)
    val isBatchTab: StateFlow<Boolean> = _isBatchTab

    // ── Queue state ────────────────────────────────────────────────────────────
    private val pendingQueue: ArrayDeque<String> = ArrayDeque()
    private var queueTotal = 0
    private var queuePosition = 0   // 1-based, incremented before each fetch
    private var queueImported = 0
    private var queueSkipped = 0
    private var queueFailed = 0
    private var currentBatchMode: BatchMode = BatchMode.MANUAL
    private val failedUrlsList: MutableList<String> = mutableListOf()
    private val skippedRecipesList: MutableList<com.zephron.app.data.Recipe> = mutableListOf()

    init {
        val dao = RecipeDatabase.getDatabase(application).recipeDao()
        repository = RecipeRepository(dao)
    }

    /** Called from MainActivity when a share intent arrives. */
    fun onSharedUrl(url: String) {
        _pendingSharedUrl.value = url
    }

    /** Clears the pending URL after it has been consumed by the UI. */
    fun consumePendingSharedUrl() {
        _pendingSharedUrl.value = null
    }

    // ── UI tab state ───────────────────────────────────────────────────────────

    fun setBatchTab(enabled: Boolean) {
        _isBatchTab.value = enabled
        // Reset import card state when explicitly switching tabs
        if (!enabled && _batchState.value is BatchImportState.Idle) {
            _importState.value = ImportState.Idle
        }
    }

    fun setBatchInputText(text: String) {
        _batchInputText.value = text
    }

    // ── Single import ──────────────────────────────────────────────────────────

    fun setUrl(newUrl: String) {
        _url.value = newUrl
        if (_importState.value is ImportState.Error || _importState.value is ImportState.Success) {
            _importState.value = ImportState.Idle
        }
    }

    fun importRecipe() {
        val currentUrl = _url.value.trim()
        if (currentUrl.isBlank()) {
            _importState.value = ImportState.Error("Bitte eine URL eingeben.")
            return
        }
        if (!currentUrl.startsWith("http://") && !currentUrl.startsWith("https://")) {
            _importState.value = ImportState.Error("Ungültiges URL-Format. Muss mit http:// oder https:// beginnen.")
            return
        }

        viewModelScope.launch {
            _importState.value = ImportState.Loading
            if (repository.existsByUrl(currentUrl)) {
                val existing = repository.getByUrl(currentUrl)
                _importState.value = if (existing != null) ImportState.AlreadyExists(existing)
                                     else ImportState.Error("Dieses Rezept wurde bereits importiert.")
                return@launch
            }
            MetadataFetcher.fetch(currentUrl).fold(
                onSuccess = { metadata ->
                    val withDefault = if (metadata.servings == 0) metadata.copy(servings = 2) else metadata
                    _importState.value = ImportState.Success(currentUrl, withDefault)
                },
                onFailure = { throwable ->
                    val msg = throwable.message ?: "Rezept konnte nicht geladen werden. Bitte URL prüfen und erneut versuchen."
                    val expired = msg == "SESSION_EXPIRED"
                    
                    _importState.value = ImportState.Error(
                        message = if (expired)
                            "Instagram Session-ID abgelaufen. Bitte in den Einstellungen aktualisieren."
                        else msg,
                        sessionExpired = expired
                    )
                }
            )
        }
    }

    fun updateTitle(newTitle: String) {
        val state = _importState.value
        if (state is ImportState.Success) {
            _importState.value = ImportState.Success(state.url, state.metadata.copy(title = newTitle))
        }
    }

    fun updateTags(tags: List<String>) {
        val state = _importState.value
        if (state is ImportState.Success) {
            _importState.value = ImportState.Success(state.url, state.metadata.copy(tags = tags))
        }
    }

    fun updateServings(servings: Int) {
        val state = _importState.value
        if (state is ImportState.Success) {
            _importState.value = ImportState.Success(state.url, state.metadata.copy(servings = servings.coerceAtLeast(0)))
        }
    }

    fun selectThumbnail(url: String) {
        val state = _importState.value
        if (state is ImportState.Success) {
            _importState.value = ImportState.Success(state.url, state.metadata.copy(thumbnailUrl = url))
        }
    }

    /**
     * Saves the current recipe.
     * - In MANUAL queue mode: increments counter and advances to next.
     * - In single mode: shows the Saved confirmation screen.
     */
    fun saveRecipe() {
        val state = _importState.value
        if (state !is ImportState.Success || state.isSaving) return
        val metadata = state.metadata
        val url = state.url
        val inQueue = _batchState.value is BatchImportState.QueueActive

        _importState.value = state.copy(isSaving = true)

        viewModelScope.launch {
            try {
                val docId = java.util.Base64.getUrlEncoder()
                    .encodeToString(url.trim().toByteArray())
                val permanentThumb = mirrorThumbnail(metadata.thumbnailUrl, docId)
                val steps = metadata.geminiSteps.map { RecipeStep(text = it) }
                val recipe = Recipe(
                    title = metadata.title,
                    url = url.trim(),
                    thumbnailUrl = permanentThumb,
                    category = metadata.category,
                    ingredients = gson.toJson(metadata.ingredients),
                    tags = gson.toJson(metadata.tags),
                    isVegetarian = metadata.isVegetarian,
                    servings = metadata.servings,
                    cookingTimeMinutes = metadata.cookingTimeMinutes,
                    notes = metadata.description,
                    steps = gson.toJson(steps),
                    slideImages = gson.toJson(metadata.slideImages)
                )
                repository.insert(recipe)
                val savedRecipe = repository.getByUrl(url.trim())

                if (inQueue) {
                    queueImported++
                    advanceQueue()
                } else {
                    _importState.value = ImportState.Saved(tags = metadata.tags, savedRecipe = savedRecipe)
                    _url.value = ""
                }
            } catch (e: Exception) {
                _importState.value = ImportState.Error("Fehler beim Speichern: ${e.message}")
            }
        }
    }

    /** Resets only the single-import card (URL + state). Does NOT touch batch state. */
    fun reset() {
        // Only reset if we're not in a running queue
        if (_batchState.value !is BatchImportState.QueueActive) {
            _importState.value = ImportState.Idle
            _url.value = ""
        }
    }

    /** Explicit reset called by user (e.g. "Neuer Import" button). Always resets. */
    fun resetExplicit() {
        _importState.value = ImportState.Idle
        _url.value = ""
    }

    // ── Batch queue ────────────────────────────────────────────────────────────

    /**
     * Starts the import queue with the given mode.
     * AUTO: fetches and saves each recipe silently.
     * MANUAL: presents each recipe for user review before saving.
     */
    fun importBatch(rawUrls: List<String>, mode: BatchMode = BatchMode.MANUAL) {
        // Support concatenated URLs (e.g. "https://...https://...") by splitting on https:// boundaries
        val expanded = rawUrls.flatMap { raw ->
            val normalized = raw.replace(Regex("(?<!^)(https?://)"), "\n$1")
            Regex("""https?://[^\s,;"'<>]+""").findAll(normalized)
                .map { it.value.trimEnd('.', ',', ')') }
                .toList()
        }
        val valid = expanded
            .map { it.trim() }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct()
        if (valid.isEmpty()) return

        currentBatchMode = mode
        pendingQueue.clear()
        pendingQueue.addAll(valid)
        queueTotal = valid.size
        queuePosition = 0
        queueImported = 0
        queueSkipped = 0
        queueFailed = 0
        failedUrlsList.clear()
        skippedRecipesList.clear()

        advanceQueue()
    }

    /** Skips the current recipe in MANUAL mode. */
    fun skipCurrentInQueue() {
        if (_batchState.value !is BatchImportState.QueueActive) return
        val currentUrl = _url.value.trim()
        if (currentUrl.isNotBlank()) failedUrlsList.add(currentUrl)
        queueFailed++
        advanceQueue()
    }

    fun resetBatch() {
        pendingQueue.clear()
        _batchState.value = BatchImportState.Idle
        _importState.value = ImportState.Idle
        _url.value = ""
        _batchInputText.value = ""
        _isBatchTab.value = false
    }

    /**
     * Pops the next URL and processes it.
     * AUTO mode: fetch → auto-save (or auto-skip on error).
     * MANUAL mode: fetch → present to user.
     * Duplicates are always silently skipped.
     */
    private fun advanceQueue() {
        if (pendingQueue.isEmpty()) {
            val imported = queueImported
            _batchState.value = BatchImportState.Done(
                imported = imported,
                skipped  = queueSkipped,
                failed   = queueFailed,
                failedUrls = failedUrlsList.toList(),
                skippedRecipes = skippedRecipesList.toList()
            )
            _importState.value = ImportState.Idle
            _url.value = ""
            if (imported > 0) {
                val msg = if (imported == 1) "1 Rezept gespeichert"
                          else "$imported Rezepte gespeichert"
                _events.tryEmit(ImportEvent.ShowToast(msg))
            }
            return
        }

        val url = pendingQueue.removeFirst()
        queuePosition++
        _batchState.value = BatchImportState.QueueActive(
            current  = queuePosition,
            total    = queueTotal,
            imported = queueImported,
            skipped  = queueSkipped,
            failed   = queueFailed,
            mode     = currentBatchMode
        )

        viewModelScope.launch {
            _url.value = url
            _importState.value = ImportState.Loading

            // Silently skip duplicates in both modes
            if (repository.existsByUrl(url)) {
                queueSkipped++
                repository.getByUrl(url)?.let { skippedRecipesList.add(it) }
                advanceQueue()
                return@launch
            }

            MetadataFetcher.fetch(url).fold(
                onSuccess = { metadata ->
                    val m = if (metadata.servings == 0) metadata.copy(servings = 2) else metadata
                    if (currentBatchMode == BatchMode.AUTO) {
                        // Auto-save without user confirmation
                        autoSave(url, m)
                    } else {
                        // Manual: let user review
                        _importState.value = ImportState.Success(url, m)
                    }
                },
                onFailure = { throwable ->
                    val msg = throwable.message ?: "Rezept konnte nicht geladen werden."
                    val expired = msg == "SESSION_EXPIRED"
                    if (currentBatchMode == BatchMode.AUTO && !expired) {
                        // Non-critical error in auto mode → record URL and skip silently
                        failedUrlsList.add(url)
                        queueFailed++
                        advanceQueue()
                    } else {
                        // Session expired (both modes) or manual mode → show error
                        _importState.value = ImportState.Error(
                            message = if (expired)
                                "Instagram Session-ID abgelaufen. Bitte in den Einstellungen aktualisieren."
                            else msg,
                            sessionExpired = expired
                        )
                    }
                }
            )
        }
    }

    private suspend fun autoSave(url: String, metadata: RecipeMetadata) {
        val docId = java.util.Base64.getUrlEncoder()
            .encodeToString(url.trim().toByteArray())
        val permanentThumb = mirrorThumbnail(metadata.thumbnailUrl, docId)
        val steps = metadata.geminiSteps.map { RecipeStep(text = it) }
        val recipe = Recipe(
            title              = metadata.title,
            url                = url.trim(),
            thumbnailUrl       = permanentThumb,
            category           = metadata.category,
            ingredients        = gson.toJson(metadata.ingredients),
            tags               = gson.toJson(metadata.tags),
            isVegetarian       = metadata.isVegetarian,
            servings           = metadata.servings,
            cookingTimeMinutes = metadata.cookingTimeMinutes,
            notes              = metadata.description,
            steps              = gson.toJson(steps),
            slideImages        = gson.toJson(metadata.slideImages)
        )
        repository.insert(recipe)
        queueImported++
        advanceQueue()
    }
}
