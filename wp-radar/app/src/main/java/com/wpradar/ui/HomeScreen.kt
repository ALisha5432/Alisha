package com.wpradar.ui

import android.Manifest
import android.os.Build
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wpradar.WpRadarApp
import com.wpradar.data.HistoryEntry
import com.wpradar.notify.AlertNotifier
import com.wpradar.service.ListenerStatus
import com.wpradar.service.WaListenerService
import com.wpradar.system.SystemStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    app: WpRadarApp,
    history: List<HistoryEntry>,
    onOpenSettings: () -> Unit,
    onOpenJournal: () -> Unit,
) {
    val context = LocalContext.current
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

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Vəziyyət ──────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (s.enabled && ready) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
            ),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📡", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                !s.enabled -> "Radar dayanıb"
                                ready -> "Radar işləyir"
                                else -> "Quraşdırma lazımdır"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Qrup: ${if (s.groups.isEmpty()) "hamısı" else s.groups.size.toString()} · " +
                                "Açar söz: ${s.keywords.size} · Bu gün: $today",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = s.enabled,
                        onCheckedChange = { value -> app.appScope.launch { app.settings.setEnabled(value) } },
                    )
                }
                Text(
                    "Son WhatsApp bildirişi: ${if (lastEvent == 0L) "hələ yoxdur" else formatTime(lastEvent)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.keywords.isEmpty()) {
                        Button(onClick = onOpenSettings) { Text("Açar söz əlavə et") }
                    }
                    FilledTonalButton(onClick = {
                        val count = WaListenerService.scanNow()
                        val message = if (count == null) {
                            "Dinləyici qoşulmayıb"
                        } else {
                            "Paneldəki $count WhatsApp bildirişi yoxlanıldı"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }) { Text("İndi yoxla") }
                    FilledTonalButton(onClick = {
                        if (SystemStatus.canPostNotifications(context)) {
                            AlertNotifier.showTest(context, s.loudMode, s.repeatAlert)
                        } else {
                            askNotifications()
                        }
                    }) { Text("Test səsi") }
                }
            }
        }

        // ── Quraşdırma ────────────────────────────────────────────────
        SectionCard(title = "Quraşdırma") {
            StatusRow(
                ok = listenerOk,
                title = "Bildiriş girişi",
                status = when {
                    !hasAccess -> "Verilməyib"
                    connected -> "İşləyir"
                    else -> "Qoşulmayıb"
                },
            ) {
                when {
                    !hasAccess -> Button(onClick = { SystemStatus.openNotificationAccessSettings(context) }) {
                        Text("İcazə ver")
                    }
                    !connected -> OutlinedButton(onClick = {
                        SystemStatus.forceReconnect(context)
                        tick++
                    }) { Text("Qoş") }
                    else -> {}
                }
            }
            if (!hasAccess) {
                HintText("«Məhdud ayar» çıxsa: Tətbiq məlumatı → ⋮ → «Məhdud ayarlara icazə ver».")
                TextButton(onClick = { SystemStatus.openAppDetails(context) }) { Text("Tətbiq məlumatı") }
            }
            HorizontalDivider()
            StatusRow(
                ok = notifyOk,
                title = "Bildiriş icazəsi",
                status = when {
                    !canNotify -> "Verilməyib"
                    !channelOk -> "Kanal söndürülüb"
                    else -> "Var"
                },
            ) {
                when {
                    !canNotify -> Button(onClick = askNotifications) { Text("İcazə ver") }
                    !channelOk -> Button(onClick = {
                        SystemStatus.openChannelSettings(context, AlertNotifier.channelId(loud))
                    }) { Text("Aç") }
                    else -> {}
                }
            }
            HorizontalDivider()
            StatusRow(
                ok = batteryOk,
                title = "Batareya",
                status = if (batteryOk) "Məhdudiyyət yoxdur" else "Sistem radarı yatıra bilər",
            ) {
                if (!batteryOk) {
                    Button(onClick = { SystemStatus.requestIgnoreBatteryOptimizations(context) }) { Text("Düzəlt") }
                }
            }
        }

        // ── Xəbər gəlmədi? ────────────────────────────────────────────
        ExpandableCard(title = "Xəbər gəlmədi? Səbəblər") {
            HintText("• Qrup WhatsApp-da səssizdədir (mute) və ya arxivdədir — WhatsApp bildiriş göstərmir.")
            HintText("• WhatsApp bildirişləri söndürülüb — söndürməyin, lazımsa «Səssiz» edin.")
            HintText("• O an WhatsApp ekranda açıq idi və ya WhatsApp Web aktiv idi.")
            HintText("• Xiaomi/Samsung-da «Avtostart» və «Fonda işləmə» icazəsi yoxdur.")
            HintText("Jurnalda radarın gördüyü hər mesaj və səbəbi yazılır.")
            OutlinedButton(onClick = onOpenJournal) { Text("Jurnala bax") }
        }
    }
}
