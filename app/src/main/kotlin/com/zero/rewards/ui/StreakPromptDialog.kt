package com.zero.rewards.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zero.rewards.AppContainer
import com.zero.rewards.core.account.Account
import com.zero.rewards.notifications.StreakPromptBus
import com.zero.rewards.service.FarmerForegroundService
import com.zero.rewards.service.ZeroAccessibilityService
import com.zero.rewards.ui.theme.ZeroTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Observes [StreakPromptBus.requestVersion] and surfaces the Sí/No
 * dialog when it ticks. Yes opens an account-picker dialog with a
 * "select all" row; confirm starts a batch farm run via
 * [FarmerForegroundService.startBatch]. No dismisses silently.
 */
@Composable
fun StreakPromptHost(container: AppContainer) {
    val context = LocalContext.current
    val version by StreakPromptBus.requestVersion.collectAsState()
    var step by remember { mutableStateOf<Step>(Step.Idle) }
    var lastSeen by remember { mutableStateOf(0) }

    LaunchedEffect(version) {
        if (version > 0 && version != lastSeen) {
            lastSeen = version
            step = Step.Confirm
        }
    }

    when (val s = step) {
        Step.Idle -> Unit
        Step.Confirm -> StreakConfirmDialog(
            onNo = { step = Step.Idle },
            onYes = {
                // Load accounts before opening picker so empty-state is accurate.
                step = Step.Loading
            },
        )
        Step.Loading -> LoadingSinkEffect(container) { accounts ->
            step = if (accounts.isEmpty()) {
                Toast.makeText(context, "No hay cuentas configuradas.", Toast.LENGTH_LONG).show()
                Step.Idle
            } else {
                Step.Pick(accounts)
            }
        }
        is Step.Pick -> AccountPickerDialog(
            accounts = s.accounts,
            onCancel = { step = Step.Idle },
            onConfirm = { ids ->
                if (ZeroAccessibilityService.get() == null) {
                    Toast.makeText(context, "Enable Accessibility first.", Toast.LENGTH_LONG).show()
                } else {
                    FarmerForegroundService.startBatch(context, ids)
                    val msg = if (ids.size == 1) "Running 1 account."
                    else "Queued ${ids.size} accounts."
                    Toast.makeText(context, "$msg Don't touch the phone.", Toast.LENGTH_LONG).show()
                }
                step = Step.Idle
            },
        )
    }
}

private sealed class Step {
    object Idle : Step()
    object Confirm : Step()
    object Loading : Step()
    data class Pick(val accounts: List<Account>) : Step()
}

@Composable
private fun LoadingSinkEffect(
    container: AppContainer,
    onLoaded: (List<Account>) -> Unit,
) {
    LaunchedEffect(Unit) {
        val list = container.accountStore.observe().first()
        onLoaded(list)
    }
}

/** Asks whether to farm now, in the same bottom-sheet style as the account picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StreakConfirmDialog(onNo: () -> Unit, onYes: () -> Unit) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Lets the sheet slide away before the next step replaces it.
    fun closeThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }

    ModalBottomSheet(
        onDismissRequest = onNo,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { SheetHandle() },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 8.dp),
        ) {
            RingingBell()
            Spacer(Modifier.height(18.dp))
            Text(
                "Automatizar Microsoft Rewards",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "¿Querés automatizar Microsoft Rewards ahora? Vas a poder elegir qué cuentas farmear antes de que arranque.",
                style = MaterialTheme.typography.bodyMedium,
                color = ZeroTheme.extras.muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            PrimaryButton(text = "Sí", onClick = { closeThen(onYes) })
            TextButton(
                onClick = { closeThen(onNo) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text("No", color = ZeroTheme.extras.muted, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** The bell swings a couple of times when the sheet opens, like it's ringing, then settles. */
@Composable
private fun RingingBell() {
    val swing = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(280)
        repeat(2) {
            swing.animateTo(16f, tween(110, easing = FastOutSlowInEasing))
            swing.animateTo(-16f, tween(220, easing = FastOutSlowInEasing))
        }
        swing.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow))
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(64.dp)
            .background(ZeroTheme.extras.accentSoft, CircleShape),
    ) {
        Icon(
            Icons.Outlined.NotificationsActive,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(30.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0.1f)
                    rotationZ = swing.value
                },
        )
    }
}

@Composable
private fun SheetHandle() {
    Box(
        Modifier
            .padding(top = 12.dp, bottom = 8.dp)
            .size(width = 36.dp, height = 4.dp)
            .background(ZeroTheme.extras.hairlineStrong, CircleShape),
    )
}

/**
 * Bottom sheet to choose which accounts to farm. Each account is a card that lights
 * up when picked; the chip in the header picks or clears all of them at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountPickerDialog(
    accounts: List<Account>,
    onCancel: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    val extras = ZeroTheme.extras
    val selected = remember(accounts) {
        mutableStateMapOf<String, Boolean>().also { m ->
            for (a in accounts) m[a.id] = true
        }
    }
    val allSelected = selected.values.all { it } && accounts.isNotEmpty()
    val noneSelected = selected.values.none { it }
    val count = accounts.count { selected[it.id] == true }

    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Lets the sheet slide away before the host removes it.
    fun closeThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }

    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { SheetHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Elegir cuentas", style = MaterialTheme.typography.titleLarge)
                    AnimatedContent(
                        targetState = count,
                        transitionSpec = {
                            val up = targetState > initialState
                            (fadeIn(tween(200)) + slideInVertically(ZeroMotion.gentle()) { if (up) it else -it })
                                .togetherWith(fadeOut(tween(120)) + slideOutVertically(ZeroMotion.gentle()) { if (up) -it else it })
                        },
                        label = "picked-count",
                    ) { n ->
                        Text(
                            "$n de ${accounts.size} seleccionadas",
                            style = MaterialTheme.typography.bodySmall,
                            color = extras.muted,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                SelectAllChip(allSelected) {
                    val pick = !allSelected
                    for (a in accounts) selected[a.id] = pick
                }
            }

            Spacer(Modifier.height(18.dp))
            StaggeredColumn(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                spacing = 10.dp,
            ) {
                for (a in accounts) {
                    AccountOption(
                        account = a,
                        checked = selected[a.id] == true,
                        onToggle = { pick -> selected[a.id] = pick },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = if (noneSelected) {
                    "Elegí al menos una cuenta"
                } else {
                    "Farm · $count ${if (count == 1) "cuenta" else "cuentas"}"
                },
                enabled = !noneSelected,
                onClick = {
                    val ids = accounts.filter { selected[it.id] == true }.map { it.id }
                    if (ids.isNotEmpty()) closeThen { onConfirm(ids) }
                },
            )
            TextButton(
                onClick = { closeThen(onCancel) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text("Cancelar", color = extras.muted, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun SelectAllChip(allSelected: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .pressScale(source)
            .clip(CircleShape)
            .background(ZeroTheme.extras.accentSoft)
            .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        AnimatedContent(
            targetState = allSelected,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "select-all",
        ) { all ->
            Text(
                if (all) "Quitar todas" else "Todas",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** One account as a card; the whole card toggles it. */
@Composable
private fun AccountOption(
    account: Account,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val extras = ZeroTheme.extras
    val source = remember { MutableInteractionSource() }
    val on = selectionFraction(checked, "account-option")
    val shape = MaterialTheme.shapes.medium
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(source, 0.98f)
            .clip(shape)
            .background(scheme.surfaceContainerHigh)
            .background(extras.accentSoft.copy(alpha = extras.accentSoft.alpha * on))
            .border(1.dp, lerp(extras.hairline, scheme.primary.copy(alpha = 0.55f), on), shape)
            .toggleable(
                value = checked,
                interactionSource = source,
                indication = LocalIndication.current,
                role = Role.Checkbox,
                onValueChange = onToggle,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Monogram(account.label.ifBlank { "?" }, active = true, highlighted = checked)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                account.label.ifBlank { "(sin label)" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                account.bingEmail.ifBlank { "(sin email)" },
                style = MaterialTheme.typography.bodySmall,
                color = extras.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        CheckMark(checked, on)
    }
}

@Composable
private fun CheckMark(checked: Boolean, on: Float) {
    val scheme = MaterialTheme.colorScheme
    val pop by animateFloatAsState(if (checked) 1f else 0f, ZeroMotion.bouncy(), label = "check-pop")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(24.dp)
            .border(2.dp, lerp(ZeroTheme.extras.hairlineStrong, scheme.primary, on), CircleShape)
            .background(scheme.primary.copy(alpha = on), CircleShape),
    ) {
        Icon(
            Icons.Rounded.Check,
            contentDescription = null,
            tint = scheme.onPrimary,
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer {
                    val s = pop.coerceAtLeast(0f)
                    scaleX = s
                    scaleY = s
                    alpha = s.coerceAtMost(1f)
                },
        )
    }
}
