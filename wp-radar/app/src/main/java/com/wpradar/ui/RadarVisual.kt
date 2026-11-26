package com.wpradar.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.wpradar.ui.theme.Brand
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Radar panelinin vəziyyəti: işləyir, diqqət lazımdır, dayanıb. */
enum class RadarMode { Active, Attention, Off }

/** Hədəf nöqtələrinin yerləri (bucaq °, radiusun payı) — "bu gün tapılanlar" qədər yanır. */
private val BlipSpots = listOf(
    -62f to 0.66f,
    28f to 0.80f,
    142f to 0.48f,
    206f to 0.74f,
    292f to 0.38f,
    96f to 0.58f,
    336f to 0.86f,
)

private val DEG = (PI / 180.0).toFloat()

private fun polar(center: Offset, radius: Float, degrees: Float): Offset {
    val rad = degrees * DEG
    return Offset(center.x + cos(rad) * radius, center.y + sin(rad) * radius)
}

/**
 * Canlı radar: dairələr, bölgü xətləri, fırlanan şüa, mərkəzdən yayılan dalğa
 * və şüa üstündən keçəndə parlayan hədəflər.
 */
@Composable
fun RadarScope(
    mode: RadarMode,
    blips: Int,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    showTicks: Boolean = true,
) {
    val target = tint ?: when (mode) {
        RadarMode.Active -> Brand.Mint
        RadarMode.Attention -> Brand.Amber
        RadarMode.Off -> Brand.Slate
    }
    val accent = animateColorAsState(target, tween(700), label = "radarAccent")
    val sweepAlpha = animateFloatAsState(if (mode == RadarMode.Off) 0f else 1f, tween(800), label = "sweepAlpha")
    val angle = remember { Animatable(0f) }
    LaunchedEffect(mode) {
        if (mode == RadarMode.Off) {
            // Dayananda şüa birdən donmur — yavaşlayıb sönür.
            angle.animateTo(angle.value + 40f, tween(1000, easing = LinearOutSlowInEasing))
        } else {
            val period = if (mode == RadarMode.Active) 3600 else 6000
            while (true) {
                angle.animateTo(angle.value + 360f, tween(period, easing = LinearEasing))
                angle.snapTo(angle.value % 360f)
            }
        }
    }
    val transition = rememberInfiniteTransition(label = "radarPulse")
    val pulse = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "pulse",
    )
    val shown = blips.coerceIn(0, BlipSpots.size)

    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f
        val hair = 1.dp.toPx()
        val a = accent.value
        val sweep = sweepAlpha.value

        // Yumşaq işıq
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(a.copy(alpha = 0.20f), a.copy(alpha = 0.05f), Color.Transparent),
                center = c,
                radius = r,
            ),
            radius = r,
            center = c,
        )
        // Dairələr və xaç
        for (i in 1..4) {
            drawCircle(
                color = a.copy(alpha = if (i == 4) 0.40f else 0.16f),
                radius = r * i / 4f * 0.985f,
                center = c,
                style = Stroke(width = if (i == 4) 1.5f * hair else hair),
            )
        }
        drawLine(a.copy(alpha = 0.12f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = hair)
        drawLine(a.copy(alpha = 0.12f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = hair)

        // Kənardakı bölgü xətləri — hər 5°, hər 30°-də uzun
        if (showTicks) {
            for (i in 0 until 72) {
                val long = i % 6 == 0
                val inner = r * if (long) 0.89f else 0.935f
                drawLine(
                    color = a.copy(alpha = if (long) 0.45f else 0.18f),
                    start = polar(c, inner, i * 5f),
                    end = polar(c, r * 0.975f, i * 5f),
                    strokeWidth = hair,
                    cap = StrokeCap.Round,
                )
            }
        }

        // Mərkəzdən yayılan dalğa
        if (sweep > 0.01f) {
            val p = pulse.value
            drawCircle(
                color = a.copy(alpha = (1f - p) * 0.35f * sweep),
                radius = r * (0.08f + 0.9f * p),
                center = c,
                style = Stroke(width = 2f * hair),
            )
        }

        // Fırlanan şüa
        val deg = angle.value % 360f
        if (sweep > 0.01f) {
            rotate(degrees = deg, pivot = c) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to Color.Transparent,
                        0.17f to a.copy(alpha = 0.10f * sweep),
                        0.25f to a.copy(alpha = 0.50f * sweep),
                        0.2501f to Color.Transparent,
                        center = c,
                    ),
                    startAngle = 0f,
                    sweepAngle = 90f,
                    useCenter = true,
                    topLeft = Offset(c.x - r, c.y - r),
                    size = Size(2f * r, 2f * r),
                )
                drawLine(
                    color = a.copy(alpha = 0.9f * sweep),
                    start = c,
                    end = Offset(c.x, c.y + r * 0.975f),
                    strokeWidth = 2f * hair,
                    cap = StrokeCap.Round,
                )
            }
        }

        // Hədəflər: şüa keçəndə parlayır, sonra yavaş-yavaş sönür
        val lead = (deg + 90f) % 360f
        for (i in 0 until shown) {
            val spot = BlipSpots[i]
            val at = ((spot.first % 360f) + 360f) % 360f
            val since = ((lead - at) % 360f + 360f) % 360f
            val glow = if (sweep > 0.01f) (1f - since / 300f).coerceIn(0f, 1f) else 0f
            val pos = polar(c, r * spot.second, at)
            drawCircle(Brand.Amber.copy(alpha = 0.22f * glow), radius = 11f * hair * (0.6f + 0.4f * glow), center = pos)
            drawCircle(Brand.Amber.copy(alpha = 0.35f + 0.65f * glow), radius = 3.2f * hair, center = pos)
        }

        // Mərkəz
        drawCircle(a.copy(alpha = 0.25f), radius = 9f * hair, center = c)
        drawCircle(a, radius = 4.5f * hair, center = c)
        drawCircle(Color.White.copy(alpha = 0.9f), radius = 2f * hair, center = c)
    }
}

/**
 * Tətbiqin logosu (launcher ikonu ilə eyni rəsm). [animated] olanda
 * baloncuğun ətrafında yumşaq "siqnal" dalğası yayılır.
 */
@Composable
fun LogoMark(modifier: Modifier = Modifier, animated: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "logo")
    val ping = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(2200, delayMillis = 900, easing = LinearOutSlowInEasing),
            RepeatMode.Restart,
        ),
        label = "ping",
    )
    Canvas(modifier) {
        val s = size.minDimension
        // Logo 108-lik şəbəkədə çəkilib; görünən hissə 18..90 aralığıdır.
        fun p(x: Float, y: Float) = Offset((x - 18f) / 72f * s, (y - 18f) / 72f * s)
        fun u(v: Float) = v / 72f * s

        val corner = CornerRadius(s * 0.28f, s * 0.28f)
        val tile = Path().apply { addRoundRect(RoundRect(0f, 0f, s, s, corner)) }
        val c = p(49f, 59f)
        clipPath(tile) {
            drawRect(Brush.linearGradient(listOf(Brand.Night, Brand.Abyss), start = Offset.Zero, end = Offset(s, s)))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Brand.Mint.copy(alpha = 0.33f), Brand.Mint.copy(alpha = 0.10f), Color.Transparent),
                    center = c,
                    radius = u(40f),
                ),
                radius = u(40f),
                center = c,
            )

            val r1 = u(21f)
            val rw = u(19.6f)
            drawArc(
                brush = Brush.linearGradient(
                    0f to Brand.Mint.copy(alpha = 0f),
                    0.6f to Brand.Mint.copy(alpha = 0.25f),
                    1f to Brand.Mint.copy(alpha = 0.70f),
                    start = polar(c, rw, -141f),
                    end = polar(c, rw * 0.95f, -45f),
                ),
                startAngle = -141f,
                sweepAngle = 96f,
                useCenter = true,
                topLeft = Offset(c.x - rw, c.y - rw),
                size = Size(2f * rw, 2f * rw),
            )
            drawCircle(Brand.Mint.copy(alpha = 0.48f), radius = u(12.5f), center = c, style = Stroke(width = u(2.2f)))
            drawArc(
                color = Brand.Mint,
                startAngle = -21f,
                sweepAngle = 312f,
                useCenter = false,
                topLeft = Offset(c.x - r1, c.y - r1),
                size = Size(2f * r1, 2f * r1),
                style = Stroke(width = u(3.4f), cap = StrokeCap.Round),
            )
            drawLine(Brand.LeadLine, c, polar(c, u(20.5f), -45f), strokeWidth = u(2.8f), cap = StrokeCap.Round)
            drawCircle(Brand.Mint.copy(alpha = 0.8f), radius = u(1.7f), center = polar(c, u(8f), -105f))
            drawCircle(Color.White, radius = u(3.3f), center = c)

            // Tapılan mesaj — baloncuq
            val b = p(69.86f, 38.14f)
            if (animated) {
                val t = ping.value
                drawCircle(Brand.Amber.copy(alpha = (1f - t) * 0.40f), radius = u(8f + 13f * t), center = b)
            }
            val bw = u(17.5f)
            val bh = u(13.5f)
            val bt = u(4f)
            val left = b.x - bw / 2f
            val top = b.y - bh / 2f
            val fill = Brush.verticalGradient(listOf(Brand.AmberLight, Brand.AmberDeep), startY = top, endY = top + bh + bt)
            drawRoundRect(fill, topLeft = Offset(left, top), size = Size(bw, bh), cornerRadius = CornerRadius(u(5.2f), u(5.2f)))
            val tail = Path().apply {
                moveTo(left + bt * 1.05f, top + bh)
                lineTo(left - bt * 0.55f, top + bh + bt * 0.95f)
                lineTo(left, top + bh - bt)
                close()
            }
            drawPath(tail, fill)
            for (dx in floatArrayOf(-3.9f, 0f, 3.9f)) {
                drawCircle(Color(0xE6402300), radius = u(1.35f), center = Offset(b.x + u(dx), b.y - u(0.2f)))
            }
        }
    }
}
