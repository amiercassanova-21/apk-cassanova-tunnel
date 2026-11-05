package com.v2ray.ang.ui.compose

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.v2ray.ang.R
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Cassanova brand palette (from the logo: royal blue, indigo, gold).
private val LightColor = lightColorScheme(
    primary = Color(0xFF2F55D4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF0F2470),
    secondary = Color(0xFFF5B530),
    onSecondary = Color(0xFF1B1608),
    secondaryContainer = Color(0xFFFFEDC2),
    onSecondaryContainer = Color(0xFF3A2C08),
    tertiary = Color(0xFF1F8A4C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCDEFD9),
    onTertiaryContainer = Color(0xFF0B2E19),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF3F5FB),
    onBackground = Color(0xFF141A2E),
    surface = Color(0xFFF3F5FB),
    onSurface = Color(0xFF141A2E),
    surfaceVariant = Color(0xFFE1E5F0),
    onSurfaceVariant = Color(0xFF5A6380),
    outline = Color(0xFF8A93AD),
    outlineVariant = Color(0xFFD5DAE8),
    inverseSurface = Color(0xFF141A2E),
    inverseOnSurface = Color(0xFFE9ECF5),
    inversePrimary = Color(0xFF9DB4FF),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF2F55D4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F9FD),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEEF1F8),
    surfaceContainerHighest = Color(0xFFE6EAF4),
)

private val DarkColor = darkColorScheme(
    primary = Color(0xFF9DB4FF),
    onPrimary = Color(0xFF0E1220),
    primaryContainer = Color(0xFF1A2550),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFFF5B530),
    onSecondary = Color(0xFF1B1608),
    secondaryContainer = Color(0xFF3A2C08),
    onSecondaryContainer = Color(0xFFFFE6A8),
    tertiary = Color(0xFF6EE7A0),
    onTertiary = Color(0xFF0B2E19),
    tertiaryContainer = Color(0xFF13301F),
    onTertiaryContainer = Color(0xFFB9F5D0),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1220),
    onBackground = Color(0xFFE9ECF5),
    surface = Color(0xFF0E1220),
    onSurface = Color(0xFFE9ECF5),
    surfaceVariant = Color(0xFF232A42),
    onSurfaceVariant = Color(0xFFAEB6CA),
    outline = Color(0xFF5A6380),
    outlineVariant = Color(0xFF262D45),
    inverseSurface = Color(0xFFE9ECF5),
    inverseOnSurface = Color(0xFF141A2E),
    inversePrimary = Color(0xFF2F55D4),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF9DB4FF),
    surfaceContainerLowest = Color(0xFF0A0D18),
    surfaceContainerLow = Color(0xFF121729),
    surfaceContainer = Color(0xFF161B2D),
    surfaceContainerHigh = Color(0xFF1D2338),
    surfaceContainerHighest = Color(0xFF262D45),
)

// Cassanova typography: Plus Jakarta Sans for every Material text style.
val CassanovaFont = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

private val CassanovaTypography: Typography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = CassanovaFont),
        displayMedium = base.displayMedium.copy(fontFamily = CassanovaFont),
        displaySmall = base.displaySmall.copy(fontFamily = CassanovaFont),
        headlineLarge = base.headlineLarge.copy(fontFamily = CassanovaFont, fontWeight = FontWeight.ExtraBold),
        headlineMedium = base.headlineMedium.copy(fontFamily = CassanovaFont, fontWeight = FontWeight.ExtraBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = CassanovaFont, fontWeight = FontWeight.ExtraBold),
        titleLarge = base.titleLarge.copy(fontFamily = CassanovaFont, fontWeight = FontWeight.ExtraBold),
        titleMedium = base.titleMedium.copy(fontFamily = CassanovaFont, fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.copy(fontFamily = CassanovaFont, fontWeight = FontWeight.Bold),
        bodyLarge = base.bodyLarge.copy(fontFamily = CassanovaFont),
        bodyMedium = base.bodyMedium.copy(fontFamily = CassanovaFont),
        bodySmall = base.bodySmall.copy(fontFamily = CassanovaFont),
        labelLarge = base.labelLarge.copy(fontFamily = CassanovaFont, fontWeight = FontWeight.Bold),
        labelMedium = base.labelMedium.copy(fontFamily = CassanovaFont, fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontFamily = CassanovaFont, fontWeight = FontWeight.SemiBold),
    )
}

// Cassanova brand constants shared by the home, config and settings screens.
val BrandBlue = Color(0xFF2F55D4)
val BrandBlueDeep = Color(0xFF1E3FB0)
val BrandBlueMid = Color(0xFF2448BF)
val BrandNavy = Color(0xFF14204F)
val BrandGold = Color(0xFFF5B530)
val BrandGoldDeep = Color(0xFFE09A12)
val OnBrandGold = Color(0xFF1B1608)

// Semantic Colors
val colorPing = Color(0xFF009966) // Green
val colorPingRed = Color(0xFFFF0099) // Pink Red
val colorConfigType = Color(0xFF7C9BFF) // Cassanova blue
val colorFabActive = Color(0xFFF5B530) // Cassanova gold
val colorFabInactiveLight = Color(0xFF9C9C9C) // Gray
val colorFabInactiveDark = Color(0xFF646464) // Dark Gray
val dividerColorLight = Color(0xFFE0E0E0) // Light Gray
val dividerColorDark = Color(0xFF424242) // Dark Gray

// Toast Colors 85%
val toastNormalBgLight = Color(0xD9353A3E) // Dark Gray
val toastNormalBgDark = Color(0xD94A4F54) // Darker Gray
val toastSuccessBg = Color(0xD9388E3C) // Green
val toastErrorBg = Color(0xD9D50000) // Red
val toastInfoBg = Color(0xD93F51B5) // Indigo Blue
val toastIconCircleBg = Color(0x33FFFFFF) // Semi-transparent White
val toastTextColor = Color.White // White

/** Style A is the Cassanova look; B and C are only used while the experimental UI is unlocked. */
fun effectiveUiStyle(stored: String?, experimental: Boolean): String =
    if (experimental && (stored == ThemeManager.STYLE_B || stored == ThemeManager.STYLE_C)) stored ?: ThemeManager.STYLE_A else ThemeManager.STYLE_A

object ThemeManager {
    const val STYLE_A = "a"
    const val STYLE_B = "b"
    const val STYLE_C = "c"

    private fun experimentalStored(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_EXPERIMENTAL_UI, false)

    private fun storedStyle(): String = effectiveUiStyle(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_STYLE, STYLE_A),
        experimentalStored()
    )

    private val _experimentalUi = MutableStateFlow(experimentalStored())
    val experimentalUi: StateFlow<Boolean> = _experimentalUi.asStateFlow()

    /** Turns the experimental styles on or off; off goes back to style A. Returns the new state. */
    fun toggleExperimentalUi(): Boolean {
        val enabled = !_experimentalUi.value
        MmkvManager.encodeSettings(AppConfig.PREF_EXPERIMENTAL_UI, enabled)
        _experimentalUi.value = enabled
        _uiStyle.value = storedStyle()
        return enabled
    }

    /** A and B are dark designs, C is light: the default night mode follows the style. */
    fun defaultModeFor(style: String): String = if (style == STYLE_C) "1" else "2"

    private fun storedMode(): String =
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT) ?: defaultModeFor(storedStyle())

    private val _uiStyle = MutableStateFlow(storedStyle())
    val uiStyle: StateFlow<String> = _uiStyle.asStateFlow()

    /** Switches the Cassanova home style and the matching light/dark mode. */
    fun setUiStyle(style: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_STYLE, style)
        _uiStyle.value = style
        setThemeMode(defaultModeFor(style))
    }

    private val _themeMode = MutableStateFlow(storedMode())
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
    )
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)
        _dynamicColorEnabled.value = enabled
    }

    fun refresh() {
        _experimentalUi.value = experimentalStored()
        _uiStyle.value = storedStyle()
        _themeMode.value = storedMode()
        _dynamicColorEnabled.value =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
    }
}

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsState()
    return when (mode) {
        "1" -> false
        "2" -> true
        else -> isSystemInDarkTheme()
    }
}

val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
    val dynamicColor by ThemeManager.dynamicColorEnabled.collectAsState()
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColor
        else -> LightColor
    }
    val snackbarController = rememberAppSnackbarController()

    val view = LocalView.current
    // Style C puts a blue header under the status bar on every screen, so its icons stay light.
    val uiStyleForBars by ThemeManager.uiStyle.collectAsState()
    val lightBarIcons = darkTheme || uiStyleForBars == ThemeManager.STYLE_C
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !lightBarIcons
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppSnackbar provides snackbarController
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CassanovaTypography
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
