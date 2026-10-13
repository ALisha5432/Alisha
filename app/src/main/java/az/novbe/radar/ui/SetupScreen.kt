package az.novbe.radar.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import az.novbe.radar.data.LogType
import az.novbe.radar.data.Prefs
import az.novbe.radar.data.Repo
import az.novbe.radar.radar.RadarService

/** İlk açılışda addım-addım quraşdırma. Hər addım bir dəfə edilir; hazır olanlar ✓ olur. */
@Composable
fun SetupScreen(checks: Checks, canClose: Boolean, onClose: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val s by Prefs.flow.collectAsState()
    var pickApp by remember { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Open.notifications(ctx)
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (canClose) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = RC.Text) }
            }
            ScreenHeader("Quraşdırma", "Hər addımı bir dəfə et — hazır olanlar ✓ olacaq")
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            RadarView(if (checks.essentialOk) RadarMode.ACTIVE else RadarMode.WARN, 0, Modifier.size(120.dp))
        }

        var n = 0
        StepCard(
            ++n, "EvoApp", checks.evoPkg != null,
            okText = "Tapıldı: ${checks.evoLabel ?: checks.evoPkg}",
            body = "EvoApp telefonda avtomatik tapılmadı. Siyahıdan özün seç.",
        ) {
            Button(onClick = { pickApp = true }, modifier = Modifier.fillMaxWidth()) { Text("Siyahıdan seç") }
        }
        StepCard(
            ++n, "Bildirişlər", checks.notifications,
            okText = "İcazə var — növbə tapılanda səs və vibrasiya ilə xəbər verəcəyəm",
            body = "Yeni növbə tapılanda xəbər verə bilməyim üçün bildiriş icazəsi lazımdır.",
        ) {
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else Open.notifications(ctx)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("İcazə ver") }
        }
        StepCard(
            ++n, "Accessibility (oxuma xidməti)", checks.accessibility,
            okText = if (checks.a11yConnected) "Yandırılıb və qoşulub" else "Yandırılıb — qoşulur…",
            body = "Radar EvoApp-ın ekranını bu xidmətlə oxuyur (heç nəyə özü basmır).\n\n" +
                "1) \"Accessibility-ni aç\" → \"Yüklənmiş tətbiqlər\" → \"Növbə Radarı\" → yandır.\n" +
                "2) \"Məhdudlaşdırılmış ayar\" (Restricted setting) pəncərəsi çıxsa: \"Tətbiq məlumatı\"na keç → " +
                "sağ yuxarıda ⋮ → \"Məhdudlaşdırılmış ayarlara icazə ver\" (Allow restricted settings) → PIN ilə təsdiqlə.\n" +
                "3) Yenidən \"Accessibility-ni aç\" və Növbə Radarını yandır.",
        ) {
            Button(onClick = { Open.accessibility(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("Accessibility-ni aç") }
            OutlinedButton(onClick = { Open.appInfo(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("Tətbiq məlumatı (⋮ menyusu)") }
        }
        StepCard(
            ++n, "Dəqiq vaxt", checks.exactAlarm,
            okText = "Yoxlamalar vaxtında olacaq",
            body = "Hər ${s.intervalMin} dəqiqədən bir dəqiq vaxtında yoxlamaq üçün \"Alarm və xatırlatmalar\" icazəsi lazımdır.",
        ) {
            Button(onClick = { Open.exactAlarm(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("İcazə ver") }
        }
        StepCard(
            ++n, "Batareya", checks.battery,
            okText = "Batareya qənaəti radarı dayandırmayacaq",
            body = "Telefon radarı yuxuya verməsin deyə batareya optimallaşdırmasından çıxar: açılan pəncərədə \"İcazə ver\" seç." +
                if (checks.xiaomi) "\n\nXiaomi-də əlavə: Tətbiq məlumatı → \"Batareya qənaəti\" → \"Məhdudiyyət yoxdur\"." else "",
        ) {
            Button(onClick = { Open.battery(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("Batareyanı ayarla") }
            if (checks.xiaomi) {
                OutlinedButton(onClick = { Open.appInfo(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("Tətbiq məlumatı") }
            }
        }
        if (checks.xiaomi) {
            StepCard(
                ++n, "Xiaomi: Avtomatik başlama", checks.autostartConfirmed,
                okText = "Təsdiqlədin",
                body = "Telefon yenidən açılanda radar özü başlasın deyə \"Avtomatik başlama\" (Autostart) siyahısında Növbə Radarını yandır, sonra \"Etdim\"-ə bas.",
            ) {
                Button(onClick = { Open.xiaomiAutostart(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("Autostart-ı aç") }
                OutlinedButton(
                    onClick = { Prefs.update { it.copy(autostartConfirmed = true) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Etdim ✓") }
            }
            StepCard(
                ++n, "Xiaomi: digər icazələr", checks.miuiPermsConfirmed,
                okText = "Təsdiqlədin",
                body = "\"Digər icazələr\" bölməsində bunlara \"İcazə ver\":\n" +
                    "• Fonda işləyərkən yeni pəncərələr aç\n" +
                    "• Kilid ekranında göstər\n" +
                    "Bunlarsız radar ekranı oyadıb EvoApp-ı aça bilmir.",
            ) {
                Button(onClick = { Open.xiaomiOtherPerms(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("İcazələri aç") }
                OutlinedButton(
                    onClick = { Prefs.update { it.copy(miuiPermsConfirmed = true) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Etdim ✓") }
            }
        }
        StepCard(
            ++n, "Ekran kilidi", !checks.secureLock,
            okText = "Kilid \"Sürüşdür\" və ya \"Yoxdur\" — radar ekran sönükkən də yoxlaya biləcək",
            body = "Telefonda PIN/naxış var. Ekran sönükkən radar telefonu özü aça bilmir, ona görə yoxlama yalnız telefon açıq olanda olacaq: " +
                "EvoApp-a baxanda, \"İndi yoxla\" ilə və ekran söndükdən dərhal sonra (telefon hələ kilidlənməmişkən).\n\n" +
                "Fonda hər ${s.intervalMin} dəqiqədən bir yoxlama istəyirsənsə, kilidi \"Sürüşdür\" və ya \"Yoxdur\" et. Bu sənin seçimindir — radar hər iki halda işləyir.",
            optional = true,
        ) {
            OutlinedButton(onClick = { Open.lockScreen(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("Kilid ayarları") }
        }

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                Prefs.update { it.copy(setupDone = true, enabled = true) }
                Repo.log(LogType.INFO, "Quraşdırma tamamlandı")
                RadarService.ensureRunning(ctx)
                RadarService.scheduleNext(ctx, initial = true)
                onDone()
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (checks.essentialOk) RC.Green else RC.Surface2,
                contentColor = if (checks.essentialOk) RC.Bg else RC.Text,
            ),
        ) {
            Text(if (checks.essentialOk) "Hazırdır — radarı başlat" else "Davam et (qalanları sonra)")
        }
        if (!checks.essentialOk) {
            Text(
                "EvoApp, bildiriş, Accessibility və dəqiq vaxt olmadan radar düzgün işləməyəcək. Qalan addımları sonra Radar ekranından da tamamlaya bilərsən.",
                style = MaterialTheme.typography.bodySmall,
                color = RC.Muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
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
private fun StepCard(
    n: Int,
    title: String,
    ok: Boolean,
    okText: String,
    body: String,
    optional: Boolean = false,
    actions: @Composable ColumnScope.() -> Unit,
) {
    val border by animateColorAsState(if (ok) RC.Green.copy(alpha = 0.45f) else RC.Line, tween(400), label = "stepBorder")
    val dot by animateColorAsState(if (ok) RC.Green else RC.Surface2, tween(400), label = "stepDot")
    Panel(Modifier.padding(top = 12.dp), border = border) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(dot), contentAlignment = Alignment.Center) {
                if (ok) Icon(Icons.Filled.Check, null, tint = RC.Bg, modifier = Modifier.size(18.dp))
                else Text("$n", style = MaterialTheme.typography.labelLarge, color = RC.Text)
            }
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (optional && !ok) Tag("seçim", RC.Muted)
        }
        AnimatedVisibility(ok, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Text(okText, style = MaterialTheme.typography.bodySmall, color = RC.Green, modifier = Modifier.padding(top = 6.dp, start = 42.dp))
        }
        AnimatedVisibility(!ok, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(body, style = MaterialTheme.typography.bodyMedium, color = RC.Muted)
                actions()
            }
        }
    }
}
