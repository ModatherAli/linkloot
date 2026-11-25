package com.modatherali.linkloot.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.modatherali.linkloot.domain.model.ThemeMode

private val LinkLootDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = OnElectricCyan,
    primaryContainer = ElectricCyanContainer,
    onPrimaryContainer = OnElectricCyanContainer,
    secondary = MintTeal,
    onSecondary = OnMintTeal,
    tertiary = IndigoAccent,
    background = DarkObsidian,
    onBackground = DarkTextPrimary,
    surface = DarkSlate,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSlateElevated,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerLow = DarkObsidian,
    surfaceContainer = DarkSlate,
    surfaceContainerHigh = DarkSlateElevated,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    error = DarkError
)

private val LinkLootLightColorScheme = lightColorScheme(
    primary = DeepCyan,
    onPrimary = OnDeepCyan,
    primaryContainer = DeepCyanContainer,
    onPrimaryContainer = OnDeepCyanContainer,
    secondary = IndigoAccent,
    onSecondary = OnIndigoAccent,
    tertiary = MintTeal,
    background = LightPearl,
    onBackground = DeepMidnight,
    surface = LightWhite,
    onSurface = DeepMidnight,
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = SlateTextSecondary,
    surfaceContainerLow = LightPearl,
    surfaceContainer = LightWhite,
    surfaceContainerHigh = LightSurfaceHigh,
    outline = LightBorder,
    outlineVariant = LightBorder,
    error = LightError
)

@Composable
fun LinkLootTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> LinkLootDarkColorScheme
        else -> LinkLootLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
