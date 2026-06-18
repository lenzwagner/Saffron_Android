package com.zephron.app.ui

/**
 * Parses ingredient strings, normalises units + names and aggregates
 * identical ingredients from multiple recipes into a single entry.
 *
 * Input : List<Pair<ingredientText, recipeTitle>>
 * Output: List<AggregatedIngredient>
 */
object IngredientAggregator {

    // ── Public model ──────────────────────────────────────────────────────────

    data class AggregatedIngredient(
        /** Full display string, e.g. "500g Mehl" or "3 Eier" */
        val text: String,
        /** Formatted amount + unit for the badge chip, e.g. "500g". Empty if none. */
        val amountText: String,
        /** Ingredient name part, e.g. "Mehl". Equals text when no amount was parsed. */
        val nameText: String,
        /** Recipe titles that contributed to this entry. Size > 1 means it was merged. */
        val sources: List<String>,
        /** Stable unique key for Compose / SwiftUI list identity. */
        val key: String
    ) {
        val isAggregated: Boolean get() = sources.size > 1
    }

    // ── Unit normalisation ────────────────────────────────────────────────────

    private val unitMap = mapOf(
        // weight
        "gramm" to "g", "gr" to "g",
        "kilogramm" to "kg",
        // volume
        "milliliter" to "ml", "millilitre" to "ml",
        "liter" to "l", "litre" to "l",
        // cooking measures
        "esslöffel" to "EL", "el" to "EL",
        "teelöffel" to "TL", "tl" to "TL",
        "tasse" to "Tasse", "tassen" to "Tasse",
        // count/misc
        "stück" to "Stk", "stk" to "Stk",
        "bund" to "Bund", "bunde" to "Bund",
        "prise" to "Prise", "prisen" to "Prise",
        "dose" to "Dose", "dosen" to "Dose",
        "packung" to "Pkg", "pkg" to "Pkg", "pck" to "Pkg",
        "zehe" to "Zehe", "zehen" to "Zehe",
        "scheibe" to "Scheibe", "scheiben" to "Scheibe",
        "handvoll" to "Handvoll"
    )

    private fun normaliseUnit(raw: String): String {
        val key = raw.lowercase().trimEnd('.')
        return unitMap[key] ?: raw
    }

    // ── Name normalisation (simple German stemming for plurals) ──────────────

    private fun normaliseName(name: String): String {
        val s = name.lowercase().trim()
        // Try removing common plural suffixes, keeping stem ≥ 3 chars
        for (suf in listOf("en", "eln", "ern", "n", "e", "s")) {
            if (s.endsWith(suf) && s.length - suf.length >= 3) {
                return s.dropLast(suf.length)
            }
        }
        return s
    }

    // ── Quantity formatting ───────────────────────────────────────────────────

    private fun formatQty(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString()
        else "%.1f".format(v).trimEnd('0').trimEnd('.')

    // ── Ingredient parser ─────────────────────────────────────────────────────

    private data class Parsed(
        val qty: Double?,
        val unit: String?,          // already normalised
        val rawName: String,        // original capitalisation
        val normKey: String,        // normalisedName__unit for grouping
        val raw: String,
        val recipe: String
    )

    // Matches: optional leading number  ·  optional unit word  ·  rest = name
    // e.g. "200g Mehl", "2 EL Öl", "½ TL Salz", "3 Eier", "etwas Pfeffer"
    private val RE = Regex("""^([\d.,/½¼¾⅓⅔⅛⅜⅝⅞]+)\s*([a-zA-ZäöüÄÖÜß]+\.?)?\s+(.+)$""")

    private fun parse(text: String, recipe: String): Parsed {
        val t = text.trim()
        val m = RE.find(t)
        if (m != null) {
            val qtyStr = m.groupValues[1].replace(",", ".")
            val qty = qtyStr.toDoubleOrNull()
            val rawUnit = m.groupValues[2].trim()
            val name = m.groupValues[3].trim()
            val unit = if (rawUnit.isBlank()) null else normaliseUnit(rawUnit)
            val normKey = "${normaliseName(name)}__${unit ?: "count"}"
            return Parsed(qty, unit, name, normKey, t, recipe)
        }
        // No numeric prefix — treat whole line as name
        val normKey = "${normaliseName(t)}__"
        return Parsed(null, null, t, normKey, t, recipe)
    }

    // ── Main entry point ──────────────────────────────────────────────────────

    /**
     * @param items  Pairs of (scaledIngredientText, recipeTitle)
     */
    fun aggregate(items: List<Pair<String, String>>): List<AggregatedIngredient> {
        // Preserve insertion order of first occurrence
        val groups = LinkedHashMap<String, MutableList<Parsed>>()
        items.forEach { (text, recipe) ->
            val p = parse(text, recipe)
            groups.getOrPut(p.normKey) { mutableListOf() }.add(p)
        }

        return groups.values.mapIndexed { idx, group ->
            val recipes = group.map { it.recipe }.distinct()

            // Only merge when every entry has a parseable quantity
            val allHaveQty = group.all { it.qty != null }
            if (group.size == 1 || !allHaveQty) {
                // No aggregation — return original text of first entry
                // (if multiple entries without qty, just show the first)
                val e = group.first()
                AggregatedIngredient(
                    text = e.raw,
                    amountText = "",
                    nameText = e.raw,
                    sources = recipes,
                    key = "agg_$idx"
                )
            } else {
                val totalQty = group.sumOf { it.qty!! }
                val unit = group.first().unit
                val name = group.first().rawName
                val amountText = if (unit != null) "${formatQty(totalQty)} $unit" else formatQty(totalQty)
                val display = "$amountText $name"
                AggregatedIngredient(
                    text = display,
                    amountText = amountText,
                    nameText = name,
                    sources = recipes,
                    key = "agg_$idx"
                )
            }
        }
    }
}
