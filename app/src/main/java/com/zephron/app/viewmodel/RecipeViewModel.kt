package com.zephron.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zephron.app.data.Recipe
import com.zephron.app.data.RecipeDatabase
import com.zephron.app.data.RecipeRepository
import com.zephron.app.utils.ImagePreloader
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SearchMode { TITLE, INGREDIENTS, CUISINE }

private data class BaseFilters(
    val query: String,
    val tags: Set<String>,
    val mode: SearchMode,
    val maxTime: Int?,
    val ownerId: String?
)

class RecipeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: RecipeRepository
    private val gson = Gson()

    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow("All")
    val selectedTags = MutableStateFlow(emptySet<String>())
    val searchMode = MutableStateFlow(SearchMode.TITLE)
    val maxCookTime = MutableStateFlow<Int?>(null) 
    val minRating = MutableStateFlow(0)             
    val showFavoritesOnly = MutableStateFlow(false)
    val showCookedOnly = MutableStateFlow(false)
    val filterOwnerId = MutableStateFlow<String?>(null)

    @OptIn(FlowPreview::class)
    val recipes: StateFlow<List<Recipe>>

    init {
        val dao = RecipeDatabase.getDatabase(application).recipeDao()
        repository = RecipeRepository(dao)

        val baseFiltersFlow = combine(
            searchQuery.debounce(300),
            selectedTags,
            searchMode,
            maxCookTime,
            filterOwnerId
        ) { query, tags, mode, maxTime, ownerId ->
            BaseFilters(query, tags, mode, maxTime, ownerId)
        }

        val filtered = combine(baseFiltersFlow, repository.allRecipes) { filters, all ->
            val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
            
            var result = if (filters.ownerId != null) {
                all.filter { it.ownerId == filters.ownerId }
            } else {
                all.filter { it.ownerId.isBlank() || it.ownerId == myUid }
            }

            if (filters.query.isNotBlank()) {
                result = when (filters.mode) {
                    SearchMode.INGREDIENTS -> {
                        val terms = filters.query.split(",", " ")
                            .map { it.trim().lowercase() }
                            .filter { it.length >= 2 }
                        if (terms.isEmpty()) result
                        else result.filter { recipe ->
                            val ingredients = parseIngredients(recipe.ingredients)
                            terms.all { term -> ingredients.any { it.lowercase().contains(term) } }
                        }
                    }
                    SearchMode.TITLE -> {
                        result.filter { it.title.contains(filters.query, ignoreCase = true) }
                    }
                    SearchMode.CUISINE -> {
                        result.filter { it.category.contains(filters.query, ignoreCase = true) }
                    }
                }
            }

            if (filters.tags.isNotEmpty()) {
                result = result.filter { recipe ->
                    val recipeTags = effectiveTags(recipe)
                    filters.tags.all { it in recipeTags }
                }
            }

            if (filters.maxTime != null) {
                result = result.filter { it.cookingTimeMinutes in 1..filters.maxTime }
            }

            result
        }

        recipes = combine(filtered, minRating, showFavoritesOnly, showCookedOnly) { list, minR, favOnly, cookedOnly ->
            var r = if (minR > 0) list.filter { it.rating >= minR } else list
            if (favOnly) r = r.filter { it.isFavorite }
            if (cookedOnly) r = r.filter { it.isCooked }
            r
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    private fun parseTags(json: String): List<String> = try {
        val raw: List<String> = gson.fromJson(json, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        com.zephron.app.network.TagDetector.normalize(raw)
    } catch (e: Exception) { emptyList() }

    /** Tags used for filtering — merges stored tags with isVeg so both sources stay in sync. */
    fun effectiveTags(recipe: Recipe): List<String> {
        val base = parseTags(recipe.tags)
        val hasDietTag = base.any { it == "Vegetarisch" || it == "Vegan" || it == "Fleisch" }
        val veg = com.zephron.app.network.TagDetector.isVeg(recipe.isVegetarian, recipe.tags)
        return when {
            !hasDietTag && veg -> base + "Vegetarisch"
            !hasDietTag && !veg -> base + "Fleisch"
            else -> base
        }
    }

    private fun parseIngredients(json: String): List<String> = try {
        gson.fromJson(json, object : TypeToken<List<String>>() {}.type) ?: emptyList()
    } catch (e: Exception) { emptyList() }

    val allRecipes: StateFlow<List<Recipe>> = repository.allRecipes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        // Preload thumbnails into Coil memory cache whenever the recipe list changes
        viewModelScope.launch {
            repository.allRecipes
                .map { list -> list.map { it.thumbnailUrl }.filter { it.isNotBlank() } }
                .distinctUntilChanged()
                .collect { urls ->
                    ImagePreloader.preload(getApplication(), urls, limit = 30)
                }
        }
    }


    fun deleteRecipe(recipe: Recipe) {
        viewModelScope.launch { repository.delete(recipe) }
    }

    fun deleteRecipeById(id: Int) {
        viewModelScope.launch { repository.deleteById(id) }
    }

    fun deleteRecipes(ids: Set<Int>) {
        viewModelScope.launch { repository.deleteByIds(ids.toList()) }
    }

    fun insertRecipe(recipe: Recipe) {
        viewModelScope.launch { repository.insert(recipe) }
    }

    fun updateRecipe(recipe: Recipe) {
        viewModelScope.launch { repository.update(recipe) }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setCategory(category: String) {
        selectedCategory.value = category
    }

    fun toggleTag(tag: String) {
        selectedTags.value = if (tag in selectedTags.value)
            selectedTags.value - tag
        else
            selectedTags.value + tag
    }

    fun clearTags() {
        selectedTags.value = emptySet()
    }

    fun setSearchMode(mode: SearchMode) {
        searchMode.value = mode
    }

    fun setMaxCookTime(minutes: Int?) {
        maxCookTime.value = minutes
    }

    fun setMinRating(stars: Int) {
        minRating.value = stars
    }

    fun setShowFavoritesOnly(value: Boolean) {
        showFavoritesOnly.value = value
    }

    fun setFilterOwnerId(uid: String?) {
        filterOwnerId.value = uid
    }

    /**
     * Copies a friend's recipe into my own collection (same Firestore docId, ownerId = mine).
     * It will appear in my carousel and recipe list immediately after the Firestore listener fires.
     */
    fun adoptRecipe(recipe: Recipe) {
        val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModelScope.launch {
            val adopted = recipe.copy(ownerId = myUid)
            repository.insert(adopted)  // inserts/updates locally + syncs to Firestore under my uid
        }
    }

    fun toggleFavorite(recipe: Recipe) {
        viewModelScope.launch { repository.update(recipe.copy(isFavorite = !recipe.isFavorite)) }
    }

    fun toggleCooked(recipe: Recipe) {
        viewModelScope.launch { repository.update(recipe.copy(isCooked = !recipe.isCooked)) }
    }

    // ── Widget deep-link: open a specific recipe by ID ────────────────────────
    val pendingOpenRecipeId = MutableStateFlow<Int?>(null)

    fun requestOpenRecipe(id: Int) {
        pendingOpenRecipeId.value = id
    }

    fun consumePendingOpenRecipe() {
        pendingOpenRecipeId.value = null
    }
}
