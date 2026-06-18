package com.zephron.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zephron.app.data.Recipe
import com.zephron.app.network.GeminiAssistant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatMessage(
    val fromUser: Boolean,
    val text: String,
    val id: String = java.util.UUID.randomUUID().toString()
)

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("saffron_prefs", Context.MODE_PRIVATE)

    val dailyLimit = 5

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _remaining = MutableStateFlow(0)
    val remaining: StateFlow<Int> = _remaining

    private val gson = Gson()
    private var recipeContext: String = ""
    private var recipeIndex: Map<String, Recipe> = emptyMap()

    init {
        _remaining.value = computeRemaining()
    }

    /** Feeds the user's saved recipes to the assistant so it can answer about them
     *  and reference them via a stable id for clickable links in the chat. */
    fun setRecipes(recipes: List<Recipe>) {
        val limited = recipes.take(80)
        recipeIndex = limited.mapIndexed { i, r -> (i + 1).toString() to r }.toMap()
        recipeContext = limited.mapIndexed { i, r ->
            val id = i + 1
            val ingredients = parseList(r.ingredients).joinToString(", ")
            val tags = parseList(r.tags)
            val tagPart = if (tags.isNotEmpty()) " [${tags.joinToString(", ")}]" else ""
            val ingPart = if (ingredients.isNotBlank()) " — Zutaten: ${ingredients.take(180)}" else ""
            "[$id] ${r.title}$tagPart$ingPart"
        }.joinToString("\n")
    }

    /** Resolves a recipe id (as used in the [[recipe:ID]] marker) back to a Recipe. */
    fun recipeFor(id: String): Recipe? = recipeIndex[id.trim()]

    private fun parseList(json: String): List<String> = try {
        gson.fromJson(json, object : TypeToken<List<String>>() {}.type) ?: emptyList()
    } catch (_: Exception) { emptyList() }

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun usedToday(): Int =
        if (prefs.getString("assistant_date", "") == today()) prefs.getInt("assistant_count", 0) else 0

    private fun computeRemaining(): Int = (dailyLimit - usedToday()).coerceAtLeast(0)

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isLoading.value) return

        // Refresh remaining (handles day rollover)
        _remaining.value = computeRemaining()
        if (_remaining.value <= 0) {
            _messages.value = _messages.value +
                ChatMessage(true, trimmed) +
                ChatMessage(false, "Du hast dein Tageslimit von $dailyLimit Fragen erreicht. Morgen geht's weiter! 🌙")
            return
        }

        // Count this question
        val used = usedToday() + 1
        prefs.edit().putString("assistant_date", today()).putInt("assistant_count", used).apply()
        _remaining.value = (dailyLimit - used).coerceAtLeast(0)

        _messages.value = _messages.value + ChatMessage(true, trimmed)
        _isLoading.value = true

        val history = _messages.value.map { (if (it.fromUser) "user" else "model") to it.text }
        viewModelScope.launch {
            val result = GeminiAssistant.ask(history, recipeContext)
            _isLoading.value = false
            val reply = result.getOrElse {
                "Ups, da ist etwas schiefgelaufen. Bitte versuche es später noch einmal."
            }
            _messages.value = _messages.value + ChatMessage(false, reply)
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
    }
}
