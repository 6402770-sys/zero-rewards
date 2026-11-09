package com.zero.rewards.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class AccentPreset(val name: String, val seed: Int)

val AccentPresets = listOf(
    AccentPreset("Champagne", 0xFFE6C27A.toInt()),
    AccentPreset("Emerald", 0xFF5FD3A0.toInt()),
    AccentPreset("Sky", 0xFF7CC4FF.toInt()),
    AccentPreset("Indigo", 0xFF9AA5FF.toInt()),
    AccentPreset("Violet", 0xFFC39BFF.toInt()),
    AccentPreset("Rose", 0xFFFF9CC0.toInt()),
    AccentPreset("Coral", 0xFFFF9A7B.toInt()),
    AccentPreset("Platinum", 0xFFD5D9E0.toInt()),
)

/** The user's look-and-feel choices. Purely visual: the automation never reads this. */
class AppearanceStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    private val _accentSeed = MutableStateFlow(prefs.getInt(KEY_ACCENT, AccentPresets.first().seed))
    val accentSeed: StateFlow<Int> = _accentSeed.asStateFlow()

    private val _themeMode = MutableStateFlow(
        ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_MODE, null) } ?: ThemeMode.SYSTEM,
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    // Continuous changes (dragging a slider) skip the crossfade, discrete ones (a preset) get it.
    @Volatile private var animateAccent = true

    fun consumeAccentAnimation(): Boolean = animateAccent.also { animateAccent = true }

    fun setAccent(seed: Int, animate: Boolean = true) {
        animateAccent = animate
        _accentSeed.value = seed
        prefs.edit().putInt(KEY_ACCENT, seed).apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    fun isDark(context: Context): Boolean = when (_themeMode.value) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM ->
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    companion object {
        private const val KEY_ACCENT = "accent"
        private const val KEY_MODE = "mode"

        @Volatile private var instance: AppearanceStore? = null

        fun get(context: Context): AppearanceStore =
            instance ?: synchronized(this) {
                instance ?: AppearanceStore(context.applicationContext).also { instance = it }
            }
    }
}

data class AccentColors(
    val primary: Int,
    val onPrimary: Int,
    val container: Int,
    val onContainer: Int,
)

private const val DARK_SURFACE = 0xFF14161A.toInt()
private const val LIGHT_SURFACE = 0xFFFFFFFF.toInt()
private const val INK = 0xFF14110A.toInt()
private const val PAPER = 0xFFFFFFFF.toInt()

/**
 * Turns any color the user picks into one that stays readable: the lightness is
 * pushed until the accent reaches a 4.5:1 contrast against the theme's surface.
 */
fun accentColors(seed: Int, dark: Boolean): AccentColors {
    val surface = if (dark) DARK_SURFACE else LIGHT_SURFACE
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed or 0xFF000000.toInt(), hsl)
    var primary = ColorUtils.HSLToColor(hsl)
    while (ColorUtils.calculateContrast(primary, surface) < 4.5 && hsl[2] in 0.01f..0.99f) {
        hsl[2] = (hsl[2] + if (dark) 0.02f else -0.02f).coerceIn(0f, 1f)
        primary = ColorUtils.HSLToColor(hsl)
    }
    val onPrimary = if (ColorUtils.calculateContrast(INK, primary) >= ColorUtils.calculateContrast(PAPER, primary)) {
        INK
    } else {
        PAPER
    }
    return AccentColors(
        primary = primary,
        onPrimary = onPrimary,
        container = ColorUtils.blendARGB(surface, primary, 0.22f),
        onContainer = ColorUtils.blendARGB(primary, if (dark) PAPER else INK, 0.7f),
    )
}
