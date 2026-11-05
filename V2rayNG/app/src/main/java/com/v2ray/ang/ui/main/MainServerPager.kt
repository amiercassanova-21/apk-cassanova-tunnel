package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import com.v2ray.ang.ui.compose.ThemeManager
import com.v2ray.ang.ui.compose.reorderableDragHandle
import sh.calvin.reorderable.ReorderableCollectionItemScope
import com.v2ray.ang.R
import com.v2ray.ang.dto.LocateTarget
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import com.v2ray.ang.ui.compose.verticalScrollbar
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.abs

@Composable
fun GroupPagerPage(
    groupId: String,
    mainViewModel: MainViewModel,
    selectedGuid: String?,
    locateTarget: LocateTarget?,
    doubleColumnDisplay: Boolean,
    searchQuery: String,
    lazyListStates: MutableMap<String, LazyListState>,
    lazyGridStates: MutableMap<String, LazyGridState>,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem) -> Unit,
    onMoreServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String, String) -> Unit,
    usageInfo: SubscriptionUsageInfo?,
    groupTitle: String,
    onOpenLink: (String) -> Unit,
    contentPadding: PaddingValues,
    style: String = ThemeManager.STYLE_B,
    filter: ProtocolFilter = ProtocolFilter.ALL,
) {
    val groupStateFlow = remember(groupId) {
        mainViewModel.serverGroupState(groupId)
    }
    val groupState by groupStateFlow.collectAsStateWithLifecycle()
    // Reordering a filtered list would move servers relative to hidden ones, so it needs the full list.
    val canReorder = groupId.isNotEmpty() && searchQuery.isEmpty() && filter == ProtocolFilter.ALL
    val allRows = groupState.rows
    val rows = remember(allRows, filter) {
        if (filter == ProtocolFilter.ALL) allRows else allRows.filter { filter.matches(it.profile) }
    }
    val actions = remember(
        onSelectServer,
        onEditServer,
        onShareServer,
        onMoreServer,
        onRemoveServer,
    ) {
        ConfigCardActions(
            select = onSelectServer,
            edit = onEditServer,
            share = onShareServer,
            more = onMoreServer,
            remove = onRemoveServer,
        )
    }
    // The box floats over the list (the list scrolls underneath it), so the list is padded by the box height.
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var infoHeightPx by remember { mutableIntStateOf(0) }
    val listPadding = if (usageInfo == null) {
        contentPadding
    } else {
        PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection),
            top = contentPadding.calculateTopPadding() + with(density) { infoHeightPx.toDp() },
            end = contentPadding.calculateEndPadding(layoutDirection),
            bottom = contentPadding.calculateBottomPadding()
        )
    }

    Box(Modifier.fillMaxSize()) {
        if (rows.isEmpty() && allRows.isNotEmpty()) {
            Text(
                text = stringResource(R.string.config_no_match),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp + listPadding.calculateTopPadding(), start = 24.dp, end = 24.dp)
            )
        }
        ServerListPage(
            style = style,
            rows = rows,
            selectedGuid = selectedGuid,
            locateTarget = locateTarget?.takeIf { it.groupId == groupId },
            canReorder = canReorder,
            doubleColumnDisplay = doubleColumnDisplay,
            groupId = groupId,
            lazyListStates = lazyListStates,
            lazyGridStates = lazyGridStates,
            actions = actions,
            onLocateHandled = { mainViewModel.onAction(MainAction.LocateHandled) },
            onMoveServer = { fromIndex, toIndex ->
                mainViewModel.moveServer(groupId, fromIndex, toIndex)
            },
            contentPadding = listPadding
        )
        if (usageInfo != null) {
            SubscriptionInfoCard(
                info = usageInfo,
                fallbackTitle = groupTitle,
                onOpenLink = onOpenLink,
                onRefresh = { mainViewModel.onAction(MainAction.UpdateGroupSubscription(groupId)) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onSizeChanged { infoHeightPx = it.height }
            )
        }
    }
}

@Composable
private fun ServerListPage(
    style: String,
    rows: List<ServerRowUiModel>,
    selectedGuid: String?,
    locateTarget: LocateTarget?,
    canReorder: Boolean,
    doubleColumnDisplay: Boolean,
    groupId: String,
    lazyListStates: MutableMap<String, LazyListState>,
    lazyGridStates: MutableMap<String, LazyGridState>,
    actions: ConfigCardActions,
    onLocateHandled: () -> Unit,
    onMoveServer: (Int, Int) -> Unit,
    contentPadding: PaddingValues
) {
    val sidePadding = if (style == ThemeManager.STYLE_B) 12.dp else 20.dp
    val gap = if (style == ThemeManager.STYLE_B) 8.dp else 10.dp
    val layoutDirection = LocalLayoutDirection.current
    val padded = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection) + sidePadding,
        top = contentPadding.calculateTopPadding() + 4.dp,
        end = contentPadding.calculateEndPadding(layoutDirection) + sidePadding,
        bottom = contentPadding.calculateBottomPadding()
    )
    val card: @Composable (ServerRowUiModel) -> Unit = { row ->
        ConfigCard(style, row, row.guid == selectedGuid, doubleColumnDisplay, actions)
    }

    if (doubleColumnDisplay) {
        val gridState = remember(groupId) {
            lazyGridStates.getOrPut(groupId) { LazyGridState() }
        }
        val reorderableGridState = if (canReorder) {
            rememberReorderableLazyGridState(gridState) { from, to ->
                onMoveServer(from.index, to.index)
            }
        } else null

        LocateTargetEffect(locateTarget, rows, gridState, onLocateHandled)

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollbar(gridState),
            contentPadding = padded,
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            itemsIndexed(items = rows, key = { _, item -> item.guid }) { _, row ->
                if (canReorder && reorderableGridState != null) {
                    ReorderableItem(reorderableGridState, key = row.guid) { isDragging ->
                        ReorderableCard(this, isDragging) { card(row) }
                    }
                } else {
                    card(row)
                }
            }
        }
    } else {
        val listState = remember(groupId) {
            lazyListStates.getOrPut(groupId) { LazyListState() }
        }
        val reorderableState = if (canReorder) {
            rememberReorderableLazyListState(listState) { from, to ->
                onMoveServer(from.index, to.index)
            }
        } else null

        LocateTargetEffect(locateTarget, rows, listState, onLocateHandled)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollbar(listState),
            contentPadding = padded,
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            itemsIndexed(items = rows, key = { _, item -> item.guid }) { _, row ->
                if (canReorder && reorderableState != null) {
                    ReorderableItem(reorderableState, key = row.guid) { isDragging ->
                        ReorderableCard(this, isDragging) { card(row) }
                    }
                } else {
                    card(row)
                }
            }
        }
    }
}

@Composable
private fun ConfigCard(
    style: String,
    row: ServerRowUiModel,
    isSelected: Boolean,
    compact: Boolean,
    actions: ConfigCardActions,
) {
    when (style) {
        ThemeManager.STYLE_A -> ConfigCardA(row, isSelected, actions)
        ThemeManager.STYLE_C -> ConfigCardC(row, isSelected, actions)
        else -> ConfigCardB(row, isSelected, compact, actions)
    }
}

/** Long-press drag handle around a card; lifts the card while it is dragged. */
@Composable
private fun ReorderableCard(
    scope: ReorderableCollectionItemScope,
    isDragging: Boolean,
    content: @Composable () -> Unit
) {
    val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "cardDrag")
    Box(
        modifier = Modifier
            .shadow(elevation, RoundedCornerShape(18.dp))
            .then(with(scope) { reorderableDragHandle() })
    ) {
        content()
    }
}

@Composable
private fun LocateTargetEffect(
    target: LocateTarget?,
    rows: List<ServerRowUiModel>,
    state: LazyListState,
    onHandled: () -> Unit,
) {
    if (target == null) return
    LaunchedEffect(target, rows) {
        val index = rows.indexOfFirst { it.guid == target.serverGuid }
        if (index < 0) return@LaunchedEffect
        state.scrollToItem(index, -state.layoutInfo.viewportSize.height / 3)
        onHandled()
    }
}

@Composable
private fun LocateTargetEffect(
    target: LocateTarget?,
    rows: List<ServerRowUiModel>,
    state: LazyGridState,
    onHandled: () -> Unit,
) {
    if (target == null) return
    LaunchedEffect(target, rows) {
        val index = rows.indexOfFirst { it.guid == target.serverGuid }
        if (index < 0) return@LaunchedEffect
        state.scrollToItem(index, -state.layoutInfo.viewportSize.height / 3)
        onHandled()
    }
}

internal suspend fun PagerState.navigateToPageOptimized(
    targetPage: Int,
    animateAdjacentPage: Boolean = true
) {
    if (pageCount <= 0) return
    val target = targetPage.coerceIn(0, pageCount - 1)
    val current = settledPage.coerceIn(0, pageCount - 1)
    if (target == current) return

    if (abs(target - current) == 1 && animateAdjacentPage) {
        animateScrollToPage(target)
    } else {
        scrollToPage(target)
    }
}
