package com.zero.rewards.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zero.rewards.ui.theme.ZeroTheme
import kotlinx.coroutines.delay

/** The app's motion vocabulary, so every animation moves with the same feel. */
object ZeroMotion {
    /** For things that move around on screen. */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** For things arriving: fast start, long soft landing. */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** For things leaving. */
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Settles without overshoot. Used for color and layout changes. */
    fun <T> gentle(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)

    /** A little overshoot, for direct feedback to a touch. */
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)
}

/**
 * Goes from 0 to 1 when [selected] turns on. Colors mixed with it animate only when the
 * selection changes: a theme change moves both ends at once, so they follow it instantly
 * instead of starting dozens of color animations under the theme reveal.
 */
@Composable
fun selectionFraction(selected: Boolean, label: String = "selection"): Float =
    animateFloatAsState(
        if (selected) 1f else 0f,
        spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
        label = label,
    ).value.coerceIn(0f, 1f) // used as an alpha, which must stay in 0..1

private const val ENTRANCE_MS = 520
private const val STAGGER_STEP_MS = 55
private const val STAGGER_MAX_STEPS = 10

/** Shrinks a little while pressed and springs back on release. */
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = 0.96f): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        ZeroMotion.bouncy(),
        label = "press-scale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Fades and lifts an item into place, [index] steps after its siblings.
 * With a [memory], each key plays once: scrolling or coming back doesn't replay it.
 */
fun Modifier.entrance(key: Any, index: Int, memory: MutableSet<Any>? = null): Modifier = composed {
    val play = remember { memory == null || key !in memory }
    val progress = remember { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (play) {
            memory?.add(key)
            delay(STAGGER_STEP_MS.toLong() * index.coerceAtMost(STAGGER_MAX_STEPS))
            progress.animateTo(1f, tween(ENTRANCE_MS, easing = ZeroMotion.EmphasizedDecelerate))
        }
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 20.dp.toPx()
    }
}

/**
 * A column whose children arrive one after the other. The animation runs in the
 * layer of each child, so it never recomposes or re-measures the content.
 */
@Composable
fun StaggeredColumn(
    modifier: Modifier = Modifier,
    spacing: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val total = ENTRANCE_MS + STAGGER_STEP_MS * STAGGER_MAX_STEPS
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        clock.animateTo(total.toFloat(), tween(total, easing = LinearEasing))
    }
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val gap = spacing.roundToPx()
        val contentHeight = placeables.sumOf { it.height } + gap * (placeables.size - 1).coerceAtLeast(0)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeables.maxOfOrNull { it.width } ?: 0
        val distance = 20.dp.toPx()
        layout(width, contentHeight.coerceAtLeast(constraints.minHeight)) {
            var y = 0
            placeables.forEachIndexed { index, placeable ->
                val start = (STAGGER_STEP_MS * index.coerceAtMost(STAGGER_MAX_STEPS)).toFloat()
                placeable.placeWithLayer(0, y) {
                    val linear = ((clock.value - start) / ENTRANCE_MS).coerceIn(0f, 1f)
                    val eased = ZeroMotion.EmphasizedDecelerate.transform(linear)
                    alpha = eased
                    translationY = (1f - eased) * distance
                }
                y += placeable.height + gap
            }
        }
    }
}

/** The base surface of the app: flat, with a hairline edge instead of a shadow. */
@Composable
fun ZeroCard(
    modifier: Modifier = Modifier,
    borderColor: Color = ZeroTheme.extras.hairline,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(content = content)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = ZeroTheme.extras.muted,
        modifier = modifier,
    )
}

/** A titled group of form content. */
@Composable
fun FormSection(
    title: String,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier) {
        SectionLabel(title, Modifier.padding(start = 4.dp, bottom = 10.dp))
        ZeroCard {
            Column(
                modifier = Modifier.padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZeroTopBar(title: String, subtitle: String? = null, onBack: () -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = ZeroTheme.extras.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

@Composable
fun ZeroTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    suffix: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        shape = MaterialTheme.shapes.medium,
        colors = zeroFieldColors(),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun zeroFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    unfocusedBorderColor = ZeroTheme.extras.hairlineStrong,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = ZeroTheme.extras.muted,
    focusedSupportingTextColor = ZeroTheme.extras.muted,
    unfocusedSupportingTextColor = ZeroTheme.extras.faint,
    cursorColor = MaterialTheme.colorScheme.primary,
)

@Composable
fun zeroSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = ZeroTheme.extras.muted,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    uncheckedBorderColor = Color.Transparent,
)

@Composable
fun PrimaryButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = ZeroTheme.extras.faint,
        ),
        modifier = modifier.pressScale(source, 0.97f).fillMaxWidth().height(54.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** Keeps the screen's main action reachable, above the keyboard and the nav bar. */
@Composable
fun BottomActionBar(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.navigationBarsPadding().imePadding()) {
            HorizontalDivider(color = ZeroTheme.extras.hairline)
            Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { content() }
        }
    }
}

/** A setting row whose whole width toggles the switch. */
@Composable
fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                interactionSource = source,
                indication = LocalIndication.current,
                role = Role.Switch,
                onValueChange = onChange,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        IconTile(icon, active = checked, modifier = Modifier.pressScale(source, 0.88f))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = ZeroTheme.extras.muted,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, colors = zeroSwitchColors())
    }
}

@Composable
fun IconTile(icon: ImageVector, active: Boolean = true, modifier: Modifier = Modifier) {
    val extras = ZeroTheme.extras
    val on = selectionFraction(active, "tile")
    val background = lerp(MaterialTheme.colorScheme.surfaceContainerHigh, extras.accentSoft, on)
    val tint = lerp(extras.faint, MaterialTheme.colorScheme.primary, on)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(38.dp)
            .background(background, MaterialTheme.shapes.small),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** An account's initial. Fills with the accent while that account is running. */
@Composable
fun Monogram(label: String, active: Boolean, highlighted: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val extras = ZeroTheme.extras
    val a = selectionFraction(active, "monogram-active")
    val h = selectionFraction(highlighted, "monogram-highlight")
    val fill = lerp(lerp(scheme.surfaceContainerHigh, extras.accentSoft, a), scheme.primary, h)
    val edge = lerp(lerp(extras.hairlineStrong, scheme.primary.copy(alpha = 0.35f), a), scheme.primary, h)
    val ink = lerp(lerp(extras.muted, scheme.primary, a), scheme.onPrimary, h)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(46.dp)
            .background(fill, CircleShape)
            .border(1.dp, edge, CircleShape),
    ) {
        Text(
            label.trim().take(1).uppercase().ifEmpty { "?" },
            style = MaterialTheme.typography.titleMedium,
            color = ink,
        )
    }
}

/** The brand mark, the same Z as the launcher icon. */
@Composable
fun ZeroMark(size: Dp = 34.dp, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size).background(scheme.primary, MaterialTheme.shapes.small),
    ) {
        Canvas(Modifier.size(size * 0.5f)) {
            val w = this.size.width
            val h = this.size.height
            drawPath(
                Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w, 0f)
                    lineTo(0f, h)
                    lineTo(w, h)
                },
                color = scheme.onPrimary,
                style = Stroke(width = w * 0.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/** A small status dot. With [pulse], a ring radiates out of it continuously. */
@Composable
fun StatusDot(color: Color, pulse: Boolean = false, modifier: Modifier = Modifier) {
    val progress = if (pulse) {
        val transition = rememberInfiniteTransition(label = "dot")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(1400, easing = LinearEasing),
                RepeatMode.Restart,
            ),
            label = "dot-progress",
        )
        value
    } else {
        0f
    }
    Canvas(modifier.size(14.dp)) {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val core = 3.5.dp.toPx()
        if (pulse) {
            drawCircle(
                color = color.copy(alpha = (1f - progress) * 0.5f),
                radius = core + (this.size.width / 2 - core) * progress,
                center = c,
            )
        }
        drawCircle(color = color, radius = core, center = c)
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color,
    background: Color,
    pulse: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(background, CircleShape)
            .padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
    ) {
        StatusDot(color, pulse)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** A value with its small caption above it. */
@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    val alpha by animateFloatAsState(if (dimmed) 0.6f else 1f, ZeroMotion.gentle(), label = "metric-alpha")
    Column(modifier.alpha(alpha)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = ZeroTheme.extras.muted,
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
