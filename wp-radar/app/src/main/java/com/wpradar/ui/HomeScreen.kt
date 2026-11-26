package com.wpradar.ui

import android.Manifest
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wpradar.R
import com.wpradar.WpRadarApp
import com.wpradar.core.RadarSettings
import com.wpradar.data.HistoryEntry
import com.wpradar.notify.AlertNotifier
import com.wpradar.service.ListenerStatus
import com.wpradar.service.WaListenerService
import com.wpradar.system.SystemStatus
import com.wpradar.ui.theme.Brand
import com.wpradar.ui.theme.RadarTheme
import com.wpradar.ui.theme.tabular
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun HomeScreen(
    app: WpRadarApp,
    history: List<HistoryEntry>,
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
    onOpenJournal: () -> Unit,
    showMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val palette = RadarTheme.palette
    val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val connected by ListenerStatus.connected.collectAsStateWithLifecycle()
    val lastEvent by ListenerStatus.lastWhatsAppEventAt.collectAsStateWithLifecycle()

    // Ayarlardan qayıdanda vəziyyəti yenidən yoxlamaq üçün.
    var tick by remember { mutableIntStateOf(0) }
    OnResumeEffect { tick++ }

    val loud = settings?.loudMode == true
    val hasAccess = remember(tick) { SystemStatus.hasNotificationAccess(context) }
    val canNotify = remember(tick) { SystemStatus.canPostNotifications(context) }
    val channelOk = remember(tick, loud) { SystemStatus.isChannelEnabled(context, AlertNotifier.channelId(loud)) }
    val batteryOk = remember(tick) { SystemStatus.isIgnoringBatteryOptimizations(context) }
    val today = remember(history) { history.count { DateUtils.isToday(it.time) } }

    // İcazə var, amma dinləyici qoşulmayıb → sistemdən yenidən bağlamağı xahiş et.
    LaunchedEffect(hasAccess, connected) {
        if (hasAccess && !connected) {
            delay(2_000)
            if (!ListenerStatus.connected.value) SystemStatus.requestRebind(context)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        tick++
        if (!granted) {
            val activity = context.findActivity()
            val canAskAgain = activity != null &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
            // "Bir daha soruşma" seçilibsə, sistem pəncərəsi açılmır — ayarlara göndəririk.
            if (!canAskAgain) SystemStatus.openAppNotificationSettings(context)
        }
    }
    val askNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !SystemStatus.hasPostPermission(context)) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            SystemStatus.openAppNotificationSettings(context)
        }
    }

    val s = settings ?: return
    val listenerOk = hasAccess && connected
    val notifyOk = canNotify && channelOk
    val ready = listenerOk && notifyOk && s.keywords.isNotEmpty()
    val mode = when {
        !s.enabled -> RadarMode.Off
        ready -> RadarMode.Active
        else -> RadarMode.Attention
    }
    val done = listOf(listenerOk, notifyOk, batteryOk).count { it }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(2.dp))

        // ── Radar paneli ──────────────────────────────────────────────
        StaggeredEntrance(0) {
            HeroPanel(
                settings = s,
                mode = mode,
                today = today,
                lastEvent = lastEvent,
                onToggle = { value ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    app.appScope.launch { app.settings.setEnabled(value) }
                },
                onAddKeyword = onOpenSettings,
            )
        }

        // ── Tez hərəkətlər ────────────────────────────────────────────
        StaggeredEntrance(1) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionTile(
                    title = "İndi yoxla",
                    subtitle = "Paneldəki bildirişlər",
                    icon = R.drawable.ic_scan,
                    tint = MaterialTheme.colorScheme.primary,
                    motion = TileMotion.Spin,
                    modifier = Modifier.weight(1f),
                ) {
                    val count = WaListenerService.scanNow()
                    val message = if (count == null) {
                        "Dinləyici qoşulmayıb"
                    } else {
                        "Paneldəki $count WhatsApp bildirişi yoxlanıldı"
                    }
                    showMessage(message)
                }
                ActionTile(
                    title = "Test səsi",
                    subtitle = "Səs və vibrasiya",
                    icon = R.drawable.ic_volume,
                    tint = palette.warning,
                    motion = TileMotion.Wiggle,
                    modifier = Modifier.weight(1f),
                ) {
                    if (SystemStatus.canPostNotifications(context)) {
                        AlertNotifier.showTest(context, s.loudMode, s.repeatAlert)
                    } else {
                        askNotifications()
                    }
                }
            }
        }

        // ── Quraşdırma ────────────────────────────────────────────────
        StaggeredEntrance(2) {
            SectionCard(
                title = "Quraşdırma",
                icon = R.drawable.ic_sparkle,
                subtitle = if (done == 3) "Hər şey qaydasındadır" else "${3 - done} addım qalıb",
                accent = if (done == 3) palette.success else palette.warning,
                trailing = { ProgressRing(done = done, total = 3) },
            ) {
                StatusRow(
                    ok = listenerOk,
                    title = "Bildiriş girişi",
                    status = when {
                        !hasAccess -> "Verilməyib"
                        connected -> "İşləyir"
                        else -> "Qoşulmayıb"
                    },
                    icon = R.drawable.ic_nav_radar,
                ) {
                    when {
                        !hasAccess -> SoftButton(
                            text = "İcazə ver",
                            onClick = { SystemStatus.openNotificationAccessSettings(context) },
                            filled = true,
                        )
                        !connected -> SoftButton(
                            text = "Qoş",
                            onClick = {
                                SystemStatus.forceReconnect(context)
                                tick++
                            },
                            icon = R.drawable.ic_scan,
                        )
                        else -> {}
                    }
                }
                AnimatedVisibility(visible = !hasAccess) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(palette.warningContainer.copy(alpha = 0.6f))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HintText("«Məhdud ayar» çıxsa: Tətbiq məlumatı → ⋮ → «Məhdud ayarlara icazə ver».")
                        SoftButton(
                            text = "Tətbiq məlumatı",
                            onClick = { SystemStatus.openAppDetails(context) },
                            icon = R.drawable.ic_external,
                            color = palette.warning,
                        )
                    }
                }
                StepDivider()
                StatusRow(
                    ok = notifyOk,
                    title = "Bildiriş icazəsi",
                    status = when {
                        !canNotify -> "Verilməyib"
                        !channelOk -> "Kanal söndürülüb"
                        else -> "Var"
                    },
                    icon = R.drawable.ic_nav_bell,
                ) {
                    when {
                        !canNotify -> SoftButton(text = "İcazə ver", onClick = askNotifications, filled = true)
                        !channelOk -> SoftButton(
                            text = "Aç",
                            onClick = { SystemStatus.openChannelSettings(context, AlertNotifier.channelId(loud)) },
                            filled = true,
                        )
                        else -> {}
                    }
                }
                StepDivider()
                StatusRow(
                    ok = batteryOk,
                    title = "Batareya",
                    status = if (batteryOk) "Məhdudiyyət yoxdur" else "Sistem radarı yatıra bilər",
                    icon = R.drawable.ic_battery,
                ) {
                    if (!batteryOk) {
                        SoftButton(
                            text = "Düzəlt",
                            onClick = { SystemStatus.requestIgnoreBatteryOptimizations(context) },
                            filled = true,
                        )
                    }
                }
            }
        }

        // ── Xəbər gəlmədi? ────────────────────────────────────────────
        StaggeredEntrance(3) {
            ExpandableCard(title = "Xəbər gəlmədi? Səbəblər", icon = R.drawable.ic_help) {
                BulletText("Qrup WhatsApp-da səssizdədir (mute) və ya arxivdədir — WhatsApp bildiriş göstərmir.")
                BulletText("WhatsApp bildirişləri söndürülüb — söndürməyin, lazımsa «Səssiz» edin.")
                BulletText("O an WhatsApp ekranda açıq idi və ya WhatsApp Web aktiv idi.")
                BulletText("Xiaomi/Samsung-da «Avtostart» və «Fonda işləmə» icazəsi yoxdur.")
                HintText("Jurnalda radarın gördüyü hər mesaj və səbəbi yazılır.")
                SoftButton(text = "Jurnala bax", onClick = onOpenJournal, icon = R.drawable.ic_nav_journal)
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

/** Qaranlıq radar paneli — tətbiqin "ürəyi". */
@Composable
private fun HeroPanel(
    settings: RadarSettings,
    mode: RadarMode,
    today: Int,
    lastEvent: Long,
    onToggle: (Boolean) -> Unit,
    onAddKeyword: () -> Unit,
) {
    val shape = RoundedCornerShape(32.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Brand.HeroTop, Brand.HeroBottom)))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LiveChip(mode)
            Spacer(Modifier.weight(1f))
            RadarSwitch(checked = settings.enabled, onCheckedChange = onToggle, onDark = true)
        }
        Spacer(Modifier.height(6.dp))
        RadarScope(
            mode = mode,
            blips = today,
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .aspectRatio(1f),
        )
        Spacer(Modifier.height(16.dp))
        FadeText(
            text = when (mode) {
                RadarMode.Off -> "Radar dayanıb"
                RadarMode.Active -> "Radar işləyir"
                RadarMode.Attention -> "Quraşdırma lazımdır"
            },
            style = MaterialTheme.typography.headlineSmall,
            color = Brand.OnHero,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.ic_clock),
                contentDescription = null,
                tint = Brand.OnHeroMuted,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            FadeText(
                text = "Son WhatsApp bildirişi: ${if (lastEvent == 0L) "hələ yoxdur" else formatTime(lastEvent)}",
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = Brand.OnHeroMuted,
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Stat(
                label = "Qrup",
                value = if (settings.groups.isEmpty()) "Hamısı" else settings.groups.size.toString(),
                modifier = Modifier.weight(1f),
            )
            StatDivider()
            Stat(label = "Açar söz", value = settings.keywords.size.toString(), modifier = Modifier.weight(1f))
            StatDivider()
            Stat(label = "Bu gün", value = today.toString(), modifier = Modifier.weight(1f), highlight = today > 0)
        }
        AnimatedVisibility(visible = settings.keywords.isEmpty()) {
            Column {
                Spacer(Modifier.height(16.dp))
                GradientButton(
                    text = "Açar söz əlavə et",
                    onClick = onAddKeyword,
                    icon = R.drawable.ic_add,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** "CANLI / DİQQƏT / DAYANIB" nişanı, canlı olanda nöqtə yanıb-sönür. */
@Composable
private fun LiveChip(mode: RadarMode) {
    val color by animateColorAsState(
        when (mode) {
            RadarMode.Active -> Brand.Mint
            RadarMode.Attention -> Brand.Amber
            RadarMode.Off -> Brand.Slate
        },
        tween(500),
        label = "liveColor",
    )
    val transition = rememberInfiniteTransition(label = "live")
    val blink by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "blink",
    )
    Row(
        Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.32f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .graphicsLayer { alpha = if (mode == RadarMode.Off) 1f else blink }
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(8.dp))
        FadeText(
            text = when (mode) {
                RadarMode.Active -> "CANLI"
                RadarMode.Attention -> "DİQQƏT"
                RadarMode.Off -> "DAYANIB"
            },
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
            color = color,
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, highlight: Boolean = false) {
    val color by animateColorAsState(if (highlight) Brand.Amber else Brand.OnHero, tween(400), label = "statColor")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        RollingText(value, MaterialTheme.typography.titleLarge, color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Brand.OnHeroMuted)
    }
}

@Composable
private fun StatDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(30.dp)
            .background(Color.White.copy(alpha = 0.10f)),
    )
}

@Composable
private fun StepDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
}

/** Quraşdırmanın neçə addımı hazırdır — dolan halqa. */
@Composable
private fun ProgressRing(done: Int, total: Int) {
    val palette = RadarTheme.palette
    val progress by animateFloatAsState(
        targetValue = done.toFloat() / total,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "setupProgress",
    )
    val color by animateColorAsState(
        if (done == total) palette.success else palette.warning,
        tween(500),
        label = "ringColor",
    )
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, topLeft = topLeft, size = arcSize, style = Stroke(width = stroke))
            drawArc(
                color,
                -90f,
                360f * progress,
                false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            "$done/$total",
            style = MaterialTheme.typography.labelMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private enum class TileMotion { Spin, Wiggle }

/** Böyük kvadrat düymə; toxunanda ikon fırlanır və ya titrəyir. */
@Composable
private fun ActionTile(
    title: String,
    subtitle: String,
    @DrawableRes icon: Int,
    tint: Color,
    motion: TileMotion,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val progress = remember { Animatable(1f) }
    RadarCard(
        modifier,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
            scope.launch {
                progress.snapTo(0f)
                progress.animateTo(
                    1f,
                    if (motion == TileMotion.Spin) {
                        tween(750, easing = FastOutSlowInEasing)
                    } else {
                        tween(650, easing = LinearEasing)
                    },
                )
            }
        },
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier
                        .size(23.dp)
                        .graphicsLayer {
                            val p = progress.value
                            rotationZ = when (motion) {
                                TileMotion.Spin -> p * 360f
                                TileMotion.Wiggle -> sin(p * 4f * PI.toFloat()) * 16f * (1f - p)
                            }
                        },
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
