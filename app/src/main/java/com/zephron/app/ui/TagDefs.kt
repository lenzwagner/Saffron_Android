package com.zephron.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EmojiFoodBeverage
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.RamenDining
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RiceBowl
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.SoupKitchen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

val TAG_GROUPS: List<Pair<String, List<String>>> = listOf(
    "Gang"        to listOf("Vorspeise", "Hauptgang", "Dessert", "Getränk"),
    "Protein"     to listOf("Hähnchen", "Pute", "Rind", "Schwein", "Fisch", "Meeresfrüchte", "Lamm", "Ente"),
    "Kohlenhydrate" to listOf("Reis", "Pasta", "Nudeln", "Kartoffeln", "Brot"),
    "Ernährung"   to listOf("Fleisch", "Vegetarisch", "Vegan"),
    "Temperatur"  to listOf("Warm", "Kalt"),
    "Typ"         to listOf("Suppe", "Salat", "Süß", "Frühstück"),
    "Küche"       to listOf(
        "Italienisch", "Asiatisch", "Chinesisch", "Japanisch", "Thailändisch",
        "Indisch", "Mexikanisch", "Mediterran", "Französisch", "Griechisch",
        "Türkisch", "Amerikanisch", "Deutsch", "Spanisch", "Koreanisch", "Vietnamesisch"
    ),
)

/**
 * Tag groups shown in the filter sheet when the "Zutaten" (ingredients) search
 * tab is active — everything except the cuisine group and the misc "Typ" group.
 */
val INGREDIENT_FILTER_GROUPS: Set<String> =
    setOf("Gang", "Protein", "Kohlenhydrate", "Ernährung", "Temperatur")

val TAG_ICONS: Map<String, ImageVector> = mapOf(
    "Vorspeise"     to Icons.Filled.Restaurant,
    "Hauptgang"     to Icons.Filled.LunchDining,
    "Dessert"       to Icons.Filled.Cake,
    "Getränk"       to Icons.Filled.EmojiFoodBeverage,
    "Hähnchen"      to Icons.Filled.Restaurant,
    "Pute"          to Icons.Filled.Restaurant,
    "Rind"          to Icons.Filled.LunchDining,
    "Schwein"       to Icons.Filled.LunchDining,
    "Fisch"         to Icons.Filled.SetMeal,
    "Meeresfrüchte" to Icons.Filled.SetMeal,
    "Lamm"          to Icons.Filled.Restaurant,
    "Ente"          to Icons.Filled.Restaurant,
    "Reis"          to Icons.Filled.RiceBowl,
    "Pasta"         to Icons.Filled.DinnerDining,
    "Nudeln"        to Icons.Filled.RamenDining,
    "Kartoffeln"    to Icons.Filled.Fastfood,
    "Brot"          to Icons.Filled.BakeryDining,
    "Fleisch"       to Icons.Filled.LunchDining,
    "Vegetarisch"   to Icons.Filled.Eco,
    "Vegan"         to Icons.Filled.Eco,
    "Warm"          to Icons.Filled.LocalFireDepartment,
    "Kalt"          to Icons.Filled.AcUnit,
    "Suppe"         to Icons.Filled.SoupKitchen,
    "Salat"         to Icons.Filled.Grass,
    "Süß"           to Icons.Filled.Cake,
    "Frühstück"     to Icons.Filled.FreeBreakfast,
    "Asiatisch"     to Icons.Filled.RamenDining,
    "Chinesisch"    to Icons.Filled.RamenDining,
    "Japanisch"     to Icons.Filled.SetMeal,
    "Thailändisch"  to Icons.Filled.RamenDining,
    "Koreanisch"    to Icons.Filled.RiceBowl,
    "Vietnamesisch" to Icons.Filled.RamenDining,
    "Mexikanisch"   to Icons.Filled.EmojiFoodBeverage,
    "Italienisch"   to Icons.Filled.DinnerDining,
    "Indisch"       to Icons.Filled.DinnerDining,
    "Mediterran"    to Icons.Filled.WbSunny,
    "Französisch"   to Icons.Filled.BakeryDining,
    "Griechisch"    to Icons.Filled.WbSunny,
    "Türkisch"      to Icons.Filled.Restaurant,
    "Amerikanisch"  to Icons.Filled.LunchDining,
    "Deutsch"       to Icons.Filled.Restaurant,
    "Spanisch"      to Icons.Filled.Restaurant,
)
