package com.zero.rewards.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zero.rewards.AppContainer
import com.zero.rewards.core.reminder.StreakReminder
import com.zero.rewards.notifications.StreakReminderScheduler
import com.zero.rewards.ui.theme.ZeroTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreakRemindersScreen(container: AppContainer, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scheduler = remember { StreakReminderScheduler(context.applicationContext) }
    val reminders by container.reminderStore.observe()
        .collectAsState(initial = emptyList())
    var pickerOpen by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<StreakReminder?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ZeroTopBar(
                title = "Streak reminders",
                subtitle = "Daily notifications",
                onBack = onDone,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { pickerOpen = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.medium,
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Add time", style = MaterialTheme.typography.labelLarge) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro") { RemindersIntro() }

            if (reminders.isEmpty()) {
                item(key = "empty") { RemindersEmpty() }
            } else {
                items(reminders, key = { it.id }) { r ->
                    ReminderCard(
                        reminder = r,
                        onToggle = { enabled ->
                            val updated = r.copy(enabled = enabled)
                            scope.launch {
                                container.reminderStore.upsert(updated)
                                scheduler.apply(updated)
                            }
                        },
                        onDelete = { pendingDelete = r },
                    )
                }
            }
        }
    }

    if (pickerOpen) {
        TimePickDialog(
            initialHour = 20,
            initialMinute = 0,
            onDismiss = { pickerOpen = false },
            onConfirm = { h, m ->
                pickerOpen = false
                val r = StreakReminder(hourOfDay = h, minute = m, enabled = true)
                scope.launch {
                    container.reminderStore.upsert(r)
                    scheduler.apply(r)
                }
            },
        )
    }

    pendingDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.large,
            title = { Text("Delete ${r.formatTime()}?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "The alarm for this time will be cancelled and the reminder removed.",
                    color = ZeroTheme.extras.muted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        container.reminderStore.delete(r.id)
                        scheduler.cancel(r.id)
                    }
                }) {
                    Text("Delete", color = ZeroTheme.extras.danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            },
        )
    }
}

@Composable
private fun RemindersIntro() {
    val extras = ZeroTheme.extras
    ZeroCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Icon(
                Icons.Outlined.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Daily streak reminders",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Set one or more times per day. Each fires a notification naming the accounts that haven't farmed yet today.",
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.muted,
                )
            }
        }
    }
}

@Composable
private fun RemindersEmpty() {
    val extras = ZeroTheme.extras
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp, start = 24.dp, end = 24.dp),
    ) {
        Icon(
            Icons.Outlined.Schedule,
            contentDescription = null,
            tint = extras.faint,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("No reminders yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "Tap Add time to schedule a daily notification — for example 8:00 PM or 11:00 PM before the streak resets.",
            style = MaterialTheme.typography.bodyMedium,
            color = extras.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
    }
}

@Composable
private fun ReminderCard(
    reminder: StreakReminder,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val extras = ZeroTheme.extras
    ZeroCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    reminder.formatTime(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (reminder.enabled) MaterialTheme.colorScheme.onSurface else extras.faint,
                )
                Text(
                    if (reminder.enabled) "Every day" else "Paused",
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.muted,
                )
            }
            Switch(
                checked = reminder.enabled,
                onCheckedChange = onToggle,
                colors = zeroSwitchColors(),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete",
                    tint = extras.muted,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        title = { Text("Pick a time", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text(
                    "Add",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
            }
        },
    )
}
