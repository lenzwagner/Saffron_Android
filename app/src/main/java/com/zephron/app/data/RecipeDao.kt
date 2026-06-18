package com.zephron.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recipe: Recipe)

    @Delete
    suspend fun delete(recipe: Recipe)

    @Update
    suspend fun update(recipe: Recipe)

    @Query("SELECT * FROM recipes ORDER BY createdAt DESC")
    fun getAllRecipes(): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE category = :category ORDER BY createdAt DESC")
    fun getByCategory(category: String): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE title LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchByTitle(query: String): Flow<List<Recipe>>

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM recipes WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Int>)

    @Query("SELECT COUNT(*) FROM recipes WHERE url = :url")
    suspend fun countByUrl(url: String): Int

    @Query("SELECT * FROM recipes WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): Recipe?

    @Query("SELECT * FROM recipes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): Recipe?

    @Query("SELECT * FROM recipes ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomRecipe(): Recipe?

    @Query("SELECT * FROM recipes ORDER BY createdAt DESC")
    suspend fun getAllRecipesOnce(): List<Recipe>

    // Match History
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatch(match: Match)

    @Query("SELECT * FROM matches ORDER BY matchedAt DESC")
    fun getAllMatches(): Flow<List<Match>>

    @Delete
    suspend fun deleteMatch(match: Match)

    @Delete
    suspend fun deleteMatches(matches: List<Match>)

    @Query("SELECT * FROM matches")
    suspend fun getMatchesOnce(): List<Match>

    @Query("DELETE FROM matches")
    suspend fun deleteAllMatches()

    @Query("DELETE FROM matches WHERE partnerId = :partnerId")
    suspend fun deleteMatchesByPartner(partnerId: String)

    @Query("DELETE FROM matches WHERE partnerId = :partnerId AND recipeUrl = :recipeUrl")
    suspend fun deleteMatchByPartnerAndUrl(partnerId: String, recipeUrl: String)
}
