package az.novbe.radar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import az.novbe.radar.BuildConfig
import az.novbe.radar.core.Az
import az.novbe.radar.data.LogType
import az.novbe.radar.data.Prefs
import az.novbe.radar.data.Repo
import az.novbe.radar.radar.EvoFinder
import az.novbe.radar.radar.RadarService
import kotlin.math.roundToInt

private enum class TimeField { START, END, QUIET_FROM, QUIET_TO }

@Composable
fun SettingsScreen(checks: Checks, onSetup: () -> Unit) {
    val ctx = LocalContext.current
    val s by Prefs.flow.collectAsState()
    var picking by remember { mutableStateOf<TimeField?>(null) }
    var pickApp by remember { mutableStateOf(false) }
    var interval by remember(s.intervalMin) { mutableFloatStateOf(s.intervalMin.toFloat()) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        ScreenHeader("Ayarlar")

        SectionTitle("Radar")
        Panel {
            ToggleRow(
                "Radar işləsin",
                if (s.enabled) "Fonda yoxlayır" else "Dayandırılıb — heç bir yoxlama olmayacaq",
                s.enabled,
                { on ->
                    Prefs.update { it.copy(enabled = on) }
                    if (on) {
                        RadarService.ensureRunning(ctx)
                        RadarService.scheduleNext(ctx, initial = true)
                    } else {
                        RadarService.stop(ctx)
                    }
                    Repo.log(LogType.INFO, if (on) "Radar yandırıldı" else "Radar dayandırıldı")
                },
            )
            HorizontalDivider(color = RC.Line, modifier = Modifier.padding(vertical = 6.dp))
            Text("Yoxlama aralığı: hər ${interval.roundToInt()} dəqiqə", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = interval,
                onValueChange = { interval = it },
                valueRange = 2f..30f,
                steps = 27,
                onValueChangeFinished = {
                    val v = interval.roundToInt().coerceIn(2, 30)
                    Prefs.update { it.copy(intervalMin = v) }
                    RadarService.scheduleNext(ctx)
                },
                colors = SliderDefaults.colors(thumbColor = RC.Cyan, activeTrackColor = RC.Cyan, inactiveTrackColor = RC.Surface2),
            )
            Text(
                "Az aralıq = növbəni daha tez görürsən, amma batareya bir az çox gedir.",
                style = MaterialTheme.typography.bodySmall,
                color = RC.Muted,
            )
        }

        SectionTitle("Növbə saatları")
        Panel {
            ValueRow("Başlama", Az.hm(s.startMin)) { picking = TimeField.START }
            ValueRow("Bitmə", Az.hm(s.endMin)) { picking = TimeField.END }
            Text(
                "Yalnız dəqiq bu saatlarla başlayıb bitən növbələr bildirilir (məs. \"16:00 - 0:00\" bəli, \"8:00 - 16:00\" yox).",
                style = MaterialTheme.typography.bodySmall,
                color = RC.Muted,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
            HorizontalDivider(color = RC.Line, modifier = Modifier.padding(vertical = 6.dp))
            ToggleRow(
                "Yalnız boş günlərim",
                "My Schedule-da SCHEDULED olan günlərdəki növbələr bildirilmir. Bilinən günlər: ${Repo.knownDays()}",
                s.freeDaysOnly,
                { on -> Prefs.update { it.copy(freeDaysOnly = on) } },
            )
        }

        SectionTitle("Bildiriş")
        Panel {
            ToggleRow("Səs", null, s.sound, { on -> Prefs.update { it.copy(sound = on) } })
            ToggleRow("Vibrasiya", null, s.vibrate, { on -> Prefs.update { it.copy(vibrate = on) } })
            ToggleRow(
                "Alarm rejimi",
                "Səssiz rejimdə də zəngli saat kimi çalır (12 saniyə)",
                s.alarmMode,
                { on -> Prefs.update { it.copy(alarmMode = on) } },
            )
            TextButton(onClick = { Open.notifications(ctx) }) { Text("Telefonun bildiriş ayarları →") }
        }

        SectionTitle("Səssiz saatlar")
        Panel {
            ToggleRow(
                "Səssiz saatlar",
                "Bu vaxt bildiriş səssiz gəlir (siyahıya yenə düşür)",
                s.quietEnabled,
                { on -> Prefs.update { it.copy(quietEnabled = on) } },
            )
            if (s.quietEnabled) {
                ValueRow("Başlayır", Az.hm(s.quietFrom)) { picking = TimeField.QUIET_FROM }
                ValueRow("Bitir", Az.hm(s.quietTo)) { picking = TimeField.QUIET_TO }
                ToggleRow(
                    "Bu saatlarda yoxlama da etmə",
                    "Ekran gecə heç oyanmasın",
                    s.pauseInQuiet,
                    { on -> Prefs.update { it.copy(pauseInQuiet = on) } },
                )
            }
        }

        SectionTitle("Fonda yoxlama")
        Panel {
            ToggleRow(
                "Ekranı qaralt",
                "Ekran sönükkən yoxlayanda üstünə qara pərdə çəkilir ki, gecə işıq saçmasın. Pərdəyə toxunsan yoxlama dayanır.",
                s.darkOverlay,
                { on -> Prefs.update { it.copy(darkOverlay = on) } },
            )
            ToggleRow(
                "EvoApp-a baxanda oxu",
                "EvoApp-ı özün açanda görünən növbələr də yoxlanır (heç nəyə toxunmadan)",
                s.passiveRead,
                { on -> Prefs.update { it.copy(passiveRead = on) } },
            )
        }

        SectionTitle("EvoApp")
        Panel {
            ValueRow("Tətbiq", checks.evoLabel?.let { "$it" } ?: "seçilməyib") { pickApp = true }
            if (checks.evoPkg != null) {
                Text(checks.evoPkg, style = MaterialTheme.typography.bodySmall, color = RC.Muted)
            }
        }

        SectionTitle("Digər")
        Panel {
            ValueRow("Quraşdırma addımları", "aç") { onSetup() }
            ValueRow("Versiya", BuildConfig.VERSION_NAME) {}
        }
        Spacer(Modifier.height(28.dp))
    }

    picking?.let { field ->
        val (title, value) = when (field) {
            TimeField.START -> "Növbə başlayır" to s.startMin
            TimeField.END -> "Növbə bitir" to s.endMin
            TimeField.QUIET_FROM -> "Səssiz saatlar başlayır" to s.quietFrom
            TimeField.QUIET_TO -> "Səssiz saatlar bitir" to s.quietTo
        }
        TimeDialog(title, value, onDismiss = { picking = null }) { m ->
            Prefs.update {
                when (field) {
                    TimeField.START -> it.copy(startMin = m)
                    TimeField.END -> it.copy(endMin = m)
                    TimeField.QUIET_FROM -> it.copy(quietFrom = m)
                    TimeField.QUIET_TO -> it.copy(quietTo = m)
                }
            }
            picking = null
        }
    }

    if (pickApp) {
        AppPickerDialog(onDismiss = { pickApp = false }) { app ->
            Prefs.update { it.copy(evoPackage = app.pkg) }
            Repo.log(LogType.INFO, "EvoApp seçildi: ${app.label}")
            pickApp = false
        }
    }
}

@Composable
fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = RC.Cyan)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(title: String, minutes: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = minutes / 60, initialMinute = minutes % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("Seç") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ləğv et") } },
        containerColor = RC.Surface2,
    )
}

@Composable
fun AppPickerDialog(onDismiss: () -> Unit, onPick: (EvoFinder.App) -> Unit) {
    val ctx = LocalContext.current
    val apps = remember { EvoFinder.launchableApps(ctx).filter { it.pkg != ctx.packageName } }
    var q by remember { mutableStateOf("") }
    val shown = apps.filter { q.isBlank() || it.label.contains(q, true) || it.pkg.contains(q, true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("EvoApp-ı seç") },
        text = {
            Column {
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    placeholder = { Text("Axtar…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(shown, key = { it.pkg }) { a ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onPick(a) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        ) {
                            Text(a.label, style = MaterialTheme.typography.bodyLarge)
                            Text(a.pkg, style = MaterialTheme.typography.bodySmall, color = RC.Muted)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Bağla") } },
        containerColor = RC.Surface2,
    )
}
