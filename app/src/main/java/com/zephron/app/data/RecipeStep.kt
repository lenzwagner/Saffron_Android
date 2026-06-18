package com.zephron.app.data

data class RecipeStep(
    val text: String = "",
    val timeMinutes: Int = 0,
    val stepIngredients: List<String> = emptyList()
)
