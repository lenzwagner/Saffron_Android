package com.zephron.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "matches")
data class Match(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val recipeTitle: String,
    val recipeThumbnailUrl: String,
    val partnerId: String,
    val partnerName: String,
    val matchedAt: Long = System.currentTimeMillis(),
    val recipeUrl: String
)
