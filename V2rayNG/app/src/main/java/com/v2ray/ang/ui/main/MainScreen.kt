package com.v2ray.ang.ui.main

import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.v2ray.ang.ui.compose.ThemeManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.dto.entities.ProfileItem
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
    style: String = ThemeManager.STYLE_B,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val groups = uiState.groups
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val isRunning = uiState.isRunning
    val home by mainViewModel.home.collectAsStateWithLifecycle()
    // Cassanova status lines: connection (or the running test) and today's data usage.
    val statusLine = when (uiState.status) {
        MainStatus.Connected, MainStatus.Disconnected -> if (isRunning) {
            listOfNotNull(
                stringResource(R.string.config_status_connected, formatDuration(home.connectedAtMillis)),
                home.pingMillis?.let { stringResource(R.string.server_test_delay_value, it) }
            ).joinToString(" · ")
        } else {
            stringResource(R.string.config_status_disconnected)
        }
        else -> mainViewModel.formatStatus(uiState.status)
    }
    val dataLine = stringResource(R.string.home_status_data_today, home.todayBytes.toTrafficString()) +
        " · " + stringResource(R.string.config_tap_to_check)
    val selectedGuid = uiState.selectedGuid
    val doubleColumnDisplay = uiState.doubleColumnDisplay
    val confirmRemove = uiState.confirmRemove

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var protocolFilter by rememberSaveable { mutableStateOf(ProtocolFilter.ALL) }
    var showDelAllConfirm by remember { mutableStateOf(false) }
    var showDelDuplicateConfirm by remember { mutableStateOf(false) }
    var showDelInvalidConfirm by remember { mutableStateOf(false) }
    var showRemoveConfirm by rememberSaveable(stateSaver = ServerDeleteTarget.Saver) {
        mutableStateOf<ServerDeleteTarget?>(null)
    }

    var shareTarget by remember { mutableStateOf<Triple<String, ProfileItem, Boolean>?>(null) }
    val removeServer: (String, String) -> Unit = { guid, profileName ->
        if (confirmRemove) {
            showRemoveConfirm = ServerDeleteTarget(guid, profileName)
        } else {
            onAction(MainAction.RemoveServer(guid))
        }
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { groups.size.coerceAtLeast(1) }
    )

    val lazyListStates = remember { mutableStateMapOf<String, LazyListState>() }
    val lazyGridStates = remember { mutableStateMapOf<String, LazyGridState>() }

    LaunchedEffect(groups) {
        val validGroupIds = groups.map { it.id }.toSet()
        lazyListStates.keys.retainAll(validGroupIds)
        lazyGridStates.keys.retainAll(validGroupIds)
    }

    LaunchedEffect(groups, uiState.selectedGroupId) {
        if (groups.isEmpty()) return@LaunchedEffect
        val selectedIndex = groups.indexOfFirst { it.id == uiState.selectedGroupId }
            .takeIf { it >= 0 } ?: 0
        if (!pagerState.isScrollInProgress && pagerState.settledPage != selectedIndex) {
            pagerState.scrollToPage(selectedIndex)
        }
    }

    val latestGroups by rememberUpdatedState(groups)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val currentGroups = latestGroups
                if (page in currentGroups.indices) {
                    onAction(MainAction.SelectGroup(currentGroups[page].id))
                }
            }
    }

    MainDialogs(
        showDelAllConfirm = showDelAllConfirm,
        onDismissDelAll = { showDelAllConfirm = false },
        onConfirmDelAll = { showDelAllConfirm = false; onAction(MainAction.RemoveAllServers) },
        showDelDuplicateConfirm = showDelDuplicateConfirm,
        onDismissDelDuplicate = { showDelDuplicateConfirm = false },
        onConfirmDelDuplicate = { showDelDuplicateConfirm = false; onAction(MainAction.RemoveDuplicateServers) },
        showDelInvalidConfirm = showDelInvalidConfirm,
        onDismissDelInvalid = { showDelInvalidConfirm = false },
        onConfirmDelInvalid = { showDelInvalidConfirm = false; onAction(MainAction.RemoveInvalidServers) },
        showRemoveConfirm = showRemoveConfirm,
        onDismissRemove = { showRemoveConfirm = null },
        onConfirmRemove = { guid -> showRemoveConfirm = null; onAction(MainAction.RemoveServer(guid)) }
    )

    if (shareTarget != null) {
        val (guid, profile, more) = shareTarget!!
        ShareMethodDialog(
            guid = guid,
            profile = profile,
            more = more,
            onDismiss = { shareTarget = null },
            onAction = onAction,
            onRemove = removeServer,
        )
    }
    uiState.shareSheet?.let { sheet ->
        ShareSheet(sheet = sheet, onAction = onAction)
    }

    val onSearchQueryChange: (String) -> Unit = { query ->
        searchQuery = query
        onAction(MainAction.Search(query))
    }
    val closeSearch: () -> Unit = {
        searchQuery = ""
        onAction(MainAction.Search(""))
        showSearch = false
    }
    val onMoreMenuAction: (MainMoreMenuAction) -> Unit = { action ->
        when (action) {
            MainMoreMenuAction.RestartService -> onAction(MainAction.RestartService)
            MainMoreMenuAction.DeleteAll -> showDelAllConfirm = true
            MainMoreMenuAction.DeleteDuplicate -> showDelDuplicateConfirm = true
            MainMoreMenuAction.DeleteInvalid -> showDelInvalidConfirm = true
            MainMoreMenuAction.ExportAll -> onAction(MainAction.ExportAll)
            MainMoreMenuAction.LocateSelected -> onAction(MainAction.LocateSelectedServer)
            MainMoreMenuAction.SortByTestResults -> onAction(MainAction.SortByTestResults)
            MainMoreMenuAction.TestAll -> onAction(MainAction.TestAllServers)
            MainMoreMenuAction.TestAllRealPing -> onAction(MainAction.TestRealAllServers)
            MainMoreMenuAction.UpdateSubscriptions -> onAction(MainAction.UpdateSubscriptions)
        }
    }
    val selectedTabIndex = pagerState.currentPage.coerceIn(0, (groups.size - 1).coerceAtLeast(0))
    val goToGroup: (Int) -> Unit = { targetIndex ->
        scope.launch {
            pagerState.navigateToPageOptimized(targetPage = targetIndex, animateAdjacentPage = true)
        }
    }
    val selectedGroupServers by remember(uiState.selectedGroupId) {
        mainViewModel.serversForGroup(uiState.selectedGroupId)
    }.collectAsStateWithLifecycle()

    val topBar: @Composable () -> Unit = {
        when (style) {
            ThemeManager.STYLE_A -> Column {
                ConfigHeaderA(
                    configCount = selectedGroupServers.size,
                    groupCount = groups.size,
                    showSearch = showSearch,
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    onSearchOpen = { showSearch = true },
                    onSearchClose = closeSearch,
                    onAction = onAction,
                    onMoreMenuAction = onMoreMenuAction,
                )
                if (groups.size > 1) {
                    GroupChipsA(groups, selectedTabIndex, mainViewModel, goToGroup)
                }
                ConfigListBar(onTestAll = { onAction(MainAction.TestRealAllServers) }) {
                    Text(
                        text = stringResource(R.string.config_list_title),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            ThemeManager.STYLE_C -> Column {
                ConfigHeaderC(
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    filter = protocolFilter,
                    onFilterChange = { protocolFilter = it },
                    onAction = onAction,
                    onMoreMenuAction = onMoreMenuAction,
                )
                Spacer(Modifier.height(8.dp))
                ConfigListBar(onTestAll = { onAction(MainAction.TestRealAllServers) }) {
                    GroupDropdownC(groups, selectedTabIndex, goToGroup)
                }
            }

            else -> Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                MainTopBar(
                    isLoading = isLoading,
                    showSearch = showSearch,
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    onSearchClose = closeSearch,
                    onSearchToggle = { show: Boolean -> showSearch = show },
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onAction = onAction,
                    onMoreMenuAction = onMoreMenuAction
                )
                if (groups.size > 1) {
                    GroupTabBar(
                        groups = groups,
                        selectedTabIndex = selectedTabIndex,
                        mainViewModel = mainViewModel,
                        onTabClick = goToGroup
                    )
                }
            }
        }
    }

    val screen: @Composable () -> Unit = {
        Scaffold(
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
            topBar = topBar,
            bottomBar = {
                when (style) {
                    ThemeManager.STYLE_A -> MiniStatusCardA(home, onAction)
                    ThemeManager.STYLE_C -> Unit
                    else -> MainBottomBar(
                        statusLine = statusLine,
                        dataLine = dataLine,
                        isRunning = isRunning,
                        onAction = onAction
                    )
                }
            },
            floatingActionButton = {},
        ) { innerPadding ->
            if (groups.isNotEmpty()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    userScrollEnabled = true,
                    beyondViewportPageCount = 1,
                    key = { page -> groups.getOrNull(page)?.id ?: "group-page-$page" }
                ) { page ->
                    val group = groups.getOrNull(page) ?: return@HorizontalPager

                    GroupPagerPage(
                        groupId = group.id,
                        mainViewModel = mainViewModel,
                        selectedGuid = selectedGuid,
                        locateTarget = uiState.locateTarget,
                        doubleColumnDisplay = doubleColumnDisplay,
                        searchQuery = searchQuery,
                        lazyListStates = lazyListStates,
                        lazyGridStates = lazyGridStates,
                        onSelectServer = { guid -> onAction(MainAction.SelectServer(guid)) },
                        onEditServer = { guid, profile -> onAction(MainAction.EditServer(guid, profile)) },
                        onShareServer = { guid, profile ->
                            // The share icon opens the sheet directly; locked configs are never shared.
                            if (!com.v2ray.ang.handler.ConfigLockManager.isLocked(profile)) {
                                onAction(MainAction.OpenShareSheet(guid))
                            }
                        },
                        onMoreServer = { guid, profile ->
                            shareTarget = Triple(guid, profile, true)
                        },
                        onRemoveServer = removeServer,
                        usageInfo = group.usageInfo,
                        groupTitle = group.remarks,
                        onOpenLink = { url -> onAction(MainAction.OpenSubscriptionLink(url)) },
                        contentPadding = PaddingValues(bottom = 16.dp),
                        style = style,
                        filter = if (style == ThemeManager.STYLE_C) protocolFilter else ProtocolFilter.ALL,
                    )
                }
            }
        }
    }

    // Style B keeps the navigation drawer; A and C live inside the Cassanova shell, whose bottom
    // navigation and tools tab replace it.
    if (style == ThemeManager.STYLE_B) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                MainDrawerContent(
                    drawerState = drawerState,
                    onNavigate = { route ->
                        scope.launch { drawerState.close() }
                        onNavigate(route)
                    }
                )
            }
        ) {
            screen()
        }
    } else {
        screen()
    }
}
