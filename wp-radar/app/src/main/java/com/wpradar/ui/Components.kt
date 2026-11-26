package com.wpradar.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wpradar.R
import com.wpradar.core.TextNormalizer
import com.wpradar.ui.theme.Brand
import com.wpradar.ui.theme.RadarTheme
import com.wpradar.ui.theme.tabular
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Ekran hər dəfə önə gələndə (məs. ayarlardan qayıdanda) çağırılır. */
@Composable
fun OnResumeEffect(onResume: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(onResume)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) latest()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

// ── Hərəkət köməkçiləri ──────────────────────────────────────────────────────

/** Basılanda yüngülcə "batan", buraxanda yaylı geri qayıdan miqyas. */
@Composable
fun rememberPressScale(interaction: InteractionSource, pressed: Float = 0.95f): State<Float> {
    val isPressed by interaction.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "pressScale",
    )
}

/** Ekran açılanda hissələr növbə ilə, yumşaq sürüşərək görünür. */
@Composable
fun StaggeredEntrance(index: Int, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(tween(360, delayMillis = 55 * index)) +
            slideInVertically(tween(440, delayMillis = 55 * index, easing = FastOutSlowInEasing)) { h -> h / 7 },
    ) {
        content()
    }
}

/** Mətn dəyişəndə köhnəsi sönür, yenisi yumşaq görünür. */
@Composable
fun FadeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = { fadeIn(tween(240, delayMillis = 60)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false) },
        modifier = modifier,
        label = "fadeText",
    ) { value ->
        Text(value, style = style, color = color, textAlign = textAlign)
    }
}

/** Saylar dəyişəndə rəqəm yuxarı/aşağı "fırlanır". */
@Composable
fun RollingText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            val up = (targetState.toIntOrNull() ?: 0) >= (initialState.toIntOrNull() ?: 0)
            val enter = slideInVertically(tween(320, easing = FastOutSlowInEasing)) { h -> if (up) h else -h } +
                fadeIn(tween(220))
            val exit = slideOutVertically(tween(320, easing = FastOutSlowInEasing)) { h -> if (up) -h else h } +
                fadeOut(tween(160))
            enter togetherWith exit using SizeTransform(clip = false)
        },
        modifier = modifier,
        label = "rollingText",
    ) { value ->
        Text(value, style = style.tabular(), color = color, maxLines = 1)
    }
}

// ── Kartlar ──────────────────────────────────────────────────────────────────

/** Tətbiqin əsas kartı: yumşaq künclər, incə haşiyə, işıqlı temada yüngül kölgə. */
@Composable
fun RadarCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    border: Color = RadarTheme.palette.cardBorder,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = RadarTheme.palette
    val shape = MaterialTheme.shapes.large
    val interaction = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interaction, pressed = 0.985f)
    val click = if (onClick != null) {
        Modifier.clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
    } else {
        Modifier
    }
    Column(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (palette.isDark) 0.dp else 10.dp,
                shape = shape,
                clip = false,
                ambientColor = palette.shadow,
                spotColor = palette.shadow,
            )
            .clip(shape)
            .background(palette.card)
            .border(1.dp, border, shape)
            .then(click),
        content = content,
    )
}

/** Rəngli fonda ikon — bölmə başlıqları və sətirlər üçün. */
@Composable
fun IconBadge(
    @DrawableRes icon: Int,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 21.dp,
    background: Color = tint.copy(alpha = 0.14f),
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun SectionCard(
    title: String,
    @DrawableRes icon: Int,
    subtitle: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    RadarCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, accent)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) {
                        FadeText(subtitle, MaterialTheme.typography.bodySmall, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                trailing()
            }
            content()
        }
    }
}

/** Başlığa toxunanda yumşaq açılan kart (məsləhətlər üçün — ekranı doldurmasın). */
@Composable
fun ExpandableCard(
    title: String,
    @DrawableRes icon: Int,
    accent: Color = RadarTheme.palette.warning,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "chevron",
    )
    RadarCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = if (expanded) "Bağla" else "Aç") { expanded = !expanded }
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon, accent)
            Spacer(Modifier.width(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_chevron_down),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotation),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(320, easing = FastOutSlowInEasing)) + fadeIn(tween(260, delayMillis = 60)),
            exit = shrinkVertically(tween(260, easing = FastOutSlowInEasing)) + fadeOut(tween(140)),
        ) {
            Column(
                Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content()
            }
        }
    }
}

/** Quraşdırma addımı: vəziyyət nişanı + ad + vəziyyət + (lazımsa) düymə. */
@Composable
fun StatusRow(
    ok: Boolean,
    title: String,
    status: String,
    @DrawableRes icon: Int,
    action: @Composable () -> Unit = {},
) {
    val palette = RadarTheme.palette
    val onOk = MaterialTheme.colorScheme.onPrimary
    val tone by animateColorAsState(if (ok) palette.success else palette.danger, tween(400), label = "statusTone")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = ok,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.6f) + fadeIn()) togetherWith
                    (scaleOut(tween(150), targetScale = 0.6f) + fadeOut(tween(150)))
            },
            label = "statusIcon",
        ) { isOk ->
            if (isOk) {
                IconBadge(R.drawable.ic_check, tint = onOk, background = palette.success, iconSize = 20.dp)
            } else {
                IconBadge(icon, tint = palette.danger, background = palette.dangerContainer)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            FadeText(status, MaterialTheme.typography.bodySmall, tone)
        }
        Spacer(Modifier.width(8.dp))
        action()
    }
}

@Composable
fun HintText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun BulletText(text: String, color: Color = RadarTheme.palette.warning) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
        )
    }
}

// ── Düymələr ─────────────────────────────────────────────────────────────────

/** Yığcam düymə: dolu (əsas hərəkət) və ya yumşaq rəngli fon. */
@Composable
fun SoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    filled: Boolean = false,
    color: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = if (filled) MaterialTheme.colorScheme.onPrimary else color,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interaction)
    Row(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (filled) color else color.copy(alpha = 0.13f))
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = contentColor, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = contentColor, maxLines = 1)
    }
}

/** Ən vacib hərəkət üçün qradiyentli böyük düymə. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    val palette = RadarTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interaction, pressed = 0.96f)
    Row(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(Brush.horizontalGradient(palette.cta))
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = palette.onCta, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = palette.onCta)
    }
}

// ── Açar sözlər / qruplar ───────────────────────────────────────────────────

@Composable
fun AddItemField(label: String, onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val submit: () -> Unit = {
        val value = text.trim()
        if (value.isNotEmpty()) {
            onAdd(value)
            text = ""
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    val canAdd = text.isNotBlank()
    val scale by animateFloatAsState(
        targetValue = if (canAdd) 1f else 0.86f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 600f),
        label = "addScale",
    )
    val addBackground by animateColorAsState(
        if (canAdd) colors.primary else colors.surfaceContainerHighest,
        tween(220),
        label = "addBg",
    )
    val addContent by animateColorAsState(
        if (canAdd) colors.onPrimary else colors.onSurfaceVariant,
        tween(220),
        label = "addFg",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(label) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.outlineVariant,
                focusedContainerColor = colors.surfaceContainer,
                unfocusedContainerColor = colors.surfaceContainer,
                cursorColor = colors.primary,
                focusedPlaceholderColor = colors.onSurfaceVariant,
                unfocusedPlaceholderColor = colors.onSurfaceVariant,
            ),
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(54.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(18.dp))
                .background(addBackground)
                .clickable(enabled = canAdd, onClickLabel = "Əlavə et", onClick = submit),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_add),
                contentDescription = "Əlavə et",
                tint = addContent,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Siyahı yığcam "çip"lər şəklində; toxunanda silinir. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemovableChips(items: List<String>, @DrawableRes icon: Int, onRemove: (String) -> Unit) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(tween(260, easing = FastOutSlowInEasing)),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            key(item) {
                TagChip(item, icon) { onRemove(item) }
            }
        }
    }
}

@Composable
private fun TagChip(text: String, @DrawableRes icon: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow))
    }
    val interaction = remember { MutableInteractionSource() }
    val press by rememberPressScale(interaction, pressed = 0.92f)
    Row(
        Modifier
            .graphicsLayer {
                val s = (0.7f + 0.3f * appear.value) * press
                scaleX = s
                scaleY = s
                alpha = appear.value.coerceIn(0f, 1f)
            }
            .clip(CircleShape)
            .background(colors.primary.copy(alpha = 0.11f))
            .border(1.dp, colors.primary.copy(alpha = 0.26f), CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClickLabel = "Sil",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            )
            .padding(start = 11.dp, end = 7.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.primary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp),
        )
        Spacer(Modifier.width(7.dp))
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(colors.onSurface.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_close),
                contentDescription = "Sil",
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

// ── Açar-söndür sətirləri ───────────────────────────────────────────────────

@Composable
fun RadarSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, onDark: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        thumbContent = if (checked) {
            {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            }
        } else {
            null
        },
        colors = if (onDark) {
            SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF06231B),
                checkedTrackColor = Brand.Mint,
                checkedIconColor = Brand.Mint,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White.copy(alpha = 0.75f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.35f),
            )
        } else {
            SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
                checkedIconColor = colors.primary,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = colors.outline,
                uncheckedTrackColor = colors.surfaceContainerHighest,
                uncheckedBorderColor = colors.outline.copy(alpha = 0.6f),
            )
        },
    )
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    @DrawableRes icon: Int,
    onChange: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val toggle: (Boolean) -> Unit = { value ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onChange(value)
    }
    val tint by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(300),
        label = "switchRowTint",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { toggle(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint, size = 38.dp, iconSize = 20.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            HintText(subtitle)
        }
        Spacer(Modifier.width(12.dp))
        RadarSwitch(checked = checked, onCheckedChange = toggle)
    }
}

// ── Siyahılar ────────────────────────────────────────────────────────────────

/** Siyahı sətri: ya gün başlığı ([dayLabel]), ya da element ([item]). */
class ListRow<out T> private constructor(val key: Any, val dayLabel: String?, val item: T?) {
    companion object {
        fun day(key: Any, label: String): ListRow<Nothing> = ListRow(key, label, null)
        fun <T> item(key: Any, value: T): ListRow<T> = ListRow(key, null, value)
    }
}

/** Elementləri günlərə bölür: "Bu gün", "Dünən", "24 noyabr, bazar ertəsi"… */
fun <T> withDayHeaders(items: List<T>, time: (T) -> Long, key: (T) -> Any): List<ListRow<T>> {
    val out = ArrayList<ListRow<T>>(items.size + 4)
    val cal = Calendar.getInstance()
    val seen = HashMap<Int, Int>()
    var lastDay = Int.MIN_VALUE
    for (item in items) {
        val t = time(item)
        cal.timeInMillis = t
        val day = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
        if (day != lastDay) {
            // Sıra pozulsa (köhnə mesaj sonradan gəlsə) eyni gün yenidən çıxa bilər — açar təkrarlanmasın.
            val n = (seen[day] ?: 0) + 1
            seen[day] = n
            out.add(ListRow.day("day-$day-$n", dayLabel(t)))
            lastDay = day
        }
        out.add(ListRow.item(key(item), item))
    }
    return out
}

private val AzLocale: Locale = Locale.forLanguageTag("az")

fun dayLabel(time: Long): String {
    if (DateUtils.isToday(time)) return "Bu gün"
    if (DateUtils.isToday(time + DateUtils.DAY_IN_MILLIS)) return "Dünən"
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = time }
    val pattern = if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR)) "d MMMM, EEEE" else "d MMMM yyyy"
    return SimpleDateFormat(pattern, AzLocale).format(Date(time))
}

@Composable
fun DayHeader(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 6.dp, top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label.uppercase(AzLocale),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.9.sp,
        )
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

private val AvatarColors = listOf(
    Color(0xFF26C6A0),
    Color(0xFF4FA3F7),
    Color(0xFFF6A23C),
    Color(0xFFEF6F8E),
    Color(0xFF9C7CF4),
    Color(0xFF3CC4D9),
    Color(0xFF7DC75A),
    Color(0xFFF07F5B),
)

/** Ada görə həmişə eyni rəng. */
fun avatarColor(name: String): Color = AvatarColors[(name.hashCode() and 0x7fffffff) % AvatarColors.size]

/** Qrupun/adamın baş hərfi rəngli dairədə. */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val color = avatarColor(name)
    val initial = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase(AzLocale) ?: "#"
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.7f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

/** Boş ekran: sakit radar və izah. */
@Composable
fun EmptyState(title: String, body: String, contentPadding: PaddingValues) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        StaggeredEntrance(0) {
            Column(
                Modifier.padding(horizontal = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RadarScope(
                    mode = RadarMode.Active,
                    blips = 0,
                    modifier = Modifier.size(150.dp),
                    tint = MaterialTheme.colorScheme.primary,
                    showTicks = false,
                )
                Spacer(Modifier.height(26.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Mətndə açar sözü qalın və rəngli göstərir. */
fun highlightKeyword(text: String, keyword: String, background: Color, foreground: Color): AnnotatedString {
    val range = TextNormalizer.findMatch(text, keyword, foldDiacritics = true) ?: return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        addStyle(
            SpanStyle(fontWeight = FontWeight.Bold, background = background, color = foreground),
            range.first,
            range.last + 1,
        )
    }
}

fun formatTime(time: Long): String {
    val pattern = if (DateUtils.isToday(time)) "HH:mm" else "dd.MM.yyyy HH:mm"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time))
}

fun clockTime(time: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
