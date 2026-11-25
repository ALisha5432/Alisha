package com.wpradar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wpradar.BuildConfig
import com.wpradar.WpRadarApp
import com.wpradar.core.TextNormalizer
import com.wpradar.notify.AlertNotifier
import com.wpradar.system.SystemStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(app: WpRadarApp) {
    val context = LocalContext.current
    val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val s = settings ?: return
    val repo = app.settings
    val scope = app.appScope

    val suggestions = remember(s.groups, s.recentGroups) {
        val added = s.groups.map { TextNormalizer.normalize(it, false) }.toSet()
        s.recentGroups.filter { TextNormalizer.normalize(it, false) !in added }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(
            title = "İzlənən qruplar",
            subtitle = if (s.groups.isEmpty()) {
                "Siyahı boşdur — BÜTÜN qruplarda axtarılır."
            } else {
                "Yalnız bu qruplarda axtarılır. Qrup adının bir hissəsini yazmaq kifayətdir."
            },
        ) {
            AddItemField(label = "Qrup adı") { name -> scope.launch { repo.addGroup(name) } }
            s.groups.forEach { group ->
                RemovableItem(group) { scope.launch { repo.removeGroup(group) } }
            }
            if (suggestions.isNotEmpty()) {
                Text("WhatsApp-da görülən qruplar — toxunub əlavə edin:", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    suggestions.forEach { group ->
                        AssistChip(
                            onClick = { scope.launch { repo.addGroup(group) } },
                            label = { Text(group, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
                            },
                        )
                    }
                }
            } else if (s.groups.isEmpty()) {
                HintText("İpucu: radar WhatsApp qrup bildirişlərini görən kimi onların adları burada çıxacaq.")
            }
        }

        SectionCard(
            title = "Açar sözlər",
            subtitle = "Böyük/kiçik hərf fərq etmir. Bir neçəsini vergüllə birdən yaza bilərsiniz.",
        ) {
            AddItemField(label = "Açar söz") { input -> scope.launch { repo.addKeywords(input) } }
            if (s.keywords.isEmpty()) {
                Text(
                    "Hələ açar söz yoxdur — radar heç nə tapmayacaq.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            s.keywords.forEach { word ->
                RemovableItem(word) { scope.launch { repo.removeKeyword(word) } }
            }
        }

        SectionCard(title = "Seçimlər") {
            SwitchRow(
                title = "Hərf fərqlərinə baxma",
                subtitle = "ə=e, ı=i, ş=s, ç=c, ğ=g, ö=o, ü=u. Məs: «Gəncə» açar sözü «GENCE» yazılışını da tapır. " +
                    "Söndürsəniz, dəqiq Azərbaycan qaydası işləyir (İ/i və I/ı ayrıdır).",
                checked = s.foldDiacritics,
            ) { value -> scope.launch { repo.setFoldDiacritics(value) } }
            SwitchRow(
                title = "Şəxsi çatlarda da axtar",
                subtitle = "Qrup olmayan yazışmalar da yoxlanılsın (qrup filtri onlara tətbiq olunmur).",
                checked = s.includePrivateChats,
            ) { value -> scope.launch { repo.setIncludePrivateChats(value) } }
            SwitchRow(
                title = "Səssiz rejimdə də səslə",
                subtitle = "Xəbərdarlıq alarm səsi kimi çalır — telefon səssiz/vibrasiyada olsa belə.",
                checked = s.loudMode,
            ) { value -> scope.launch { repo.setLoudMode(value) } }
            OutlinedButton(onClick = {
                SystemStatus.openChannelSettings(context, AlertNotifier.channelId(s.loudMode))
            }) { Text("Səs və vibrasiya ayarları") }
        }

        Text(
            "WP Radar ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
