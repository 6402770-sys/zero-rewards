package com.zero.rewards.ui

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.zero.rewards.service.FarmerState
import com.zero.rewards.ui.theme.AccentPresets
import com.zero.rewards.ui.theme.AppearanceStore
import com.zero.rewards.ui.theme.ThemeMode
import com.zero.rewards.ui.theme.ThemeReveal
import com.zero.rewards.ui.theme.ZeroTheme
import com.zero.rewards.ui.theme.accentColors

private const val GRID_COLUMNS = 4

@Composable
fun AppearanceScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AppearanceStore.get(context) }
    val seed by store.accentSeed.collectAsState()
    val mode by store.themeMode.collectAsState()
    val running by FarmerState.runningAccountId.collectAsState()
    var currentIcon by remember { mutableStateOf(AppIcons.current(context)) }
    val onPickIcon: (AppIcon) -> Unit = { icon ->
        if (icon != currentIcon) {
            AppIcons.select(context, icon)
            currentIcon = icon
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ZeroTopBar(title = "Appearance", onBack = onDone) },
    ) { padding ->
        StaggeredColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            spacing = 24.dp,
        ) {
            FormSection("Theme", contentPadding = 6.dp) {
                ThemeModeSelector(mode, store::setThemeMode)
            }

            FormSection("Accent color") {
                AccentPresetGrid(seed) { store.setAccent(it) }
            }

            FormSection("Custom color") {
                CustomColorPicker(seed) { store.setAccent(it, animate = false) }
            }

            IconSection("App icon", AppIcons.Colors, currentIcon, running == null, onPickIcon)
            IconSection("Special icons", AppIcons.Specials, currentIcon, running == null, onPickIcon)

            AnimatedContent(
                targetState = running == null,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(150)) },
                label = "icon-note",
            ) { idle ->
                Text(
                    if (idle) {
                        "The new icon appears when you leave the app. " +
                            "Some launchers also remove the home screen shortcut, so you may need to add it again."
                    } else {
                        "You can change the icon when the current run finishes."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = ZeroTheme.extras.muted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/** Three segments with an indicator that slides to the chosen one. */
@Composable
private fun ThemeModeSelector(mode: ThemeMode, onPick: (ThemeMode) -> Unit) {
    val options = listOf(
        Triple(ThemeMode.SYSTEM, "System", Icons.Outlined.PhoneAndroid),
        Triple(ThemeMode.DARK, "Dark", Icons.Outlined.DarkMode),
        Triple(ThemeMode.LIGHT, "Light", Icons.Outlined.LightMode),
    )
    val gap = 6.dp
    BoxWithConstraints(Modifier.fillMaxWidth().height(46.dp)) {
        val segment = (maxWidth - gap * (options.size - 1)) / options.size
        val index = options.indexOfFirst { it.first == mode }.coerceAtLeast(0)
        val indicatorX by animateDpAsState(
            (segment + gap) * index,
            spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
            label = "theme-indicator",
        )
        Box(
            Modifier
                .offset { IntOffset(indicatorX.roundToPx(), 0) }
                .width(segment)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxSize()) {
            options.forEach { (value, label, icon) ->
                ThemeOption(label, icon, value == mode, Modifier.weight(1f)) { onPick(value) }
            }
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val content = lerp(ZeroTheme.extras.muted, MaterialTheme.colorScheme.onPrimary, selectionFraction(selected, "theme-option"))
    val source = remember { MutableInteractionSource() }
    val center = remember { floatArrayOf(Float.NaN, Float.NaN) }
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .onGloballyPositioned {
                val c = it.boundsInRoot().center
                center[0] = c.x
                center[1] = c.y
            }
            .fillMaxHeight()
            .pressScale(source)
            .clip(MaterialTheme.shapes.small)
            .selectable(
                selected = selected,
                interactionSource = source,
                indication = LocalIndication.current,
                role = Role.RadioButton,
                onClick = {
                    if (!center[0].isNaN()) ThemeReveal.origin = Offset(center[0], center[1])
                    onClick()
                },
            ),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

@Composable
private fun AccentPresetGrid(seed: Int, onPick: (Int) -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AccentPresets.chunked(GRID_COLUMNS).forEach { row ->
            Row {
                row.forEach { preset ->
                    val accent = accentColors(preset.seed, dark)
                    AccentSwatch(
                        name = preset.name,
                        color = Color(accent.primary),
                        onColor = Color(accent.onPrimary),
                        selected = preset.seed == seed,
                        modifier = Modifier.weight(1f),
                    ) { onPick(preset.seed) }
                }
            }
        }
    }
}

@Composable
private fun AccentSwatch(
    name: String,
    color: Color,
    onColor: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val on = selectionFraction(selected, "swatch")
    val ring = color.copy(alpha = on)
    val inset by animateDpAsState(if (selected) 5.dp else 0.dp, ZeroMotion.bouncy(), label = "swatch-inset")
    val checkScale by animateFloatAsState(if (selected) 1f else 0f, ZeroMotion.bouncy(), label = "swatch-check")
    val labelColor = lerp(ZeroTheme.extras.muted, MaterialTheme.colorScheme.onSurface, on)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .selectable(
                selected = selected,
                interactionSource = source,
                indication = LocalIndication.current,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .pressScale(source, 0.9f)
                .size(46.dp)
                .border(2.dp, ring, CircleShape)
                .padding(inset)
                .background(color, CircleShape),
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = onColor,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        val s = checkScale.coerceAtLeast(0f)
                        scaleX = s
                        scaleY = s
                        alpha = s.coerceAtMost(1f)
                    },
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(name, style = MaterialTheme.typography.labelMedium, color = labelColor, maxLines = 1)
    }
}

private fun toHex(color: Int): String = "%06X".format(color and 0xFFFFFF)

private fun parseHex(text: String): Int? =
    if (text.length == 6) text.toIntOrNull(16)?.let { it or 0xFF000000.toInt() } else null

@Composable
private fun CustomColorPicker(seed: Int, onPick: (Int) -> Unit) {
    val hsv = remember(seed) { FloatArray(3).also { AndroidColor.colorToHSV(seed, it) } }
    // Hue is undefined for greys, so remember the last one instead of snapping back to red.
    var lastHue by remember { mutableFloatStateOf(hsv[0]) }
    if (hsv[1] > 0.01f) lastHue = hsv[0]
    val brightness = hsv[2].coerceAtLeast(0.4f)

    var hex by remember { mutableStateOf(toHex(seed)) }
    LaunchedEffect(seed) {
        if (parseHex(hex) != seed) hex = toHex(seed)
    }

    ColorSlider(
        label = "Hue",
        value = lastHue / 360f,
        brush = Brush.horizontalGradient(
            listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map {
                Color(AndroidColor.HSVToColor(floatArrayOf(it, 0.8f, 1f)))
            },
        ),
        thumb = Color(AndroidColor.HSVToColor(floatArrayOf(lastHue, 0.8f, 1f))),
        onChange = { h ->
            lastHue = h * 360f
            onPick(AndroidColor.HSVToColor(floatArrayOf(lastHue, hsv[1].coerceAtLeast(0.05f), brightness)))
        },
    )
    ColorSlider(
        label = "Saturation",
        value = hsv[1],
        brush = Brush.horizontalGradient(
            listOf(
                Color(AndroidColor.HSVToColor(floatArrayOf(lastHue, 0f, 1f))),
                Color(AndroidColor.HSVToColor(floatArrayOf(lastHue, 1f, 1f))),
            ),
        ),
        thumb = Color(seed),
        onChange = { s -> onPick(AndroidColor.HSVToColor(floatArrayOf(lastHue, s, brightness))) },
    )
    ZeroTextField(
        value = hex,
        onValueChange = { v ->
            hex = v.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
            parseHex(hex)?.let(onPick)
        },
        label = "Hex (RRGGBB)",
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            keyboardType = KeyboardType.Ascii,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
    )
}

@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    brush: Brush,
    thumb: Color,
    onChange: (Float) -> Unit,
) {
    val update by rememberUpdatedState(onChange)
    val outline = MaterialTheme.colorScheme.onSurface
    var dragging by remember { mutableStateOf(false) }
    // The thumb grows while held, so it's clear it's being dragged.
    val thumbScale by animateFloatAsState(if (dragging) 1.18f else 1f, ZeroMotion.bouncy(), label = "thumb-scale")
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = ZeroTheme.extras.muted)
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .semantics {
                    contentDescription = label
                    progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                    setProgress { update(it.coerceIn(0f, 1f)); true }
                }
                .pointerInput(Unit) {
                    val inset = 16.dp.toPx()
                    fun fraction(x: Float) = ((x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f)
                    detectTapGestures(
                        onPress = {
                            dragging = true
                            tryAwaitRelease()
                            dragging = false
                        },
                        onTap = { update(fraction(it.x)) },
                    )
                }
                .pointerInput(Unit) {
                    val inset = 16.dp.toPx()
                    fun fraction(x: Float) = ((x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f)
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, _ ->
                        change.consume()
                        update(fraction(change.position.x))
                    }
                },
        ) {
            val trackHeight = 12.dp.toPx()
            val inset = 16.dp.toPx()
            drawRoundRect(
                brush = brush,
                topLeft = Offset(0f, (size.height - trackHeight) / 2),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2),
            )
            val center = Offset(inset + value.coerceIn(0f, 1f) * (size.width - 2 * inset), size.height / 2)
            val radius = 12.dp.toPx() * thumbScale
            drawCircle(thumb, radius = radius, center = center)
            drawCircle(outline, radius = radius, center = center, style = Stroke(2.5.dp.toPx()))
        }
    }
}

@Composable
private fun IconSection(
    title: String,
    icons: List<AppIcon>,
    current: AppIcon,
    enabled: Boolean,
    onPick: (AppIcon) -> Unit,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.5f, ZeroMotion.gentle(), label = "icons-alpha")
    FormSection(title) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.alpha(alpha),
        ) {
            icons.chunked(GRID_COLUMNS).forEach { row ->
                Row {
                    row.forEach { icon ->
                        IconTile(icon, icon == current, enabled, Modifier.weight(1f)) { onPick(icon) }
                    }
                }
            }
        }
    }
}

/**
 * Draws an adaptive icon's two layers the way a launcher does: both are 108dp
 * layers of which only the central 72dp is visible, so they are scaled by 1.5 and clipped.
 */
@Composable
private fun IconTile(
    icon: AppIcon,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val source = remember { MutableInteractionSource() }
    val on = selectionFraction(selected, "icon")
    val ring = MaterialTheme.colorScheme.primary.copy(alpha = on)
    val labelColor = lerp(ZeroTheme.extras.muted, MaterialTheme.colorScheme.onSurface, on)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .selectable(
                selected = selected,
                enabled = enabled,
                interactionSource = source,
                indication = LocalIndication.current,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .pressScale(source, 0.9f)
                .size(66.dp)
                .border(2.dp, ring, RoundedCornerShape(20.dp))
                .padding(5.dp)
                .border(1.dp, ZeroTheme.extras.hairlineStrong, shape)
                .clip(shape),
        ) {
            Image(painterResource(icon.background), null, Modifier.requiredSize(84.dp))
            Image(painterResource(icon.foreground), null, Modifier.requiredSize(84.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            icon.label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
