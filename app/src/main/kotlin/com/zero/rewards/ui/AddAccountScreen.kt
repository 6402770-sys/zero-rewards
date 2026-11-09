package com.zero.rewards.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zero.rewards.AppContainer
import com.zero.rewards.core.account.Account
import com.zero.rewards.core.market.MarketConfig
import com.zero.rewards.ui.theme.ZeroTheme
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(
    container: AppContainer,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var label by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var market by remember { mutableStateOf(MarketConfig.DEFAULT) }
    var searchCount by remember { mutableStateOf("20") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ZeroTopBar(title = "Add account", onBack = onDone) },
        bottomBar = {
            BottomActionBar {
                PrimaryButton(
                    text = "Save account",
                    enabled = label.isNotBlank() && email.isNotBlank(),
                    onClick = {
                        scope.launch {
                            container.accountStore.upsert(
                                Account(
                                    label = label.trim(),
                                    bingEmail = email.trim(),
                                    locale = market.code,
                                    market = market.code,
                                    country = market.country,
                                    searchCount = searchCount.toIntOrNull() ?: 20,
                                ),
                            )
                            onDone()
                        }
                    },
                )
            }
        },
    ) { padding ->
        StaggeredColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            spacing = 24.dp,
        ) {
            SignInNotice()

            FormSection("Account") {
                ZeroTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = "Label",
                    supporting = "e.g. Pepe personal, hermano",
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next,
                    ),
                )
                ZeroTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Microsoft account email",
                    supporting = "Must match the email as it appears in the Bing app's account switcher",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next,
                    ),
                )
            }

            FormSection("Preferences") {
                MarketPicker(selected = market, onPick = { market = it })
                ZeroTextField(
                    value = searchCount,
                    onValueChange = { v -> searchCount = v.filter { it.isDigit() }.take(3) },
                    label = "Searches per day",
                    supporting = "Microsoft caps around 30/day. Set higher at your own risk.",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SignInNotice() {
    val extras = ZeroTheme.extras
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = extras.accentSoft,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp)) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Sign in to Bing first", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Sign in to this Microsoft account inside the Bing app before adding it here. " +
                        "Zero Rewards drives the Bing app via Android's Accessibility API, " +
                        "it does not sign in for you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.muted,
                )
            }
        }
    }
}

private fun MarketConfig.displayName(): String =
    Locale.forLanguageTag(code).getDisplayName(Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarketPicker(selected: MarketConfig, onPick: (MarketConfig) -> Unit) {
    val extras = ZeroTheme.extras
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.displayName(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Market") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = MaterialTheme.shapes.medium,
            colors = zeroFieldColors(),
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            MarketConfig.ALL.forEach { m ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                m.displayName(),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                m.code,
                                style = MaterialTheme.typography.labelMedium,
                                color = extras.muted,
                            )
                        }
                    },
                    trailingIcon = {
                        if (m == selected) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                    onClick = { onPick(m); expanded = false },
                )
            }
        }
    }
}
