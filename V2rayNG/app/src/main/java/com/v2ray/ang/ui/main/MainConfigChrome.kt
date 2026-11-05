package com.v2ray.ang.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.Brush
import com.v2ray.ang.R
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.ui.compose.BrandBlue
import com.v2ray.ang.ui.compose.BrandBlueDeep
import com.v2ray.ang.ui.compose.BrandBlueMid
import com.v2ray.ang.ui.compose.BrandGold
import com.v2ray.ang.ui.compose.CassanovaFont
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.OnBrandGold
import com.v2ray.ang.ui.compose.verticalScrollbar

// ---------- Menus shared by the A and C headers ----------

/** A header button that opens the import menu ("add") or the list menu ("more"). */
@Composable
internal fun ConfigMenuButton(
    anchor: @Composable (open: () -> Unit) -> Unit,
    menu: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp - statusBar - navBar - 20.dp
    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
        anchor { expanded = true }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            scrollState = scrollState,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .heightIn(max = maxHeight)
                .verticalScrollbar(scrollState)
        ) {
            menu { expanded = false }
        }
    }
}

@Composable
internal fun ImportMenuButton(onAction: (MainAction) -> Unit, anchor: @Composable (open: () -> Unit) -> Unit) =
    ConfigMenuButton(anchor = anchor) { dismiss ->
        ImportMenuContent(onAction = { dismiss(); onAction(it) })
    }

@Composable
internal fun MoreMenuButton(onMoreMenuAction: (MainMoreMenuAction) -> Unit, anchor: @Composable (open: () -> Unit) -> Unit) =
    ConfigMenuButton(anchor = anchor) { dismiss ->
        MoreMenuContent { dismiss(); onMoreMenuAction(it) }
    }

/** Square icon tile with an accessible name. */
@Composable
internal fun HeaderIconTile(
    iconRes: Int,
    label: String,
    background: Color,
    tint: Color,
    onClick: () -> Unit,
    border: Color? = null,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    size: Dp = 44.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(iconRes), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

// ---------- Search field (C always, A when toggled) ----------

@Composable
internal fun ConfigSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    background: Color,
    textColor: Color,
    hintColor: Color,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    onClose: (() -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
    val hint = stringResource(R.string.config_search_hint)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_search_24dp), contentDescription = null, tint = hintColor, modifier = Modifier.size(20.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(text = hint, fontSize = 14.sp, color = hintColor, maxLines = 1)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = CassanovaFont, fontSize = 14.sp, color = textColor),
                cursorBrush = SolidColor(textColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = hint },
            )
        }
        if (onClose != null) {
            HeaderIconTile(
                iconRes = R.drawable.ic_close_24dp,
                label = stringResource(R.string.acc_close_search),
                background = Color.Transparent,
                tint = hintColor,
                onClick = onClose,
                size = 40.dp,
            )
        }
    }
}

// ---------- Style A ----------

@Composable
internal fun ConfigHeaderA(
    configCount: Int,
    groupCount: Int,
    showSearch: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    onAction: (MainAction) -> Unit,
    onMoreMenuAction: (MainMoreMenuAction) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = stringResource(R.string.nav_config), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = colors.onBackground)
                Text(
                    text = stringResource(R.string.config_count_groups, configCount, groupCount),
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                )
            }
            if (!showSearch) {
                HeaderIconTile(
                    iconRes = R.drawable.ic_search_24dp,
                    label = stringResource(R.string.acc_search),
                    background = colors.surfaceContainer,
                    tint = colors.onSurfaceVariant,
                    border = colors.outlineVariant,
                    onClick = onSearchOpen,
                )
            }
            ImportMenuButton(onAction) { open ->
                RaisedGoldTile(R.drawable.ic_add_24dp, stringResource(R.string.acc_add), open)
            }
            MoreMenuButton(onMoreMenuAction) { open ->
                HeaderIconTile(R.drawable.ic_more_vert_24dp, stringResource(R.string.acc_more), colors.surfaceContainer, colors.onSurfaceVariant, open, border = colors.outlineVariant)
            }
        }
        if (showSearch) {
            ConfigSearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                background = colors.surfaceContainer,
                textColor = colors.onSurface,
                hintColor = colors.onSurfaceVariant,
                autoFocus = true,
                onClose = onSearchClose,
            )
        }
    }
}

/** Gold pill chips with a count per group. */
@Composable
internal fun GroupChipsA(
    groups: List<GroupMapItem>,
    selectedIndex: Int,
    mainViewModel: MainViewModel,
    onSelect: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        groups.forEachIndexed { index, group ->
            key(group.id) {
                val servers by remember(group.id, mainViewModel) { mainViewModel.serversForGroup(group.id) }
                    .collectAsStateWithLifecycle()
                val selected = index == selectedIndex
                val shape = RoundedCornerShape(999.dp)
                Row(
                    modifier = Modifier
                        .heightIn(min = 38.dp)
                        .clip(shape)
                        .background(if (selected) BrandGold else colors.surfaceContainer)
                        .then(if (selected) Modifier else Modifier.border(1.dp, colors.outlineVariant, shape))
                        .selectable(selected = selected, role = Role.Tab) { onSelect(index) }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = group.remarks,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (selected) OnBrandGold else colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(
                        text = servers.size.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) BrandGold else colors.onSurfaceVariant,
                        modifier = Modifier
                            .clip(shape)
                            .background(if (selected) OnBrandGold else colors.surfaceContainerHighest)
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

/** "CONFIG LIST ··· Test all" (A) or "Group: X ▾ ··· Test all" (C). */
@Composable
internal fun ConfigListBar(
    onTestAll: () -> Unit,
    leading: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Box(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onTestAll)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.tool_test_all),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (LocalDarkTheme.current) MaterialTheme.colorScheme.primary else BrandBlue,
            )
        }
    }
}

/** Mini status card above the bottom navigation of style A. */
@Composable
internal fun MiniStatusCardA(home: HomeState, onAction: (MainAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val palette = configPalette()
    val ping = home.pingMillis?.let { stringResource(R.string.server_test_delay_value, it) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceContainer)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button) { onAction(MainAction.TestCurrentServer) }
            .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = if (home.isRunning) {
                    stringResource(R.string.config_status_connected, formatDuration(home.connectedAtMillis))
                } else {
                    stringResource(R.string.config_status_disconnected)
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (home.isRunning) palette.green else colors.onSurfaceVariant,
            )
            Text(
                text = listOfNotNull(
                    stringResource(R.string.home_status_data_today, home.todayBytes.toTrafficString()),
                    ping,
                ).joinToString(" · "),
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PowerTile(running = home.isRunning, size = 48.dp, corner = 14.dp, raised = true) { onAction(MainAction.ToggleService) }
    }
}

/** Gold square power button (A status card, B status bar). */
@Composable
internal fun PowerTile(running: Boolean, size: Dp, corner: Dp, raised: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(if (running) R.string.acc_stop else R.string.acc_start)
    val glyph = if (running) OnBrandGold else colors.onSurfaceVariant
    if (raised) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        Raised3D(
            shape = RoundedCornerShape(corner),
            face = if (running) {
                Brush.verticalGradient(listOf(Color(0xFFFFD66E), Color(0xFFE39A12)))
            } else {
                Brush.verticalGradient(listOf(colors.surfaceContainerHighest, colors.surfaceContainerHigh))
            },
            side = if (running) Color(0xFF8A5600) else colors.outlineVariant,
            pressed = pressed,
            modifier = Modifier
                .size(width = size, height = size + 4.dp)
                .clickable(interactionSource = interaction, indication = null, onClickLabel = label, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
        ) {
            Canvas(Modifier.size(size)) {
                drawPower(center, this.size.minDimension * 0.2f, glyph, this.size.minDimension * 0.06f)
            }
        }
        return
    }
    Canvas(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(if (running) BrandGold else colors.surfaceContainerHighest)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        drawPower(center, this.size.minDimension * 0.2f, glyph, this.size.minDimension * 0.06f)
    }
}

/** Gold square tile with a raised edge (style A "add"). */
@Composable
private fun RaisedGoldTile(iconRes: Int, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Raised3D(
        shape = RoundedCornerShape(14.dp),
        face = Brush.verticalGradient(listOf(Color(0xFFFFD66E), Color(0xFFE39A12))),
        side = Color(0xFF8A5600),
        pressed = pressed,
        modifier = Modifier
            .size(width = 44.dp, height = 48.dp)
            .clickable(interactionSource = interaction, indication = null, onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(iconRes), contentDescription = null, tint = OnBrandGold, modifier = Modifier.size(22.dp))
        }
    }
}

// ---------- Style C ----------

@Composable
internal fun ConfigHeaderC(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filter: ProtocolFilter,
    onFilterChange: (ProtocolFilter) -> Unit,
    onAction: (MainAction) -> Unit,
    onMoreMenuAction: (MainMoreMenuAction) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(BrandBlue)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.nav_config),
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            MoreMenuButton(onMoreMenuAction) { open ->
                HeaderIconTile(R.drawable.ic_more_vert_24dp, stringResource(R.string.acc_more), Color.Transparent, Color.White, open, shape = CircleShape)
            }
            ImportMenuButton(onAction) { open ->
                HeaderIconTile(R.drawable.ic_add_24dp, stringResource(R.string.acc_add), BrandGold, OnBrandGold, open, shape = CircleShape)
            }
        }
        ConfigSearchField(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            background = if (dark) colors.surfaceContainer else Color.White,
            textColor = if (dark) colors.onSurface else Color(0xFF141A2E),
            hintColor = if (dark) colors.onSurfaceVariant else Color(0xFF5A6380),
        )
        ProtocolChips(filter, onFilterChange)
    }
}

@Composable
private fun ProtocolChips(filter: ProtocolFilter, onFilterChange: (ProtocolFilter) -> Unit) {
    val groupLabel = stringResource(R.string.config_filter_protocol)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .semantics { contentDescription = groupLabel },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ProtocolFilter.entries.forEach { option ->
            val selected = option == filter
            Box(
                modifier = Modifier
                    .heightIn(min = 34.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selected) Color.White else BrandBlueMid)
                    .selectable(selected = selected, role = Role.RadioButton) { onFilterChange(option) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = protocolFilterLabel(option),
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (selected) BrandBlueDeep else Color.White,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun protocolFilterLabel(filter: ProtocolFilter): String = when (filter) {
    ProtocolFilter.ALL -> stringResource(R.string.config_filter_all)
    ProtocolFilter.SSH -> "SSH"
    ProtocolFilter.XRAY -> "Xray"
    ProtocolFilter.HY2 -> "Hy2"
    ProtocolFilter.SLOWDNS -> stringResource(R.string.ssh_mode_short_slowdns)
}

/** "Group: X ▾" button that opens the list of groups. */
@Composable
internal fun RowScope.GroupDropdownC(
    groups: List<GroupMapItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    var expanded by remember { mutableStateOf(false) }
    val name = groups.getOrNull(selectedIndex)?.remarks.orEmpty()
    Box(modifier = Modifier.weight(1f)) {
        Row(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceContainer)
                .border(1.dp, if (dark) colors.outlineVariant else Color(0xFFD5DAE8), RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, enabled = groups.size > 1) { expanded = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.config_group_label, name),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (groups.size > 1) {
                Icon(painterResource(R.drawable.ic_expand_more_24dp), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = colors.surface,
        ) {
            groups.forEachIndexed { index, group ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = group.remarks,
                            fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(index)
                    },
                )
            }
        }
    }
}
