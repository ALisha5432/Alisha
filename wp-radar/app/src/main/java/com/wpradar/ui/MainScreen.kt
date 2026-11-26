package com.wpradar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wpradar.WpRadarApp
import kotlinx.coroutines.launch

private data class Tab(val label: String, val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Əsas", "WP Radar", Icons.Filled.Home),
    Tab("Ayarlar", "Ayarlar", Icons.Filled.Settings),
    Tab("Tapılanlar", "Tapılan mesajlar", Icons.Filled.Notifications),
    Tab("Jurnal", "Jurnal", Icons.AutoMirrored.Filled.List),
)

private const val TAB_HOME = 0
private const val TAB_SETTINGS = 1
private const val TAB_FOUND = 2
private const val TAB_JOURNAL = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(app: WpRadarApp) {
    var selected by rememberSaveable { mutableIntStateOf(TAB_HOME) }
    var clearTab by remember { mutableStateOf<Int?>(null) }
    val history by app.history.entries.collectAsStateWithLifecycle(initialValue = emptyList())
    val journal by app.journal.entries.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tabs[selected].title) },
                actions = {
                    val canClear = (selected == TAB_FOUND && history.isNotEmpty()) ||
                        (selected == TAB_JOURNAL && journal.isNotEmpty())
                    if (canClear) {
                        TextButton(onClick = { clearTab = selected }) { Text("Təmizlə") }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (selected) {
                TAB_HOME -> HomeScreen(
                    app = app,
                    history = history,
                    onOpenSettings = { selected = TAB_SETTINGS },
                    onOpenJournal = { selected = TAB_JOURNAL },
                )
                TAB_SETTINGS -> SettingsScreen(app)
                TAB_FOUND -> HistoryScreen(history)
                else -> JournalScreen(journal)
            }
        }
    }

    clearTab?.let { tab ->
        AlertDialog(
            onDismissRequest = { clearTab = null },
            title = { Text(if (tab == TAB_FOUND) "Tapılanlar silinsin?" else "Jurnal silinsin?") },
            confirmButton = {
                TextButton(onClick = {
                    clearTab = null
                    if (tab == TAB_FOUND) {
                        app.appScope.launch { app.history.clear() }
                    } else {
                        app.journal.clear()
                    }
                }) { Text("Sil") }
            },
            dismissButton = {
                TextButton(onClick = { clearTab = null }) { Text("Ləğv et") }
            },
        )
    }
}
