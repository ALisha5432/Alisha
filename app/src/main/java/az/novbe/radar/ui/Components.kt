package az.novbe.radar.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class RadarMode { ACTIVE, SCANNING, WARN, OFF }

/** Fırlanan radar: aktivdə yaşıl, yoxlayanda mavi və sürətli, problem olanda sarı. */
@Composable
fun RadarView(mode: RadarMode, blips: Int, modifier: Modifier = Modifier) {
    val color by animateColorAsState(
        when (mode) {
            RadarMode.ACTIVE -> RC.Green
            RadarMode.SCANNING -> RC.Cyan
            RadarMode.WARN -> RC.Amber
            RadarMode.OFF -> RC.Muted
        },
        tween(700),
        label = "radarColor",
    )
    val period = when (mode) {
        RadarMode.SCANNING -> 1100
        RadarMode.WARN -> 4500
        RadarMode.OFF -> 12000
        RadarMode.ACTIVE -> 2800
    }
    val inf = rememberInfiniteTransition(label = "radar")
    val angle by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(period, easing = LinearEasing)),
        label = "sweep",
    )
    val pulse by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "pulse",
    )
    Canvas(modifier.aspectRatio(1f)) {
        val r = size.minDimension / 2f
        val c = center
        val px = 1.dp.toPx()
        drawCircle(
            brush = Brush.radialGradient(listOf(color.copy(alpha = 0.13f), Color.Transparent), center = c, radius = r),
            radius = r,
            center = c,
        )
        for (i in 1..4) drawCircle(color = RC.Line, radius = r * i / 4f, center = c, style = Stroke(width = px))
        drawLine(RC.Line, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = px)
        drawLine(RC.Line, Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = px)
        if (mode != RadarMode.OFF) {
            drawCircle(color = color.copy(alpha = (1f - pulse) * 0.45f), radius = r * pulse, center = c, style = Stroke(width = 2 * px))
        }
        rotate(angle, pivot = c) {
            drawCircle(
                brush = Brush.sweepGradient(
                    0f to Color.Transparent,
                    0.78f to Color.Transparent,
                    1f to color.copy(alpha = if (mode == RadarMode.OFF) 0.15f else 0.5f),
                    center = c,
                ),
                radius = r,
                center = c,
            )
            drawLine(color.copy(alpha = 0.9f), c, Offset(c.x + r, c.y), strokeWidth = 2 * px, cap = StrokeCap.Round)
        }
        for (i in 0 until blips.coerceAtMost(9)) {
            val a = (i * 137.5 + 35.0) * PI / 180.0
            val d = r * (0.32f + 0.55f * ((i * 37) % 10) / 10f)
            val p = Offset(c.x + (cos(a) * d).toFloat(), c.y + (sin(a) * d).toFloat())
            drawCircle(RC.Amber.copy(alpha = 0.25f + 0.25f * (1f - pulse)), radius = 10 * px * (0.6f + 0.4f * pulse), center = p)
            drawCircle(RC.Amber, radius = 4 * px, center = p)
        }
        drawCircle(color, radius = 5 * px, center = c)
    }
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    val fg by animateColorAsState(color, tween(500), label = "pill")
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .background(fg.copy(alpha = 0.13f))
            .border(1.dp, fg.copy(alpha = 0.35f), shape)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(fg))
        Spacer(Modifier.width(9.dp))
        AnimatedContent(
            targetState = text,
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
            label = "pillText",
        ) { t ->
            Text(t, color = fg, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Yuvarlaq künclü panel. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    border: Color = RC.Line,
    background: Color = RC.Surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = RC.Muted,
        modifier = modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = RC.Text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = RC.Muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), content = actions)
    }
}

@Composable
fun ToggleRow(
    title: String,
    sub: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) RC.Text else RC.Muted)
            if (sub != null) {
                Spacer(Modifier.height(2.dp))
                Text(sub, style = MaterialTheme.typography.bodySmall, color = RC.Muted)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = RC.Bg,
                checkedTrackColor = RC.Cyan,
                checkedBorderColor = RC.Cyan,
                uncheckedThumbColor = RC.Muted,
                uncheckedTrackColor = RC.Surface2,
                uncheckedBorderColor = RC.Line,
            ),
        )
    }
}

/** Kiçik məlumat bloku: başlıq + dəyər. */
@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, color: Color = RC.Text) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(RC.Surface)
            .border(1.dp, RC.Line, shape)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = RC.Muted)
        Spacer(Modifier.height(4.dp))
        AnimatedContent(
            targetState = value,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "stat",
        ) { v ->
            Text(v, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

// ---------- Vaxt formatları ----------

fun hhmm(ms: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(ms))

fun hhmmss(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))

fun dayMonth(ms: Long): String = SimpleDateFormat("dd.MM", Locale.US).format(Date(ms))

fun ago(ms: Long, now: Long): String {
    if (ms <= 0) return "—"
    val s = (now - ms) / 1000
    return when {
        s < 45 -> "indicə"
        s < 3600 -> "${(s + 30) / 60} dəq əvvəl"
        s < 86_400 -> "${s / 3600} saat əvvəl"
        else -> "${s / 86_400} gün əvvəl"
    }
}

fun countdown(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = (ms + 999) / 1000
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
