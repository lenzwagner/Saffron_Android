package com.zephron.app.network

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

data class RecipeMetadata(
    val title: String,
    val thumbnailUrl: String,
    val description: String,
    val ingredients: List<String>,
    val category: String,
    val tags: List<String>,
    val isVegetarian: Boolean,
    val servings: Int = 0,
    val cookingTimeMinutes: Int = 0,
    val geminiSteps: List<String> = emptyList(),
    /** High-quality Google Images result for the recipe title. Empty if unavailable. */
    val googleImageUrl: String = "",
    /** The original post thumbnail, preserved so the user can switch back after picking the Google image. */
    val originalThumbnailUrl: String = "",
    val slideImages: List<String> = emptyList()
)

object MetadataFetcher {

    @Volatile private var stepCallback: (String) -> Unit = {}

    private fun step(msg: String) = stepCallback(msg)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Separate client for backend calls — longer timeout to survive Render/Cloud Function cold starts
    private val backendClient = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private val geminiClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    /** Executes a Gemini HTTP request, retrying up to 5× on 429 with exponential back-off. */
    private suspend fun geminiExecute(request: okhttp3.Request): okhttp3.Response {
        var delayMs = 10_000L
        repeat(5) { attempt ->
            val response = geminiClient.newCall(request).execute()
            if (response.code != 429) return response
            response.close()
            if (attempt < 4) delay(delayMs).also { delayMs *= 2 }
        }
        // Final attempt — return whatever we get
        return geminiClient.newCall(request).execute()
    }

    private fun logGeminiUsage(responseBody: String, type: String, sourceUrl: String) {
        try {
            val usage = JSONObject(responseBody).optJSONObject("usageMetadata") ?: return
            val inputTokens  = usage.optInt("promptTokenCount")
            val outputTokens = usage.optInt("candidatesTokenCount")
            val totalTokens  = usage.optInt("totalTokenCount")
            FirebaseFirestore.getInstance().collection("geminiLog").add(
                mapOf(
                    "ts"           to System.currentTimeMillis(),
                    "type"         to type,
                    "model"        to "gemini-2.5-flash",
                    "inputTokens"  to inputTokens,
                    "outputTokens" to outputTokens,
                    "totalTokens"  to totalTokens,
                    "sourceUrl"    to sourceUrl
                )
            )
        } catch (_: Exception) {}
    }

    private val BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val MEAT_KEYWORDS = listOf(
        "chicken", "beef", "pork", "lamb", "turkey", "salmon", "tuna", "shrimp",
        "bacon", "steak", "meat", "ham", "sausage", "pepperoni", "prosciutto",
        "chorizo", "duck", "veal", "venison", "lobster", "crab", "anchovy",
        "ente", "hähnchen", "rind", "schwein", "fleisch", "fisch"
    )
    private val VEG_KEYWORDS = listOf(
        "vegetarian", "vegan", "veggie", "meatless", "plant-based", "plant based"
    )

    suspend fun fetch(url: String, onStep: (String) -> Unit = {}): Result<RecipeMetadata> = withContext(Dispatchers.IO) {
        stepCallback = onStep
        try {
            val trimmed = url.trim()
            coroutineScope {
                step("Rezept wird geladen…")
                val metaDeferred = async {
                    when {
                        isInstagram(trimmed) -> fetchInstagram(trimmed)
                        isTikTok(trimmed) -> fetchTikTok(trimmed)
                        else -> fetchGenericHtml(trimmed)
                    }
                }
                val metaResult = metaDeferred.await()
                if (metaResult.isFailure) return@coroutineScope metaResult

                val metadata = metaResult.getOrThrow()
                if (metadata.ingredients.isEmpty() && metadata.geminiSteps.isEmpty()) {
                    return@coroutineScope Result.failure(Exception("Kein Rezept auf dieser Seite gefunden."))
                }

                step("Bild wird gesucht…")
                val googleImageDeferred = async { fetchGoogleImage(metadata.title) }
                val googleImageUrl = googleImageDeferred.await()

                if (googleImageUrl.isBlank()) {
                    Result.success(metadata)
                } else {
                    Result.success(
                        metadata.copy(
                            googleImageUrl = googleImageUrl,
                            originalThumbnailUrl = metadata.thumbnailUrl
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Searches Google Custom Search Image API for a high-quality food photo of the recipe.
     * Returns an empty string if the API keys are not configured or the request fails.
     * Setup: https://programmablesearch.google.com → create engine → get CX ID
     *        Google Cloud Console → enable Custom Search API → get API key
     */
    private fun fetchGoogleImage(recipeTitle: String): String {
        val key = com.zephron.app.BuildConfig.PEXELS_API_KEY
        if (key.isBlank()) return ""
        return try {
            val query = URLEncoder.encode("$recipeTitle food", "UTF-8")
            val request = Request.Builder()
                .url("https://api.pexels.com/v1/search?query=$query&per_page=1&orientation=landscape")
                .header("Authorization", key)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return ""
            val body = response.body?.string() ?: return ""
            org.json.JSONObject(body)
                .optJSONArray("photos")
                ?.optJSONObject(0)
                ?.optJSONObject("src")
                ?.optString("large2x")
                ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun isInstagram(url: String) =
        url.contains("instagram.com") || url.contains("instagr.am")

    private fun isTikTok(url: String) =
        url.contains("tiktok.com") || url.contains("vm.tiktok.com")

    // ── Instagram ────────────────────────────────────────────────────────────
    // Strategy: try 2 approaches in order, return first success.
    // 1. Python Cloud Function (instaloader) — most reliable
    // 2. OG meta tags from the main post URL — fallback

    private const val INSTA_PYTHON_BACKEND_URL = "https://us-central1-saffron-498311.cloudfunctions.net/get_insta_recipe"
    private const val INSTA_PYTHON_BACKEND_URL_ALT = "https://get-insta-recipe-498311-uc.a.run.app"

    private suspend fun fetchInstagram(url: String): Result<RecipeMetadata> {
        val shortcode = extractInstagramShortcode(url)
            ?: return Result.failure(Exception("Could not parse Instagram URL. Expected: instagram.com/p/… or /reel/…"))

        // 1. Try primary Cloud Function URL
        var cloudResult = fetchWithPythonCloudFunction(INSTA_PYTHON_BACKEND_URL, shortcode)
        
        // 2. If primary fails with DNS/Connect error, try the alternative Cloud Run URL
        if (cloudResult.isFailure) {
            val error = cloudResult.exceptionOrNull()?.message ?: ""
            if (error.contains("resolve host") || error.contains("Connect") || error.contains("404")) {
                cloudResult = fetchWithPythonCloudFunction(INSTA_PYTHON_BACKEND_URL_ALT, shortcode)
            }
        }
        
        if (cloudResult.isSuccess) {
            return cloudResult
        } else {
            val error = cloudResult.exceptionOrNull()?.message ?: "Unbekannter Fehler"
            android.util.Log.e("ZephronImport", "Cloud Function failed: $error")
            val hint = when {
                error.contains("401") || error.contains("login", ignoreCase = true) ->
                    "Instagram-Session abgelaufen. Bitte später erneut versuchen."
                error.contains("not found", ignoreCase = true) || error.contains("404") ->
                    "Beitrag nicht gefunden oder privat."
                error.contains("resolve host") || error.contains("Connect") ->
                    "Keine Internetverbindung."
                else -> "Cloud-Import fehlgeschlagen: $error"
            }
            return Result.failure(Exception(hint))
        }
    }

    private suspend fun fetchWithPythonCloudFunction(baseUrl: String, shortcode: String): Result<RecipeMetadata> {
        return try {
            val jsonPayload = JSONObject().apply {
                put("shortcode", shortcode)
            }
            
            val request = Request.Builder()
                .url(baseUrl)
                .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                .header("Accept", "application/json")
                .build()

            val response = backendClient.newCall(request).execute()
            val body = response.body?.string() ?: return Result.failure(Exception("Empty body"))
            val json = try { JSONObject(body) } catch (_: Exception) {
                return Result.failure(Exception("Cloud Function Fehler ${response.code}"))
            }
            if (!json.optBoolean("success", false)) {
                val detail = json.optString("error", "").ifBlank { "Fehler ${response.code}" }
                return Result.failure(Exception(detail))
            }
            
            val caption = json.optString("description")
            val thumbnail = json.optString("thumbnail_url")
            
            if (caption.isBlank() && thumbnail.isBlank())
                return Result.failure(Exception("Empty cloud response"))
                
            extractWithGemini(caption, thumbnail)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Requests the main post URL and reads OG meta tags.
    // Instagram serves these server-side for all public posts (used by link previews).
    private suspend fun fetchInstagramOg(shortcode: String): Result<RecipeMetadata> {
        return try {
            val postUrl = "https://www.instagram.com/p/$shortcode/"
            val doc = Jsoup.connect(postUrl)
                .userAgent(BROWSER_UA)
                .header("Accept-Language", "en-US,en;q=0.9")
                .timeout(15_000)
                .get()

            val description = doc.selectFirst("meta[property=og:description]")?.attr("content")
                ?: doc.selectFirst("meta[name=description]")?.attr("content")
                ?: ""
            val thumbnail = doc.selectFirst("meta[property=og:image]")?.attr("content") ?: ""

            if (description.isBlank() && thumbnail.isBlank())
                return Result.failure(Exception("No OG data"))

            extractWithGemini(description, thumbnail)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractInstagramShortcode(url: String): String? {
        // Matches /p/CODE, /reel/CODE, /reels/CODE
        val pattern = Regex("""instagram\.com/(?:p|reel|reels)/([A-Za-z0-9_\-]+)""")
        return pattern.find(url)?.groupValues?.getOrNull(1)
    }

    // ── TikTok ────────────────────────────────────────────────────────────────
    private suspend fun fetchTikTok(url: String): Result<RecipeMetadata> {
        val resolvedUrl = resolveRedirect(url)
        val encoded = URLEncoder.encode(resolvedUrl, "UTF-8")
        val oembedUrl = "https://www.tiktok.com/oembed?url=$encoded"
        val json = getJson(oembedUrl)

        val rawCaption = json?.optString("title")?.takeIf { it.isNotBlank() } ?: ""
        val thumbnail = json?.optString("thumbnail_url") ?: ""
        val authorName = json?.optString("author_name") ?: ""

        // Try to extract all slide images from the page HTML
        val pageHtml = getHtml(resolvedUrl) ?: ""
        val slideImages = if (pageHtml.isNotBlank()) extractTikTokSlideImages(pageHtml) else emptyList()

        if (slideImages.size > 1) {
            // Slideshow — let Gemini Vision read the actual slides
            val visionResult = extractWithGeminiVision(slideImages, rawCaption, thumbnail)
            if (visionResult.isSuccess) {
                return visionResult.map { it.copy(slideImages = slideImages) }
            }
        }

        // Regular video or vision failed — fall back to caption-based Gemini
        if (rawCaption.isNotBlank()) {
            val cleaned = cleanSocialCaption(rawCaption)
            val geminiResult = extractWithGemini(cleaned, thumbnail)
            if (geminiResult.isSuccess) {
                return geminiResult.map { it.copy(slideImages = slideImages) }
            }
        }

        // Full local fallback
        val text = rawCaption.ifBlank { authorName }
        val description = text.ifBlank { "Rezept in der Beschreibung" }
        val title = TitleExtractor.extract(text).ifBlank { "TikTok Rezept" }
        return Result.success(
            RecipeMetadata(
                title = title,
                thumbnailUrl = thumbnail,
                description = description,
                ingredients = extractIngredients(text),
                category = detectCategory(title, text),
                tags = TagDetector.detect(title, text),
                isVegetarian = detectVegetarian(title, text),
                geminiSteps = listOf("Siehe Video für die Zubereitung."),
                slideImages = slideImages
            )
        )
    }

    /**
     * Strips TikTok/Instagram noise before sending to Gemini.
     *
     * Key idea: single emojis at the start of a line often serve as bullet points
     * (e.g. "🥚 2 Eier") — replace them with "• " so Gemini sees a list structure.
     * Emoji clusters (3+) in the middle of text are pure decoration and are removed.
     */
    private fun cleanSocialCaption(text: String): String {
        var cleaned = text
        // Remove bare URLs first
        cleaned = cleaned.replace(Regex("""https?://\S+"""), "")
        // Remove hashtags and @mentions
        cleaned = cleaned.replace(Regex("""[#@]\S+"""), "")
        // Lines that start with one or two emojis/symbols → treat as bullet point
        // Match: optional whitespace, 1-2 emoji-range chars, then a space or colon
        cleaned = cleaned.lines().joinToString("\n") { line ->
            val trimmed = line.trim()
            // Count leading codepoints that look like emojis
            var i = 0; var emojiCount = 0
            while (i < trimmed.length && emojiCount < 3) {
                val cp = trimmed.codePointAt(i)
                val isEmoji = (cp in 0x1F300..0x1FAFF) || (cp in 0x2600..0x27FF) ||
                              (cp in 0xFE00..0xFEFF) || (cp in 0x1F000..0x1F02F)
                if (isEmoji) { emojiCount++; i += Character.charCount(cp) } else break
            }
            if (emojiCount in 1..2 && i < trimmed.length) {
                // Leading emoji(s) followed by actual text → bullet point
                "• " + trimmed.substring(i).trim()
            } else line
        }
        // Remove remaining emoji clusters (3 or more consecutive)
        cleaned = cleaned.replace(Regex("""[\p{So}\p{Sm}\p{Sk}]{3,}"""), " ")
        // Remove stray isolated emojis that aren't bullets (not at line start, surrounded by spaces)
        cleaned = cleaned.replace(Regex("""(?<!\n)[\p{So}\p{Sm}\p{Sk}]+(?!\S)"""), " ")
        // Normalise whitespace / blank lines
        cleaned = cleaned.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
        return cleaned.trim()
    }

    /**
     * Parses TikTok page HTML to extract all slide image URLs from a photo post.
     * TikTok embeds full post data in a <script id="__UNIVERSAL_DATA_FOR_REHYDRATION__"> tag.
     * Returns an empty list for video posts or if parsing fails.
     */
    /**
     * Extracts all slide image URLs from a TikTok photo-post page.
     * Tries three strategies in order, returning on first success.
     *
     * Strategy 1 — __UNIVERSAL_DATA_FOR_REHYDRATION__ (modern TikTok desktop)
     * Strategy 2 — SIGI_STATE (older TikTok web)
     * Strategy 3 — Regex scan of any script tag for "imageURL":{"urlList":["…"]} patterns
     */
    private fun extractTikTokSlideImages(html: String): List<String> {
        val doc = Jsoup.parse(html)

        // ── Strategy 1: __UNIVERSAL_DATA_FOR_REHYDRATION__ ───────────────
        doc.selectFirst("script#__UNIVERSAL_DATA_FOR_REHYDRATION__")?.html()
            ?.takeIf { it.isNotBlank() }
            ?.let { content ->
                runCatching {
                    val root = org.json.JSONObject(content)
                    val itemStruct = root
                        .optJSONObject("__DEFAULT_SCOPE__")
                        ?.optJSONObject("webapp.video-detail")
                        ?.optJSONObject("itemInfo")
                        ?.optJSONObject("itemStruct")
                    extractSlideUrlsFromItemStruct(itemStruct)
                }.getOrNull()
            }
            ?.takeIf { it.size > 1 }
            ?.let { return it }

        // ── Strategy 2: SIGI_STATE ────────────────────────────────────────
        doc.selectFirst("script#SIGI_STATE")?.html()
            ?.takeIf { it.isNotBlank() }
            ?.let { content ->
                runCatching {
                    val root = org.json.JSONObject(content)
                    // ItemModule is a map of videoId → itemStruct
                    val module = root.optJSONObject("ItemModule")
                    val firstKey = module?.keys()?.takeIf { it.hasNext() }?.next()
                    val itemStruct = if (firstKey != null) module?.optJSONObject(firstKey) else null
                    extractSlideUrlsFromItemStruct(itemStruct)
                }.getOrNull()
            }
            ?.takeIf { it.size > 1 }
            ?.let { return it }

        // ── Strategy 3: Regex scan of all script tags ─────────────────────
        // Matches the first URL in each "imageURL":{"urlList":["url",...]} block
        val urlPattern = Regex(""""imageURL"\s*:\s*\{\s*"urlList"\s*:\s*\[\s*"([^"]+)"""")
        for (script in doc.select("script")) {
            val text = script.html()
            if (!text.contains("\"imagePost\"") || !text.contains("\"imageURL\"")) continue
            val urls = urlPattern.findAll(text)
                .map { it.groupValues[1] }
                .filter { it.startsWith("https://") }
                .distinct()
                .toList()
            if (urls.size > 1) return urls
        }

        return emptyList()
    }

    /** Pulls slide URLs from an itemStruct JSONObject (shared by strategies 1 & 2). */
    private fun extractSlideUrlsFromItemStruct(itemStruct: org.json.JSONObject?): List<String> {
        val images = itemStruct?.optJSONObject("imagePost")?.optJSONArray("images")
            ?: return emptyList()
        return (0 until images.length()).mapNotNull { i ->
            val imgObj = images.optJSONObject(i) ?: return@mapNotNull null
            val urlList = imgObj.optJSONObject("imageURL")?.optJSONArray("urlList")
                ?: return@mapNotNull null
            urlList.optString(0).takeIf { it.isNotBlank() }
        }
    }

    /**
     * Sends TikTok slide images to Gemini Vision to extract recipe information.
     * Downloads up to 5 images, base64-encodes them, and sends as a multimodal prompt.
     */
    private suspend fun extractWithGeminiVision(
        imageUrls: List<String>,
        caption: String,
        thumbnailFallback: String
    ): Result<RecipeMetadata> {
        step("KI analysiert Inhalt…")
        return try {
            // Download up to 5 slides
            val imageParts = imageUrls.take(5).mapNotNull { url ->
                try {
                    val req = Request.Builder().url(url).header("User-Agent", BROWSER_UA).build()
                    val resp = client.newCall(req).execute()
                    if (!resp.isSuccessful) return@mapNotNull null
                    val bytes = resp.body?.bytes() ?: return@mapNotNull null
                    val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    val mime = resp.header("Content-Type")
                        ?.split(";")?.first()?.trim()?.takeIf { it.startsWith("image/") }
                        ?: "image/jpeg"
                    b64 to mime
                } catch (e: Exception) { null }
            }
            if (imageParts.isEmpty()) return Result.failure(Exception("No images downloaded"))

            val prompt = """
                Du bist ein Koch-Assistent. Diese Bilder stammen aus einer TikTok Slideshow mit einem Rezept.
                Analysiere alle Bilder und extrahiere sämtliche Rezeptinformationen.
                Nur wenn die Bilder überhaupt nichts mit Kochen oder Essen zu tun haben, gib zurück: {"error":"no_recipe"}

                TITEL:
                - Extrahiere den konkreten Gerichtsnamen — NICHT Hype-/Marketing-Text aus der Caption.
                - Kurz und beschreibend auf Deutsch, alle Substantive großschreiben.

                ZUTATEN:
                - Alle erkennbaren Zutaten, auch wenn auf mehreren Folien verteilt.
                - Format: "Menge Einheit Zutat", Einheiten auf Deutsch.
                - Falls keine erkennbar: []

                SCHRITTE:
                - Zubereitungsschritte aus den Bildern ableiten, jeden als eigenen String.
                - Falls keine erkennbar: []

                SONSTIGE REGELN:
                - Alles auf Deutsch
                - "category": genau eines von: Hähnchen, Pute, Rind, Fisch, Pasta, Reis, Kartoffeln, Mexikanisch, Asiatisch, Vegetarisch, Andere
                - "course": PFLICHT – genau eines von: Vorspeise, Hauptgang, Dessert, Getränk
                - "weight": PFLICHT – genau eines von: Leicht, Deftig (Leicht = Salate/Gemüse/gesund, Deftig = viel Fett/Käse/Fleisch)
                - "tags": 2–5 Begriffe aus bekannten Tags

                Gib NUR valides JSON zurück:
                {"title":"...","ingredients":["..."],"steps":["..."],"category":"...","course":"...","weight":"...","tags":["..."]}
                ${if (caption.isNotBlank()) "\nZusätzlicher Caption-Text:\n$caption" else ""}
            """.trimIndent()

            // Build multimodal request body
            val partsArray = org.json.JSONArray()
            imageParts.forEach { (b64, mime) ->
                partsArray.put(
                    org.json.JSONObject()
                        .put("inlineData", org.json.JSONObject()
                            .put("mimeType", mime)
                            .put("data", b64))
                )
            }
            partsArray.put(org.json.JSONObject().put("text", prompt))

            val bodyJson = org.json.JSONObject()
                .put("contents", org.json.JSONArray()
                    .put(org.json.JSONObject().put("parts", partsArray)))
                .toString()

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${com.zephron.app.BuildConfig.GEMINI_API_KEY}")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            val response = geminiExecute(request)
            if (response.code == 429) return Result.failure(Exception("Gemini-Limit erreicht. Bitte kurz warten und erneut versuchen."))
            if (!response.isSuccessful) return Result.failure(Exception("Gemini Vision ${response.code}"))

            val responseBody = response.body?.string()
                ?: return Result.failure(Exception("Empty vision response"))
            logGeminiUsage(responseBody, "vision", caption.take(200))
            val rawText = org.json.JSONObject(responseBody)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
                .getJSONObject(0).getString("text").trim()

            val jsonString = rawText
                .replace(Regex("""^```[a-zA-Z]*\s*"""), "")
                .replace(Regex("""```\s*$"""), "")
                .trim()
                .let { if (it.startsWith("{")) it else Regex("""\{[\s\S]*\}""").find(it)?.value ?: it }

            val recipeJson = org.json.JSONObject(jsonString)
            if (recipeJson.optString("error") == "no_recipe")
                return Result.failure(Exception("Kein Rezept auf dieser Seite gefunden."))

            val rawTitle = recipeJson.optString("title")
            val title = if (rawTitle.isBlank()) "TikTok Slideshow Rezept" else rawTitle
            val ingredients = recipeJson.optJSONArray("ingredients")
                ?.let { arr -> (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() } }
                ?: emptyList()
            val geminiSteps = recipeJson.optJSONArray("steps")
                ?.let { arr -> (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() } }
                ?.ifEmpty { listOf("Siehe Slideshow für die Zubereitung.") }
                ?: listOf("Siehe Slideshow für die Zubereitung.")
            val category = recipeJson.optString("category").ifBlank { detectCategory(title, caption) }
            val geminiCourse = recipeJson.optString("course").trim()
                .let { c -> listOf("Vorspeise", "Hauptgang", "Dessert", "Getränk").firstOrNull { it == c } }
            val geminiWeight = recipeJson.optString("weight").trim()
                .let { w -> listOf("Leicht", "Deftig").firstOrNull { it == w } }
            val baseTags = recipeJson.optJSONArray("tags")
                ?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }
                ?.takeIf { it.isNotEmpty() }
                ?: TagDetector.detect(title, caption)
            val existingCourses = setOf("Vorspeise", "Hauptgang", "Dessert", "Getränk")
            val existingWeights = setOf("Leicht", "Deftig")
            val tagsWithoutCourseOrWeight = baseTags.filter { it !in existingCourses && it !in existingWeights }
            val courseTag = geminiCourse ?: baseTags.firstOrNull { it in existingCourses } ?: "Hauptgang"
            val weightTag = geminiWeight ?: "Deftig"
            val tags = (tagsWithoutCourseOrWeight + courseTag + weightTag).distinct()

            Result.success(
                RecipeMetadata(
                    title = title,
                    thumbnailUrl = thumbnailFallback,
                    description = caption.ifBlank { "Rezept in der Slideshow" },
                    ingredients = ingredients,
                    category = category,
                    tags = tags,
                    isVegetarian = detectVegetarian(title, caption),
                    geminiSteps = geminiSteps
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun extractWithGemini(caption: String, thumbnail: String): Result<RecipeMetadata> {
        step("KI analysiert Inhalt…")
        return try {
            val prompt = """
                Du bist ein Koch-Assistent. Der folgende Text stammt aus einer Social-Media-Caption (TikTok/Instagram) und kann informal, unvollständig oder unstrukturiert sein.

                Aufgabe: Extrahiere alle erkennbaren Rezeptinformationen.
                Nur wenn der Text überhaupt nichts mit Kochen oder Essen zu tun hat, gib zurück: {"error":"no_recipe"}

                TITEL:
                - Extrahiere den konkreten Gerichts-/Rezeptnamen — NICHT den Marketing- oder Hype-Text.
                - Social-Media-Captions fangen oft mit Hype-Sätzen an ("when you want...", "this is so good",
                  "you need to try this", "POV: you made...") und nennen das Gericht erst danach.
                  Ignoriere solche Einleitungssätze und extrahiere nur den tatsächlichen Gerichtsnamen.
                - Kurz und beschreibend auf Deutsch, alle Substantive großschreiben.
                - Beispiele: "Cremige Tomatensuppe", "Knusprige Hähnchen-Streifen mit Joghurt-Dip"

                ZUTATEN:
                - Alle erkennbaren Zutaten extrahieren, auch wenn Mengen fehlen.
                - Format: "Menge Einheit Zutat" — falls keine Menge genannt: nur Zutat (z.B. "Salz")
                - Einheiten auf Deutsch: g, kg, ml, l, EL, TL, Prise, Bund
                - Beispiele: "200 g Mehl", "2 Eier", "Salz", "Knoblauch", "400 g Hähnchenbrustfilet"
                - Falls keine Zutaten erkennbar: leeres Array []

                SCHRITTE:
                - Zubereitungsschritte extrahieren oder aus informellem Text ableiten.
                - Jeden Schritt als eigenen String, beginnend mit Verb im Imperativ.
                - Konkret: Temperatur, Zeit und Technik nennen wo vorhanden.
                - Beispiele: "Zwiebeln in Olivenöl 3 Minuten glasig dünsten.",
                             "Bei 180 °C Umluft 25 Minuten goldbraun backen."
                - Falls keine Schritte erkennbar: leeres Array []

                SONSTIGE REGELN:
                - Alles auf Deutsch (Titel, Zutaten, Schritte)
                - "category": genau eines von: Hähnchen, Pute, Rind, Fisch, Pasta, Reis, Kartoffeln, Mexikanisch, Asiatisch, Vegetarisch, Andere
                - "course": PFLICHT – genau eines von: Vorspeise, Hauptgang, Dessert, Getränk
                    • Vorspeise = Starter, Suppe als Vorspeise, Antipasto, Tapas, Dip, Fingerfood
                    • Hauptgang  = Haupt-Mahlzeit, herzhafte Gerichte, Suppe als Mahlzeit
                    • Dessert    = Süßspeise, Kuchen, Eis, Mousse, Tiramisu, Pralinen
                    • Getränk    = Smoothie, Saft, Cocktail, Kaffee, Tee, Shake, Sirup
                - "weight": PFLICHT – genau eines von: Leicht, Deftig
                    • Leicht = Salate, Suppen, Smoothies, Gerichte mit viel Gemüse, unter ~500 kcal, gesund/kalorienarm
                    • Deftig  = Gerichte mit viel Fett, Käse, Fleisch, Teig, Sahne, Burgern, Frittiertem — über ~600 kcal
                - "tags": 2–5 Begriffe aus: Hähnchen, Pute, Rind, Schwein, Fisch, Meeresfrüchte, Lamm, Ente, Fleisch, Reis, Pasta, Nudeln, Kartoffeln, Brot, Vegetarisch, Vegan, Warm, Kalt, Suppe, Salat, Süß, Frühstück, Italienisch, Asiatisch, Chinesisch, Japanisch, Thailändisch, Indisch, Mexikanisch, Mediterran, Französisch, Griechisch, Türkisch, Amerikanisch, Deutsch, Spanisch, Koreanisch, Vietnamesisch

                Gib NUR valides JSON zurück, kein Markdown, kein Codeblock:
                {"title":"...","ingredients":["..."],"steps":["..."],"category":"...","course":"...","weight":"...","tags":["..."]}

                Text:
                $caption
            """.trimIndent()

            val body = """{"contents":[{"parts":[{"text":${org.json.JSONObject.quote(prompt)}}]}]}"""
            val mediaType = "application/json".toMediaType()
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${com.zephron.app.BuildConfig.GEMINI_API_KEY}")
                .post(body.toRequestBody(mediaType))
                .build()

            val response = geminiExecute(request)
            if (response.code == 429) return Result.failure(Exception("Gemini-Limit erreicht. Bitte kurz warten und erneut versuchen."))
            if (!response.isSuccessful) return Result.failure(Exception("Gemini ${response.code}"))

            val responseBody = response.body?.string() ?: return Result.failure(Exception("Empty response"))
            logGeminiUsage(responseBody, "text", thumbnail)
            val rawText = org.json.JSONObject(responseBody)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()

            // Robustly extract JSON: strip markdown fences, then find the {...} block
            val jsonString = rawText
                .replace(Regex("""^```[a-zA-Z]*\s*"""), "")
                .replace(Regex("""```\s*$"""), "")
                .trim()
                .let { cleaned ->
                    if (cleaned.startsWith("{")) cleaned
                    else Regex("""\{[\s\S]*\}""").find(cleaned)?.value ?: cleaned
                }

            val recipeJson = org.json.JSONObject(jsonString)
            if (recipeJson.optString("error") == "no_recipe")
                return Result.failure(Exception("Kein Rezept auf dieser Seite gefunden."))
            val rawTitle = recipeJson.optString("title")
            val title = if (rawTitle.contains('#') || rawTitle.contains('@') || rawTitle.isBlank())
                TitleExtractor.extract(caption).ifBlank { "Rezept" }
            else rawTitle
            val geminiIngredients = recipeJson.optJSONArray("ingredients")
                ?.let { arr -> (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() } }
                ?: emptyList()
            // Fallback: if Gemini returned no ingredients, try local extraction from the original text
            val ingredients = geminiIngredients.ifEmpty { extractIngredients(caption) }
            val geminiSteps = recipeJson.optJSONArray("steps")
                ?.let { arr -> (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() } }
                ?.ifEmpty { listOf("Siehe Video für die Zubereitung.") }
                ?: listOf("Siehe Video für die Zubereitung.")
            val category = recipeJson.optString("category").ifBlank { detectCategory(title, caption) }
            val geminiCourse = recipeJson.optString("course").trim()
                .let { c -> listOf("Vorspeise", "Hauptgang", "Dessert", "Getränk").firstOrNull { it == c } }
            val geminiWeight = recipeJson.optString("weight").trim()
                .let { w -> listOf("Leicht", "Deftig").firstOrNull { it == w } }
            val baseTags = recipeJson.optJSONArray("tags")
                ?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }
                ?.takeIf { it.isNotEmpty() }
                ?: TagDetector.detect(title, caption)
            // Ensure exactly one course tag and one weight tag are present
            val existingCourses = setOf("Vorspeise", "Hauptgang", "Dessert", "Getränk")
            val existingWeights = setOf("Leicht", "Deftig")
            val tagsWithoutCourseOrWeight = baseTags.filter { it !in existingCourses && it !in existingWeights }
            val courseTag = geminiCourse
                ?: baseTags.firstOrNull { it in existingCourses }
                ?: TagDetector.detect(title, caption).firstOrNull { it in existingCourses }
                ?: "Hauptgang"
            val weightTag = geminiWeight ?: "Deftig"
            val tags = (tagsWithoutCourseOrWeight + courseTag + weightTag).distinct()

            Result.success(
                RecipeMetadata(
                    title = title,
                    thumbnailUrl = thumbnail,
                    description = caption.ifBlank { "Rezept in der Beschreibung" },
                    ingredients = ingredients,
                    category = category,
                    tags = tags,
                    isVegetarian = detectVegetarian(title, caption),
                    geminiSteps = geminiSteps
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Generic HTML ─────────────────────────────────────────────────────────
    private suspend fun fetchGenericHtml(url: String): Result<RecipeMetadata> {
        val html = getHtml(url)
            ?: return Result.failure(Exception("Could not load the URL. Check your connection."))

        val doc = Jsoup.parse(html, url)
        val ogImage = doc.selectFirst("meta[property=og:image]")?.attr("content") ?: ""

        // 1. Structured JSON-LD — most recipe sites embed complete recipe data here
        extractJsonLdRecipe(doc, ogImage)?.let { return Result.success(it) }

        // 2. Extract clean page text and try Gemini
        val pageText = extractPageText(doc)
        if (pageText.isNotBlank()) {
            val geminiResult = extractWithGemini(pageText, ogImage)
            geminiResult.onSuccess { return Result.success(it) }
            // Propagate "no recipe" signal directly — don't fall back to OG tags
            if (geminiResult.exceptionOrNull()?.message == "Kein Rezept auf dieser Seite gefunden.")
                return geminiResult
        }

        // 3. Fallback: OG meta tags + local extraction
        val ogTitle = doc.selectFirst("meta[property=og:title]")?.attr("content")
        val rawTitle = ogTitle?.takeIf { it.isNotBlank() }
            ?: doc.title().takeIf { it.isNotBlank() }
            ?: "Untitled Recipe"
        val ogDescription = doc.selectFirst("meta[property=og:description]")?.attr("content")
            ?: doc.selectFirst("meta[name=description]")?.attr("content")
            ?: ""

        val title = TitleExtractor.extract(rawTitle)
        return Result.success(
            RecipeMetadata(
                title = title,
                thumbnailUrl = ogImage,
                description = ogDescription,
                ingredients = extractIngredients(ogDescription),
                category = detectCategory(title, ogDescription),
                tags = TagDetector.detect(title, ogDescription),
                isVegetarian = detectVegetarian(title, ogDescription)
            )
        )
    }

    /**
     * Tries to extract a Recipe from JSON-LD structured data embedded in the page.
     * Many recipe sites (Chefkoch, AllRecipes, BBC Food, etc.) include this.
     * Returns null if no valid Recipe object is found.
     */
    private fun extractJsonLdRecipe(doc: org.jsoup.nodes.Document, fallbackImage: String): RecipeMetadata? {
        for (script in doc.select("script[type=application/ld+json]")) {
            try {
                val raw = script.html().trim()
                val json: org.json.JSONObject = try {
                    org.json.JSONObject(raw)
                } catch (_: Exception) {
                    // Some sites wrap it in an array: [{ "@type": "Recipe" }]
                    val arr = org.json.JSONArray(raw)
                    arr.optJSONObject(0) ?: continue
                }
                val recipe = findRecipeObject(json) ?: continue

                val title = recipe.optString("name").takeIf { it.isNotBlank() } ?: continue
                val ingredientsArr = recipe.optJSONArray("recipeIngredient") ?: continue
                val ingredients = (0 until ingredientsArr.length()).map { ingredientsArr.getString(it) }
                if (ingredients.isEmpty()) continue

                val steps = extractJsonLdSteps(recipe)
                val image = extractJsonLdImage(recipe).ifBlank { fallbackImage }
                val cookTime = parseDuration(recipe.optString("totalTime"))
                    .takeIf { it > 0 }
                    ?: (parseDuration(recipe.optString("cookTime")) + parseDuration(recipe.optString("prepTime")))
                val servings = recipe.optString("recipeYield")
                    .filter { it.isDigit() }.take(4).toIntOrNull() ?: 0
                val description = recipe.optString("description")
                val allText = "$title $description ${ingredients.joinToString()}"

                return RecipeMetadata(
                    title = title,
                    thumbnailUrl = image,
                    description = description,
                    ingredients = ingredients,
                    category = detectCategory(title, allText),
                    tags = TagDetector.detect(title, allText),
                    isVegetarian = detectVegetarian(title, allText),
                    servings = servings,
                    cookingTimeMinutes = cookTime,
                    geminiSteps = steps
                )
            } catch (_: Exception) { continue }
        }
        return null
    }

    /** Walks a JSON-LD object looking for @type == "Recipe", also checking @graph arrays. */
    private fun findRecipeObject(json: org.json.JSONObject): org.json.JSONObject? {
        val type = json.optString("@type")
        if (type == "Recipe") return json
        // @type can also be an array: "@type": ["Recipe", "NewsArticle"]
        val typeArr = json.optJSONArray("@type")
        if (typeArr != null) {
            for (i in 0 until typeArr.length()) {
                if (typeArr.optString(i) == "Recipe") return json
            }
        }
        // @graph is a common wrapper: { "@graph": [ { "@type": "Recipe" }, ... ] }
        val graph = json.optJSONArray("@graph") ?: return null
        for (i in 0 until graph.length()) {
            val obj = graph.optJSONObject(i) ?: continue
            findRecipeObject(obj)?.let { return it }
        }
        return null
    }

    /** Extracts step texts from recipeInstructions, handling HowToStep and HowToSection. */
    private fun extractJsonLdSteps(recipe: org.json.JSONObject): List<String> {
        val arr = recipe.optJSONArray("recipeInstructions") ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            when (val item = arr.get(i)) {
                is String -> if (item.isNotBlank()) result.add(item.trim())
                is org.json.JSONObject -> {
                    if (item.optString("@type") == "HowToSection") {
                        // Section contains nested steps
                        val sub = item.optJSONArray("itemListElement")
                        if (sub != null) {
                            for (j in 0 until sub.length()) {
                                val s = sub.optJSONObject(j)
                                val t = s?.optString("text")?.takeIf { it.isNotBlank() }
                                    ?: s?.optString("name")?.takeIf { it.isNotBlank() }
                                if (t != null) result.add(t.trim())
                            }
                        }
                    } else {
                        val text = item.optString("text").takeIf { it.isNotBlank() }
                            ?: item.optString("name").takeIf { it.isNotBlank() }
                        if (text != null) result.add(text.trim())
                    }
                }
            }
        }
        return result
    }

    /** Extracts the first image URL from a Recipe's image field (String, ImageObject, or array). */
    private fun extractJsonLdImage(recipe: org.json.JSONObject): String {
        if (!recipe.has("image")) return ""
        return when (val img = recipe.get("image")) {
            is String -> img
            is org.json.JSONObject -> img.optString("url")
            is org.json.JSONArray -> {
                val first = img.opt(0)
                when (first) {
                    is String -> first
                    is org.json.JSONObject -> first.optString("url")
                    else -> ""
                }
            }
            else -> ""
        }
    }

    /** Parses ISO 8601 duration strings like PT30M, PT1H30M → total minutes. */
    private fun parseDuration(iso: String): Int {
        if (iso.isBlank()) return 0
        var minutes = 0
        Regex("""(\d+)H""").find(iso)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { minutes += it * 60 }
        Regex("""(\d+)M""").find(iso)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { minutes += it }
        return minutes
    }

    /**
     * Strips boilerplate (nav, footer, scripts, ads) from the page and returns
     * structured text with newlines preserved between block elements.
     * Trimmed to 8 000 chars so Gemini can identify ingredients vs. steps.
     */
    private fun extractPageText(doc: org.jsoup.nodes.Document): String {
        val clone = doc.clone()
        clone.select(
            "script, style, noscript, iframe, " +
            "nav, footer, header, aside, " +
            "[class*='nav'], [class*='menu'], [class*='sidebar'], " +
            "[class*='cookie'], [class*='banner'], [class*='ad-'], " +
            "[class*='popup'], [class*='modal'], [class*='newsletter'], " +
            "[id*='nav'], [id*='menu'], [id*='footer'], [id*='header'], " +
            "[id*='sidebar'], [id*='cookie'], [id*='banner']"
        ).remove()

        val mainContent = clone.selectFirst(
            "main, article, [class*='recipe'], [class*='content'], [id*='recipe'], [id*='content']"
        )
        val el = mainContent ?: clone.body() ?: return ""

        // Traverse DOM preserving block-element newlines so Gemini can see structure
        val sb = StringBuilder()
        val blockTags = setOf("p", "li", "br", "h1", "h2", "h3", "h4", "h5", "h6",
                              "div", "section", "td", "dt", "dd")
        el.traverse(object : org.jsoup.select.NodeVisitor {
            override fun head(node: org.jsoup.nodes.Node, depth: Int) {
                if (node is org.jsoup.nodes.TextNode) {
                    val text = node.text().trim()
                    if (text.isNotBlank()) sb.append(text).append(" ")
                }
            }
            override fun tail(node: org.jsoup.nodes.Node, depth: Int) {
                if (node is org.jsoup.nodes.Element && node.tagName() in blockTags) {
                    if (sb.isNotEmpty() && sb.last() != '\n') sb.append("\n")
                }
            }
        })
        return sb.toString().trim().take(8_000)
    }

    // ── HTTP helpers ─────────────────────────────────────────────────────────

    private fun getHtml(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "identity")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) null else response.body?.string()
        } catch (e: Exception) {
            null
        }
    }

    private fun getJson(url: String): JSONObject? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_UA)
                .header("Accept", "application/json")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            JSONObject(body)
        } catch (e: Exception) {
            null
        }
    }

    // Follow redirects and return the final URL (e.g. vm.tiktok.com → full URL).
    // Uses a no-redirect client to read the Location header manually so we get
    // the resolved URL without downloading the full page.
    private fun resolveRedirect(url: String): String {
        return try {
            val noRedirectClient = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
            var current = url
            repeat(5) {
                val request = Request.Builder()
                    .url(current)
                    .header("User-Agent", BROWSER_UA)
                    .head() // HEAD request — no body needed, just headers
                    .build()
                val response = noRedirectClient.newCall(request).execute()
                val location = response.header("Location")
                if (response.isRedirect && location != null) {
                    current = if (location.startsWith("http")) location
                    else "https://www.tiktok.com$location"
                } else {
                    return current
                }
            }
            current
        } catch (e: Exception) {
            url // return original if resolution fails
        }
    }

    // ── Text processing ───────────────────────────────────────────────────────

    private fun extractIngredients(description: String): List<String> {
        if (description.isBlank()) return listOf("Recipe in the video")

        val numberedPattern = Regex("""(?m)^\d+[.)]\s*(.+)$""")
        val numbered = numberedPattern.findAll(description).map { it.groupValues[1].trim() }.toList()
        if (numbered.isNotEmpty()) return numbered

        // Handle TikTok's tab-bullet format: \t•\tItem or plain bullet lines
        val bulletPattern = Regex("""(?:^|\t)[•\-*]\t?(.+?)(?=\t[•\-*]|\n|$)""")
        val bullets = bulletPattern.findAll(description).map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.toList()
        if (bullets.isNotEmpty()) return bullets

        val measureWords = listOf("cup", "tbsp", "tsp", "gram", "g ", "oz", "lb", "ml", "liter")
        val lines = description.split("\n", ",")
            .map { it.trim() }
            .filter { it.length in 3..100 }
            .filter { line ->
                val lower = line.lowercase()
                line.first().isDigit() || measureWords.any { lower.contains(it) }
            }
        if (lines.isNotEmpty()) return lines.take(20)

        return listOf("Recipe in the video")
    }

    private fun detectCategory(title: String, description: String): String {
        val text = (title + " " + description).lowercase()
        return when {
            text.any("turkey", "pute", "truthahn", "putenbrust", "putenfilet", "putenschnitzel") -> "Pute"
            text.any("chicken", "poultry", "hen", "wings", "drumstick", "duck", "goose",
                "hähnchen", "huhn", "hühnchen", "ente", "gans", "geflügel") -> "Hähnchen"
            text.any("beef", "steak", "brisket", "ground beef", "short rib",
                "rind", "rindfleisch", "ochse") -> "Rind"
            text.any("salmon", "tuna", "cod", "fish", "trout", "tilapia", "sea bass", "anchovy",
                "lachs", "thunfisch", "forelle", "kabeljau", "dorade", "zander", "fisch") -> "Fisch"
            text.any("shrimp", "prawn", "lobster", "crab", "seafood", "scallop", "mussel", "squid",
                "garnele", "hummer", "krabbe", "meeresfrüchte", "muschel", "tintenfisch") -> "Meeresfrüchte"
            text.any("pasta", "spaghetti", "penne", "fettuccine", "linguine", "tagliatelle",
                "carbonara", "lasagna", "lasagne", "ravioli", "gnocchi", "nudel") -> "Pasta"
            text.any("rice", "risotto", "fried rice", "paella", "reis") -> "Reis"
            text.any("potato", "potatoes", "fries", "kartoffel", "pommes", "bratkartoffel") -> "Kartoffeln"
            text.any("taco", "burrito", "quesadilla", "mexican", "enchilada", "mexikanisch") -> "Mexikanisch"
            text.any("sushi", "ramen", "asian", "stir fry", "stir-fry", "teriyaki",
                "thai", "korean", "japanese", "chinese", "asiatisch", "wok") -> "Asiatisch"
            text.any("vegetarian", "vegan", "veggie", "plant-based",
                "vegetarisch", "pflanzlich") -> "Vegetarisch"
            else -> "Andere"
        }
    }

    private fun String.any(vararg keywords: String) = keywords.any { this.contains(it) }

    private fun detectVegetarian(title: String, description: String): Boolean {
        val text = (title + " " + description).lowercase()
        val hasMeatKeyword = MEAT_KEYWORDS.any { text.contains(it) }
        if (hasMeatKeyword) return false

        val hasVegKeyword = VEG_KEYWORDS.any { text.contains(it) }
        return hasVegKeyword || text.contains("vegetable") || text.contains("gemüse")
    }
}
