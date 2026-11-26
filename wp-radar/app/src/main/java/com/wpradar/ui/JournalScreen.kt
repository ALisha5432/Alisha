package com.wpradar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wpradar.core.Verdict
import com.wpradar.data.JournalEntry

@Composable
fun JournalScreen(entries: List<JournalEntry>) {
    if (entries.isEmpty()) {
        EmptyState(
            "Hələ WhatsApp mesajı görünməyib.\n\n" +
                "Mesaj gəlib, amma burada yoxdursa — WhatsApp onun üçün bildiriş göstərməyib " +
                "(qrup səssizdə/arxivdə, ya da WhatsApp bildirişləri söndürülüb).",
        )
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { HintText("Radarın gördüyü son WhatsApp mesajları və nəticə.") }
        items(entries, key = { it.id }) { entry -> JournalItem(entry) }
    }
}

@Composable
private fun JournalItem(entry: JournalEntry) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (entry.isGroup) entry.group ?: "Qrup" else "Şəxsi: ${entry.sender ?: "?"}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatTime(entry.time),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entry.isGroup && entry.sender != null) {
                Text(entry.sender, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (entry.text != null) {
                Text(entry.text, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            VerdictBadge(entry.verdict, entry.keyword)
        }
    }
}

@Composable
private fun VerdictBadge(verdict: Verdict, keyword: String?) {
    val colors = MaterialTheme.colorScheme
    val (label, background: Color) = when (verdict) {
        Verdict.MATCHED -> "✅ Xəbər verildi · «${keyword.orEmpty()}»" to colors.primaryContainer
        Verdict.DUPLICATE -> "↺ Təkrar — artıq xəbər verilib" to colors.surfaceVariant
        Verdict.CANNOT_NOTIFY -> "⚠ Tapıldı, amma bildiriş icazəsi yoxdur" to colors.errorContainer
        Verdict.NO_KEYWORD -> "Açar söz yoxdur" to colors.surfaceVariant
        Verdict.CHAT_NOT_WATCHED -> "Qrup siyahıda deyil" to colors.surfaceVariant
        Verdict.PRIVATE_SKIPPED -> "Şəxsi çat — yoxlanmır" to colors.surfaceVariant
        Verdict.DISABLED -> "⚠ Radar söndürülüb" to colors.errorContainer
        Verdict.NO_KEYWORDS -> "⚠ Açar söz əlavə edilməyib" to colors.errorContainer
        Verdict.UNREADABLE -> "⚠ Mesaj mətni oxunmadı" to colors.errorContainer
    }
    Surface(color = background, shape = MaterialTheme.shapes.small) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
