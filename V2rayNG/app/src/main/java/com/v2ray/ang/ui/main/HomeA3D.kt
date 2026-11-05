package com.v2ray.ang.ui.main

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.layout.heightIn
import kotlinx.coroutines.launch
import com.v2ray.ang.R
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.handler.ConfigLockManager
import com.v2ray.ang.ui.compose.BrandGold
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.OnBrandGold
import kotlin.math.max

// ---------- Shared 3D pieces ----------

/**
 * A raised surface: a darker "side" [edge] below the face, which sinks onto it while [pressed].
 * Gives buttons and tiles a physical, pressable look without platform blur APIs.
 */
@Composable
internal fun Raised3D(
    shape: Shape,
    face: Brush,
    side: Color,
    modifier: Modifier = Modifier,
    edge: Dp = 4.dp,
    pressed: Boolean = false,
    border: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val sink by animateFloatAsState(if (pressed) 1f else 0f, tween(90), label = "raisedSink")
    Box(modifier = modifier.padding(bottom = edge)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = edge)
                .clip(shape)
                .background(side)
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = edge * sink)
                .clip(shape)
                .background(face)
                .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
                .drawWithContent {
                    drawContent()
                    // Top highlight line, like light catching the bevel.
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.16f),
                            0.12f to Color.Transparent,
                        )
                    )
                }
        )
        Box(
            modifier = Modifier.offset(y = edge * sink),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/** A tile pressed into its card: dark fill, inner shadow at the top, faint light at the bottom. */
internal fun Modifier.sunken(shape: Shape, fill: Color): Modifier =
    this
        .clip(shape)
        .background(fill)
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.45f),
                    0.25f to Color.Transparent,
                    0.96f to Color.Transparent,
                    1f to Color.White.copy(alpha = 0.06f),
                )
            )
        }

@Composable
internal fun sunkenFill(): Color =
    if (LocalDarkTheme.current) Color(0xFF0C1020) else MaterialTheme.colorScheme.surfaceContainerHigh

// ---------- Tilt sensor ----------

/** Phone tilt as (rotationX, rotationY) degrees, smoothed and limited to ±[maxDeg]. */
internal class TiltState {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
}

/**
 * Listens to the gravity sensor only while the screen is resumed, so the parallax costs nothing
 * in the background. Without a gravity sensor the button simply stays flat.
 */
@Composable
internal fun rememberTilt(maxDeg: Float = 8f): TiltState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = remember { TiltState() }
    DisposableEffect(lifecycleOwner) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (rx, ry) = tiltFromGravity(event.values[0], event.values[1], maxDeg)
                // Low-pass so the button glides instead of shaking.
                state.x += (rx - state.x) * 0.15f
                state.y += (ry - state.y) * 0.15f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME ->
                    if (sensor != null) manager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)

                Lifecycle.Event.ON_PAUSE -> {
                    manager?.unregisterListener(listener)
                    state.x = 0f
                    state.y = 0f
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && sensor != null) {
            manager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            manager?.unregisterListener(listener)
        }
    }
    return state
}

/**
 * Pure: gravity x/y (m/s², phone held upright ≈ (0, 9.8)) → (rotationX, rotationY) degrees.
 * Upright is the neutral pose, so only leaning away from it tilts the button.
 */
internal fun tiltFromGravity(gx: Float, gy: Float, maxDeg: Float): Pair<Float, Float> {
    val g = STANDARD_GRAVITY
    val rx = ((gy - g * 0.75f) / g * maxDeg * 2f).coerceIn(-maxDeg, maxDeg)
    val ry = (-gx / g * maxDeg * 2f).coerceIn(-maxDeg, maxDeg)
    return rx to ry
}

// ---------- The 3D hexagon power button ----------

/**
 * Cassanova connect button: an extruded blue hexagon with a raised gold core (the logo), a
 * running gold-blue ring while connected, a press that sinks the faces, phone-tilt parallax and
 * haptics on press and on connect.
 */
@Composable
internal fun HexPowerButton3D(
    running: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 236.dp,
    height: Dp = 258.dp,
) {
    val label = stringResource(if (running) R.string.acc_stop else R.string.acc_start)
    val stateLabel = stringResource(if (running) R.string.home_protected else R.string.home_not_protected)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val view = LocalView.current
    val tilt = rememberTilt()

    val press by animateFloatAsState(if (pressed) 1f else 0f, tween(110), label = "hexPress")
    val on by animateFloatAsState(if (running) 1f else 0f, tween(350), label = "hexOn")
    val spin = rememberInfiniteTransition(label = "hexRing")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "hexRingAngle",
    )

    LaunchedEffect(pressed) {
        if (pressed) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
    val wasRunning = remember { booleanArrayOf(running) }
    LaunchedEffect(running) {
        if (running && !wasRunning[0]) {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                else HapticFeedbackConstants.LONG_PRESS
            )
        }
        wasRunning[0] = running
    }
    val currentAngle by rememberUpdatedState(angle)

    Canvas(
        modifier = modifier
            .size(width, height)
            .graphicsLayer {
                rotationX = tilt.x
                rotationY = tilt.y
                cameraDistance = 12f * density
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            )
            .semantics {
                contentDescription = label
                stateDescription = stateLabel
            },
    ) {
        // Design space 220 × 245 (like the preview), scaled to the canvas.
        val s = minOf(size.width / 220f, size.height / 245f)
        val ox = (size.width - 220f * s) / 2f
        val oy = (size.height - 245f * s) / 2f
        translate(ox, oy) {
            drawHexButton(s, on, press, if (running) currentAngle else 0f)
        }
    }
}

private fun lerp(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

private fun hexPath(cx: Float, cy: Float, r: Float, s: Float, dy: Float = 0f): Path =
    hexagon(Offset(cx * s, (cy + dy) * s), r * s)

private fun DrawScope.drawHexButton(s: Float, on: Float, press: Float, angle: Float) {
    val c = Offset(110f * s, 112f * s)
    // Glow behind (connected) and the floor shadow.
    if (on > 0f) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(BrandGold.copy(alpha = 0.22f * on), Color(0xFF2F55D4).copy(alpha = 0.14f * on), Color.Transparent),
                center = c, radius = 125f * s,
            ),
            radius = 125f * s, center = c,
        )
    }
    drawOval(
        brush = Brush.radialGradient(
            listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent),
            center = Offset(110f * s, 230f * s), radius = 92f * s,
        ),
        topLeft = Offset(18f * s, 216f * s), size = Size(184f * s, 28f * s),
    )

    // Running ring: a soft wide pass, then a crisp line.
    val ringAlpha = 0.12f + 0.88f * on
    rotate(angle, pivot = c) {
        val sweep = Brush.sweepGradient(
            0f to BrandGold, 0.35f to BrandGold.copy(alpha = 0f), 0.65f to Color(0xFF7C9BFF).copy(alpha = 0f),
            1f to Color(0xFF7C9BFF), center = c,
        )
        drawCircle(sweep, radius = 108f * s, center = c, alpha = 0.35f * ringAlpha, style = Stroke(width = 9f * s))
        drawCircle(sweep, radius = 108f * s, center = c, alpha = ringAlpha, style = Stroke(width = 2.5f * s))
    }

    val blueA = lerp(Color(0xFF3A4260), Color(0xFF4D73F0), on)
    val blueB = lerp(Color(0xFF262C44), Color(0xFF2443B8), on)
    val blueSide = lerp(Color(0xFF151A2C), Color(0xFF14206B), on)
    val goldA = lerp(Color(0xFFC9CFE0), Color(0xFFFFE7A3), on)
    val goldB = lerp(Color(0xFF8E96AE), Color(0xFFF5B530), on)
    val goldC = lerp(Color(0xFF5F677E), Color(0xFFD58A08), on)
    val goldSide = lerp(Color(0xFF3A4058), Color(0xFF8A5600), on)
    val glyph = lerp(Color(0xFF262C44), Color(0xFF3A2600), on)

    val faceY = 9f * press
    val coreY = 4f * press

    // Outer hexagon: side (extrusion), then the face with a bright upper rim.
    drawPath(hexPath(110f, 124f, 100f, s), blueSide)
    val outer = hexPath(110f, 110f, 100f, s, faceY)
    drawPath(outer, Brush.linearGradient(listOf(blueA, blueB), start = Offset(80f * s, 10f * s), end = Offset(140f * s, 210f * s)))
    drawPath(
        outer,
        Brush.verticalGradient(0f to Color.White.copy(alpha = 0.75f), 0.5f to Color.Transparent, startY = 10f * s, endY = 210f * s),
        style = Stroke(width = 3f * s, join = StrokeJoin.Round),
    )
    // Gold core: side, face, inner rim.
    drawPath(hexPath(110f, 120f, 64f, s, faceY), goldSide)
    val core = hexPath(110f, 110f, 64f, s, faceY + coreY)
    drawPath(
        core,
        Brush.linearGradient(
            0f to goldA, 0.55f to goldB, 1f to goldC,
            start = Offset(90f * s, 46f * s), end = Offset(130f * s, 174f * s),
        ),
    )
    drawPath(
        hexPath(110f, 110f, 58f, s, faceY + coreY),
        Color.White.copy(alpha = 0.35f),
        style = Stroke(width = 2f * s),
    )
    // Engraved power glyph: a light edge under the dark stroke.
    val gy = faceY + coreY
    drawPowerGlyph(s, gy + 2f, Color(0xFFFFF3CF).copy(alpha = 0.55f * on + 0.15f))
    drawPowerGlyph(s, gy, glyph)
}

private fun DrawScope.drawPowerGlyph(s: Float, dy: Float, color: Color) {
    val r = 26f * s
    val cx = 110f * s
    val cy = (110f + dy) * s
    drawArc(
        color = color,
        startAngle = -60f,
        sweepAngle = 300f,
        useCenter = false,
        topLeft = Offset(cx - r, cy - r),
        size = Size(r * 2, r * 2),
        style = Stroke(width = 7f * s, cap = StrokeCap.Round),
    )
    drawLine(color, Offset(cx, cy - 34f * s), Offset(cx, cy - 9f * s), strokeWidth = 7f * s, cap = StrokeCap.Round)
}

// ---------- Stats row and speed sparkline ----------

@Composable
internal fun StatsRow3D(home: HomeState) {
    val palette = configPalette()
    val ping = when {
        home.isTesting && home.isRunning -> stringResource(R.string.connection_test_testing)
        home.pingFailed -> stringResource(R.string.home_ping_failed)
        home.pingMillis != null -> stringResource(R.string.server_test_delay_value, home.pingMillis ?: 0L)
        else -> "—"
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MiniStat(stringResource(R.string.home_ping), ping, if (home.pingMillis != null) palette.green else null)
        MiniStat(stringResource(R.string.home_today_short), home.todayBytes.toTrafficString(), null)
        MiniStat(stringResource(R.string.home_download), home.downBytesPerSec.toTrafficString() + "/s", null)
        MiniStat(stringResource(R.string.home_upload), home.upBytesPerSec.toTrafficString() + "/s", null)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.MiniStat(label: String, value: String, valueColor: Color?) {
    val colors = MaterialTheme.colorScheme
    val split = value.lastIndexOf(' ')
    val number = if (split > 0) value.substring(0, split) else value
    val unit = if (split > 0) value.substring(split + 1) else ""
    Column(
        modifier = Modifier
            .weight(1f)
            .sunken(RoundedCornerShape(14.dp), sunkenFill())
            .padding(horizontal = 4.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = label, fontSize = 11.sp, color = colors.onSurfaceVariant, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = number,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor ?: colors.onSurface,
                maxLines = 1,
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = " $unit",
                    fontSize = 10.sp,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
    }
}

/** Download (blue) and upload (gold) over the last 60 seconds. */
@Composable
internal fun SpeedSparkline(history: List<SpeedSample>) {
    val colors = MaterialTheme.colorScheme
    val downColor = Color(0xFF7C9BFF)
    val graphLabel = stringResource(R.string.home_speed_graph)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .sunken(RoundedCornerShape(14.dp), sunkenFill())
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 6.dp)
            .semantics { contentDescription = graphLabel },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = graphLabel,
                fontSize = 11.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(text = "↓ " + stringResource(R.string.home_download_short), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = downColor)
            Text(text = "  ·  ", fontSize = 11.sp, color = colors.onSurfaceVariant)
            Text(text = "↑ " + stringResource(R.string.home_upload_short), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandGold)
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            val top = sparklineMax(history).toFloat()
            fun line(values: List<Long>, color: Color) {
                if (values.size < 2) return
                val step = size.width / (SPEED_HISTORY_SIZE - 1)
                val startX = size.width - step * (values.size - 1)
                val path = Path()
                values.forEachIndexed { i, v ->
                    val x = startX + step * i
                    val y = size.height - 2f - (v / top) * (size.height - 4f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round))
            }
            line(history.map { it.up }, BrandGold)
            line(history.map { it.down }, downColor)
        }
    }
}

/** Pure: the graph's top value, at least 1 KB/s so a quiet line stays near the bottom. */
internal fun sparklineMax(history: List<SpeedSample>): Long =
    max(1024L, history.maxOfOrNull { max(it.down, it.up) } ?: 0L)

// ---------- Quick servers ----------

/** Servers of the selected group as raised cards; one tap selects a server. */
@Composable
internal fun QuickServers(
    rows: List<ServerRowUiModel>,
    selectedGuid: String?,
    onSelect: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val palette = configPalette()
    val selectedLabel = stringResource(R.string.acc_selected_server)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(rows, key = { it.guid }) { row ->
            val isSelected = row.guid == selectedGuid
            val locked = ConfigLockManager.isLocked(row.profile)
            val family = ProtocolFilter.of(row.profile)
            val parts = typeParts(row.profile, locked)
            val kind = when {
                locked -> stringResource(R.string.config_locked)
                row.profile.configType == EConfigType.SSH && parts.size > 1 -> parts[1]
                else -> parts.first()
            }
            val shape = RoundedCornerShape(18.dp)
            Column(
                modifier = Modifier
                    .width(152.dp)
                    .padding(bottom = 6.dp)
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            listOf(colors.surfaceContainerHigh, colors.surfaceContainer)
                        )
                    )
                    .border(if (isSelected) 1.5.dp else 1.dp, if (isSelected) BrandGold else colors.outlineVariant, shape)
                    .semantics { if (isSelected) stateDescription = selectedLabel }
                    .clickable(role = Role.RadioButton) { onSelect(row.guid) }
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = if (isSelected) stringResource(R.string.config_in_use) else protocolName(row.profile),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isSelected) OnBrandGold else palette.familyColor(family),
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) BrandGold else palette.familyBg(family))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Text(
                    text = row.remarks,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = kind + " · " + pingText(row.testDelayMillis),
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Small extruded hexagon (gold) used for the active tab and badges. */
@Composable
internal fun HexChip3D(size: Dp, face: Color, side: Color, modifier: Modifier = Modifier, inner: Color? = null) {
    Canvas(modifier = modifier.size(size)) {
        val r = this.size.minDimension * 0.42f
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f - r * 0.12f
        drawPath(hexagon(Offset(cx, cy + r * 0.2f), r), side)
        drawPath(hexagon(Offset(cx, cy), r), Brush.verticalGradient(listOf(lerp(face, Color.White, 0.25f), face), startY = cy - r, endY = cy + r))
        drawPath(hexagon(Offset(cx, cy), r), Color.White.copy(alpha = 0.3f), style = Stroke(width = r * 0.08f))
        if (inner != null) drawPath(hexagon(Offset(cx, cy), r * 0.42f), inner)
    }
}

private const val STANDARD_GRAVITY = 9.80665f

// ---------- Proxy Tethering card ----------

/** Shown while connected with Proxy Tethering on: the address other devices use, Copy and QR. */
@Composable
internal fun TetherCard(info: TetherInfo) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var qr by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<android.graphics.Bitmap?>(null) }
    val copiedText = stringResource(R.string.tether_copied)
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(colors.surfaceContainerHigh, colors.surfaceContainer)))
            .border(1.dp, colors.outlineVariant, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.tether_card_title),
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = BrandGold,
        )
        val ip = info.ip
        if (ip == null) {
            Text(
                text = stringResource(R.string.tether_card_no_hotspot),
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
            )
        } else {
            val lines = com.v2ray.ang.handler.TetherManager.guide(ip, info.socksPort, info.httpPort)
            Text(
                text = lines[0] + "\n" + lines[1],
                fontSize = 13.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = colors.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TetherButton(stringResource(R.string.tether_copy)) {
                    com.v2ray.ang.util.Utils.setClipboard(context, lines.joinToString("\n"))
                    android.widget.Toast.makeText(context, copiedText, android.widget.Toast.LENGTH_SHORT).show()
                }
                TetherButton(stringResource(R.string.tether_qr)) {
                    scope.launch {
                        qr = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                            com.v2ray.ang.util.QRCodeDecoder.createQRCode(lines[2])
                        }
                    }
                }
            }
        }
    }
    com.v2ray.ang.ui.compose.QRCodeDialog(bitmap = qr, onDismiss = { qr = null })
}

@Composable
private fun TetherButton(label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceContainerHighest)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
    }
}
