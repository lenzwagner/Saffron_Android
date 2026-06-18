package com.zephron.app.network

import com.zephron.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Lightweight cooking-assistant chat backed by Gemini. Multi-turn: pass the
 * whole conversation so the model has context.
 */
object GeminiAssistant {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val SYSTEM_PROMPT =
        "Du bist der freundliche Koch-Assistent der App Zephron. " +
        "Beantworte Fragen rund ums Kochen: Rezeptideen, Zutaten-Alternativen, Mengen umrechnen, " +
        "Nährwerte/Kalorien (grobe Schätzung, klar als Schätzung kennzeichnen), Techniken, Aufbewahrung & Resteverwertung. " +
        "Antworte kurz, konkret und in der Sprache des Nutzers (Deutsch oder Englisch). " +
        "Nutze gerne kurze Listen. Wenn eine Frage nichts mit Essen oder Kochen zu tun hat, " +
        "weise freundlich darauf hin, dass du nur beim Kochen helfen kannst. " +
        "Wenn der Nutzer nach 'meinen Rezepten' fragt, beziehe dich auf die unten aufgelistete Rezept-Sammlung. " +
        "WICHTIG: Wenn du ein konkretes gespeichertes Rezept empfiehlst (z. B. ein zufälliges), " +
        "hänge direkt dahinter den Marker [[recipe:NUMMER]] an – wobei NUMMER exakt die Zahl in eckigen Klammern " +
        "aus der Rezept-Liste ist. Beispiel: 'Wie wäre es mit Spaghetti Carbonara? [[recipe:3]]'. " +
        "Erfinde niemals Nummern und nutze den Marker nur für Rezepte aus der Liste."

    /**
     * @param history conversation as pairs of (role, text) where role is "user" or "model".
     *   Must start with a "user" turn.
     * @param recipeContext optional summary of the user's saved recipes, appended to the system prompt.
     */
    suspend fun ask(history: List<Pair<String, String>>, recipeContext: String = ""): Result<String> = withContext(Dispatchers.IO) {
        try {
            val contents = JSONArray()
            history.forEach { (role, text) ->
                contents.put(
                    JSONObject()
                        .put("role", role)
                        .put("parts", JSONArray().put(JSONObject().put("text", text)))
                )
            }
            val systemText = if (recipeContext.isBlank()) SYSTEM_PROMPT
                else SYSTEM_PROMPT + "\n\nGespeicherte Rezepte des Nutzers:\n" + recipeContext
            val body = JSONObject()
                .put(
                    "system_instruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemText)))
                )
                .put("contents", contents)
                // Disable "thinking" so 2.5-flash always returns a normal text part
                .put(
                    "generationConfig",
                    JSONObject().put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
                )
                .toString()

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${BuildConfig.GEMINI_API_KEY}")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string()
                if (!response.isSuccessful) {
                    android.util.Log.e("GeminiAssistant", "HTTP ${response.code}: $respBody")
                    return@withContext Result.failure(Exception("Gemini ${response.code}"))
                }
                if (respBody == null) return@withContext Result.failure(Exception("Empty response"))

                // Defensive parse: concatenate all text parts of the first candidate
                val parts = JSONObject(respBody)
                    .optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                val sb = StringBuilder()
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        parts.optJSONObject(i)?.optString("text")?.let { sb.append(it) }
                    }
                }
                val text = sb.toString().trim()
                if (text.isEmpty()) {
                    android.util.Log.e("GeminiAssistant", "No text in response: $respBody")
                    return@withContext Result.failure(Exception("Leere Antwort"))
                }
                Result.success(text)
            }
        } catch (e: Exception) {
            android.util.Log.e("GeminiAssistant", "ask failed: ${e.javaClass.simpleName}: ${e.message}", e)
            Result.failure(e)
        }
    }
}
