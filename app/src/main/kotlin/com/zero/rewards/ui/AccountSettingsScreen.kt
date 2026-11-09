package com.zero.rewards.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zero.rewards.AppContainer
import com.zero.rewards.ui.theme.ZeroTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    container: AppContainer,
    accountId: String,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var searchCount by remember { mutableStateOf("20") }
    var delayMin by remember { mutableStateOf("8") }
    var delayMax by remember { mutableStateOf("16") }
    var farmSearch by remember { mutableStateOf(true) }
    var farmDailySet by remember { mutableStateOf(true) }
    var farmMoreActivities by remember { mutableStateOf(true) }
    var farmReadToEarn by remember { mutableStateOf(true) }
    var farmIntervalos by remember { mutableStateOf(true) }

    LaunchedEffect(accountId) {
        val a = container.accountStore.byId(accountId) ?: return@LaunchedEffect
        label = a.label
        email = a.bingEmail
        searchCount = a.searchCount.toString()
        delayMin = a.searchDelayMinSec.toString()
        delayMax = a.searchDelayMaxSec.toString()
        farmSearch = a.farmSearch
        farmDailySet = a.farmDailySet
        farmMoreActivities = a.farmMoreActivities
        farmReadToEarn = a.farmReadToEarn
        farmIntervalos = a.farmIntervalos
        loaded = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ZeroTopBar(title = "Settings", subtitle = email, onBack = onDone) },
        bottomBar = {
            if (loaded) {
                BottomActionBar {
                    PrimaryButton(
                        text = "Save",
                        onClick = {
                            scope.launch {
                                val a = container.accountStore.byId(accountId) ?: return@launch
                                val dMin = (delayMin.toIntOrNull() ?: 8).coerceAtLeast(1)
                                val dMax = (delayMax.toIntOrNull() ?: 16).coerceAtLeast(dMin + 1)
                                container.accountStore.upsert(
                                    a.copy(
                                        label = label.trim(),
                                        bingEmail = email.trim(),
                                        searchCount = searchCount.toIntOrNull() ?: 20,
                                        searchDelayMinSec = dMin,
                                        searchDelayMaxSec = dMax,
                                        farmSearch = farmSearch,
                                        farmDailySet = farmDailySet,
                                        farmMoreActivities = farmMoreActivities,
                                        farmReadToEarn = farmReadToEarn,
                                        farmIntervalos = farmIntervalos,
                                    ),
                                )
                                onDone()
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        StaggeredColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            spacing = 24.dp,
        ) {
            FormSection("Account") {
                ZeroTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = "Label",
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next,
                    ),
                )
                ZeroTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Bing account email",
                    supporting = "Must match the email in Bing's account switcher",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next,
                    ),
                )
            }

            FormSection("Search pace") {
                val number = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                )
                ZeroTextField(
                    value = searchCount,
                    onValueChange = { v -> searchCount = v.filter { it.isDigit() }.take(3) },
                    label = "Searches per day",
                    keyboardOptions = number,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ZeroTextField(
                        value = delayMin,
                        onValueChange = { v -> delayMin = v.filter { it.isDigit() }.take(3) },
                        label = "Min delay",
                        suffix = "sec",
                        keyboardOptions = number,
                        modifier = Modifier.weight(1f),
                    )
                    ZeroTextField(
                        value = delayMax,
                        onValueChange = { v -> delayMax = v.filter { it.isDigit() }.take(3) },
                        label = "Max delay",
                        suffix = "sec",
                        keyboardOptions = number.copy(imeAction = ImeAction.Done),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            FormSection("Phases to farm", contentPadding = 0.dp) {
                Column(Modifier.padding(PaddingValues(vertical = 4.dp))) {
                    ToggleRow(
                        icon = Icons.Outlined.TouchApp,
                        title = "Daily streak",
                        subtitle = "Rewards panel touch",
                        checked = farmIntervalos,
                        onChange = { farmIntervalos = it },
                    )
                    PhaseDivider()
                    ToggleRow(
                        icon = Icons.Outlined.Search,
                        title = "Search",
                        checked = farmSearch,
                        onChange = { farmSearch = it },
                    )
                    PhaseDivider()
                    ToggleRow(
                        icon = Icons.Outlined.EventAvailable,
                        title = "Daily set",
                        checked = farmDailySet,
                        onChange = { farmDailySet = it },
                    )
                    PhaseDivider()
                    ToggleRow(
                        icon = Icons.Outlined.Explore,
                        title = "More activities",
                        checked = farmMoreActivities,
                        onChange = { farmMoreActivities = it },
                    )
                    PhaseDivider()
                    ToggleRow(
                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                        title = "Read to earn",
                        checked = farmReadToEarn,
                        onChange = { farmReadToEarn = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun PhaseDivider() {
    HorizontalDivider(
        color = ZeroTheme.extras.hairline,
        modifier = Modifier.padding(start = 68.dp),
    )
}
