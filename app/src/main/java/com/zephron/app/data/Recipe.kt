package com.zephron.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val url: String,
    val thumbnailUrl: String,
    val category: String,
    val ingredients: String, // JSON array as string
    val tags: String = "[]", // JSON array of tag strings e.g. ["Chicken","Hot","Asian"]
    val isVegetarian: Boolean,
    val servings: Int = 0,
    val cookingTimeMinutes: Int = 0,
    val notes: String = "",
    val steps: String = "[]",
    val rating: Int = 0,           // 0 = unbewertet, 1–5 = Sterne
    val createdAt: Long = System.currentTimeMillis(),
    val slideImages: String = "[]", // JSON array of slide image URLs (TikTok slideshows)
    val ownerId: String = "",       // UID of the user who owns this recipe
    val isFavorite: Boolean = false, // Bookmarked by the user (separate from rating)
    val isCooked: Boolean = false    // Marked as already cooked
)
