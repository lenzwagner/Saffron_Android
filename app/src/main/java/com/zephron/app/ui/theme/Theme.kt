package com.zephron.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun ZephronTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    appColors: AppColors = AppColors(),
    content: @Composable () -> Unit
) {
    val accent = appColors.accent
    val secondary = appColors.secondary

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary             = accent.lighten(0.30f),
            onPrimary           = accent.darken(0.50f),
            primaryContainer    = accent.darken(0.35f),
            onPrimaryContainer  = accent.lighten(0.55f),

            secondary           = Amber,
            onSecondary         = Color.White,
            secondaryContainer  = AmberContainerDark,
            onSecondaryContainer = AmberContainer,

            tertiary            = secondary.lighten(0.35f),
            onTertiary          = secondary.darken(0.55f),
            tertiaryContainer   = secondary.darken(0.40f),
            onTertiaryContainer = secondary.lighten(0.55f),

            background          = BackgroundDark,
            onBackground        = OnBackgroundDark,
            surface             = SurfaceDark,
            onSurface           = OnSurfaceDark,
            surfaceVariant      = SurfaceVariantDark,
            onSurfaceVariant    = OnSurfaceVariantDark,

            outline             = OutlineDark,
            outlineVariant      = OutlineVariantDark,

            error               = ErrorRedDark,
            onError             = Color(0xFF690005),
            errorContainer      = ErrorContainerDark,
            onErrorContainer    = Color(0xFFFFDAD6),
        )
    } else {
        lightColorScheme(
            primary             = accent,
            onPrimary           = Color.White,
            primaryContainer    = accent.lighten(0.50f),
            onPrimaryContainer  = accent.darken(0.55f),

            secondary           = Amber,
            onSecondary         = Color.White,
            secondaryContainer  = AmberContainer,
            onSecondaryContainer = OnAmberContainer,

            tertiary            = secondary,
            onTertiary          = Color.White,
            tertiaryContainer   = secondary.lighten(0.55f),
            onTertiaryContainer = secondary.darken(0.55f),

            background          = BackgroundLight,
            onBackground        = OnBackgroundLight,
            surface             = SurfaceLight,
            onSurface           = OnBackgroundLight,
            surfaceVariant      = SurfaceVariantLight,
            onSurfaceVariant    = OnSurfaceVariantLight,

            outline             = OutlineLight,
            outlineVariant      = OutlineVariantLight,

            error               = ErrorRed,
            onError             = Color.White,
        )
    }

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ZephronTypography
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = colorScheme.background,
                contentColor = colorScheme.onBackground,
                content = content
            )
        }
    }
}
