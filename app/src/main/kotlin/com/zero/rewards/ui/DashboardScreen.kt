package com.zero.rewards.ui

import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zero.rewards.AppContainer
import com.zero.rewards.R
import com.zero.rewards.core.account.Account
import com.zero.rewards.service.FarmerForegroundService
import com.zero.rewards.service.FarmerState
import com.zero.rewards.service.ZeroAccessibilityService
import com.zero.rewards.ui.theme.ZeroTheme
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Items that already played their entrance, so coming back to the dashboard doesn't replay it. */
private val DashboardEntrances = mutableSetOf<Any>()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    container: AppContainer,
    onAddAccount: () -> Unit,
    onOpenSettings: (String) -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenCredits: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // null until the first database emission, so the empty state never flashes on launch.
    val loaded by container.accountStore.observe()
        .collectAsState(initial = null as List<Account>?)
    val accounts = loaded.orEmpty()
    val runningId by FarmerState.runningAccountId.collectAsState()
    val runningPhase by FarmerState.phase.collectAsState()
    var pendingDelete by remember { mutableStateOf<Account?>(null) }

    val listState = rememberLazyListState()
    val fabExpanded by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 200 }
    }
    val fabSource = remember { MutableInteractionSource() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddAccount,
                expanded = fabExpanded,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.medium,
                interactionSource = fabSource,
                icon = { Icon(Icons.Rounded.Add, "Add account") },
                text = { Text("Add account", style = MaterialTheme.typography.labelLarge) },
                modifier = Modifier
                    .pressScale(fabSource)
                    .entrance("fab", 3, DashboardEntrances),
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                DashboardHeader(
                    isRunning = runningId != null,
                    onOpenAppearance = onOpenAppearance,
                    onOpenReminders = onOpenReminders,
                    modifier = Modifier.entrance("header", 0, DashboardEntrances),
                )
            }
            item(key = "accessibility") { AccessibilityBanner() }
            item(key = "running") {
                RunningBanner(
                    runningId = runningId,
                    runningPhase = runningPhase,
                    accounts = accounts,
                    onStop = { FarmerForegroundService.stop(context) },
                )
            }

            if (loaded != null && accounts.isEmpty()) {
                item(key = "empty") { EmptyState(Modifier.entrance("empty", 1, DashboardEntrances)) }
            }

            if (accounts.isNotEmpty()) {
                item(key = "summary") {
                    SummaryCard(accounts, Modifier.entrance("summary", 1, DashboardEntrances))
                }
                item(key = "accounts-label") {
                    SectionLabel(
                        "Accounts · ${accounts.size}",
                        Modifier
                            .padding(start = 4.dp, top = 12.dp, bottom = 2.dp)
                            .entrance("accounts-label", 2, DashboardEntrances),
                    )
                }
            }

            itemsIndexed(accounts, key = { _, account -> account.id }) { index, account ->
                val isRunning = runningId == account.id
                val anyRunning = runningId != null
                AccountCard(
                    account = account,
                    isRunning = isRunning,
                    anyRunning = anyRunning,
                    onRunNow = {
                        if (ZeroAccessibilityService.get() == null) {
                            Toast.makeText(context, "Enable Accessibility first", Toast.LENGTH_LONG).show()
                        } else if (anyRunning) {
                            Toast.makeText(context, "Another run is in progress. Stop it first.", Toast.LENGTH_LONG).show()
                        } else {
                            FarmerForegroundService.start(context, account.id)
                            Toast.makeText(
                                context,
                                "Running: ${account.label}. Don't touch the phone.",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                    onStop = { FarmerForegroundService.stop(context) },
                    onDelete = { pendingDelete = account },
                    onSettings = { onOpenSettings(account.id) },
                    modifier = Modifier
                        .animateItem()
                        .entrance(account.id, 3 + index, DashboardEntrances),
                )
            }

            if (loaded != null) {
                item(key = "credits") {
                    CreditsFooter(
                        onClick = onOpenCredits,
                        modifier = Modifier
                            .animateItem()
                            .entrance("credits", 4 + accounts.size, DashboardEntrances),
                    )
                }
            }
        }
    }

    pendingDelete?.let { account ->
        DeleteDialog(
            account = account,
            onConfirm = {
                pendingDelete = null
                scope.launch { container.accountStore.delete(account.id) }
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun DashboardHeader(
    isRunning: Boolean,
    onOpenAppearance: () -> Unit,
    onOpenReminders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extras = ZeroTheme.extras
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        ZeroMark()
        Spacer(Modifier.width(12.dp))
        Text(
            "Zero Rewards",
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        AnimatedContent(
            targetState = isRunning,
            transitionSpec = {
                (fadeIn(tween(240)) + scaleIn(initialScale = 0.85f, animationSpec = ZeroMotion.bouncy()))
                    .togetherWith(fadeOut(tween(140)) + scaleOut(targetScale = 0.85f))
                    .using(SizeTransform(clip = false))
            },
            label = "status-pill",
        ) { running ->
            if (running) {
                StatusPill("Running", extras.positive, extras.positiveSoft, pulse = true)
            } else {
                StatusPill("Idle", extras.muted, MaterialTheme.colorScheme.surfaceContainerHigh)
            }
        }
        IconButton(onClick = onOpenReminders) {
            Icon(Icons.Outlined.Notifications, "Streak reminders")
        }
        IconButton(onClick = onOpenAppearance) {
            Icon(Icons.Outlined.Palette, "Appearance")
        }
    }
}

@Composable
private fun SummaryCard(accounts: List<Account>, modifier: Modifier = Modifier) {
    val extras = ZeroTheme.extras
    val glow = extras.accentGlow
    val total = accounts.sumOf { it.lastKnownPoints }
    val searches = accounts.sumOf { it.searchCount }
    val lastRun = accounts.mapNotNull { it.lastRunEpochMs }.maxOrNull()

    val shown = remember { Animatable(0f) }
    LaunchedEffect(total) {
        shown.animateTo(total.toFloat(), tween(1100, easing = ZeroMotion.EmphasizedDecelerate))
    }

    ZeroCard(modifier) {
        Column(
            Modifier
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(glow, Color.Transparent),
                            center = Offset(size.width * 0.9f, 0f),
                            radius = size.width * 0.85f,
                        ),
                    )
                }
                .padding(20.dp),
        ) {
            SectionLabel("Total balance")
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatPoints(shown.value.toInt()),
                    style = MaterialTheme.typography.displayMedium,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "pts",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = extras.hairline)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric("Accounts", accounts.size.toString(), Modifier.weight(1f))
                Metric("Searches/day", searches.toString(), Modifier.weight(1f))
                Metric("Last run", lastRun?.let(::formatShortDay) ?: "Never", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AccessibilityBanner() {
    val context = LocalContext.current
    val extras = ZeroTheme.extras
    val granted = ZeroAccessibilityService.get() != null
    val visible = remember { MutableTransitionState(false) }.apply { targetState = !granted }
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(tween(320)) + expandVertically(ZeroMotion.gentle()),
        exit = fadeOut(tween(160)) + shrinkVertically(ZeroMotion.gentle()),
    ) {
        ZeroCard(borderColor = extras.warning.copy(alpha = 0.35f)) {
            Column(Modifier.background(extras.warningSoft).padding(16.dp)) {
                Row {
                    Icon(
                        Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = extras.warning,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Accessibility service not enabled",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Enable Zero Rewards in Settings → Accessibility → Downloaded apps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = extras.muted,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                val source = remember { MutableInteractionSource() }
                FilledTonalButton(
                    onClick = {
                        context.startActivity(android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    interactionSource = source,
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = extras.warning,
                        contentColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                    modifier = Modifier.padding(start = 34.dp).pressScale(source),
                ) { Text("Open settings") }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val extras = ZeroTheme.extras
    val float = rememberInfiniteTransition(label = "empty-float")
    val lift by float.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = ZeroMotion.Emphasized), RepeatMode.Reverse),
        label = "empty-lift",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth().padding(top = 96.dp, start = 24.dp, end = 24.dp),
    ) {
        ZeroMark(size = 56.dp, modifier = Modifier.graphicsLayer { translationY = -lift * 8.dp.toPx() })
        Spacer(Modifier.height(20.dp))
        Text("No accounts yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap Add account to get started. Sign in to each MS account inside the Bing app first.",
            style = MaterialTheme.typography.bodyMedium,
            color = extras.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp),
        )
    }
}

@Composable
private fun AccountCard(
    account: Account,
    isRunning: Boolean,
    anyRunning: Boolean,
    onRunNow: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extras = ZeroTheme.extras
    val scheme = MaterialTheme.colorScheme
    val border = lerp(extras.hairline, scheme.primary.copy(alpha = 0.7f), selectionFraction(isRunning, "card-border"))
    val points by animateIntAsState(
        account.lastKnownPoints,
        tween(900, easing = ZeroMotion.EmphasizedDecelerate),
        label = "card-points",
    )

    ZeroCard(modifier = modifier, borderColor = border) {
        Column(Modifier.animateContentSize(ZeroMotion.gentle()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Monogram(account.label, active = true, highlighted = isRunning)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        account.label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        account.bingEmail.ifBlank { "(no email set)" },
                        style = MaterialTheme.typography.bodySmall,
                        color = extras.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric("Points", formatPoints(points), Modifier.weight(1f))
                Metric("Searches/day", account.searchCount.toString(), Modifier.weight(1f))
                Metric("Market", account.market, Modifier.weight(1f))
            }

            account.lastRunEpochMs?.let {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = extras.faint,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Last run ${formatLastRun(it)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = extras.muted,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AnimatedContent(
                    targetState = isRunning,
                    transitionSpec = {
                        (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(ZeroMotion.gentle()) { it / 3 })
                            .togetherWith(fadeOut(tween(140)) + slideOutVertically(ZeroMotion.gentle()) { -it / 3 })
                    },
                    label = "run-action",
                    modifier = Modifier.weight(1f),
                ) { running ->
                    if (running) {
                        RunAction("Stop", Icons.Rounded.Stop, extras.dangerSoft, extras.danger, true, onStop, Modifier.fillMaxWidth())
                    } else {
                        RunAction("Run now", Icons.Rounded.PlayArrow, extras.accentSoft, scheme.primary, !anyRunning, onRunNow, Modifier.fillMaxWidth())
                    }
                }
                CardIconButton(Icons.Outlined.Tune, "Settings", true, onSettings)
                CardIconButton(Icons.Outlined.DeleteOutline, "Delete", !isRunning, onDelete)
            }
        }
    }
}

@Composable
private fun RunAction(
    text: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = ZeroTheme.extras.faint,
        ),
        modifier = modifier.pressScale(source).height(46.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CardIconButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val extras = ZeroTheme.extras
    val source = remember { MutableInteractionSource() }
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, extras.hairlineStrong),
        colors = IconButtonDefaults.outlinedIconButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContentColor = extras.faint,
        ),
        modifier = Modifier.pressScale(source, 0.9f).size(46.dp),
    ) {
        Icon(icon, description, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun RunningBanner(
    runningId: String?,
    runningPhase: String?,
    accounts: List<Account>,
    onStop: () -> Unit,
) {
    val account = runningId?.let { id -> accounts.firstOrNull { it.id == id } }
    // Keeps the card filled in while it animates away after the run ends.
    var shown by remember { mutableStateOf(account) }
    if (account != null && account != shown) shown = account
    val visible = remember { MutableTransitionState(false) }.apply { targetState = account != null }
    val extras = ZeroTheme.extras

    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(tween(320)) + expandVertically(ZeroMotion.gentle()),
        exit = fadeOut(tween(200)) + shrinkVertically(ZeroMotion.gentle()),
    ) {
        val current = shown ?: return@AnimatedVisibility
        ZeroCard(borderColor = extras.positive.copy(alpha = 0.4f)) {
            Column(Modifier.background(extras.positiveSoft)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    StatusDot(extras.positive, pulse = true)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Running: ${current.label}",
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        AnimatedContent(
                            targetState = runningPhase ?: "in progress",
                            transitionSpec = {
                                (fadeIn(tween(220, delayMillis = 60)) + slideInVertically(ZeroMotion.gentle()) { it / 2 })
                                    .togetherWith(fadeOut(tween(120)) + slideOutVertically(ZeroMotion.gentle()) { -it / 2 })
                            },
                            label = "running-phase",
                        ) { phase ->
                            Text(
                                phase,
                                style = MaterialTheme.typography.bodySmall,
                                color = extras.muted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    RunAction("Stop", Icons.Rounded.Stop, extras.dangerSoft, extras.danger, true, onStop)
                }
                Box(Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        color = extras.positive,
                        trackColor = Color.Transparent,
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CreditsFooter(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .pressScale(source)
                .clip(CircleShape)
                .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
                .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Image(
                painterResource(R.drawable.credits_claude),
                contentDescription = null,
                modifier = Modifier.height(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Credits · ${Credits.DISPLAY_NAME} & Claude",
                style = MaterialTheme.typography.labelMedium,
                color = ZeroTheme.extras.muted,
            )
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = ZeroTheme.extras.faint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun DeleteDialog(account: Account, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val extras = ZeroTheme.extras
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        title = { Text("Delete ${account.label}?", style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                "This removes the account and its settings from Zero Rewards. " +
                    "Your Microsoft account isn't affected.",
                color = extras.muted,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = extras.danger, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
            }
        },
    )
}

private fun formatPoints(value: Int): String = NumberFormat.getIntegerInstance().format(value)

private fun dayDistance(epochMs: Long): Int? {
    val then = Calendar.getInstance().apply { timeInMillis = epochMs }
    val now = Calendar.getInstance()
    return when {
        then.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) -> 0
        else -> {
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            if (then.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                then.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
            ) 1 else null
        }
    }
}

private fun formatLastRun(epochMs: Long): String {
    val date = Date(epochMs)
    val time = SimpleDateFormat("HH:mm", Locale.US).format(date)
    return when (dayDistance(epochMs)) {
        0 -> "today at $time"
        1 -> "yesterday at $time"
        else -> "on ${SimpleDateFormat("MMM d", Locale.US).format(date)} at $time"
    }
}

private fun formatShortDay(epochMs: Long): String = when (dayDistance(epochMs)) {
    0 -> SimpleDateFormat("HH:mm", Locale.US).format(Date(epochMs))
    1 -> "Yesterday"
    else -> SimpleDateFormat("MMM d", Locale.US).format(Date(epochMs))
}
