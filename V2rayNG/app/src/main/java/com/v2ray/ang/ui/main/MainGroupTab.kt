package com.v2ray.ang.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.entities.ServersCache
import kotlinx.coroutines.flow.StateFlow

private val TAB_BAR_PADDING = 3.dp
private val TAB_SPACING = 2.dp
private val TAB_HEIGHT = 36.dp
private val SELECTED_TAB_MARGIN = 12.dp

/**
 * Subscription group tabs (style B): a segmented control whose selected group is a tinted
 * segment labelled "name · count".
 *
 * While all tabs fit, they share the bar width equally (like Telegram's folder tabs); once they
 * no longer fit, they keep their natural width and the bar scrolls horizontally, keeping the
 * selected tab in view.
 */
@Composable
fun GroupTabBar(
    groups: List<GroupMapItem>,
    selectedTabIndex: Int,
    mainViewModel: MainViewModel,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = selectedTabIndex.coerceIn(0, groups.lastIndex)
    val colors = MaterialTheme.colorScheme
    // The plain surface color equals the screen background in dark mode, so the bar needs its own tone there.
    val dark = colors.surface.luminance() < 0.5f

    // Cassanova segmented control: a recessed track with the selected group as a raised segment.
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        color = colors.background,
        border = BorderStroke(0.5.dp, colors.outlineVariant.copy(alpha = if (dark) 0.6f else 0.35f))
    ) {
        BoxWithConstraints {
            val barPaddingPx = with(LocalDensity.current) { TAB_BAR_PADDING.roundToPx() }
            val availablePx = constraints.maxWidth - 2 * barPaddingPx
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(TAB_BAR_PADDING)
            ) {
                EqualOrNaturalTabsLayout(availablePx = availablePx) {
                    groups.forEachIndexed { index, group ->
                        key(group.id) {
                            val serverFlow = remember(group.id, mainViewModel) {
                                mainViewModel.serversForGroup(group.id)
                            }
                            GroupTabItem(
                                group = group,
                                selected = index == selectedIndex,
                                serverFlow = serverFlow,
                                onClick = { onTabClick(index) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Lays the tabs out in a row. If every tab fits in an equal share of [availablePx] they all get
 * that share; otherwise, if they still fit together, the spare space is spread evenly; otherwise
 * they keep their natural width (the parent scrolls).
 */
@Composable
private fun EqualOrNaturalTabsLayout(
    availablePx: Int,
    content: @Composable () -> Unit
) {
    Layout(content = content) { measurables, _ ->
        if (measurables.isEmpty()) return@Layout layout(0, 0) {}

        val spacing = TAB_SPACING.roundToPx()
        val tabHeight = TAB_HEIGHT.roundToPx()
        val count = measurables.size
        val gaps = spacing * (count - 1)
        val natural = measurables.map { it.maxIntrinsicWidth(tabHeight) }
        val equalShare = (availablePx - gaps) / count

        val widths = when {
            availablePx <= 0 -> natural
            natural.all { it <= equalShare } -> List(count) { equalShare }
            natural.sum() + gaps <= availablePx -> {
                val extra = (availablePx - gaps - natural.sum()) / count
                natural.map { it + extra }
            }
            else -> natural
        }

        val placeables = measurables.mapIndexed { i, measurable ->
            measurable.measure(Constraints.fixed(widths[i], tabHeight))
        }
        layout(widths.sum() + gaps, tabHeight) {
            var x = 0
            placeables.forEach { placeable ->
                placeable.placeRelative(x, 0)
                x += placeable.width + spacing
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupTabItem(
    group: GroupMapItem,
    selected: Boolean,
    serverFlow: StateFlow<List<ServersCache>>,
    onClick: () -> Unit
) {
    val servers by serverFlow.collectAsStateWithLifecycle()
    val count = servers.size
    val colors = MaterialTheme.colorScheme
    val bringIntoView = remember { BringIntoViewRequester() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val marginPx = with(LocalDensity.current) { SELECTED_TAB_MARGIN.toPx() }

    // Bring the tab in with some room around it so the pill never ends up flush against the bar's
    // rounded edge; at the first and last tab the scroll stops at the bar's own inner padding.
    LaunchedEffect(selected, size) {
        if (selected && size != IntSize.Zero) {
            bringIntoView.bringIntoView(
                Rect(-marginPx, 0f, size.width + marginPx, size.height.toFloat())
            )
        }
    }

    // One selectable node: name, count and selected state are announced together.
    Row(
        modifier = Modifier
            .bringIntoViewRequester(bringIntoView)
            .onSizeChanged { size = it }
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) configPalette().blueBg else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (count > 0) "${group.remarks} · $count" else group.remarks,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
