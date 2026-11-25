package com.wpradar.ui

import android.Manifest
import android.os.Build
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.wpradar.notify.AlertNotifier
import com.wpradar.service.ListenerStatus
import com.wpradar.system.SystemStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(app: WpRadarApp, onOpenSettings: () -> Unit) {
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
    val allGood = s.enabled && listenerOk && notifyOk && s.keywords.isNotEmpty()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Ümumi vəziyyət ────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (allGood) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
            ),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📡", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                !s.enabled -> "Radar dayandırılıb"
                                allGood -> "Radar işləyir"
                                else -> "Quraşdırma tamamlanmayıb"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (s.enabled) "Açar söz gələn kimi səsli xəbər veriləcək" else "Heç bir xəbərdarlıq göndərilmir",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = s.enabled,
                        onCheckedChange = { value -> app.appScope.launch { app.settings.setEnabled(value) } },
                    )
                }
                Text(
                    "Qruplar: ${if (s.groups.isEmpty()) "hamısı" else s.groups.size.toString()}   ·   " +
                        "Açar sözlər: ${s.keywords.size}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Son WhatsApp bildirişi: ${if (lastEvent == 0L) "—" else formatTime(lastEvent)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (s.keywords.isEmpty()) {
                    Button(onClick = onOpenSettings) { Text("Açar söz əlavə et") }
                }
            }
        }

        // ── 1. Bildiriş girişi ────────────────────────────────────────
        StepCard(
            title = "1. Bildiriş girişi",
            ok = listenerOk,
            status = when {
                !hasAccess -> "İcazə verilməyib"
                connected -> "Verilib · dinləyici qoşulub"
                else -> "Verilib, amma dinləyici qoşulmayıb"
            },
        ) {
            HintText("WhatsApp bildirişlərini oxumaq üçün lazımdır. Açılan siyahıda «WP Radar»-ı aktiv edin.")
            if (!hasAccess) {
                HintText(
                    "Android 13+: «Məhdudlaşdırılmış ayar» yazısı çıxsa → «Tətbiq məlumatı» düyməsi → " +
                        "sağ yuxarıda ⋮ → «Məhdud ayarlara icazə verin». Sonra yenidən «İcazə ver»-ə basın.",
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { SystemStatus.openNotificationAccessSettings(context) }) {
                    Text(if (hasAccess) "Ayarlar" else "İcazə ver")
                }
                if (!hasAccess) {
                    OutlinedButton(onClick = { SystemStatus.openAppDetails(context) }) { Text("Tətbiq məlumatı") }
                } else {
                    OutlinedButton(onClick = {
                        SystemStatus.forceReconnect(context)
                        tick++
                    }) { Text("Yenidən qoş") }
                }
            }
        }

        // ── 2. Bildiriş göndərmə icazəsi ──────────────────────────────
        StepCard(
            title = "2. Bildiriş icazəsi",
            ok = notifyOk,
            status = when {
                !canNotify -> "WP Radar bildiriş göstərə bilmir"
                !channelOk -> "Xəbərdarlıq kanalı söndürülüb"
                else -> "Verilib"
            },
        ) {
            HintText("Açar söz tapılanda sizə ayrıca, səsli bildiriş göndərmək üçün.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!canNotify) {
                    Button(onClick = askNotifications) { Text("İcazə ver") }
                } else if (!channelOk) {
                    Button(onClick = {
                        SystemStatus.openChannelSettings(context, AlertNotifier.channelId(loud))
                    }) { Text("Kanalı aç") }
                }
                OutlinedButton(onClick = { SystemStatus.openAppNotificationSettings(context) }) {
                    Text("Bildiriş ayarları")
                }
            }
        }

        // ── 3. Batareya ───────────────────────────────────────────────
        StepCard(
            title = "3. Batareya optimizasiyası",
            ok = batteryOk,
            status = if (batteryOk) "Məhdudiyyət yoxdur" else "Sistem radarı fonda dayandıra bilər",
        ) {
            HintText(
                "Telefon radarı yatırmasın deyə «Məhdudiyyətsiz / Optimallaşdırma» siyahısından çıxarın. " +
                    "Xiaomi, Huawei, Oppo-da «Avtostart»-a da icazə verin.",
            )
            if (!batteryOk) {
                Button(onClick = { SystemStatus.requestIgnoreBatteryOptimizations(context) }) {
                    Text("Məhdudiyyəti götür")
                }
            }
        }

        // ── Yoxlama ───────────────────────────────────────────────────
        SectionCard(title = "Yoxlama", subtitle = "Səsi və vibrasiyanı indi eşidin.") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (SystemStatus.canPostNotifications(context)) {
                        AlertNotifier.showTest(context, loud)
                    } else {
                        askNotifications()
                    }
                }) { Text("Test bildirişi") }
                OutlinedButton(onClick = {
                    SystemStatus.openChannelSettings(context, AlertNotifier.channelId(loud))
                }) { Text("Səs ayarları") }
            }
        }

        // ── Məsləhətlər ───────────────────────────────────────────────
        SectionCard(title = "Vacib məsləhətlər") {
            HintText(
                "• WhatsApp-da qrupu «Səssiz» (mute) etməyin — onda WhatsApp bildiriş yaratmır və radar onu görmür. " +
                    "Qrupun səsini söndürmək istəsəniz: telefon Ayarları → Tətbiqlər → WhatsApp → Bildirişlər → " +
                    "«Qrup bildirişləri» kanalını səssiz edin.",
            )
            HintText("• WhatsApp → Ayarlar → Bildirişlər bölməsində bildiriş önizləməsi açıq qalsın.")
            HintText("• Söhbət ekranda açıq olanda WhatsApp bildiriş göstərmir — radar da o anda susur.")
        }
    }
}
