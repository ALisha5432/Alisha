package az.novbe.radar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.novbe.radar.core.Az
import az.novbe.radar.core.Tracked
import az.novbe.radar.data.Prefs
import az.novbe.radar.data.Repo

@Composable
fun FoundScreen() {
    val found by Repo.found.collectAsState()
    val s by Prefs.flow.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    val list = remember(found) {
        found.sortedWith(compareByDescending<Tracked> { it.present }.thenBy { it.date }.thenBy { it.time }.thenBy { it.table })
    }
    val active = found.count { it.present && !it.test }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        ScreenHeader("Tapılanlar", if (active > 0) "$active uyğun növbə ortadadır" else "${Az.hm(s.startMin)}–${Az.hm(s.endMin)} növbələri") {
            if (found.any { it.test }) {
                TextButton(onClick = { Repo.clearTests() }) { Text("Testləri sil") }
            }
            if (found.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.Delete, "Hamısını sil", tint = RC.Muted) }
            }
        }
        if (list.isEmpty()) {
            EmptyFound(s.startMin, s.endMin)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.key }) { t ->
                    ShiftCard(t, Modifier.animateItem())
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Siyahını təmizlə?") },
            text = { Text("Tapılan növbələr siyahıdan silinəcək. Hələ ortada olanlar növbəti yoxlamada yenidən bildiriləcək.") },
            confirmButton = {
                TextButton(onClick = {
                    Repo.clearFound()
                    confirmClear = false
                }) { Text("Sil", color = RC.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Ləğv et") } },
            containerColor = RC.Surface2,
        )
    }
}

@Composable
private fun EmptyFound(start: Int, end: Int) {
    Column(
        Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(140.dp)) { RadarView(RadarMode.ACTIVE, 0) }
        Spacer(Modifier.height(18.dp))
        Text("Hələ uyğun növbə yoxdur", style = MaterialTheme.typography.titleMedium, color = RC.Text)
        Spacer(Modifier.height(6.dp))
        Text(
            "${Az.hm(start)}–${Az.hm(end)} növbəsi ortaya atılan kimi burada görünəcək və bildiriş gələcək.",
            style = MaterialTheme.typography.bodyMedium,
            color = RC.Muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ShiftCard(t: Tracked, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val date = t.localDate
    val shape = RoundedCornerShape(20.dp)
    val accent = when {
        t.test -> RC.Cyan
        t.present -> RC.Amber
        else -> RC.Muted
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(RC.Surface)
            .border(1.dp, accent.copy(alpha = if (t.present) 0.45f else 0.2f), shape)
            .alpha(if (t.present) 1f else 0.6f)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Tarix nişanı
            Column(
                Modifier
                    .width(62.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.12f))
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(date?.dayOfMonth?.toString() ?: "?", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = accent)
                Text(date?.let { Az.monthShort(it.monthValue) } ?: "", style = MaterialTheme.typography.labelMedium, color = accent)
                Text(
                    date?.let { Az.dow(it.dayOfWeek).take(3) } ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = RC.Muted,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    t.table.ifBlank { "Masa bilinmir" },
                    style = MaterialTheme.typography.titleMedium,
                    color = RC.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (t.present) null else TextDecoration.LineThrough,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    listOf(t.time, t.duration, t.role).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = RC.Cyan,
                )
                Text(
                    "ortadadır" + if (t.initials.isNotBlank()) " (${t.initials})" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = RC.Muted,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (t.test) Tag("TEST", RC.Cyan)
            if (!t.present) Tag("götürülüb", RC.Muted)
            if (t.notifyCount > 1) Tag("yenidən atıldı ×${t.notifyCount}", RC.Amber)
            if (t.passive) Tag("baxarkən tapıldı", RC.Green)
            Spacer(Modifier.weight(1f))
            Text("tapıldı ${hhmm(t.firstSeen)} · ${dayMonth(t.firstSeen)}", style = MaterialTheme.typography.bodySmall, color = RC.Muted)
        }
        if (t.present) {
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { Open.evoApp(ctx) },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = RC.Bg),
            ) {
                Icon(Icons.Filled.Star, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("EvoApp-ı aç")
            }
        }
    }
}

@Composable
fun Tag(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}
