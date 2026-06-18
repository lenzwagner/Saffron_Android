package com.zephron.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.zephron.app.notification.NotificationScheduler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zephron.app.ui.screens.LoginScreen
import com.zephron.app.ui.screens.MainScreen
import com.zephron.app.ui.screens.OnboardingScreen
import com.zephron.app.ui.screens.SplashScreen
import com.zephron.app.update.UpdateChecker
import com.zephron.app.update.UpdateDialog
import com.zephron.app.update.UpdateInfo
import com.zephron.app.ui.theme.AppColors
import com.zephron.app.ui.theme.ZephronTheme
import com.zephron.app.viewmodel.DarkModePreference
import com.zephron.app.viewmodel.ImportViewModel
import com.zephron.app.viewmodel.SettingsViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val importViewModel: ImportViewModel by lazy {
        ViewModelProvider(this)[ImportViewModel::class.java]
    }

    private val recipeViewModel: com.zephron.app.viewmodel.RecipeViewModel by lazy {
        ViewModelProvider(this)[com.zephron.app.viewmodel.RecipeViewModel::class.java]
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("saffron_prefs", Context.MODE_PRIVATE)
        val tag = prefs.getString("language", "system") ?: "system"
        val context = if (tag == "system") {
            newBase
        } else {
            val locale = Locale(tag)
            Locale.setDefault(locale)
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            newBase.createConfigurationContext(config)
        }
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        // Instant exit to suppress the native screen as much as possible
        splashScreen.setOnExitAnimationListener { splashProvider ->
            splashProvider.remove()
        }

        enableEdgeToEdge()

        // Ensure the FCM/notifications channel exists for background pushes
        com.zephron.app.utils.NotificationHelper.ensureChannel(this)

        // Schedule daily notification if not already queued
        NotificationScheduler.createChannel(this)
        if (!NotificationScheduler.isAlreadyScheduled(this)) {
            NotificationScheduler.scheduleNext(this)
        }

        // Handle URL shared at cold launch
        handleSharedIntent(intent)

        setContent {
            ZephronApp(onLanguageChange = { recreate() })
        }
    }

    /** Called when the app is already open and a new share arrives (singleTask). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSharedIntent(intent)
    }

    private fun handleSharedIntent(intent: Intent?) {
        // Widget tap → open recipe detail
        val widgetRecipeId = intent?.getIntExtra(EXTRA_WIDGET_RECIPE_ID, -1) ?: -1
        if (widgetRecipeId != -1) {
            recipeViewModel.requestOpenRecipe(widgetRecipeId)
            return
        }
        // URL share → go to import
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            val url = text.split("\\s+".toRegex())
                .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
                ?: return
            importViewModel.onSharedUrl(url)
        }
    }

    companion object {
        const val EXTRA_WIDGET_RECIPE_ID = "widget_recipe_id"
    }
}

@Composable
fun ZephronApp(onLanguageChange: () -> Unit = {}) {
    val settingsViewModel: SettingsViewModel = viewModel()
    val language by settingsViewModel.language.collectAsState()
    val context = LocalContext.current

    // Recreate if the language from settings doesn't match the active prefs
    // This handles sync-from-cloud cases.
    LaunchedEffect(language) {
        val prefs = context.getSharedPreferences("saffron_prefs", Context.MODE_PRIVATE)
        val activeTag = prefs.getString("language", "system")
        if (language.tag != activeTag) {
            onLanguageChange()
        }
    }

    val darkMode by settingsViewModel.darkMode.collectAsState()
    val systemDark = isSystemInDarkTheme()

    val isDark = when (darkMode) {
        DarkModePreference.SYSTEM -> systemDark
        DarkModePreference.LIGHT -> false
        DarkModePreference.DARK -> true
    }

    val accentColor by settingsViewModel.accentColor.collectAsState()
    val secondaryColor by settingsViewModel.secondaryColor.collectAsState()
    val appColors = AppColors(accent = accentColor, secondary = secondaryColor)

    ZephronTheme(darkTheme = isDark, appColors = appColors) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val isLoginComplete by settingsViewModel.loginCompleteFlow.collectAsState()
        val isOnboarded by settingsViewModel.onboarded.collectAsState()

        android.util.Log.d("ZephronApp", "ZephronApp recomposing. isLoginComplete=$isLoginComplete, isOnboarded=$isOnboarded")
        var showSplash by remember { mutableStateOf(true) }
        var pendingUpdate by remember { mutableStateOf<UpdateInfo?>(null) }

        LaunchedEffect(isOnboarded) {
            if (isOnboarded) {
                val current = context.packageManager
                    .getPackageInfo(context.packageName, 0).longVersionCode.toInt()
                pendingUpdate = UpdateChecker.checkForUpdate(current)
            }
        }

        LaunchedEffect(isOnboarded) {
            if (isOnboarded && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(context as ComponentActivity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
            }
        }

        pendingUpdate?.let { info ->
            UpdateDialog(
                info = info,
                onConfirm = {
                    pendingUpdate = null
                    UpdateChecker.downloadAndInstall(context, info.apkUrl)
                },
                onDismiss = { pendingUpdate = null }
            )
        }

        when {
            showSplash -> SplashScreen(onSplashComplete = { showSplash = false })
            !isLoginComplete -> LoginScreen(
                onContinueAsGuest = { settingsViewModel.setGuestMode() },
                onGoogleSignIn = { name, email ->
                    scope.launch {
                        android.util.Log.d("ZephronApp", "Starting Google Sign In from UI for $name ($email)")
                        val result = settingsViewModel.signInWithGoogle(context)
                        android.util.Log.d("ZephronApp", "Sign In result: ${result.isSuccess}")
                        if (result.isFailure) {
                            android.util.Log.e("ZephronApp", "Sign In failed", result.exceptionOrNull())
                        }
                    }
                }
            )
            !isOnboarded -> OnboardingScreen(
                onDone = { name, mode, accent, secondary ->
                    settingsViewModel.completeOnboarding(name, mode, accent, secondary)
                }
            )
            else -> MainScreen(
                settingsViewModel = settingsViewModel,
                onLanguageChange = onLanguageChange
            )
        }
    }
}
