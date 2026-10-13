package az.novbe.radar.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import az.novbe.radar.core.NodeSig
import az.novbe.radar.data.LogType
import az.novbe.radar.data.Prefs
import az.novbe.radar.data.Repo
import az.novbe.radar.data.Status
import az.novbe.radar.radar.LearnStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun LearnScreen() {
    val ctx = LocalContext.current
    val s by Prefs.flow.collectAsState()
    val st by Status.flow.collectAsState()
    var count by remember { mutableIntStateOf(0) }
    var sizeKb by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            val c = withContext(Dispatchers.IO) { LearnStore.count(ctx) to LearnStore.sizeKb(ctx) }
            count = c.first
            sizeKb = c.second
            delay(1500)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        ScreenHeader("Öyrənmə rejimi", "EvoApp-ın quruluşunu yaz və mənə göndər")

        Panel {
            ToggleRow(
                "Öyrənmə rejimi",
                if (s.learnMode) "Yazılır — indi EvoApp-a keç" else "Söndürülüb",
                s.learnMode,
                { on ->
                    Prefs.update { it.copy(learnMode = on) }
                    Repo.log(LogType.INFO, if (on) "Öyrənmə rejimi açıldı" else "Öyrənmə rejimi söndürüldü")
                },
            )
            if (!st.accessibilityOn) {
                Text(
                    "⚠️ Accessibility xidməti qoşulmayıb — öyrənmə işləməyəcək. Əvvəlcə quraşdırmanı tamamla.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RC.Amber,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Yazılan ekran", count.toString(), Modifier.weight(1f), color = RC.Cyan)
                Stat("Həcm", "$sizeKb KB", Modifier.weight(1f))
            }
        }

        SectionTitle("Necə istifadə etmək")
        Panel {
            Steps(
                listOf(
                    "Yuxarıdakı düyməni aç.",
                    "EvoApp-ı aç: \"My Schedule\" ekranına bax, sonra \"Available Shifts\"-ə keç.",
                    "Siyahını yavaş-yavaş sona qədər aşağı, sonra yuxarı sürüşdür. Növbəti aya keç və geri qayıt.",
                    "Bura qayıt, rejimi söndür və \"Paylaş\"-a bas. Mətni mənə (Claude-a) göndər — oxumanı sənin telefonuna görə dəqiqləşdirəcəyəm.",
                ),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Bu rejimdə radar heç nəyə toxunmur — yalnız ekrandakı mətnləri, id-ləri və yerləşməni yazır.",
                style = MaterialTheme.typography.bodySmall,
                color = RC.Muted,
            )
        }

        SectionTitle("Göndər")
        Panel {
            Button(
                onClick = { shareTextDump(ctx) },
                enabled = count > 0,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Paylaş (mətn kimi)")
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { shareFileDump(ctx) }, enabled = count > 0, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Send, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Fayl kimi")
                }
                OutlinedButton(onClick = { copyDump(ctx) }, enabled = count > 0, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Kopyala")
                }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = {
                LearnStore.clear(ctx)
                count = 0
                sizeKb = 0
                Toast.makeText(ctx, "Öyrənmə yazıları silindi", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Filled.Delete, null, tint = RC.Muted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Yazıları sil", color = RC.Muted)
            }
            Text(
                "Diqqət: yazılarda iş yoldaşlarının inisialları və masa adları olur. Yalnız mənə göndər, ictimai yerdə paylaşma.",
                style = MaterialTheme.typography.bodySmall,
                color = RC.Amber,
            )
        }

        val learned = NodeSig.parse(s.learnedNav)
        if (learned != null) {
            SectionTitle("Öyrənilmiş keçid")
            Panel {
                Text(
                    "\"Available Shifts\"-ə keçid: ${learned.text ?: learned.desc ?: learned.viewId ?: "?"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { Prefs.update { it.copy(learnedNav = null) } }) { Text("Unut") }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
fun Steps(items: List<String>) {
    items.forEachIndexed { i, t ->
        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
            Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, color = RC.Cyan, modifier = Modifier.width(22.dp))
            Text(t, style = MaterialTheme.typography.bodyMedium, color = RC.Text)
        }
    }
}

private fun shareTextDump(ctx: Context) {
    Open.shareText(ctx, "EvoApp öyrənmə dump", LearnStore.exportTail(ctx))
}

private fun shareFileDump(ctx: Context) {
    runCatching {
        val f = LearnStore.exportFile(ctx)
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "EvoApp öyrənmə dump")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Fayl kimi paylaş").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(ctx, "Paylaşmaq alınmadı: ${it.message}", Toast.LENGTH_LONG).show()
    }
}

private fun copyDump(ctx: Context) {
    val cm = ctx.getSystemService(ClipboardManager::class.java) ?: return
    runCatching {
        cm.setPrimaryClip(ClipData.newPlainText("EvoApp dump", LearnStore.exportTail(ctx, 90_000)))
        Toast.makeText(ctx, "Kopyalandı — indi mesaja yapışdır", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(ctx, "Kopyalamaq alınmadı — \"Paylaş\"-dan istifadə et", Toast.LENGTH_LONG).show()
    }
}
