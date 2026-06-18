package com.zephron.app.network

/**
 * Extracts a clean recipe name from a raw social media caption.
 *
 * TikTok / Instagram captions follow a predictable pattern:
 *   "Recipe Name 🔥🥪 some hype text >> more text #hashtag1 #hashtag2"
 *
 * Strategy:
 *  1. Strip hashtags and @mentions
 *  2. Cut at the first emoji — recipe name almost always comes before emojis
 *  3. If no emoji found, cut at the first text separator (>>, |, newline, etc.)
 *  4. Remove leftover filler phrases ("how to make", "easy", etc.)
 *  5. Clamp to max 6 words
 *  6. Title-case the result
 */
object TitleExtractor {

    private val SEPARATORS = listOf(">>", " | ", " // ", " – ", " — ", " - ", ": ", "\n", "!")

    private val FILLER_PREFIXES = listOf(
        "how to make ", "how to cook ", "the best ", "best ",
        "homemade ", "quick ", "simple ", "recipe for ", "making ", "easy "
    )

    // Emoji Unicode codepoint ranges (no regex — avoids PatternSyntaxException)
    private val EMOJI_RANGES = listOf(
        0x1F300..0x1FAFF,
        0x2600..0x27FF,
        0xFE00..0xFEFF,
        0x1F000..0x1F02F,
        0x200D..0x200D,
        0xFE0F..0xFE0F
    )

    private val HASHTAG_MENTION_REGEX = Regex("""[#@]\S+""")
    private val TRAILING_NOISE = Regex("""[!?,.:;~*\s]+$""")
    private val EXCESS_WHITESPACE = Regex("""\s{2,}""")

    // Hype phrases that appear BEFORE the actual recipe name in social-media captions.
    // e.g. "when you want something healthy… Crispy Chicken Strips"
    private val HYPE_STARTS = listOf(
        "when you", "when i", "pov:", "pov ", "this is it", "this is the",
        "you need to try", "you have to try", "trust me", "hear me out",
        "ok so", "okay so", "not me", "literally", "the way", "i made",
        "trying", "made this", "obsessed with", "can we talk about"
    )

    fun extract(raw: String): String {
        if (raw.isBlank()) return raw

        // 1. Remove hashtags and @mentions
        var text = raw.replace(HASHTAG_MENTION_REGEX, "").trim()

        // 2. Split text into segments separated by emojis, collect non-empty segments
        val segments = splitByEmoji(text).map { it.trim() }.filter { it.length >= 3 }

        // 3. Pick the best segment as candidate:
        //    - Skip segments that start with known hype phrases
        //    - Skip segments that look like questions or calls-to-action ("who's making", "comment", etc.)
        //    - Prefer the first segment that doesn't look like hype
        val candidate = segments.firstOrNull { seg ->
            val lower = seg.lowercase()
            HYPE_STARTS.none { lower.startsWith(it) } &&
            !lower.startsWith("who") &&
            !lower.startsWith("comment") &&
            !lower.startsWith("follow") &&
            !lower.startsWith("save") &&
            !lower.contains("?")
        } ?: segments.firstOrNull() ?: text

        var result = candidate

        // 4. Cut at the first text separator within the candidate
        result = SEPARATORS.fold(result) { t, sep ->
            val idx = t.indexOf(sep)
            if (idx > 0) t.substring(0, idx) else t
        }

        // 5. Clean whitespace and trailing punctuation
        result = result.replace(EXCESS_WHITESPACE, " ").trim()
        result = result.replace(TRAILING_NOISE, "").trim()

        // 6. Strip known filler prefixes (case-insensitive)
        val lower = result.lowercase()
        for (prefix in FILLER_PREFIXES) {
            if (lower.startsWith(prefix)) {
                result = result.substring(prefix.length).trim()
                break
            }
        }

        // 7. Clamp to max 8 words (recipe names like "Crispy chicken strips, homemade fries & dip" can be longer)
        val words = result.split(" ").filter { it.isNotBlank() }
        result = if (words.size > 8) words.take(8).joinToString(" ") else words.joinToString(" ")

        // 8. Sentence case — only first letter uppercase
        result = result.lowercase().replaceFirstChar { it.uppercase() }

        return result.ifBlank { raw.take(50).trim() }
    }

    /**
     * Splits text into segments at every emoji boundary.
     * e.g. "hello😀world🔥foo" → ["hello", "world", "foo"]
     */
    private fun splitByEmoji(text: String): List<String> {
        val segments = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (EMOJI_RANGES.any { cp in it }) {
                if (current.isNotBlank()) segments.add(current.toString().trim())
                current.clear()
                // skip consecutive emojis and ZWJ sequences
                while (i < text.length) {
                    val cp2 = text.codePointAt(i)
                    if (EMOJI_RANGES.any { cp2 in it }) {
                        i += Character.charCount(cp2)
                    } else break
                }
            } else {
                current.appendCodePoint(cp)
                i += Character.charCount(cp)
            }
        }
        if (current.isNotBlank()) segments.add(current.toString().trim())
        return segments
    }

    /** Returns all text before the first emoji codepoint. */
    private fun textBeforeFirstEmoji(text: String): String {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (EMOJI_RANGES.any { cp in it }) return text.substring(0, i).trim()
            i += Character.charCount(cp)
        }
        return text // no emoji found
    }

    /** Strips all emoji codepoints from the string. */
    private fun stripEmojis(text: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (EMOJI_RANGES.none { cp in it }) sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return sb.toString()
    }
}
