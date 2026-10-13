package az.novbe.radar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import az.novbe.radar.data.Prefs
import az.novbe.radar.data.Repo
import az.novbe.radar.data.Status
import kotlinx.coroutines.delay

private class TabDef(val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabDef("Radar", Icons.Filled.Home),
    TabDef("Tapılanlar", Icons.Filled.Star),
    TabDef("Jurnal", Icons.Filled.List),
    TabDef("Öyrən", Icons.Filled.Search),
    TabDef("Ayarlar", Icons.Filled.Settings),
)

@Composable
fun AppRoot(resumeTick: Int) {
    val ctx = LocalContext.current
    val s by Prefs.flow.collectAsState()
    val st by Status.flow.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            refresh++
        }
    }
    val checks = remember(resumeTick, refresh, s, st.accessibilityOn) { Checks.read(ctx) }
    var showSetup by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val inSetup = !s.setupDone || showSetup

    BackHandler(enabled = showSetup && s.setupDone) { showSetup = false }
    BackHandler(enabled = !inSetup && tab != 0) { tab = 0 }

    Box(Modifier.fillMaxSize().background(RC.Bg)) {
        AnimatedContent(
            targetState = inSetup,
            transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(200)) },
            label = "root",
        ) { setup ->
            if (setup) {
                SetupScreen(
                    checks = checks,
                    canClose = s.setupDone,
                    onClose = { showSetup = false },
                    onDone = {
                        showSetup = false
                        tab = 0
                    },
                )
            } else {
                MainTabs(checks, tab, { tab = it }, onSetup = { showSetup = true })
            }
        }
    }
}

@Composable
private fun MainTabs(checks: Checks, tab: Int, onTab: (Int) -> Unit, onSetup: () -> Unit) {
    val found by Repo.found.collectAsState()
    val active = found.count { it.present && !it.test }
    Scaffold(
        containerColor = RC.Bg,
        bottomBar = {
            NavigationBar(containerColor = RC.Surface, tonalElevation = 0.dp) {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { onTab(i) },
                        icon = {
                            if (i == 1 && active > 0) {
                                BadgedBox(badge = { Badge(containerColor = RC.Amber, contentColor = RC.Bg) { Text("$active") } }) {
                                    Icon(t.icon, t.label)
                                }
                            } else {
                                Icon(t.icon, t.label)
                            }
                        },
                        label = { Text(t.label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = RC.Cyan,
                            selectedTextColor = RC.Cyan,
                            indicatorColor = RC.Cyan.copy(alpha = 0.14f),
                            unselectedIconColor = RC.Muted,
                            unselectedTextColor = RC.Muted,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 30 }) togetherWith fadeOut(tween(120))
                },
                label = "tabs",
            ) { t ->
                when (t) {
                    0 -> HomeScreen(checks, onFix = onSetup, onShowFound = { onTab(1) })
                    1 -> FoundScreen()
                    2 -> JournalScreen()
                    3 -> LearnScreen()
                    else -> SettingsScreen(checks, onSetup = onSetup)
                }
            }
        }
    }
}
