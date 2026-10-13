package az.novbe.radar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import az.novbe.radar.core.Az
import az.novbe.radar.data.Prefs
import az.novbe.radar.data.Repo
import az.novbe.radar.data.Status
import az.novbe.radar.radar.RadarService
import az.novbe.radar.radar.TestChain
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(checks: Checks, onFix: () -> Unit, onShowFound: () -> Unit) {
    val ctx = LocalContext.current
    val st by Status.flow.collectAsState()
    val s by Prefs.flow.collectAsState()
    val found by Repo.found.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    var testSteps by remember { mutableStateOf<List<TestChain.Step>>(emptyList()) }
    var testRun by remember { mutableIntStateOf(0) }

    val activeShifts = found.count { it.present && !it.test }
    val a11yMissing = !checks.accessibility || !st.accessibilityOn
    val mode = when {
        !s.enabled -> RadarMode.OFF
        st.scanning -> RadarMode.SCANNING
        a11yMissing || !checks.essentialOk -> RadarMode.WARN
        st.lastError != null -> RadarMode.WARN
        else -> RadarMode.ACTIVE
    }
    val (pillText, pillColor) = when {
        !s.enabled -> "Radar dayandırılıb" to RC.Muted
        st.scanning -> "Yoxlanır: ${st.phase.ifBlank { "…" }}" to RC.Cyan
        a11yMissing || !checks.essentialOk -> "İcazə çatmır ⚠️" to RC.Amber
        st.lastError != null -> "Problem var ⚠️" to RC.Red
        else -> "Radar aktivdir ✓" to RC.Green
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        ScreenHeader(
            "Növbə Radarı",
            "${Az.hm(s.startMin)}–${Az.hm(s.endMin)} növbələri izlənir" + if (s.freeDaysOnly) " · yalnız boş günlər" else "",
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            RadarView(mode, activeShifts, Modifier.fillMaxWidth(0.78f))
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StatusPill(pillText, pillColor)
        }
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat(
                "Son yoxlama",
                if (st.lastScanAt > 0) ago(st.lastScanAt, now) else "hələ yox",
                Modifier.weight(1f),
                color = if (st.lastScanAt > 0 && !st.lastScanOk) RC.Red else RC.Text,
            )
            Stat(
                "Növbəti",
                when {
                    !s.enabled -> "—"
                    st.scanning -> "indi"
                    st.nextScanAt > 0 -> countdown(st.nextScanAt - now)
                    else -> "—"
                },
                Modifier.weight(1f),
                color = RC.Cyan,
            )
            Stat("Tapılan", activeShifts.toString(), Modifier.weight(1f), color = if (activeShifts > 0) RC.Amber else RC.Text)
        }
        if (st.lastScanAt > 0) {
            Text(
                "Son yoxlama: ${hhmm(st.lastScanAt)}" + if (st.totalScans > 0) " · bu açılışdan bəri ${st.totalScans} yoxlama" else "",
                style = MaterialTheme.typography.bodySmall,
                color = RC.Muted,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
        }

        val missing = checks.missing()
        AnimatedVisibility(missing.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Panel(Modifier.padding(top = 16.dp), border = RC.Amber.copy(alpha = 0.5f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null, tint = RC.Amber, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Çatışmayanlar", style = MaterialTheme.typography.titleMedium, color = RC.Amber)
                }
                Spacer(Modifier.height(8.dp))
                missing.forEach { m -> Text("• $m", style = MaterialTheme.typography.bodyMedium, color = RC.Text) }
                Spacer(Modifier.height(10.dp))
                Button(onClick = onFix, colors = ButtonDefaults.buttonColors(containerColor = RC.Amber, contentColor = RC.Bg)) {
                    Text("Quraşdırmaya keç")
                }
            }
        }

        AnimatedVisibility(st.lastError != null && !st.scanning, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Panel(Modifier.padding(top = 16.dp), border = RC.Red.copy(alpha = 0.5f)) {
                Text("⚠️ Radar işləmir", style = MaterialTheme.typography.titleMedium, color = RC.Red)
                Spacer(Modifier.height(4.dp))
                Text(st.lastError ?: "", style = MaterialTheme.typography.bodyMedium, color = RC.Text)
            }
        }

        if (checks.secureLock) {
            Panel(Modifier.padding(top = 16.dp), border = RC.Cyan.copy(alpha = 0.35f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, null, tint = RC.Cyan, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Telefonda PIN/naxış kilidi var", style = MaterialTheme.typography.titleMedium, color = RC.Cyan)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Ekran sönük və telefon kilidli olanda radar onu özü aça bilmir. Ona görə yoxlama yalnız telefon açıq olanda olur: " +
                        "EvoApp-a baxanda (avtomatik oxuyur), \"İndi yoxla\" düyməsi ilə (həm də bildirişdən) və ekran söndükdən dərhal sonra, " +
                        "telefon hələ kilidlənməmişkən. Hər ${s.intervalMin} dəqiqədən bir fonda yoxlama üçün kilid \"Sürüşdür\" və ya \"Yoxdur\" olmalıdır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RC.Muted,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { RadarService.scanNow(ctx, returnToApp = true) },
                enabled = !st.scanning && checks.accessibility,
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("İndi yoxla")
            }
            OutlinedButton(
                onClick = {
                    testSteps = TestChain.run(ctx)
                    testRun++
                },
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Test")
            }
        }
        Text(
            "\"İndi yoxla\" EvoApp-ı açıb oxuyacaq və sonra bura qayıdacaq. \"Test\" EvoApp-a toxunmadan saxta növbə ilə bütün zənciri yoxlayır və bildiriş göndərir.",
            style = MaterialTheme.typography.bodySmall,
            color = RC.Muted,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )

        AnimatedVisibility(testSteps.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Panel(Modifier.padding(top = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Test nəticəsi", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { testSteps = emptyList() }) { Text("Bağla") }
                }
                testSteps.forEachIndexed { i, step -> TestStepRow(step, i, testRun) }
                if (testSteps.isNotEmpty() && testSteps.all { it.ok }) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onShowFound) { Text("Tapılanlara bax →") }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun TestStepRow(step: TestChain.Step, index: Int, run: Int) {
    var visible by remember(run) { mutableStateOf(false) }
    LaunchedEffect(run) {
        delay(140L * index)
        visible = true
    }
    AnimatedVisibility(visible, enter = fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 6 }) {
        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
            Icon(
                if (step.ok) Icons.Filled.CheckCircle else Icons.Filled.Close,
                null,
                tint = if (step.ok) RC.Green else RC.Red,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(step.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = RC.Text)
                Text(step.detail, style = MaterialTheme.typography.bodySmall, color = if (step.ok) RC.Muted else RC.Red)
            }
        }
    }
}
