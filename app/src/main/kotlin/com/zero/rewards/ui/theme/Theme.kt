package com.zero.rewards.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize

/** Colors Material's scheme has no slot for. */
@Immutable
data class ZeroExtras(
    val hairline: Color,
    val hairlineStrong: Color,
    val muted: Color,
    val faint: Color,
    val accentSoft: Color,
    val accentGlow: Color,
    val positive: Color,
    val positiveSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
)

private val DarkExtras = ZeroExtras(
    hairline = Color(0xFF23262C),
    hairlineStrong = Color(0xFF353942),
    muted = Color(0xFF9BA1AB),
    faint = Color(0xFF6C727C),
    accentSoft = Color(0x1FE6C27A),
    accentGlow = Color(0x2EE6C27A),
    positive = Color(0xFF6FD6A3),
    positiveSoft = Color(0x1F6FD6A3),
    warning = Color(0xFFF0B866),
    warningSoft = Color(0x1FF0B866),
    danger = Color(0xFFFF8A80),
    dangerSoft = Color(0x24FF8A80),
)

private val LightExtras = ZeroExtras(
    hairline = Color(0xFFE6E3DC),
    hairlineStrong = Color(0xFFCFCBC2),
    muted = Color(0xFF5F636B),
    faint = Color(0xFF8A8D94),
    accentSoft = Color(0x1F8A6414),
    accentGlow = Color(0x298A6414),
    positive = Color(0xFF17794B),
    positiveSoft = Color(0x1F17794B),
    warning = Color(0xFF9A5F00),
    warningSoft = Color(0x1F9A5F00),
    danger = Color(0xFFBA2B22),
    dangerSoft = Color(0x1FBA2B22),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFE6C27A),
    onPrimary = Color(0xFF2A1F08),
    primaryContainer = Color(0xFF3A2E14),
    onPrimaryContainer = Color(0xFFF6E3B8),
    secondary = Color(0xFFC9CDD4),
    onSecondary = Color(0xFF17191D),
    secondaryContainer = Color(0xFF24272D),
    onSecondaryContainer = Color(0xFFE9EBEE),
    tertiary = Color(0xFF6FD6A3),
    background = Color(0xFF0A0B0D),
    onBackground = Color(0xFFF1F2F4),
    surface = Color(0xFF0A0B0D),
    onSurface = Color(0xFFF1F2F4),
    surfaceVariant = Color(0xFF1B1E23),
    onSurfaceVariant = Color(0xFF9BA1AB),
    surfaceContainerLowest = Color(0xFF08090A),
    surfaceContainerLow = Color(0xFF111316),
    surfaceContainer = Color(0xFF14161A),
    surfaceContainerHigh = Color(0xFF1B1E23),
    surfaceContainerHighest = Color(0xFF25282E),
    outline = Color(0xFF353942),
    outlineVariant = Color(0xFF23262C),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3A0B07),
    errorContainer = Color(0xFF3A1714),
    onErrorContainer = Color(0xFFFFDAD5),
)

private val Light = lightColorScheme(
    primary = Color(0xFF8A6414),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF4E7C9),
    onPrimaryContainer = Color(0xFF2E2105),
    secondary = Color(0xFF4A4E56),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFECEAE4),
    onSecondaryContainer = Color(0xFF1B1D21),
    tertiary = Color(0xFF17794B),
    background = Color(0xFFF7F6F2),
    onBackground = Color(0xFF15171A),
    surface = Color(0xFFF7F6F2),
    onSurface = Color(0xFF15171A),
    surfaceVariant = Color(0xFFECEAE4),
    onSurfaceVariant = Color(0xFF5F636B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCFBF8),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF1EFEA),
    surfaceContainerHighest = Color(0xFFE6E3DC),
    outline = Color(0xFFCFCBC2),
    outlineVariant = Color(0xFFE6E3DC),
    error = Color(0xFFBA2B22),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE3E0),
    onErrorContainer = Color(0xFF410E0A),
)

private val Sans = FontFamily.SansSerif

private val ZeroTypography = Typography(
    displayMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 46.sp,
        lineHeight = 50.sp,
        letterSpacing = (-1.4).sp,
        fontFeatureSettings = "tnum",
    ),
    headlineSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.25).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Sans,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Sans,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.9.sp,
    ),
)

private val ZeroShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val LocalZeroExtras = staticCompositionLocalOf { DarkExtras }

object ZeroTheme {
    val extras: ZeroExtras
        @Composable @ReadOnlyComposable get() = LocalZeroExtras.current
}

/** Where the theme switch was tapped, so the new theme can spread out from that spot. */
object ThemeReveal {
    var origin: Offset? = null
}

private enum class ThemeChange { NONE, REVEAL, FADE }

private val RevealEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Theme changes are animated without animating any color: the old frame is captured
 * as a bitmap, the new theme is applied in a single recomposition, and the bitmap is
 * peeled away on top. The animation only redraws one image, so it stays smooth.
 */
@Composable
fun ZeroTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val store = remember { AppearanceStore.get(context) }
    val targetSeed by store.accentSeed.collectAsState()
    val mode by store.themeMode.collectAsState()
    val targetDark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    // What's on screen. It trails the target for the instant the old frame is captured.
    var appliedSeed by remember { mutableIntStateOf(targetSeed) }
    var appliedDark by remember { mutableStateOf(targetDark) }

    val layer = rememberGraphicsLayer()
    val progress = remember { Animatable(0f) }
    var overlay by remember { mutableStateOf<ImageBitmap?>(null) }
    var overlayChange by remember { mutableStateOf(ThemeChange.NONE) }
    var origin by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(targetSeed, targetDark) {
        val change = when {
            targetDark != appliedDark -> ThemeChange.REVEAL
            targetSeed != appliedSeed && store.consumeAccentAnimation() -> ThemeChange.FADE
            else -> ThemeChange.NONE
        }
        if (change == ThemeChange.NONE || layer.size == IntSize.Zero) {
            overlay = null
            appliedSeed = targetSeed
            appliedDark = targetDark
            return@LaunchedEffect
        }
        overlay = layer.toImageBitmap()
        overlayChange = change
        origin = ThemeReveal.origin
        ThemeReveal.origin = null
        progress.snapTo(0f)
        appliedSeed = targetSeed
        appliedDark = targetDark
        progress.animateTo(
            1f,
            if (change == ThemeChange.REVEAL) tween(650, easing = RevealEasing) else tween(280),
        )
        overlay = null
    }

    val scheme = remember(appliedSeed, appliedDark) {
        val accent = accentColors(appliedSeed, appliedDark)
        (if (appliedDark) Dark else Light).copy(
            primary = Color(accent.primary),
            onPrimary = Color(accent.onPrimary),
            primaryContainer = Color(accent.container),
            onPrimaryContainer = Color(accent.onContainer),
            surfaceTint = Color(accent.primary),
        )
    }
    val extras = remember(scheme, appliedDark) {
        (if (appliedDark) DarkExtras else LightExtras).copy(
            accentSoft = scheme.primary.copy(alpha = 0.12f),
            accentGlow = scheme.primary.copy(alpha = 0.18f),
        )
    }

    // The app's theme can differ from the system's, so the bar icons have to follow the app.
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).run {
            isAppearanceLightStatusBars = !appliedDark
            isAppearanceLightNavigationBars = !appliedDark
        }
    }

    CompositionLocalProvider(LocalZeroExtras provides extras) {
        MaterialTheme(
            colorScheme = scheme,
            typography = ZeroTypography,
            shapes = ZeroShapes,
        ) {
            Box(Modifier.fillMaxSize()) {
                // Always keep the latest frame, so there is something to capture. This layer
                // is only re-recorded when the app itself redraws, never by the reveal.
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        },
                ) {
                    content()
                }
                // The reveal lives in its own layer: each frame redraws one bitmap and nothing else.
                Spacer(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer()
                        .drawBehind {
                        val image = overlay ?: return@drawBehind
                        val t = progress.value
                        if (overlayChange == ThemeChange.REVEAL) {
                            val center = origin ?: Offset(size.width / 2f, 0f)
                            val reach = maxOf(
                                (center - Offset(0f, 0f)).getDistance(),
                                (center - Offset(size.width, 0f)).getDistance(),
                                (center - Offset(0f, size.height)).getDistance(),
                                (center - Offset(size.width, size.height)).getDistance(),
                            )
                            val hole = Path().apply { addOval(Rect(center, reach * t)) }
                            clipPath(hole, ClipOp.Difference) { drawImage(image) }
                        } else {
                            drawImage(image, alpha = 1f - t)
                        }
                    },
                )
            }
        }
    }
}
