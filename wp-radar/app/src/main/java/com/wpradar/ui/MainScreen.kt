package com.wpradar.ui

import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wpradar.R
import com.wpradar.WpRadarApp
import com.wpradar.ui.theme.Brand
import com.wpradar.ui.theme.RadarTheme
import kotlinx.coroutines.launch

private data class Tab(val label: String, val title: String, @DrawableRes val icon: Int)

private val tabs = listOf(
    Tab("Əsas", "WP Radar", R.drawable.ic_nav_radar),
    Tab("Ayarlar", "Ayarlar", R.drawable.ic_nav_tune),
    Tab("Tapılanlar", "Tapılan mesajlar", R.drawable.ic_nav_bell),
    Tab("Jurnal", "Jurnal", R.drawable.ic_nav_journal),
)

private const val TAB_HOME = 0
private const val TAB_SETTINGS = 1
private const val TAB_FOUND = 2
private const val TAB_JOURNAL = 3

@Composable
fun MainScreen(app: WpRadarApp) {
    var selected by rememberSaveable { mutableIntStateOf(TAB_HOME) }
    var clearTab by remember { mutableStateOf<Int?>(null) }
    val history by app.history.entries.collectAsStateWithLifecycle(initialValue = emptyList())
    val journal by app.journal.entries.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()
    val todayFound = remember(history) { history.count { DateUtils.isToday(it.time) } }
    val showMessage: (String) -> Unit = { message ->
        uiScope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }
    val canClear = (selected == TAB_FOUND && history.isNotEmpty()) ||
        (selected == TAB_JOURNAL && journal.isNotEmpty())

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopBar(
                tab = selected,
                foundCount = history.size,
                journalCount = journal.size,
                canClear = canClear,
                onClear = { clearTab = selected },
            )
        },
        bottomBar = {
            RadarNavBar(selected = selected, badge = todayFound, onSelect = { selected = it })
        },
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    snackbarData = data,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(18.dp),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                val forward = targetState > initialState
                val enter = fadeIn(tween(260, delayMillis = 70)) +
                    slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { w -> if (forward) w / 9 else -w / 9 }
                val exit = fadeOut(tween(150)) +
                    slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { w -> if (forward) -w / 14 else w / 14 }
                enter togetherWith exit
            },
            modifier = Modifier.fillMaxSize(),
            label = "tabs",
        ) { tab ->
            // Məzmun üst və alt panelin altından keçir — boşluğu siyahılar özü saxlayır.
            val insets = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
            when (tab) {
                TAB_HOME -> HomeScreen(
                    app = app,
                    history = history,
                    contentPadding = insets,
                    onOpenSettings = { selected = TAB_SETTINGS },
                    onOpenJournal = { selected = TAB_JOURNAL },
                    showMessage = showMessage,
                )
                TAB_SETTINGS -> SettingsScreen(app, insets)
                TAB_FOUND -> HistoryScreen(history, insets)
                else -> JournalScreen(journal, insets)
            }
        }
    }

    clearTab?.let { tab ->
        val found = tab == TAB_FOUND
        AlertDialog(
            onDismissRequest = { clearTab = null },
            icon = {
                IconBadge(R.drawable.ic_trash, MaterialTheme.colorScheme.error, size = 54.dp, iconSize = 26.dp)
            },
            title = {
                Text(
                    if (found) "Tapılanlar silinsin?" else "Jurnal silinsin?",
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Text(
                    if (found) "Bütün tapılan mesajlar bu siyahıdan silinəcək." else "Jurnaldakı bütün qeydlər silinəcək.",
                    textAlign = TextAlign.Center,
                )
            },
            confirmButton = {
                SoftButton(
                    text = "Sil",
                    onClick = {
                        clearTab = null
                        if (found) {
                            app.appScope.launch { app.history.clear() }
                        } else {
                            app.journal.clear()
                        }
                    },
                    icon = R.drawable.ic_trash,
                    filled = true,
                    color = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                )
            },
            dismissButton = {
                TextButton(onClick = { clearTab = null }) { Text("Ləğv et") }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = RadarTheme.palette.card,
        )
    }
}

@Composable
private fun TopBar(
    tab: Int,
    foundCount: Int,
    journalCount: Int,
    canClear: Boolean,
    onClear: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 14.dp, top = 8.dp, bottom = 6.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(tween(320, easing = FastOutSlowInEasing)) { h -> h / 3 }) togetherWith
                    fadeOut(tween(120)) using SizeTransform(clip = false)
            },
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
            label = "topTitle",
        ) { t ->
            if (t == TAB_HOME) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LogoMark(Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("WP Radar", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "WhatsApp açar söz radarı",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Column {
                    Text(tabs[t].title, style = MaterialTheme.typography.headlineSmall)
                    val sub = when (t) {
                        TAB_FOUND -> if (foundCount > 0) "$foundCount mesaj" else "Açar sözlü mesajlar"
                        TAB_JOURNAL -> if (journalCount > 0) "Son $journalCount qeyd" else "Radarın gördükləri"
                        else -> "Radarı özünə uyğunlaşdır"
                    }
                    Text(
                        sub,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = canClear,
            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.8f),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.8f),
        ) {
            SoftButton(
                text = "Təmizlə",
                onClick = onClear,
                icon = R.drawable.ic_trash,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Üzən alt panel: seçilmiş bölmənin arxasında yaylı sürüşən "həb". */
@Composable
private fun RadarNavBar(selected: Int, badge: Int, onSelect: (Int) -> Unit) {
    val palette = RadarTheme.palette
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(30.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(
                    elevation = if (palette.isDark) 0.dp else 18.dp,
                    shape = shape,
                    ambientColor = palette.shadow,
                    spotColor = palette.shadow,
                )
                .clip(shape)
                .background(palette.navBar)
                .border(1.dp, palette.cardBorder, shape)
                .padding(6.dp),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val itemWidth = maxWidth / tabs.size
                val indicatorX by animateDpAsState(
                    targetValue = itemWidth * selected,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
                    label = "navIndicator",
                )
                Box(
                    Modifier
                        .offset { IntOffset(indicatorX.roundToPx(), 0) }
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (palette.isDark) 0.16f else 0.12f)),
                )
                Row(Modifier.fillMaxSize()) {
                    tabs.forEachIndexed { index, tab ->
                        NavItem(
                            tab = tab,
                            selected = index == selected,
                            badge = if (index == TAB_FOUND) badge else 0,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            onClick = {
                                if (index != selected) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelect(index)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    tab: Tab,
    selected: Boolean,
    badge: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val tint by animateColorAsState(
        if (selected) colors.primary else colors.onSurfaceVariant,
        tween(250),
        label = "navTint",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "navIconScale",
    )
    val interaction = remember { MutableInteractionSource() }
    val press by rememberPressScale(interaction, pressed = 0.9f)
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .graphicsLayer {
                scaleX = press
                scaleY = press
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Icon(
                painterResource(tab.icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
            if (badge > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 10.dp, y = (-6).dp)
                        .heightIn(min = 17.dp)
                        .widthIn(min = 17.dp)
                        .clip(CircleShape)
                        .background(Brand.Amber)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (badge > 99) "99+" else badge.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = Color(0xFF3A2200),
                    )
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            tab.label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}
