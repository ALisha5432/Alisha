package com.wpradar.ui

import android.os.Build
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/** Son silinən element — "geri qaytar" üçün. */
private data class Removed(val isKeyword: Boolean, val value: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(app: WpRadarApp) {
    val context = LocalContext.current
    val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    var removed by remember { mutableStateOf<Removed?>(null) }
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
        SectionCard(title = "Açar sözlər", subtitle = "Bir neçəsini vergüllə yazın: ortada, təcili, iclas") {
            AddItemField(label = "Açar söz") { input -> scope.launch { repo.addKeywords(input) } }
            if (s.keywords.isEmpty()) {
                Text(
                    "Açar söz yoxdur — radar heç nə tapmayacaq.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            RemovableChips(s.keywords) { word ->
                removed = Removed(isKeyword = true, value = word)
                scope.launch { repo.removeKeyword(word) }
            }
            removed?.takeIf { it.isKeyword }?.let { r ->
                TextButton(onClick = {
                    removed = null
                    scope.launch { repo.addKeywords(r.value) }
                }) { Text("↶ «${r.value}» geri qaytar") }
            }
        }

        SectionCard(
            title = "Qruplar",
            subtitle = if (s.groups.isEmpty()) {
                "Boşdur — bütün qruplar izlənir."
            } else {
                "Yalnız bu qruplar. Adın bir hissəsi kifayətdir."
            },
        ) {
            AddItemField(label = "Qrup adı") { name -> scope.launch { repo.addGroup(name) } }
            RemovableChips(s.groups) { group ->
                removed = Removed(isKeyword = false, value = group)
                scope.launch { repo.removeGroup(group) }
            }
            removed?.takeIf { !it.isKeyword }?.let { r ->
                TextButton(onClick = {
                    removed = null
                    scope.launch { repo.addGroup(r.value) }
                }) { Text("↶ «${r.value}» geri qaytar") }
            }
            if (suggestions.isNotEmpty()) {
                HintText("WhatsApp-da görülən qruplar:")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { group ->
                        AssistChip(
                            onClick = { scope.launch { repo.addGroup(group) } },
                            label = { Text(group, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                                )
                            },
                        )
                    }
                }
            }
        }

        SectionCard(title = "Səs") {
            SwitchRow(
                title = "Açana qədər çalsın",
                subtitle = "Səs bildirişə toxunana qədər təkrarlanır.",
                checked = s.repeatAlert,
            ) { value -> scope.launch { repo.setRepeatAlert(value) } }
            SwitchRow(
                title = "Səssiz rejimdə də çalsın",
                subtitle = "Alarm səsi kimi çalır.",
                checked = s.loudMode,
            ) { value -> scope.launch { repo.setLoudMode(value) } }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    SystemStatus.openChannelSettings(context, AlertNotifier.channelId(s.loudMode))
                }) { Text("Səs və vibrasiya") }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    OutlinedButton(onClick = { SystemStatus.requestAddTile(context) }) {
                        Text("Sürətli panelə əlavə et")
                    }
                }
            }
        }

        SectionCard(title = "Axtarış") {
            SwitchRow(
                title = "Hərf fərqlərinə baxma",
                subtitle = "ə=e, ş=s, ı=i… və kiril: ортада = ortada",
                checked = s.foldDiacritics,
            ) { value -> scope.launch { repo.setFoldDiacritics(value) } }
            SwitchRow(
                title = "Şəxsi çatlar da",
                subtitle = "Qrup olmayan yazışmalar da yoxlansın.",
                checked = s.includePrivateChats,
            ) { value -> scope.launch { repo.setIncludePrivateChats(value) } }
        }

        Text(
            "WP Radar ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
