package com.v2ray.ang.ui.main

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.ui.compose.ThemeManager
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// Cassanova brand colors (the logo's blue and gold).
private val BrandBlue = Color(0xFF2F55D4)
private val BrandBlueDeep = Color(0xFF1E3FB0)
private val BrandGold = Color(0xFFF5B530)
private val OnGold = Color(0xFF1B1608)
private val GreenText = Color(0xFF6EE7A0)
private val GreenBg = Color(0xFF13301F)
private val GreenDot = Color(0xFF34D27A)
private val GreenTextLight = Color(0xFF1F8A4C)

/** Tabs of the Cassanova shell used by styles A and C. */
private enum class HomeTab { Home, Config, Tools }

/**
 * Styles A and C: a home screen with a bottom navigation; the classic config list (style B)
 * stays reachable as the "Config" tab.
 */
@Composable
fun CassanovaShell(
    style: String,
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
    configContent: @Composable () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.Home) }
    val home by mainViewModel.home.collectAsStateWithLifecycle()
    val isLight = style == ThemeManager.STYLE_C

    androidx.activity.compose.BackHandler(enabled = tab != HomeTab.Home) { tab = HomeTab.Home }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (isLight) {
                PillNav(
                    current = tab,
                    onSelect = { tab = it },
                    onSettings = { onNavigate(MainDestination.Settings) },
                )
            } else {
                BottomNav(
                    current = tab,
                    onSelect = { tab = it },
                    onSettings = { onNavigate(MainDestination.Settings) },
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            when (tab) {
                HomeTab.Home -> if (isLight) {
                    HomeStyleC(home, onAction, onNavigate)
                } else {
                    HomeStyleA(home, onAction, onNavigate, onOpenConfigs = { tab = HomeTab.Config })
                }

                HomeTab.Config -> configContent()
                HomeTab.Tools -> ToolsTab(onAction, onNavigate)
            }
        }
    }
}

// ---------- Style A: big hexagon button (dark) ----------

@Composable
private fun HomeStyleA(
    home: HomeState,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
    onOpenConfigs: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BrandTitle(dark = true)
            IconTile(
                label = stringResource(R.string.tool_log),
                iconRes = R.drawable.ic_logcat_24dp,
                onClick = { onNavigate(MainDestination.Logcat) },
            )
        }

        Spacer(Modifier.height(18.dp))
        StatusPill(home.isRunning, light = false)
        Spacer(Modifier.height(6.dp))
        Text(
            text = formatDuration(home.connectedAtMillis),
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.onBackground,
        )

        Spacer(Modifier.height(16.dp))
        HexConnectButton(
            running = home.isRunning,
            size = 220.dp,
            onClick = { onAction(MainAction.ToggleService) },
        )
        Text(
            text = stringResource(if (home.isRunning) R.string.home_tap_to_disconnect else R.string.home_tap_to_connect),
            fontSize = 13.sp,
            color = colors.onSurfaceVariant,
        )

        Spacer(Modifier.height(18.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceContainer)
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.home_active_server),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        text = home.serverName.ifBlank { stringResource(R.string.home_no_server) },
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TagRow(home.serverTags, light = false)
                }
                TextTile(
                    label = stringResource(R.string.home_change),
                    onClick = onOpenConfigs,
                )
            }
            StatGrid(home, light = false)
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ---------- Style C: light dashboard ----------

@Composable
private fun HomeStyleC(
    home: HomeState,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(BrandBlue)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = greeting(), fontSize = 13.sp, color = Color(0xFFD6DFFF))
                    Text(
                        text = stringResource(R.string.app_name),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                    )
                }
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = if (home.isRunning) {
                            stringResource(R.string.home_connected) + " · " + formatDuration(home.connectedAtMillis)
                        } else {
                            stringResource(R.string.home_disconnected)
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (home.isRunning) GreenTextLight else Color(0xFF5A6380),
                    )
                    Text(
                        text = home.serverName.ifBlank { stringResource(R.string.home_no_server) },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF141A2E),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = home.serverTags.joinToString(" · "),
                        fontSize = 12.sp,
                        color = Color(0xFF5A6380),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PowerCircleButton(running = home.isRunning, onClick = { onAction(MainAction.ToggleService) })
            }
        }

        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatGrid(home, light = true, large = true)

            Text(
                text = stringResource(R.string.home_quick_tools),
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.onBackground,
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTool(stringResource(R.string.tool_import_qr), R.drawable.ic_scan_24dp, light = true) { onAction(MainAction.ImportQRcode) }
                QuickTool(stringResource(R.string.tool_import_clipboard), R.drawable.ic_copy, light = true) { onAction(MainAction.ImportClipboard) }
                QuickTool(stringResource(R.string.tool_log), R.drawable.ic_logcat_24dp, light = true) { onNavigate(MainDestination.Logcat) }
                QuickTool(stringResource(R.string.tool_test_all), R.drawable.ic_refresh_24dp, light = true) { onAction(MainAction.TestRealAllServers) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------- Tools tab (style A) ----------

@Composable
private fun ToolsTab(onAction: (MainAction) -> Unit, onNavigate: (MainDestination) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_tools),
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.onBackground,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTool(stringResource(R.string.tool_import_qr), R.drawable.ic_scan_24dp) { onAction(MainAction.ImportQRcode) }
            QuickTool(stringResource(R.string.tool_import_clipboard), R.drawable.ic_copy) { onAction(MainAction.ImportClipboard) }
            QuickTool(stringResource(R.string.tool_test_all), R.drawable.ic_refresh_24dp) { onAction(MainAction.TestRealAllServers) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTool(stringResource(R.string.tool_log), R.drawable.ic_logcat_24dp) { onNavigate(MainDestination.Logcat) }
            QuickTool(stringResource(R.string.tool_subscriptions), R.drawable.ic_subscriptions_24dp) { onNavigate(MainDestination.Subscriptions) }
            QuickTool(stringResource(R.string.tool_routing), R.drawable.ic_routing_24dp) { onNavigate(MainDestination.Routing) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTool(stringResource(R.string.tool_per_app), R.drawable.ic_per_apps_24dp) { onNavigate(MainDestination.PerAppProxy) }
            QuickTool(stringResource(R.string.tool_backup), R.drawable.ic_backup_24dp) { onNavigate(MainDestination.BackupRestore) }
            QuickTool(stringResource(R.string.nav_settings), R.drawable.ic_settings_24dp) { onNavigate(MainDestination.Settings) }
        }
    }
}

// ---------- Shared pieces ----------

@Composable
private fun BrandTitle(dark: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(44.dp),
        )
        Text(
            text = "Cassanova ",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (dark) MaterialTheme.colorScheme.onBackground else Color.White,
        )
        Text(text = "Tunnel", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = BrandGold)
    }
}

@Composable
private fun StatusPill(running: Boolean, light: Boolean) {
    val bg = if (running) GreenBg else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (running) GreenText else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (light && running) Color(0xFFCDEFD9) else bg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (running) GreenDot else MaterialTheme.colorScheme.outline)
        )
        Text(
            text = stringResource(if (running) R.string.home_protected else R.string.home_not_protected),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (light && running) GreenTextLight else fg,
        )
    }
}

@Composable
private fun TagRow(tags: List<String>, light: Boolean) {
    if (tags.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEachIndexed { index, tag ->
            val (bg, fg) = when (index) {
                0 -> if (light) Color(0xFFFFEDC2) to Color(0xFF6B4A00) else Color(0xFF2A2410) to Color(0xFFF5C451)
                1 -> if (light) Color(0xFFDCE4FF) to BrandBlueDeep else Color(0xFF1A2550) to Color(0xFF9DB4FF)
                else -> if (light) Color(0xFFCDEFD9) to GreenTextLight else GreenBg to GreenText
            }
            Text(
                text = tag,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = fg,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(bg)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/** Ping, data today, download and upload; 2×2 tiles. */
@Composable
private fun StatGrid(home: HomeState, light: Boolean, large: Boolean = false) {
    val ping = when {
        home.isTesting && home.isRunning -> stringResource(R.string.connection_test_testing)
        home.pingFailed -> stringResource(R.string.home_ping_failed)
        home.pingMillis != null -> stringResource(R.string.server_test_delay_value, home.pingMillis ?: 0L)
        else -> "—"
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stringResource(R.string.home_ping), ping, light, large)
            StatTile(stringResource(R.string.home_data_today), home.todayBytes.toTrafficString(), light, large)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stringResource(R.string.home_download), home.downBytesPerSec.toSpeedString(), light, large)
            StatTile(stringResource(R.string.home_upload), home.upBytesPerSec.toSpeedString(), light, large)
        }
    }
}

@Composable
private fun RowScope.StatTile(label: String, value: String, light: Boolean, large: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(if (large) 16.dp else 12.dp))
            .background(if (light) Color.White else colors.surfaceContainerLowest)
            .padding(if (large) 14.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = label, fontSize = 12.sp, color = colors.onSurfaceVariant)
        Text(
            text = value,
            fontSize = if (large) 20.sp else 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun RowScope.QuickTool(label: String, iconRes: Int, light: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (light) Color.White else colors.surfaceContainer)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (light) BrandBlue else BrandGold,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun IconTile(label: String, iconRes: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainer)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TextTile(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** The logo-shaped hexagon connect button of style A. */
@Composable
private fun HexConnectButton(running: Boolean, size: Dp, onClick: () -> Unit) {
    val label = stringResource(if (running) R.string.acc_stop else R.string.acc_start)
    val transition = rememberInfiniteTransition(label = "hexPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "hexPulseAlpha",
    )
    val idle = MaterialTheme.colorScheme.surfaceContainerHighest
    val idleInner = MaterialTheme.colorScheme.outline
    val iconColor = MaterialTheme.colorScheme.background
    Canvas(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        val r = min(this.size.width, this.size.height) / 2f
        drawPath(hexagon(center, r * 0.98f), color = (if (running) BrandGold else idleInner).copy(alpha = if (running) pulse else 0.2f), style = Stroke(width = r * 0.08f))
        drawPath(hexagon(center, r * 0.86f), color = if (running) BrandBlue else idle)
        drawPath(hexagon(center, r * 0.58f), color = if (running) BrandGold else idleInner)
        drawPower(center, r * 0.22f, iconColor, r * 0.07f)
    }
}

@Composable
private fun PowerCircleButton(running: Boolean, onClick: () -> Unit) {
    val label = stringResource(if (running) R.string.acc_stop else R.string.acc_start)
    Canvas(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (running) BrandGold else Color(0xFFE1E5F0))
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        drawPower(center, size.minDimension * 0.22f, if (running) OnGold else Color(0xFF5A6380), size.minDimension * 0.06f)
    }
}

private fun hexagon(center: Offset, radius: Float): Path = Path().apply {
    for (i in 0 until 6) {
        val angle = Math.toRadians((60.0 * i) - 90.0)
        val x = center.x + radius * cos(angle).toFloat()
        val y = center.y + radius * sin(angle).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun DrawScope.drawPower(center: Offset, radius: Float, color: Color, width: Float) {
    drawArc(
        color = color,
        startAngle = -60f,
        sweepAngle = 300f,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = width, cap = StrokeCap.Round),
    )
    drawLine(
        color = color,
        start = Offset(center.x, center.y - radius * 1.25f),
        end = Offset(center.x, center.y - radius * 0.15f),
        strokeWidth = width,
        cap = StrokeCap.Round,
    )
}

// ---------- Navigation bars ----------

@Composable
private fun BottomNav(current: HomeTab, onSelect: (HomeTab) -> Unit, onSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerLow)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        NavItem(stringResource(R.string.nav_home), R.drawable.ic_home_24dp, current == HomeTab.Home) { onSelect(HomeTab.Home) }
        NavItem(stringResource(R.string.nav_config), R.drawable.ic_subscriptions_24dp, current == HomeTab.Config) { onSelect(HomeTab.Config) }
        NavItem(stringResource(R.string.nav_tools), R.drawable.ic_routing_24dp, current == HomeTab.Tools) { onSelect(HomeTab.Tools) }
        NavItem(stringResource(R.string.nav_settings), R.drawable.ic_settings_24dp, false, onSettings)
    }
}

@Composable
private fun RowScope.NavItem(label: String, iconRes: Int, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) BrandGold else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(text = label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun PillNav(current: HomeTab, onSelect: (HomeTab) -> Unit, onSettings: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 20.dp, end = 20.dp, bottom = 14.dp, top = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF141A2E))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PillItem(stringResource(R.string.nav_home), current == HomeTab.Home) { onSelect(HomeTab.Home) }
            PillItem(stringResource(R.string.nav_config), current == HomeTab.Config) { onSelect(HomeTab.Config) }
            PillItem(stringResource(R.string.nav_settings), false, onSettings)
        }
    }
}

@Composable
private fun RowScope.PillItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) BrandGold else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (selected) OnGold else Color(0xFFC9CFE0),
        )
    }
}

// ---------- Formatting ----------

private fun formatDuration(connectedAtMillis: Long): String {
    if (connectedAtMillis <= 0L) return "00:00:00"
    val total = ((System.currentTimeMillis() - connectedAtMillis) / 1000L).coerceAtLeast(0L)
    return "%02d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
}

@Composable
private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return stringResource(
        when (hour) {
            in 4..10 -> R.string.greeting_morning
            in 11..14 -> R.string.greeting_afternoon
            in 15..17 -> R.string.greeting_evening
            else -> R.string.greeting_night
        }
    )
}
