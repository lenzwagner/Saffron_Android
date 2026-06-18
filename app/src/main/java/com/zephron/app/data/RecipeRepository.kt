package com.zephron.app.data

import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class RecipeRepository(private val recipeDao: RecipeDao) {

    private val firestore = Firebase.firestore
    private val auth = Firebase.auth
    private val tag = "RecipeRepository"

    val allRecipes: Flow<List<Recipe>> = recipeDao.getAllRecipes()

    fun getByCategory(category: String): Flow<List<Recipe>> =
        recipeDao.getByCategory(category)

    fun searchByTitle(query: String): Flow<List<Recipe>> =
        recipeDao.searchByTitle(query)

    suspend fun insert(recipe: Recipe) {
        val recipeWithOwner = if (recipe.ownerId.isEmpty()) {
            recipe.copy(ownerId = auth.currentUser?.uid ?: "")
        } else {
            recipe
        }
        recipeDao.insert(recipeWithOwner)
        // If we are logged in, sync to Firestore
        auth.currentUser?.uid?.let { uid ->
            syncToFirestore(uid, recipeWithOwner)
        }
    }

    suspend fun delete(recipe: Recipe) {
        recipeDao.delete(recipe)
        auth.currentUser?.uid?.let { uid ->
            deleteFromFirestore(uid, recipe.url) // Using URL as a unique-ish key for sync
        }
    }

    suspend fun deleteById(id: Int) {
        val recipe = recipeDao.getById(id)
        recipeDao.deleteById(id)
        if (recipe != null) {
            auth.currentUser?.uid?.let { uid -> deleteFromFirestore(uid, recipe.url) }
        }
    }

    suspend fun deleteByIds(ids: List<Int>) {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            ids.forEach { id ->
                val recipe = recipeDao.getById(id)
                if (recipe != null) deleteFromFirestore(uid, recipe.url)
            }
        }
        recipeDao.deleteByIds(ids)
    }

    suspend fun update(recipe: Recipe) {
        recipeDao.update(recipe)
        auth.currentUser?.uid?.let { uid ->
            syncToFirestore(uid, recipe)
        }
    }

    suspend fun getAllRecipesOnce(): List<Recipe> = recipeDao.getAllRecipesOnce()

    suspend fun existsByUrl(url: String): Boolean =
        recipeDao.countByUrl(url) > 0

    suspend fun getByUrl(url: String): Recipe? =
        recipeDao.getByUrl(url)

    private suspend fun syncToFirestore(userId: String, recipe: Recipe) {
        try {
            // Never upload a local file:// path to Firestore — other devices can't
            // access it. Only sync http(s) URLs.
            val cloudThumbnail = recipe.thumbnailUrl
                .takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?: ""
            val recipeMap = mapOf(
                "title" to recipe.title,
                "url" to recipe.url,
                "thumbnailUrl" to cloudThumbnail,
                "category" to recipe.category,
                "ingredients" to recipe.ingredients,
                "tags" to recipe.tags,
                "isVegetarian" to recipe.isVegetarian,
                "servings" to recipe.servings,
                "cookingTimeMinutes" to recipe.cookingTimeMinutes,
                "notes" to recipe.notes,
                "steps" to recipe.steps,
                "rating" to recipe.rating,
                "createdAt" to recipe.createdAt,
                "slideImages" to recipe.slideImages,
                "ownerId" to recipe.ownerId,
                "isFavorite" to recipe.isFavorite
            )
            // Use URL as document ID to avoid duplicates (base64 encoded or sanitized)
            val docId = java.util.Base64.getUrlEncoder().encodeToString(recipe.url.toByteArray())
            // Fire-and-forget: do NOT await the server ack here. Awaiting would
            // suspend insert()/update() indefinitely when offline, leaving the
            // save UI stuck on its spinner even though the recipe is already in
            // the local DB. Firestore queues the write and syncs it in the
            // background on its own.
            firestore.collection("users").document(userId)
                .collection("recipes").document(docId)
                .set(recipeMap, SetOptions.merge())
                .addOnFailureListener { e -> Log.e(tag, "Error syncing recipe to Firestore", e) }
        } catch (e: Exception) {
            Log.e(tag, "Error syncing recipe to Firestore", e)
        }
    }

    private suspend fun deleteFromFirestore(userId: String, url: String) {
        try {
            val docId = java.util.Base64.getUrlEncoder().encodeToString(url.toByteArray())
            firestore.collection("users").document(userId)
                .collection("recipes").document(docId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e(tag, "Error deleting recipe from Firestore", e)
        }
    }

    /**
     * Fetches all recipes from Firestore for this user and merges them into local DB.
     */
    suspend fun syncFromCloud(userId: String) {
        Log.d(tag, "syncFromCloud started for $userId")
        try {
            val snapshot = firestore.collection("users").document(userId)
                .collection("recipes")
                .get()
                .await()

            Log.d(tag, "Found ${snapshot.size()} recipes in Firestore")
            for (doc in snapshot.documents) {
                val url = doc.getString("url") ?: continue

                val cloudRecipe = Recipe(
                    title = doc.getString("title") ?: "",
                    url = url,
                    thumbnailUrl = doc.getString("thumbnailUrl") ?: "",
                    category = doc.getString("category") ?: "",
                    ingredients = doc.getString("ingredients") ?: "[]",
                    tags = doc.getString("tags") ?: "[]",
                    isVegetarian = doc.getBoolean("isVegetarian") ?: false,
                    servings = doc.getLong("servings")?.toInt() ?: 1,
                    cookingTimeMinutes = doc.getLong("cookingTimeMinutes")?.toInt() ?: 0,
                    notes = doc.getString("notes") ?: "",
                    steps = doc.getString("steps") ?: "[]",
                    rating = doc.getLong("rating")?.toInt() ?: 0,
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                    slideImages = doc.getString("slideImages") ?: "[]",
                    ownerId = doc.getString("ownerId") ?: userId,
                    isFavorite = doc.getBoolean("isFavorite") ?: false
                )

                val existing = recipeDao.getByUrl(url)
                if (existing == null) {
                    recipeDao.insert(cloudRecipe)
                    Log.d(tag, "Synced recipe: ${cloudRecipe.title}")
                } else if (existing.ownerId == userId) {
                    // This row belongs to the user we're syncing → refresh its
                    // content so edits (title, image, steps, …) propagate.
                    // Keep the local row id and the viewer's own bookmark flag;
                    // never overwrite someone else's recipe with a friend's copy.
                    // If cloud has no thumbnail (was a local file:// path that we
                    // correctly didn't upload), keep the existing local file:// path.
                    val mergedThumbnail = cloudRecipe.thumbnailUrl.ifBlank { existing.thumbnailUrl }
                    val updated = cloudRecipe.copy(id = existing.id, isFavorite = existing.isFavorite,
                        thumbnailUrl = mergedThumbnail)
                    if (updated != existing) {
                        recipeDao.update(updated)
                        Log.d(tag, "Updated recipe: ${updated.title}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error syncing from Firestore", e)
        }
    }
}
