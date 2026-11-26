package com.wpradar.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wpradar.R
import com.wpradar.data.HistoryEntry
import com.wpradar.service.WaListenerService
import com.wpradar.system.SystemStatus
import com.wpradar.ui.theme.RadarTheme
import com.wpradar.ui.theme.tabular

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(entries: List<HistoryEntry>, contentPadding: PaddingValues) {
    val context = LocalContext.current
    if (entries.isEmpty()) {
        EmptyState(
            title = "Hələ heç nə tapılmayıb",
            body = "Açar söz olan mesaj gələndə burada görünəcək.",
            contentPadding = contentPadding,
        )
        return
    }
    val rows = remember(entries) { withDayHeaders(entries, time = { it.time }, key = { it.id }) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 2.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(rows, key = { it.key }, contentType = { if (it.dayLabel != null) 0 else 1 }) { row ->
            val label = row.dayLabel
            val entry = row.item
            if (label != null) {
                DayHeader(label, Modifier.animateItem())
            } else if (entry != null) {
                HistoryItem(entry, Modifier.animateItem()) {
                    SystemStatus.openWhatsApp(context, entry.packageName)
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(entry: HistoryEntry, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val palette = RadarTheme.palette
    val chat = if (entry.isGroup) entry.group ?: "Qrup" else "Şəxsi: ${entry.sender ?: "?"}"
    val text = remember(entry.id, palette.highlight, colors.onSurface) {
        highlightKeyword(entry.text, entry.keyword, palette.highlight, colors.onSurface)
    }
    RadarCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(16.dp)) {
            Avatar(name = if (entry.isGroup) entry.group ?: "Qrup" else entry.sender ?: "?")
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        chat,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        clockTime(entry.time),
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = colors.onSurfaceVariant,
                    )
                }
                if (entry.isGroup) {
                    val sender = entry.sender ?: "Naməlum"
                    Text(
                        sender,
                        style = MaterialTheme.typography.labelLarge,
                        color = avatarColor(sender),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface.copy(alpha = 0.9f),
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(palette.warningContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_key),
                            contentDescription = null,
                            tint = palette.warning,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            entry.keyword,
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.warning,
                            maxLines = 1,
                        )
                    }
                    if (entry.packageName == WaListenerService.WHATSAPP_BUSINESS) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "WA Business",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(palette.neutralContainer)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Aç", style = MaterialTheme.typography.labelMedium, color = colors.primary)
                    Icon(
                        painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
