package com.wpradar.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wpradar.R
import com.wpradar.core.Verdict
import com.wpradar.data.JournalEntry
import com.wpradar.ui.theme.RadarPalette
import com.wpradar.ui.theme.RadarTheme
import com.wpradar.ui.theme.tabular

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JournalScreen(entries: List<JournalEntry>, contentPadding: PaddingValues) {
    if (entries.isEmpty()) {
        EmptyState(
            title = "Hələ WhatsApp mesajı görünməyib",
            body = "Mesaj gəlib, amma burada yoxdursa — WhatsApp onun üçün bildiriş göstərməyib " +
                "(qrup səssizdə/arxivdə, ya da WhatsApp bildirişləri söndürülüb).",
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "intro", contentType = 2) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_nav_radar),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                HintText("Radarın gördüyü son WhatsApp mesajları və nəticə.")
            }
        }
        items(rows, key = { it.key }, contentType = { if (it.dayLabel != null) 0 else 1 }) { row ->
            val label = row.dayLabel
            val entry = row.item
            if (label != null) {
                DayHeader(label, Modifier.animateItem())
            } else if (entry != null) {
                JournalItem(entry, Modifier.animateItem())
            }
        }
    }
}

/** Qərarın görünüşü: rəng tonu, ikon və mətn. */
private data class VerdictLook(val label: String, @DrawableRes val icon: Int, val tone: Color, val background: Color)

private fun verdictLook(verdict: Verdict, keyword: String?, p: RadarPalette): VerdictLook = when (verdict) {
    Verdict.MATCHED -> VerdictLook("Xəbər verildi · «${keyword.orEmpty()}»", R.drawable.ic_check, p.success, p.successContainer)
    Verdict.DUPLICATE -> VerdictLook("Təkrar — artıq xəbər verilib", R.drawable.ic_repeat, p.onNeutral, p.neutralContainer)
    Verdict.CANNOT_NOTIFY -> VerdictLook("Tapıldı, amma bildiriş icazəsi yoxdur", R.drawable.ic_alert, p.danger, p.dangerContainer)
    Verdict.NO_KEYWORD -> VerdictLook("Açar söz yoxdur", R.drawable.ic_search, p.onNeutral, p.neutralContainer)
    Verdict.CHAT_NOT_WATCHED -> VerdictLook("Qrup siyahıda deyil", R.drawable.ic_users, p.onNeutral, p.neutralContainer)
    Verdict.PRIVATE_SKIPPED -> VerdictLook("Şəxsi çat — yoxlanmır", R.drawable.ic_person, p.onNeutral, p.neutralContainer)
    Verdict.DISABLED -> VerdictLook("Radar söndürülüb", R.drawable.ic_power, p.danger, p.dangerContainer)
    Verdict.NO_KEYWORDS -> VerdictLook("Açar söz əlavə edilməyib", R.drawable.ic_key, p.warning, p.warningContainer)
    Verdict.UNREADABLE -> VerdictLook("Mesaj mətni oxunmadı", R.drawable.ic_alert, p.warning, p.warningContainer)
}

/** Zaman xətti üzərində bir qeyd: solda rəngli nöqtə, sağda kart. */
@Composable
private fun JournalItem(entry: JournalEntry, modifier: Modifier) {
    val palette = RadarTheme.palette
    val colors = MaterialTheme.colorScheme
    val look = verdictLook(entry.verdict, entry.keyword, palette)
    val line = colors.outlineVariant
    val matched = entry.verdict == Verdict.MATCHED
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        // Zaman xətti
        Box(
            Modifier
                .width(22.dp)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2f
                    // Qonşu qeydlərlə birləşsin deyə xətt aralıq boşluğa da uzanır.
                    drawLine(line, Offset(x, -4.dp.toPx()), Offset(x, size.height + 4.dp.toPx()), strokeWidth = 2.dp.toPx())
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                Modifier
                    .padding(top = 18.dp)
                    .size(if (matched) 14.dp else 10.dp)
                    .clip(CircleShape)
                    .background(look.tone),
            )
        }
        Spacer(Modifier.width(8.dp))
        RadarCard(
            Modifier.weight(1f),
            border = if (matched) palette.success.copy(alpha = 0.45f) else palette.cardBorder,
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (entry.isGroup) entry.group ?: "Qrup" else "Şəxsi: ${entry.sender ?: "?"}",
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
                if (entry.isGroup && entry.sender != null) {
                    Text(
                        entry.sender,
                        style = MaterialTheme.typography.labelMedium,
                        color = avatarColor(entry.sender),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (entry.text != null) {
                    Text(
                        entry.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurface.copy(alpha = 0.85f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(look.background)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(look.icon), contentDescription = null, tint = look.tone, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        look.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = look.tone,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
