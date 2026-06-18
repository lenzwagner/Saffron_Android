package com.zephron.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.zephron.app.MainActivity
import com.zephron.app.R
import com.zephron.app.data.Recipe
import com.zephron.app.data.RecipeDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// ─── Prefs keys ───────────────────────────────────────────────────────────────

private const val PREFS_NAME = "recipe_of_day_prefs"
private const val KEY_DATE   = "rotd_date"
private const val KEY_ID     = "rotd_id"

private fun todayString() =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

// 5 bundled food background drawables — cycled by recipe.id
private val FOOD_BG_DRAWABLES = listOf(
    R.drawable.widget_food_bg_1,
    R.drawable.widget_food_bg_2,
    R.drawable.widget_food_bg_3,
    R.drawable.widget_food_bg_4,
    R.drawable.widget_food_bg_5
)

// ─── Widget ───────────────────────────────────────────────────────────────────

class RecipeOfTheDayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val recipe = loadRecipeOfTheDay(context)
        provideContent { WidgetContent(recipe) }
    }

    companion object {
        suspend fun loadRecipeOfTheDay(context: Context, forceNew: Boolean = false): Recipe? =
            withContext(Dispatchers.IO) {
                val prefs: SharedPreferences =
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val today     = todayString()
                val savedDate = prefs.getString(KEY_DATE, null)
                val savedId   = prefs.getInt(KEY_ID, -1)
                val dao = RecipeDatabase.getDatabase(context).recipeDao()

                if (!forceNew && savedDate == today && savedId != -1) {
                    val existing = dao.getById(savedId)
                    if (existing != null) return@withContext existing
                }

                val all = dao.getAllRecipesOnce()
                if (all.isEmpty()) return@withContext null
                val candidate = if (all.size > 1) all.filter { it.id != savedId }.random()
                                else all.first()
                prefs.edit()
                    .putString(KEY_DATE, today)
                    .putInt(KEY_ID, candidate.id)
                    .apply()
                candidate
            }
    }
}

// ─── Content ──────────────────────────────────────────────────────────────────

@Composable
private fun WidgetContent(recipe: Recipe?) {
    val accent  = GlanceTheme.colors.primary
    val onAcc   = GlanceTheme.colors.onPrimary
    val onSurf  = GlanceTheme.colors.onSurfaceVariant

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
    ) {
        if (recipe == null) {
            // Plain surface background for empty state
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.surface)
            ) {
                EmptyWidgetContent(GlanceTheme.colors.onSurface)
            }
        } else {
            // ── Food background image ─────────────────────────────────────────
            val bgRes = FOOD_BG_DRAWABLES[recipe.id % FOOD_BG_DRAWABLES.size]
            Image(
                provider = ImageProvider(bgRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize()
            )

            // ── Dark gradient overlay (semi-transparent, so image peeks through) ──
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Color(0xCC111111)))  // ~80% dark
            ) {}

            // ── Content on top ────────────────────────────────────────────────
            FilledWidgetContent(recipe, accent, onAcc, onSurf)
        }
    }
}

@Composable
private fun FilledWidgetContent(
    recipe: Recipe,
    accent: ColorProvider,
    onAcc: ColorProvider,
    onSurf: ColorProvider
) {
    val white       = ColorProvider(Color.White)
    val whiteSubtle = ColorProvider(Color(0xBBFFFFFF))

    Column(
        modifier = GlanceModifier.fillMaxSize().padding(0.dp)
    ) {
        // ── Header strip ─────────────────────────────────────────────────────
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ColorProvider(Color(0x88000000)))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = GlanceModifier.fillMaxWidth()
            ) {
                Text(
                    text = "✨  Rezept des Tages",
                    style = TextStyle(
                        color = white,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                Box(
                    modifier = GlanceModifier
                        .size(28.dp)
                        .cornerRadius(14.dp)
                        .background(ColorProvider(Color(0x44FFFFFF)))
                        .clickable(actionRunCallback<RandomizeCallback>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⟳",
                        style = TextStyle(
                            color = white,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        // ── Recipe info ───────────────────────────────────────────────────────
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (recipe.category.isNotBlank()) {
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(8.dp)
                        .background(ColorProvider(Color(0x55FFFFFF)))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = recipe.category,
                        style = TextStyle(color = white, fontSize = 10.sp),
                        maxLines = 1
                    )
                }
                Spacer(modifier = GlanceModifier.height(6.dp))
            }

            Text(
                text = recipe.title,
                style = TextStyle(
                    color = white,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 3
            )

            Spacer(modifier = GlanceModifier.defaultWeight())

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = GlanceModifier.fillMaxWidth()
            ) {
                if (recipe.cookingTimeMinutes > 0) {
                    Text(
                        text = "⏱ ${recipe.cookingTimeMinutes} min",
                        style = TextStyle(color = whiteSubtle, fontSize = 11.sp),
                        modifier = GlanceModifier.defaultWeight()
                    )
                }
                if (recipe.servings > 0) {
                    Text(
                        text = "👤 ${recipe.servings}",
                        style = TextStyle(color = whiteSubtle, fontSize = 11.sp)
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // CTA — opens the recipe inside the app (not in browser)
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .cornerRadius(12.dp)
                    .background(accent)
                    .padding(vertical = 8.dp)
                    .clickable(
                        androidx.glance.appwidget.action.actionStartActivity(
                            Intent(Intent.ACTION_MAIN).apply {
                                setClassName(
                                    "com.zephron.app",
                                    "com.zephron.app.MainActivity"
                                )
                                putExtra(MainActivity.EXTRA_WIDGET_RECIPE_ID, recipe.id)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Rezept ansehen →",
                    style = TextStyle(
                        color = onAcc,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
private fun EmptyWidgetContent(onBg: ColorProvider) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "🍽️", style = TextStyle(fontSize = 32.sp))
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = "Noch keine Rezepte",
            style = TextStyle(color = onBg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = "Füge Rezepte in Zephron hinzu",
            style = TextStyle(color = onBg, fontSize = 11.sp),
            maxLines = 2
        )
    }
}

// ─── Randomize action ─────────────────────────────────────────────────────────

class RandomizeCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        RecipeOfTheDayWidget.loadRecipeOfTheDay(context, forceNew = true)
        RecipeOfTheDayWidget().update(context, glanceId)
    }
}

// ─── Receiver ─────────────────────────────────────────────────────────────────

class RecipeOfTheDayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecipeOfTheDayWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_DATE_CHANGED ||
            intent.action == Intent.ACTION_TIME_CHANGED) {
            CoroutineScope(Dispatchers.IO).launch {
                RecipeOfTheDayWidget.loadRecipeOfTheDay(context, forceNew = false)
                GlanceAppWidgetManager(context)
                    .getGlanceIds(RecipeOfTheDayWidget::class.java)
                    .forEach { RecipeOfTheDayWidget().update(context, it) }
            }
        }
    }
}

// ─── Date-change boot receiver ────────────────────────────────────────────────

class WidgetDateChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_DATE_CHANGED &&
            intent.action != Intent.ACTION_TIME_CHANGED) return
        CoroutineScope(Dispatchers.IO).launch {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().remove(KEY_DATE).apply()
            GlanceAppWidgetManager(context)
                .getGlanceIds(RecipeOfTheDayWidget::class.java)
                .forEach { RecipeOfTheDayWidget().update(context, it) }
        }
    }
}
