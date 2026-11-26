package com.wpradar.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wpradar.BuildConfig
import com.wpradar.R
import com.wpradar.WpRadarApp
import com.wpradar.core.TextNormalizer
import com.wpradar.notify.AlertNotifier
import com.wpradar.system.SystemStatus
import com.wpradar.ui.theme.RadarTheme
import com.wpradar.ui.theme.tabular
import kotlinx.coroutines.launch

/** Son silinən element — "geri qaytar" üçün. */
private data class Removed(val isKeyword: Boolean, val value: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(app: WpRadarApp, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    var removed by remember { mutableStateOf<Removed?>(null) }
    val s = settings ?: return
    val repo = app.settings
    val scope = app.appScope
    val palette = RadarTheme.palette

    val suggestions = remember(s.groups, s.recentGroups) {
        val added = s.groups.map { TextNormalizer.normalize(it, false) }.toSet()
        s.recentGroups.filter { TextNormalizer.normalize(it, false) !in added }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(2.dp))

        // ── Açar sözlər ───────────────────────────────────────────────
        StaggeredEntrance(0) {
            SectionCard(
                title = "Açar sözlər",
                icon = R.drawable.ic_key,
                subtitle = "Bir neçəsini vergüllə yazın: ortada, təcili, iclas",
                trailing = { CountPill(s.keywords.size) },
            ) {
                AddItemField(label = "Açar söz") { input -> scope.launch { repo.addKeywords(input) } }
                AnimatedVisibility(
                    visible = s.keywords.isEmpty(),
                    enter = expandVertically(tween(260)) + fadeIn(),
                    exit = shrinkVertically(tween(220)) + fadeOut(),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.dangerContainer.copy(alpha = 0.7f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_alert),
                            contentDescription = null,
                            tint = palette.danger,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Açar söz yoxdur — radar heç nə tapmayacaq.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.danger,
                        )
                    }
                }
                RemovableChips(s.keywords, icon = R.drawable.ic_key) { word ->
                    removed = Removed(isKeyword = true, value = word)
                    scope.launch { repo.removeKeyword(word) }
                }
                UndoPill(removed?.takeIf { it.isKeyword }?.value) { value ->
                    removed = null
                    scope.launch { repo.addKeywords(value) }
                }
            }
        }

        // ── Qruplar ───────────────────────────────────────────────────
        StaggeredEntrance(1) {
            SectionCard(
                title = "Qruplar",
                icon = R.drawable.ic_users,
                subtitle = if (s.groups.isEmpty()) {
                    "Boşdur — bütün qruplar izlənir."
                } else {
                    "Yalnız bu qruplar. Adın bir hissəsi kifayətdir."
                },
                accent = MaterialTheme.colorScheme.secondary,
                trailing = { CountPill(s.groups.size, emptyLabel = "Hamısı") },
            ) {
                AddItemField(label = "Qrup adı") { name -> scope.launch { repo.addGroup(name) } }
                RemovableChips(s.groups, icon = R.drawable.ic_users) { group ->
                    removed = Removed(isKeyword = false, value = group)
                    scope.launch { repo.removeGroup(group) }
                }
                UndoPill(removed?.takeIf { !it.isKeyword }?.value) { value ->
                    removed = null
                    scope.launch { repo.addGroup(value) }
                }
                if (suggestions.isNotEmpty()) {
                    Text(
                        "WhatsApp-da görülən qruplar:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        suggestions.forEach { group ->
                            SuggestionChip(group) { scope.launch { repo.addGroup(group) } }
                        }
                    }
                }
            }
        }

        // ── Səs ───────────────────────────────────────────────────────
        StaggeredEntrance(2) {
            SectionCard(title = "Səs", icon = R.drawable.ic_volume, accent = palette.warning) {
                SwitchRow(
                    title = "Açana qədər çalsın",
                    subtitle = "Səs bildirişə toxunana qədər təkrarlanır.",
                    checked = s.repeatAlert,
                    icon = R.drawable.ic_repeat,
                ) { value -> scope.launch { repo.setRepeatAlert(value) } }
                SwitchRow(
                    title = "Səssiz rejimdə də çalsın",
                    subtitle = "Alarm səsi kimi çalır.",
                    checked = s.loudMode,
                    icon = R.drawable.ic_alarm,
                ) { value -> scope.launch { repo.setLoudMode(value) } }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SoftButton(
                        text = "Səs və vibrasiya",
                        onClick = { SystemStatus.openChannelSettings(context, AlertNotifier.channelId(s.loudMode)) },
                        icon = R.drawable.ic_vibrate,
                        color = palette.warning,
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        SoftButton(
                            text = "Sürətli panelə əlavə et",
                            onClick = { SystemStatus.requestAddTile(context) },
                            icon = R.drawable.ic_tile,
                            color = palette.warning,
                        )
                    }
                }
            }
        }

        // ── Axtarış ───────────────────────────────────────────────────
        StaggeredEntrance(3) {
            SectionCard(title = "Axtarış", icon = R.drawable.ic_search, accent = MaterialTheme.colorScheme.secondary) {
                SwitchRow(
                    title = "Hərf fərqlərinə baxma",
                    subtitle = "ə=e, ş=s, ı=i… və kiril: ортада = ortada",
                    checked = s.foldDiacritics,
                    icon = R.drawable.ic_letters,
                ) { value -> scope.launch { repo.setFoldDiacritics(value) } }
                SwitchRow(
                    title = "Şəxsi çatlar da",
                    subtitle = "Qrup olmayan yazışmalar da yoxlansın.",
                    checked = s.includePrivateChats,
                    icon = R.drawable.ic_person,
                ) { value -> scope.launch { repo.setIncludePrivateChats(value) } }
            }
        }

        // ── Versiya ───────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LogoMark(Modifier.size(22.dp), animated = false)
            Spacer(Modifier.width(8.dp))
            Text(
                "WP Radar ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Bölmə başlığında say: "3" və ya boş olanda "Hamısı". */
@Composable
private fun CountPill(count: Int, emptyLabel: String? = null) {
    val colors = MaterialTheme.colorScheme
    val text = if (count == 0 && emptyLabel != null) emptyLabel else count.toString()
    Box(
        Modifier
            .clip(CircleShape)
            .background(colors.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 11.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        RollingText(text, MaterialTheme.typography.labelLarge, colors.onSurfaceVariant)
    }
}

/** Silinəndən sonra görünən "geri qaytar" düyməsi. */
private class LastValue(var value: String? = null)

@Composable
private fun UndoPill(value: String?, onUndo: (String) -> Unit) {
    // Gizlənərkən də son silinən adı göstərmək üçün yadda saxlanır.
    val last = remember { LastValue() }
    if (value != null) last.value = value
    AnimatedVisibility(
        visible = value != null,
        enter = fadeIn(tween(220)) + expandVertically(tween(260, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(150)) + shrinkVertically(tween(220, easing = FastOutSlowInEasing)),
    ) {
        val current = last.value ?: return@AnimatedVisibility
        val tint = MaterialTheme.colorScheme.secondary
        val interaction = remember { MutableInteractionSource() }
        val scale by rememberPressScale(interaction)
        Row(
            Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f))
                .clickable(interactionSource = interaction, indication = LocalIndication.current) { onUndo(current) }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_undo), contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "«$current» geri qaytar",
                style = MaterialTheme.typography.labelLarge,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 260.dp),
            )
        }
    }
}

/** Hələ əlavə edilməmiş qrup: qırıq xətli çərçivə, toxunanda əlavə olunur. */
@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val outline = colors.outline
    val interaction = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interaction, pressed = 0.92f)
    Row(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .drawBehind {
                val stroke = 1.2.dp.toPx()
                drawRoundRect(
                    color = outline,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                    ),
                )
            }
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClickLabel = "Əlavə et", onClick = onClick)
            .padding(start = 10.dp, end = 13.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = colors.secondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp),
        )
    }
}
