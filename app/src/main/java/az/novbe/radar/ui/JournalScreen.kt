package az.novbe.radar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import az.novbe.radar.data.LogEntry
import az.novbe.radar.data.LogType
import az.novbe.radar.data.Repo

private fun LogType.color(): Color = when (this) {
    LogType.OK -> RC.Green
    LogType.FOUND -> RC.Amber
    LogType.PASSIVE -> RC.Green
    LogType.SKIP -> RC.Muted
    LogType.ERROR -> RC.Red
    LogType.INFO -> RC.Cyan
    LogType.TEST -> RC.Cyan
}

private fun LogType.label(): String = when (this) {
    LogType.OK -> "skan"
    LogType.FOUND -> "TAPILDI"
    LogType.PASSIVE -> "baxarkən"
    LogType.SKIP -> "ötürüldü"
    LogType.ERROR -> "xəta"
    LogType.INFO -> "məlumat"
    LogType.TEST -> "test"
}

@Composable
fun JournalScreen() {
    val ctx = LocalContext.current
    val journal by Repo.journal.collectAsState()
    var onlyImportant by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val list = remember(journal, onlyImportant) {
        journal.asReversed().filter { !onlyImportant || it.type != LogType.SKIP }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        ScreenHeader("Jurnal", "Hər yoxlamanın nəticəsi və xətalar") {
            IconButton(onClick = {
                val text = journal.takeLast(300).joinToString("\n") { e ->
                    "${dayMonth(e.time)} ${hhmmss(e.time)} [${e.type.label()}] ${e.msg}" + if (e.count > 1) " (×${e.count})" else ""
                }
                Open.shareText(ctx, "Növbə Radarı jurnalı", text.ifBlank { "Jurnal boşdur" })
            }) { Icon(Icons.Filled.Share, "Paylaş", tint = RC.Muted) }
            IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.Delete, "Təmizlə", tint = RC.Muted) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !onlyImportant,
                onClick = { onlyImportant = false },
                label = { Text("Hamısı") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = RC.Surface2, selectedLabelColor = RC.Cyan),
            )
            FilterChip(
                selected = onlyImportant,
                onClick = { onlyImportant = true },
                label = { Text("Ötürülənlərsiz") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = RC.Surface2, selectedLabelColor = RC.Cyan),
            )
        }
        if (list.isEmpty()) {
            Text(
                "Hələ qeyd yoxdur. Radar ilk yoxlamanı edəndə burada görünəcək.",
                style = MaterialTheme.typography.bodyMedium,
                color = RC.Muted,
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                items(list, key = { it.id }) { e -> LogRow(e, Modifier.animateItem()) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Jurnalı təmizlə?") },
            confirmButton = {
                TextButton(onClick = {
                    Repo.clearJournal()
                    confirmClear = false
                }) { Text("Təmizlə", color = RC.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Ləğv et") } },
            containerColor = RC.Surface2,
        )
    }
}

@Composable
private fun LogRow(e: LogEntry, modifier: Modifier = Modifier) {
    val c = e.type.color()
    Row(modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.msg,
                style = MaterialTheme.typography.bodyMedium,
                color = if (e.type == LogType.SKIP) RC.Muted else RC.Text,
            )
            Text(
                "${hhmmss(e.time)} · ${dayMonth(e.time)} · ${e.type.label()}" + if (e.count > 1) " · ×${e.count}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = c.copy(alpha = 0.8f),
            )
        }
    }
}
