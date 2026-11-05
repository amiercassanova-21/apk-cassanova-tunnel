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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.stateDescription
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.ConfigLockManager
import com.v2ray.ang.ui.compose.LocalDarkTheme
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.shadow
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
private val BrandBlueMid = Color(0xFF2448BF)
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
                    HomeStyleC(home, mainViewModel, onAction, onNavigate, onOpenConfigs = { tab = HomeTab.Config })
                } else {
                    HomeStyleA(home, mainViewModel, onAction, onNavigate, onOpenConfigs = { tab = HomeTab.Config })
                }

                HomeTab.Config -> configContent()
                HomeTab.Tools -> ToolsTab(onAction, onNavigate)
            }
        }
    }
}

// ---------- Style A: the Cassanova home (3D hexagon button) ----------

@Composable
private fun HomeStyleA(
    home: HomeState,
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
    onOpenConfigs: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val groupState by remember(uiState.selectedGroupId) {
        mainViewModel.serverGroupState(uiState.selectedGroupId)
    }.collectAsStateWithLifecycle()
    val glow = if (dark) Color(0xFF18204A) else colors.surfaceContainerHigh
    val background = colors.background
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .drawBehind {
                // A soft blue light behind the button, so it seems to float.
                drawRect(
                    Brush.radialGradient(
                        listOf(glow, background),
                        center = Offset(size.width / 2f, size.height * 0.3f),
                        radius = size.width * 0.9f,
                    )
                )
            }
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BrandTitle(dark = true)
            RaisedIconTile(
                label = stringResource(R.string.tool_log),
                iconRes = R.drawable.ic_logcat_24dp,
                onClick = { onNavigate(MainDestination.ConnectionLog) },
            )
        }

        Spacer(Modifier.height(10.dp))
        StatusPill(home.isRunning, light = false)
        Spacer(Modifier.height(4.dp))
        Text(
            text = formatDuration(home.connectedAtMillis),
            style = TextStyle(
                fontFamily = MaterialTheme.typography.headlineLarge.fontFamily,
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                color = colors.onBackground,
                shadow = Shadow(
                    color = if (dark) Color(0xFF05070F) else Color(0x33000000),
                    offset = Offset(0f, 4f),
                    blurRadius = 2f,
                ),
            ),
        )

        HexPowerButton3D(
            running = home.isRunning,
            onClick = { onAction(MainAction.ToggleService) },
        )
        Text(
            text = stringResource(if (home.isRunning) R.string.home_tap_to_disconnect else R.string.home_tap_to_connect),
            fontSize = 13.sp,
            color = colors.onSurfaceVariant,
        )

        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            home.tether?.let { TetherCard(it) }
            StatsRow3D(home)
            SpeedSparkline(home.speedHistory)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_quick_servers),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button, onClick = onOpenConfigs)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.home_all_configs),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (dark) BrandGold else BrandBlueDeep,
                )
            }
        }
        if (groupState.rows.isEmpty()) {
            Text(
                text = stringResource(R.string.home_no_server),
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        } else {
            QuickServers(
                rows = groupState.rows,
                selectedGuid = uiState.selectedGuid,
                onSelect = { guid -> onAction(MainAction.SelectServer(guid)) },
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Square icon tile with a raised edge (style A header). */
@Composable
private fun RaisedIconTile(label: String, iconRes: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Raised3D(
        shape = RoundedCornerShape(14.dp),
        face = Brush.verticalGradient(listOf(colors.surfaceContainerHigh, colors.surfaceContainer)),
        side = if (LocalDarkTheme.current) Color(0xFF0A0E1C) else colors.outlineVariant,
        pressed = pressed,
        border = colors.outlineVariant,
        modifier = Modifier
            .size(width = 44.dp, height = 48.dp)
            .clickable(interactionSource = interaction, indication = null, onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Icon(painter = painterResource(iconRes), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

// ---------- Style C: dashboard (light by default, follows the theme) ----------

@Composable
private fun HomeStyleC(
    home: HomeState,
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
    onOpenConfigs: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val palette = configPalette()
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val groupState by remember(uiState.selectedGroupId) {
        mainViewModel.serverGroupState(uiState.selectedGroupId)
    }.collectAsStateWithLifecycle()
    val rows = groupState.rows
    // Until the user picks a protocol, follow the protocol of the selected server.
    var chosenFilter by rememberSaveable { mutableStateOf<ProtocolFilter?>(null) }
    val filter = chosenFilter
        ?: rows.firstOrNull { it.guid == uiState.selectedGuid }?.let { ProtocolFilter.of(it.profile) }
        ?: ProtocolFilter.SSH
    val filteredRows = remember(rows, filter) { rows.filter { filter.matches(it.profile) } }

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

            ProtocolSegments(selected = filter, onSelect = { chosenFilter = it })

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surfaceContainer)
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
                        color = if (home.isRunning) palette.green else colors.onSurfaceVariant,
                    )
                    Text(
                        text = home.serverName.ifBlank { stringResource(R.string.home_no_server) },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = home.serverTags.joinToString(" · "),
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PowerCircleButton(running = home.isRunning, onClick = { onAction(MainAction.ToggleService) })
            }
        }

        Column(
            modifier = Modifier.padding(top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                StatGrid(home, light = true, large = true)
            }

            Row(
                modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_servers_of, protocolFilterLabel(filter)),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClick = onOpenConfigs)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.home_see_all),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (LocalDarkTheme.current) colors.primary else BrandBlue,
                    )
                }
            }
            ServerCarousel(
                rows = filteredRows,
                selectedGuid = uiState.selectedGuid,
                onSelect = { guid -> onAction(MainAction.SelectServer(guid)) },
            )

            Text(
                text = stringResource(R.string.home_quick_tools),
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.onBackground,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
            )
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QuickTool(stringResource(R.string.tool_import_qr), R.drawable.ic_scan_24dp, light = true) { onAction(MainAction.ImportQRcode) }
                QuickTool(stringResource(R.string.tool_import_clipboard), R.drawable.ic_copy, light = true) { onAction(MainAction.ImportClipboard) }
                QuickTool(stringResource(R.string.tool_log), R.drawable.ic_logcat_24dp, light = true) { onNavigate(MainDestination.ConnectionLog) }
                QuickTool(stringResource(R.string.tool_test_all), R.drawable.ic_refresh_24dp, light = true) { onAction(MainAction.TestRealAllServers) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** SSH / Xray / Hy2 / SlowDNS segmented control in the blue header. */
@Composable
private fun ProtocolSegments(selected: ProtocolFilter, onSelect: (ProtocolFilter) -> Unit) {
    val groupLabel = stringResource(R.string.home_choose_protocol)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BrandBlueMid)
            .padding(4.dp)
            .semantics { contentDescription = groupLabel },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(ProtocolFilter.SSH, ProtocolFilter.XRAY, ProtocolFilter.HY2, ProtocolFilter.SLOWDNS).forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Color.White else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Tab) { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = protocolFilterLabel(option),
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (isSelected) BrandBlueDeep else Color.White,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Horizontal list of servers of the chosen protocol; tapping one selects it. */
@Composable
private fun ServerCarousel(
    rows: List<ServerRowUiModel>,
    selectedGuid: String?,
    onSelect: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    if (rows.isEmpty()) {
        Text(
            text = stringResource(R.string.config_no_match),
            fontSize = 13.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        return
    }
    val accent = if (LocalDarkTheme.current) BrandGold else colors.primary
    val selectedLabel = stringResource(R.string.acc_selected_server)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(rows, key = { it.guid }) { row ->
            val isSelected = row.guid == selectedGuid
            val locked = ConfigLockManager.isLocked(row.profile)
            val parts = typeParts(row.profile, locked)
            val kind = if (row.profile.configType == EConfigType.SSH && parts.size > 1) parts[1] else parts.first()
            val shape = RoundedCornerShape(16.dp)
            Column(
                modifier = Modifier
                    .width(150.dp)
                    .clip(shape)
                    .background(colors.surfaceContainer)
                    .border(if (isSelected) 2.dp else 1.dp, if (isSelected) accent else colors.outlineVariant, shape)
                    .semantics { if (isSelected) stateDescription = selectedLabel }
                    .clickable(role = Role.RadioButton) { onSelect(row.guid) }
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
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
            QuickTool(stringResource(R.string.tool_log), R.drawable.ic_logcat_24dp) { onNavigate(MainDestination.ConnectionLog) }
            QuickTool(stringResource(R.string.tool_subscriptions), R.drawable.ic_subscriptions_24dp) { onNavigate(MainDestination.Subscriptions) }
            QuickTool(stringResource(R.string.tool_routing), R.drawable.ic_routing_24dp) { onNavigate(MainDestination.Routing) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTool(stringResource(R.string.tool_per_app), R.drawable.ic_per_apps_24dp) { onNavigate(MainDestination.PerAppProxy) }
            QuickTool(stringResource(R.string.tool_backup), R.drawable.ic_backup_24dp) { onNavigate(MainDestination.BackupRestore) }
            QuickTool(stringResource(R.string.nav_settings), R.drawable.ic_settings_24dp) { onNavigate(MainDestination.Settings) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTool(stringResource(R.string.tool_host_to_ip), R.drawable.ic_dns_24dp) { onNavigate(MainDestination.HostToIp) }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
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
    val shape = RoundedCornerShape(999.dp)
    val fg = when {
        running && light -> GreenTextLight
        running -> GreenText
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val bg = when {
        running && light -> Brush.verticalGradient(listOf(Color(0xFFDDF6E6), Color(0xFFCDEFD9)))
        running -> Brush.verticalGradient(listOf(Color(0xFF1B4A30), GreenBg))
        else -> Brush.verticalGradient(
            listOf(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.surfaceContainerHigh)
        )
    }
    Row(
        modifier = Modifier
            // Connected: a soft green glow around the pill.
            .shadow(if (running) 10.dp else 0.dp, shape, ambientColor = GreenDot, spotColor = GreenDot)
            .clip(shape)
            .background(bg)
            .border(1.dp, Color.White.copy(alpha = if (running) 0.12f else 0.06f), shape)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .shadow(if (running) 4.dp else 0.dp, CircleShape, ambientColor = GreenDot, spotColor = GreenDot)
                .clip(CircleShape)
                .background(if (running) GreenDot else MaterialTheme.colorScheme.outline)
        )
        Text(
            text = stringResource(if (running) R.string.home_protected else R.string.home_not_protected),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
        )
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
    // "73 ms" → big "73" with a smaller "ms".
    val split = value.lastIndexOf(' ')
    val number = if (split > 0) value.substring(0, split) else value
    val unit = if (split > 0) value.substring(split + 1) else ""
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(if (large) 16.dp else 12.dp))
            .background(if (light) colors.surfaceContainer else colors.surfaceContainerLowest)
            .padding(if (large) 14.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = label, fontSize = 12.sp, color = colors.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = number,
                fontSize = if (large) 22.sp else 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.onSurface,
                maxLines = 1,
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = unit,
                    fontSize = if (large) 13.sp else 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = if (large) 3.dp else 2.dp),
                )
            }
        }
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
            .background(colors.surfaceContainer)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (light && !LocalDarkTheme.current) BrandBlue else BrandGold,
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
private fun PowerCircleButton(running: Boolean, onClick: () -> Unit) {
    val idleGlyph = MaterialTheme.colorScheme.onSurfaceVariant
    val label = stringResource(if (running) R.string.acc_stop else R.string.acc_start)
    Canvas(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (running) BrandGold else MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        drawPower(center, size.minDimension * 0.22f, if (running) OnGold else idleGlyph, size.minDimension * 0.06f)
    }
}

internal fun hexagon(center: Offset, radius: Float): Path = Path().apply {
    for (i in 0 until 6) {
        val angle = Math.toRadians((60.0 * i) - 90.0)
        val x = center.x + radius * cos(angle).toFloat()
        val y = center.y + radius * sin(angle).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

internal fun DrawScope.drawPower(center: Offset, radius: Float, color: Color, width: Float) {
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
    val shape = RoundedCornerShape(22.dp)
    // Floating bar: lifted off the screen with a shadow and a light top edge.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(colors.surfaceContainerHigh, colors.surfaceContainerLow)))
                .border(1.dp, colors.outlineVariant, shape)
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            NavItem(stringResource(R.string.nav_home), R.drawable.ic_home_24dp, current == HomeTab.Home) { onSelect(HomeTab.Home) }
            NavItem(stringResource(R.string.nav_config), R.drawable.ic_subscriptions_24dp, current == HomeTab.Config) { onSelect(HomeTab.Config) }
            NavItem(stringResource(R.string.nav_tools), R.drawable.ic_routing_24dp, current == HomeTab.Tools) { onSelect(HomeTab.Tools) }
            NavItem(stringResource(R.string.nav_settings), R.drawable.ic_settings_24dp, false, onSettings)
        }
    }
}

@Composable
private fun RowScope.NavItem(label: String, iconRes: Int, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) BrandGold else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (selected) {
            // Active tab: a small extruded gold hexagon, the logo shape.
            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                HexChip3D(size = 30.dp, face = BrandGold, side = Color(0xFF8A5600))
                Icon(painter = painterResource(iconRes), contentDescription = null, tint = OnGold, modifier = Modifier.size(15.dp))
            }
        } else {
            Icon(painter = painterResource(iconRes), contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        Text(text = label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun PillNav(current: HomeTab, onSelect: (HomeTab) -> Unit, onSettings: () -> Unit) {
    val dark = LocalDarkTheme.current
    val pillShape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 20.dp, end = 20.dp, bottom = 14.dp, top = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(pillShape)
                .background(if (dark) Color(0xFF1D2338) else Color(0xFF141A2E))
                .then(if (dark) Modifier.border(1.dp, Color(0xFF262D45), pillShape) else Modifier)
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

internal fun formatDuration(connectedAtMillis: Long): String {
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
