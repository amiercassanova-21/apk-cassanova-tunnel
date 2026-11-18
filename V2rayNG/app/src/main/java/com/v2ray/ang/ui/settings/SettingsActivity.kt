package com.v2ray.ang.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.VPN
import com.v2ray.ang.core.AutoPing
import com.v2ray.ang.handler.TetherManager
import com.v2ray.ang.handler.DnsPresets
import com.v2ray.ang.handler.DnsLeakCheck
import com.v2ray.ang.R
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.MmkvManager.rememberMmkvBool
import com.v2ray.ang.handler.MmkvManager.rememberMmkvString
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.root.RootManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.AboutActivity
import com.v2ray.ang.ui.backup.BackupActivity
import com.v2ray.ang.ui.routing.RoutingSettingActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.CollapsiblePreferenceGroupHeader
import com.v2ray.ang.ui.compose.SelectListDialog
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.SettingsEditItem
import com.v2ray.ang.ui.compose.SettingsListItem
import com.v2ray.ang.ui.compose.SettingsMenuItem
import com.v2ray.ang.ui.compose.SettingsSection
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.ThemeManager
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.launch

class SettingsActivity : BaseComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.refreshSystemVpnSettingsAvailability()
                viewModel.refreshPrivateDns()
            }
        }
    }

    private fun openSystemVpnSettings() {
        try {
            startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
        } catch (error: ActivityNotFoundException) {
            reportSystemVpnSettingsFailure(error)
        } catch (error: SecurityException) {
            reportSystemVpnSettingsFailure(error)
        }
    }

    /** Android has no public link to the Private DNS page; it sits under network settings. */
    private fun openPrivateDnsSettings() {
        try {
            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        } catch (error: ActivityNotFoundException) {
            LogUtil.e(AppConfig.TAG, "Cannot open network settings for Private DNS", error)
            toastError(R.string.toast_system_vpn_settings_unavailable)
        } catch (error: SecurityException) {
            LogUtil.e(AppConfig.TAG, "Cannot open network settings for Private DNS", error)
            toastError(R.string.toast_system_vpn_settings_unavailable)
        }
    }

    private fun reportSystemVpnSettingsFailure(error: RuntimeException) {
        LogUtil.e(AppConfig.TAG, "Cannot open system VPN settings", error)
        toastError(R.string.toast_system_vpn_settings_unavailable)
    }

    @Composable
    override fun ScreenContent() {
        SettingsScreen(
            viewModel = viewModel,
            onBackClick = { finish() },
            onModeHelpClicked = { Utils.openUri(this, AppConfig.APP_WIKI_MODE) },
            onSystemVpnSettingsClicked = ::openSystemVpnSettings,
            onPrivateDnsSettingsClicked = ::openPrivateDnsSettings,
            onOpenUrl = { Utils.openUri(this, it) },
            onRoutingClicked = { startActivity(Intent(this, RoutingSettingActivity::class.java)) },
            onBackupClicked = { startActivity(Intent(this, BackupActivity::class.java)) },
            onAboutClicked = { startActivity(Intent(this, AboutActivity::class.java)) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    onModeHelpClicked: () -> Unit,
    onSystemVpnSettingsClicked: () -> Unit,
    onPrivateDnsSettingsClicked: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
    onRoutingClicked: () -> Unit = {},
    onBackupClicked: () -> Unit = {},
    onAboutClicked: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val systemVpnSettingsAvailable by viewModel.systemVpnSettingsAvailable.collectAsStateWithLifecycle()
    var uiSettingsExpanded by rememberSaveable { mutableStateOf(true) }
    var vpnSettingsExpanded by rememberSaveable { mutableStateOf(true) }
    var dnsSettingsExpanded by rememberSaveable { mutableStateOf(true) }
    var dnsAdvancedExpanded by rememberSaveable { mutableStateOf(false) }
    var showDnsPresetDialog by rememberSaveable { mutableStateOf(false) }
    val privateDnsHost by viewModel.privateDnsHost.collectAsStateWithLifecycle()
    val dnsTest by viewModel.dnsTest.collectAsStateWithLifecycle()
    val dnsLeak by viewModel.dnsLeak.collectAsStateWithLifecycle()
    var coreSettingsExpanded by rememberSaveable { mutableStateOf(true) }
    var muxSettingsExpanded by rememberSaveable { mutableStateOf(false) }
    var fragmentSettingsExpanded by rememberSaveable { mutableStateOf(false) }
    var observatorySettingsExpanded by rememberSaveable { mutableStateOf(false) }
    var advancedSettingsExpanded by rememberSaveable { mutableStateOf(true) }
    var modeSettingsExpanded by rememberSaveable { mutableStateOf(true) }
    var autoPingExpanded by rememberSaveable { mutableStateOf(true) }
    var tetherExpanded by rememberSaveable { mutableStateOf(true) }
    var tetherEnabled by rememberMmkvBool(AppConfig.PREF_TETHER_ENABLED, false)
    var tetherSocksPort by rememberMmkvString(AppConfig.PREF_TETHER_SOCKS_PORT, TetherManager.DEFAULT_SOCKS_PORT.toString())
    var tetherHttpPort by rememberMmkvString(AppConfig.PREF_TETHER_HTTP_PORT, TetherManager.DEFAULT_HTTP_PORT.toString())
    var autoPingMode by rememberMmkvString(AppConfig.PREF_AUTOPING_MODE, AutoPing.Mode.RESET.key)
    var autoPingMethod by rememberMmkvString(AppConfig.PREF_AUTOPING_METHOD, AutoPing.Method.AUTO.key)
    var autoPingTarget by rememberMmkvString(AppConfig.PREF_AUTOPING_TARGET, AutoPing.DEFAULT_STUN_TARGET)
    var autoPingInterval by rememberMmkvString(AppConfig.PREF_AUTOPING_INTERVAL, AutoPing.DEFAULT_INTERVAL_SEC.toString())
    var autoPingTimeout by rememberMmkvString(AppConfig.PREF_AUTOPING_TIMEOUT, AutoPing.DEFAULT_TIMEOUT_SEC.toString())
    var autoPingFailLimit by rememberMmkvString(AppConfig.PREF_AUTOPING_FAIL_LIMIT, AutoPing.DEFAULT_FAIL_LIMIT.toString())

    var localDns by rememberMmkvBool(AppConfig.PREF_LOCAL_DNS_ENABLED, false)
    var fakeDns by rememberMmkvBool(AppConfig.PREF_FAKE_DNS_ENABLED, false)
    var appendHttpProxy by rememberMmkvBool(AppConfig.PREF_APPEND_HTTP_PROXY, false)
    var vpnDns by rememberMmkvString(AppConfig.PREF_VPN_DNS, "")
    var vpnBypassLan by rememberMmkvString(AppConfig.PREF_VPN_BYPASS_LAN, AppConfig.DEFAULT_VPN_BYPASS_LAN)
    var vpnInterfaceAddress by rememberMmkvString(AppConfig.PREF_VPN_INTERFACE_ADDRESS_CONFIG_INDEX, "0")
    var vpnMtu by rememberMmkvString(AppConfig.PREF_VPN_MTU, "")

    var mux by rememberMmkvBool(AppConfig.PREF_MUX_ENABLED, false)
    var muxConcurrency by rememberMmkvString(AppConfig.PREF_MUX_CONCURRENCY, "8")
    var muxXudpConcurrency by rememberMmkvString(AppConfig.PREF_MUX_XUDP_CONCURRENCY, AppConfig.DEFAULT_MUX_XUDP_CONCURRENCY)
    var muxXudpQuic by rememberMmkvString(AppConfig.PREF_MUX_XUDP_QUIC, "reject")

    var fragment by rememberMmkvBool(AppConfig.PREF_FRAGMENT_ENABLED, false)
    var fragmentPackets by rememberMmkvString(AppConfig.PREF_FRAGMENT_PACKETS, "tlshello")
    var fragmentLength by rememberMmkvString(AppConfig.PREF_FRAGMENT_LENGTH, "50-100")
    var fragmentInterval by rememberMmkvString(AppConfig.PREF_FRAGMENT_INTERVAL, "10-20")
    var fragmentMaxSplit by rememberMmkvString(AppConfig.PREF_FRAGMENT_MAXSPLIT, "10")
    var observatoryLeastPingInterval by rememberMmkvString(AppConfig.PREF_OBSERVATORY_LEAST_PING_INTERVAL, AppConfig.OBSERVATORY_LEAST_PING_INTERVAL)
    var observatoryLeastLoadInterval by rememberMmkvString(AppConfig.PREF_OBSERVATORY_LEAST_LOAD_INTERVAL, AppConfig.OBSERVATORY_LEAST_LOAD_INTERVAL)
    var observatoryLeastLoadMethod by rememberMmkvString(AppConfig.PREF_OBSERVATORY_LEAST_LOAD_METHOD, AppConfig.OBSERVATORY_LEAST_LOAD_METHOD)
    var observatoryLeastLoadSampling by rememberMmkvString(AppConfig.PREF_OBSERVATORY_LEAST_LOAD_SAMPLING, AppConfig.OBSERVATORY_LEAST_LOAD_SAMPLING)
    var observatoryLeastLoadTimeout by rememberMmkvString(AppConfig.PREF_OBSERVATORY_LEAST_LOAD_TIMEOUT, AppConfig.OBSERVATORY_LEAST_LOAD_TIMEOUT)

    var mode by rememberMmkvString(AppConfig.PREF_MODE, VPN)
    var sshEngine by rememberMmkvString(AppConfig.PREF_SSH_ENGINE, AppConfig.SSH_ENGINE_NATIVE)
    var enableRootMode by rememberMmkvBool(AppConfig.PREF_ROOT_MODE_ENABLE, false)
    var lanSharing by rememberMmkvBool(AppConfig.PREF_ROOT_LAN_SHARING, false)

    var hevTunLogLevel by rememberMmkvString(AppConfig.PREF_HEV_TUNNEL_LOGLEVEL, AppConfig.DEFAULT_HEV_TUNNEL_LOGLEVEL)
    var hevTunRwTimeout by rememberMmkvString(AppConfig.PREF_HEV_TUNNEL_RW_TIMEOUT, "")
    var useHevTun by rememberMmkvBool(AppConfig.PREF_USE_HEV_TUNNEL, true)

    var enableLocalProxy by rememberMmkvBool(AppConfig.PREF_ENABLE_LOCAL_PROXY, true)
    var socksPort by rememberMmkvString(AppConfig.PREF_SOCKS_PORT, "")
    var dynamicSocksPort by rememberMmkvBool(AppConfig.PREF_DYNAMIC_SOCKS_PORT, false)
    var socksUsername by rememberMmkvString(AppConfig.PREF_SOCKS_USERNAME, "")
    var socksPassword by rememberMmkvString(AppConfig.PREF_SOCKS_PASSWORD, "")
    var socksEnableUdp by rememberMmkvBool(AppConfig.PREF_SOCKS_ENABLE_UDP, AppConfig.DEFAULT_SOCKS_ENABLE_UDP)
    var proxySharing by rememberMmkvBool(AppConfig.PREF_PROXY_SHARING, false)

    var speedEnabled by rememberMmkvBool(AppConfig.PREF_SPEED_ENABLED, false)
    var confirmRemove by rememberMmkvBool(AppConfig.PREF_CONFIRM_REMOVE, true)
    var doubleColumnDisplay by rememberMmkvBool(AppConfig.PREF_DOUBLE_COLUMN_DISPLAY, false)
    var groupAllDisplay by rememberMmkvBool(AppConfig.PREF_GROUP_ALL_DISPLAY, false)
    var language by remember {
        mutableStateOf(
            MmkvManager.decodeSettingsString(AppConfig.PREF_LANGUAGE, "auto") ?: "auto"
        )
    }
    var uiModeNight by rememberMmkvString(AppConfig.PREF_UI_MODE_NIGHT, ThemeManager.themeMode.value)
    var dynamicColor by rememberMmkvBool(AppConfig.PREF_DYNAMIC_COLOR, false)
    var uiStyle by rememberMmkvString(AppConfig.PREF_UI_STYLE, ThemeManager.uiStyle.value)

    var ipv6Enabled by rememberMmkvBool(AppConfig.PREF_IPV6_ENABLED, false)
    var preferIpv6 by rememberMmkvBool(AppConfig.PREF_PREFER_IPV6, false)
    var sniffingEnabled by rememberMmkvBool(AppConfig.PREF_SNIFFING_ENABLED, true)
    var routeOnlyEnabled by rememberMmkvBool(AppConfig.PREF_ROUTE_ONLY_ENABLED, false)
    var remoteDns by rememberMmkvString(AppConfig.PREF_REMOTE_DNS, "")
    var domesticDns by rememberMmkvString(AppConfig.PREF_DOMESTIC_DNS, "")
    var dnsHosts by rememberMmkvString(AppConfig.PREF_DNS_HOSTS, "")
    var dnsBlockList by rememberMmkvString(AppConfig.PREF_DNS_BLOCK_LIST, "")
    var dnsAllowList by rememberMmkvString(AppConfig.PREF_DNS_ALLOW_LIST, "")
    var slowDnsFakeDns by rememberMmkvBool(AppConfig.PREF_SLOWDNS_FAKEDNS, false)
    var showAdvanced by rememberMmkvBool(AppConfig.PREF_SHOW_ADVANCED_SETTINGS, false)
    var coreLogLevel by rememberMmkvString(AppConfig.PREF_LOGLEVEL, "warning")
    var outboundResolveMethod by rememberMmkvString(AppConfig.PREF_OUTBOUND_DOMAIN_RESOLVE_METHOD, AppConfig.DEFAULT_OUTBOUND_DOMAIN_RESOLVE_METHOD)

    var isBooted by rememberMmkvBool(AppConfig.PREF_IS_BOOTED, false)
    var delayTestUrl by rememberMmkvString(AppConfig.PREF_DELAY_TEST_URL, "")
    var realPingConcurrency by rememberMmkvString(AppConfig.PREF_REAL_PING_CONCURRENCY, "16")
    var ipApiUrl by rememberMmkvString(AppConfig.PREF_IP_API_URL, "")

    val isVpn = mode == VPN
    val hevTunEnabled = isVpn && useHevTun
    val localProxyForced = hevTunEnabled
    val effectiveLocalProxy = enableLocalProxy || localProxyForced
    val muxXudpConcurrencyInt = muxXudpConcurrency.toIntOrNull() ?: AppConfig.DEFAULT_MUX_XUDP_CONCURRENCY.toInt()

    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    LaunchedEffect(dynamicColorSupported) {
        if (!dynamicColorSupported && dynamicColor) {
            dynamicColor = false
            ThemeManager.setDynamicColorEnabled(false)
        }
    }

    val languageEntries = stringArrayResource(R.array.language_select).toList()
    val languageValues = stringArrayResource(R.array.language_select_value).toList()
    val uiStyleEntries = stringArrayResource(R.array.ui_style).toList()
    val uiStyleValues = stringArrayResource(R.array.ui_style_value).toList()
    val uiModeNightEntries = stringArrayResource(R.array.ui_mode_night).toList()
    val uiModeNightValues = stringArrayResource(R.array.ui_mode_night_value).toList()
    val bypassLanEntries = stringArrayResource(R.array.vpn_bypass_lan).toList()
    val bypassLanValues = stringArrayResource(R.array.vpn_bypass_lan_value).toList()
    val interfaceAddrEntries = stringArrayResource(R.array.vpn_interface_address).toList()
    val interfaceAddrValues = stringArrayResource(R.array.vpn_interface_address_value).toList()
    val hevLogEntries = stringArrayResource(R.array.hev_tunnel_loglevel).toList()
    val hevLogValues = stringArrayResource(R.array.hev_tunnel_loglevel).toList()
    val coreLogLevelEntries = stringArrayResource(R.array.core_loglevel).toList()
    val coreLogLevelValues = stringArrayResource(R.array.core_loglevel).toList()
    val outboundResolveEntries = stringArrayResource(R.array.outbound_domain_resolve_method).toList()
    val outboundResolveValues = stringArrayResource(R.array.outbound_domain_resolve_method_value).toList()
    val xudpQuicEntries = stringArrayResource(R.array.mux_xudp_quic_entries).toList()
    val xudpQuicValues = stringArrayResource(R.array.mux_xudp_quic_value).toList()
    val fragmentPacketsEntries = stringArrayResource(R.array.fragment_packets).toList()
    val fragmentPacketsValues = stringArrayResource(R.array.fragment_packets).toList()
    val observatoryLeastLoadMethodEntries = stringArrayResource(R.array.observatory_least_load_method).toList()
    val observatoryLeastLoadMethodValues = stringArrayResource(R.array.observatory_least_load_method).toList()
    val modeEntries = stringArrayResource(R.array.mode_entries).toList()
    val modeValues = stringArrayResource(R.array.mode_value).toList()

    val currentStyle by ThemeManager.uiStyle.collectAsStateWithLifecycle()
    val experimentalUi by ThemeManager.experimentalUi.collectAsStateWithLifecycle()
    val brandHeader = currentStyle != ThemeManager.STYLE_B
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            if (!brandHeader) {
                AppTopBar(
                    title = stringResource(R.string.title_settings),
                    onBackClick = onBackClick,
                    isLoading = isLoading
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScrollbar(scrollState)
                .verticalScroll(scrollState)
        ) {
            if (brandHeader) {
                CassanovaSettingsHeader(
                    style = currentStyle,
                    showStyle = experimentalUi,
                    themeMode = uiModeNight,
                    onThemeModeChange = {
                        uiModeNight = it
                        ThemeManager.setThemeMode(it)
                    },
                    onBackClick = onBackClick
                )
                SettingsShortcutRow(
                    onRouting = onRoutingClicked,
                    onBackup = onBackupClicked,
                    onAbout = onAboutClicked
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }
            SettingsSection(
                title = stringResource(R.string.title_ui_settings),
                expanded = uiSettingsExpanded,
                onExpandedChange = { uiSettingsExpanded = it }
            ) {
                SettingsSwitchItem(
                    title = stringResource(R.string.title_show_advanced_settings),
                    summary = stringResource(R.string.summary_show_advanced_settings),
                    checked = showAdvanced,
                    onCheckedChange = { showAdvanced = it }
                )
                // Styles B and C are experimental: the picker shows only after the About unlock.
                if (experimentalUi) {
                    SettingsListItem(
                        title = stringResource(R.string.title_pref_ui_style),
                        entries = uiStyleEntries,
                        values = uiStyleValues,
                        selectedValue = currentStyle,
                        help = stringResource(R.string.help_ui_style),
                        onSelected = {
                            uiStyle = it
                            ThemeManager.setUiStyle(it)
                            uiModeNight = ThemeManager.themeMode.value
                        }
                    )
                    Text(
                        text = stringResource(R.string.settings_admin_mode_note),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                SettingsSwitchItem(
                    title = stringResource(R.string.title_pref_speed_enabled),
                    summary = stringResource(R.string.summary_pref_speed_enabled),
                    checked = speedEnabled,
                    onCheckedChange = { speedEnabled = it }
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.title_pref_confirm_remove),
                    summary = stringResource(R.string.summary_pref_confirm_remove),
                    checked = confirmRemove,
                    onCheckedChange = { confirmRemove = it }
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.title_pref_double_column_display),
                    summary = stringResource(R.string.summary_pref_double_column_display),
                    checked = doubleColumnDisplay,
                    onCheckedChange = {
                        doubleColumnDisplay = it
                        SettingsChangeManager.makeSetupGroupTab()
                    }
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.title_pref_group_all_display),
                    summary = stringResource(R.string.summary_pref_group_all_display),
                    checked = groupAllDisplay,
                    onCheckedChange = {
                        groupAllDisplay = it
                        SettingsChangeManager.makeSetupGroupTab()
                    }
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.title_pref_dynamic_color),
                    summary = stringResource(R.string.summary_pref_dynamic_color),
                    checked = dynamicColor,
                    enabled = dynamicColorSupported,
                    onCheckedChange = {
                        dynamicColor = it
                        ThemeManager.setDynamicColorEnabled(it)
                    }
                )
                SettingsListItem(
                    title = stringResource(R.string.title_language),
                    entries = languageEntries,
                    values = languageValues,
                    selectedValue = language,
                    help = stringResource(R.string.help_language),
                    onSelected = {
                        language = it
                        AppLocaleManager.setApplicationLanguage(it)
                    }
                )
                SettingsListItem(
                    title = stringResource(R.string.title_pref_ui_mode_night),
                    entries = uiModeNightEntries,
                    values = uiModeNightValues,
                    selectedValue = uiModeNight,
                    help = stringResource(R.string.help_ui_mode_night),
                    onSelected = {
                        uiModeNight = it
                        ThemeManager.setThemeMode(it)
                    }
                )
            }

            SettingsSection(
                title = stringResource(R.string.title_vpn_settings),
                expanded = vpnSettingsExpanded,
                onExpandedChange = { vpnSettingsExpanded = it }
            ) {
                SettingsSwitchItem(
                    title = stringResource(R.string.title_pref_ipv6_enabled),
                    summary = stringResource(R.string.summary_pref_ipv6_enabled),
                    checked = ipv6Enabled,
                    onCheckedChange = { ipv6Enabled = it }
                )
                if (showAdvanced) {
                    SettingsSwitchItem(
                        title = stringResource(R.string.title_pref_prefer_ipv6),
                        summary = stringResource(R.string.summary_pref_prefer_ipv6),
                        checked = preferIpv6,
                        onCheckedChange = { preferIpv6 = it }
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.title_pref_append_http_proxy),
                        summary = stringResource(R.string.summary_pref_append_http_proxy),
                        checked = appendHttpProxy,
                        enabled = effectiveLocalProxy,
                        onCheckedChange = { appendHttpProxy = it }
                    )
                }
                SettingsListItem(
                    title = stringResource(R.string.title_pref_vpn_bypass_lan),
                    entries = bypassLanEntries,
                    values = bypassLanValues,
                    selectedValue = vpnBypassLan,
                    enabled = isVpn,
                    help = stringResource(R.string.help_vpn_bypass_lan),
                    onSelected = { vpnBypassLan = it }
                )
                if (showAdvanced) {
                    SettingsListItem(
                        title = stringResource(R.string.title_pref_vpn_interface_address),
                        entries = interfaceAddrEntries,
                        values = interfaceAddrValues,
                        selectedValue = vpnInterfaceAddress,
                        enabled = isVpn,
                        help = stringResource(R.string.help_vpn_interface_address),
                        onSelected = { vpnInterfaceAddress = it }
                    )
                }
                SettingsEditItem(
                    title = stringResource(R.string.title_pref_vpn_mtu),
                    value = vpnMtu,
                    enabled = isVpn,
                    keyboardNumber = true,
                    help = stringResource(R.string.help_vpn_mtu),
                    onValueChanged = { vpnMtu = it }
                )
                if (showAdvanced || !useHevTun) SettingsSwitchItem(
                    title = stringResource(R.string.title_pref_use_hev_tunnel),
                    summary = stringResource(R.string.summary_pref_use_hev_tunnel),
                    checked = useHevTun,
                    enabled = isVpn,
                    onCheckedChange = {
                        useHevTun = it
                        if (it && !enableLocalProxy) {
                            enableLocalProxy = true
                        }
                    }
                )
                if (showAdvanced) {
                    SettingsListItem(
                        title = stringResource(R.string.title_pref_hev_tunnel_loglevel),
                        entries = hevLogEntries,
                        values = hevLogValues,
                        selectedValue = hevTunLogLevel,
                        enabled = hevTunEnabled,
                        help = stringResource(R.string.help_hev_loglevel),
                        onSelected = { hevTunLogLevel = it }
                    )
                    SettingsEditItem(
                        title = stringResource(R.string.title_pref_hev_tunnel_rw_timeout),
                        value = hevTunRwTimeout,
                        enabled = hevTunEnabled,
                        keyboardNumber = true,
                        help = stringResource(R.string.help_hev_rw_timeout),
                        onValueChanged = { hevTunRwTimeout = it }
                    )
                }
            }

            // Cassanova: one DNS choice for everyday use; the original fields under "Advanced".
            val dnsPreset = DnsPresets.detect(vpnDns, remoteDns)
            SettingsSection(
                title = stringResource(R.string.title_dns_settings),
                expanded = dnsSettingsExpanded,
                onExpandedChange = { dnsSettingsExpanded = it }
            ) {
                privateDnsHost?.let { host ->
                    SettingsMenuItem(
                        title = stringResource(R.string.dns_private_warning_title),
                        subtitle = stringResource(R.string.dns_private_warning_summary, host),
                        onClick = onPrivateDnsSettingsClicked
                    )
                }
                SettingsMenuItem(
                    title = stringResource(R.string.title_dns_preset),
                    subtitle = dnsPreset?.let {
                        stringResource(R.string.dns_preset_summary, stringResource(it.nameRes), stringResource(it.category.labelRes))
                    } ?: stringResource(R.string.dns_preset_custom_summary),
                    onClick = { showDnsPresetDialog = true }
                )
                SettingsMenuItem(
                    title = stringResource(R.string.dns_test_title),
                    subtitle = stringResource(R.string.dns_test_summary),
                    onClick = {
                        viewModel.startDnsTest(if (dnsPreset == null) DnsPresets.customDohUrl(remoteDns) else null)
                    }
                )
                // Cassanova: DNS checks (leak check in the app, full leak test and ad-block test in the browser).
                SettingsMenuItem(
                    title = stringResource(R.string.dns_leak_title),
                    subtitle = stringResource(R.string.dns_leak_summary),
                    onClick = { viewModel.startDnsLeakCheck() }
                )
                SettingsMenuItem(
                    title = stringResource(R.string.dns_leak_full_test),
                    subtitle = stringResource(R.string.dns_leak_full_summary),
                    onClick = { onOpenUrl(DnsLeakCheck.FULL_TEST_URL) }
                )
                if (dnsPreset?.category == DnsPresets.Category.ADBLOCK) {
                    SettingsMenuItem(
                        title = stringResource(R.string.dns_adblock_web_title),
                        subtitle = stringResource(R.string.dns_adblock_web_summary),
                        onClick = { onOpenUrl(DnsLeakCheck.ADBLOCK_TEST_URL) }
                    )
                }
                SettingsEditItem(
                    title = stringResource(R.string.title_dns_block_list),
                    value = dnsBlockList,
                    help = stringResource(R.string.help_dns_block_list),
                    onValueChanged = { dnsBlockList = it.trim() }
                )
                SettingsEditItem(
                    title = stringResource(R.string.title_dns_allow_list),
                    value = dnsAllowList,
                    help = stringResource(R.string.help_dns_allow_list),
                    onValueChanged = { dnsAllowList = it.trim() }
                )
                if (!com.v2ray.ang.handler.Edition.isLite) {
                    SettingsSwitchItem(
                        title = stringResource(R.string.title_slowdns_fakedns),
                        summary = stringResource(R.string.summary_slowdns_fakedns),
                        checked = slowDnsFakeDns,
                        enabled = isVpn,
                        onCheckedChange = { slowDnsFakeDns = it }
                    )
                }
                if (showAdvanced) {
                    CollapsiblePreferenceGroupHeader(
                        title = stringResource(R.string.title_dns_advanced),
                        expanded = dnsAdvancedExpanded,
                        onExpandedChange = { dnsAdvancedExpanded = it }
                    )
                }
                if (showAdvanced && dnsAdvancedExpanded) {
                    SettingsSwitchItem(
                        title = stringResource(R.string.title_pref_local_dns_enabled),
                        summary = stringResource(R.string.summary_pref_local_dns_enabled),
                        checked = localDns,
                        enabled = isVpn,
                        onCheckedChange = { localDns = it }
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.title_pref_fake_dns_enabled),
                        summary = stringResource(R.string.summary_pref_fake_dns_enabled),
                        checked = fakeDns,
                        enabled = isVpn && localDns,
                        onCheckedChange = { fakeDns = it }
                    )
                    SettingsEditItem(
                        title = stringResource(R.string.title_pref_vpn_dns),
                        value = vpnDns,
                        enabled = isVpn && !localDns,
                        help = stringResource(R.string.help_vpn_dns),
                        onValueChanged = { vpnDns = it }
                    )
                    SettingsEditItem(
                        title = stringResource(R.string.title_pref_remote_dns),
                        value = remoteDns,
                        help = stringResource(R.string.help_remote_dns),
                        onValueChanged = { remoteDns = it }
                    )
                    SettingsEditItem(
                        title = stringResource(R.string.title_pref_domestic_dns),
                        value = domesticDns,
                        help = stringResource(R.string.help_domestic_dns),
                        onValueChanged = { domesticDns = it }
                    )
                    SettingsEditItem(
                        title = stringResource(R.string.title_pref_dns_hosts),
                        value = dnsHosts,
                        help = stringResource(R.string.help_dns_hosts),
                        onValueChanged = { dnsHosts = it }
                    )
                }
            }

            if (showDnsPresetDialog) {
                SelectListDialog(
                    title = stringResource(R.string.title_dns_preset),
                    options = DnsPresets.ALL,
                    optionText = {
                        stringResource(R.string.dns_preset_summary, stringResource(it.nameRes), stringResource(it.category.labelRes))
                    },
                    selectedOption = dnsPreset,
                    onSelected = { preset ->
                        showDnsPresetDialog = false
                        vpnDns = preset.vpnDnsValue
                        remoteDns = preset.remoteDns
                    },
                    onDismiss = { showDnsPresetDialog = false },
                    showRadio = true
                )
            }

            if (dnsLeak.visible) {
                DnsLeakDialog(
                    state = dnsLeak,
                    onFullTest = { onOpenUrl(DnsLeakCheck.FULL_TEST_URL) },
                    onDismiss = { viewModel.closeDnsLeakCheck() }
                )
            }

            if (dnsTest.visible) {
                DnsTestDialog(
                    state = dnsTest,
                    currentPresetId = dnsPreset?.id,
                    onPick = { id ->
                        DnsPresets.byId(id)?.let { preset ->
                            vpnDns = preset.vpnDnsValue
                            remoteDns = preset.remoteDns
                        }
                        viewModel.closeDnsTest()
                    },
                    onRetry = {
                        viewModel.startDnsTest(if (dnsPreset == null) DnsPresets.customDohUrl(remoteDns) else null)
                    },
                    onDismiss = { viewModel.closeDnsTest() }
                )
            }

            // Hidden sections still show while one of their risky options is in use.
            if (showAdvanced || dynamicSocksPort || socksUsername.isNotBlank() || !enableLocalProxy) SettingsSection(
                title = stringResource(R.string.title_core_settings),
                expanded = coreSettingsExpanded,
                onExpandedChange = { coreSettingsExpanded = it }
            ) {
                SettingsSwitchItem(
                    title = stringResource(R.string.title_pref_sniffing_enabled),
                    summary = stringResource(R.string.summary_pref_sniffing_enabled),
                    checked = sniffingEnabled,
                    onCheckedChange = { sniffingEnabled = it }
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.title_pref_route_only_enabled),
                    summary = stringResource(R.string.summary_pref_route_only_enabled),
                    checked = routeOnlyEnabled,
                    onCheckedChange = { routeOnlyEnabled = it }
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.title_pref_enable_local_proxy),
                    summary = stringResource(R.string.summary_pref_enable_local_proxy),
                    checked = enableLocalProxy,
                    enabled = !localProxyForced,
                    onCheckedChange = {
                        if (!localProxyForced) {
                            enableLocalProxy = it
                            if (!it && appendHttpProxy) {
                                appendHttpProxy = false
                            }
                        }
                    }
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.title_pref_proxy_sharing_enabled),
                    summary = stringResource(R.string.summary_pref_proxy_sharing_enabled),
                    checked = proxySharing,
                    enabled = effectiveLocalProxy,
                    onCheckedChange = { proxySharing = it }
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.title_pref_dynamic_socks_port),
                    summary = stringResource(R.string.summary_pref_dynamic_socks_port),
                    checked = dynamicSocksPort,
                    enabled = effectiveLocalProxy,
                    onCheckedChange = { dynamicSocksPort = it }
                )
                SettingsEditItem(
                    title = stringResource(R.string.title_pref_socks_port),
                    value = socksPort,
                    enabled = effectiveLocalProxy && !dynamicSocksPort,
                    keyboardNumber = true,
                    help = stringResource(R.string.help_socks_port),
                    onValueChanged = { socksPort = it }
                )
                SettingsEditItem(
                    title = stringResource(R.string.title_pref_socks_username),
                    value = socksUsername,
                    enabled = effectiveLocalProxy,
                    help = stringResource(R.string.help_socks_username),
                    onValueChanged = { socksUsername = it }
                )
                SettingsEditItem(
                    title = stringResource(R.string.title_pref_socks_password),
                    value = socksPassword,
                    enabled = effectiveLocalProxy,
                    isPassword = true,
                    help = stringResource(R.string.help_socks_password),
                    onValueChanged = { socksPassword = it }
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.title_pref_socks_enable_udp),
                    summary = stringResource(R.string.summary_pref_socks_enable_udp),
                    checked = socksEnableUdp,
                    enabled = effectiveLocalProxy,
                    onCheckedChange = { socksEnableUdp = it }
                )
                SettingsListItem(
                    title = stringResource(R.string.title_core_loglevel),
                    entries = coreLogLevelEntries,
                    values = coreLogLevelValues,
                    selectedValue = coreLogLevel,
                    help = stringResource(R.string.help_core_loglevel),
                    onSelected = { coreLogLevel = it }
                )
                SettingsListItem(
                    title = stringResource(R.string.title_outbound_domain_resolve_method),
                    entries = outboundResolveEntries,
                    values = outboundResolveValues,
                    selectedValue = outboundResolveMethod,
                    help = stringResource(R.string.help_outbound_resolve),
                    onSelected = { outboundResolveMethod = it }
                )
            }

            if (showAdvanced || mux) SettingsSection(
                title = stringResource(R.string.title_mux_settings),
                expanded = muxSettingsExpanded,
                onExpandedChange = { muxSettingsExpanded = it }
            ) {
                SettingsSwitchItem(
                    title = stringResource(R.string.title_pref_mux_enabled),
                    summary = stringResource(R.string.summary_pref_mux_enabled),
                    checked = mux,
                    onCheckedChange = { mux = it }
                )
                SettingsEditItem(
                    title = stringResource(R.string.title_pref_mux_concurrency),
                    value = muxConcurrency,
                    enabled = mux,
                    keyboardNumber = true,
                    help = stringResource(R.string.help_mux_concurrency),
                    onValueChanged = { muxConcurrency = it }
                )
                SettingsEditItem(
                    title = stringResource(R.string.title_pref_mux_xudp_concurrency),
                    value = muxXudpConcurrency,
                    enabled = mux,
                    keyboardNumber = true,
                    help = stringResource(R.string.help_mux_xudp_concurrency),
                    onValueChanged = { muxXudpConcurrency = it }
                )
                SettingsListItem(
                    title = stringResource(R.string.title_pref_mux_xudp_quic),
                    entries = xudpQuicEntries,
                    values = xudpQuicValues,
                    selectedValue = muxXudpQuic,
                    enabled = mux && muxXudpConcurrencyInt >= 0,
                    help = stringResource(R.string.help_mux_xudp_quic),
                    onSelected = { muxXudpQuic = it }
                )
            }

            if (showAdvanced || fragment) SettingsSection(
                title = stringResource(R.string.title_fragment_settings),
                expanded = fragmentSettingsExpanded,
                onExpandedChange = { fragmentSettingsExpanded = it }
            ) {
                SettingsSwitchItem(
                    title = stringResource(R.string.title_pref_fragment_enabled),
                    summary = stringResource(R.string.summary_fragment_enabled),
                    checked = fragment,
                    onCheckedChange = { fragment = it }
                )
                SettingsListItem(
                    title = stringResource(R.string.title_pref_fragment_packets),
                    entries = fragmentPacketsEntries,
                    values = fragmentPacketsValues,
                    selectedValue = fragmentPackets,
                    enabled = fragment,
                    help = stringResource(R.string.help_fragment_packets),
                    onSelected = { fragmentPackets = it }
                )
                SettingsEditItem(
                    title = stringResource(R.string.title_pref_fragment_length),
                    value = fragmentLength,
                    enabled = fragment,
                    help = stringResource(R.string.help_fragment_length),
                    onValueChanged = { fragmentLength = it }
                )
                SettingsEditItem(
                    title = stringResource(R.string.title_pref_fragment_interval),
                    value = fragmentInterval,
                    enabled = fragment,
                    help = stringResource(R.string.help_fragment_interval),
                    onValueChanged = { fragmentInterval = it }
                )
                SettingsEditItem(
                    title = stringResource(R.string.title_pref_fragment_maxsplit),
                    value = fragmentMaxSplit,
                    enabled = fragment,
                    keyboardNumber = true,
                    help = stringResource(R.string.help_fragment_maxsplit),
                    onValueChanged = { fragmentMaxSplit = it }
                )
            }

            if (showAdvanced) SettingsSection(
                title = stringResource(R.string.title_observatory_settings),
                expanded = observatorySettingsExpanded,
                onExpandedChange = { observatorySettingsExpanded = it }
            ) {
                Text(
                    text = stringResource(R.string.help_observatory),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                SettingsEditItem(
                    title = stringResource(R.string.title_pref_observatory_least_ping_interval),
                    value = observatoryLeastPingInterval,
                    help = stringResource(R.string.help_observatory_least_ping_interval),
                    onValueChanged = {
                        viewModel.validateObservatoryDuration(it)?.let { value ->
                            observatoryLeastPingInterval = value
                        }
                    }
                )
                SettingsEditItem(
                    title = stringResource(R.string.title_pref_observatory_least_load_interval),
                    value = observatoryLeastLoadInterval,
                    help = stringResource(R.string.help_observatory_least_load_interval),
                    onValueChanged = {
                        viewModel.validateObservatoryDuration(it)?.let { value ->
                            observatoryLeastLoadInterval = value
                        }
                    }
                )
                SettingsListItem(
                    title = stringResource(R.string.title_pref_observatory_least_load_method),
                    entries = observatoryLeastLoadMethodEntries,
                    values = observatoryLeastLoadMethodValues,
                    selectedValue = observatoryLeastLoadMethod,
                    help = stringResource(R.string.help_observatory_least_load_method),
                    onSelected = { observatoryLeastLoadMethod = it }
                )
                SettingsEditItem(
                    title = stringResource(R.string.title_pref_observatory_least_load_sampling),
                    value = observatoryLeastLoadSampling,
                    keyboardNumber = true,
                    help = stringResource(R.string.help_observatory_least_load_sampling),
                    onValueChanged = {
                        viewModel.validateObservatorySampling(it)?.let { value ->
                            observatoryLeastLoadSampling = value
                        }
                    }
                )
                SettingsEditItem(
                    title = stringResource(R.string.title_pref_observatory_least_load_timeout),
                    value = observatoryLeastLoadTimeout,
                    help = stringResource(R.string.help_observatory_least_load_timeout),
                    onValueChanged = {
                        viewModel.validateObservatoryDuration(it)?.let { value ->
                            observatoryLeastLoadTimeout = value
                        }
                    }
                )
            }

            SettingsSection(
                title = stringResource(R.string.title_advanced),
                expanded = advancedSettingsExpanded,
                onExpandedChange = { advancedSettingsExpanded = it }
            ) {
                SettingsSwitchItem(
                    title = stringResource(R.string.title_pref_is_booted),
                    summary = stringResource(R.string.summary_pref_is_booted),
                    checked = isBooted,
                    onCheckedChange = { isBooted = it }
                )
                if (systemVpnSettingsAvailable) {
                    SettingsMenuItem(
                        title = stringResource(R.string.title_system_vpn_settings),
                        subtitle = stringResource(R.string.summary_system_vpn_settings),
                        onClick = onSystemVpnSettingsClicked
                    )
                }
                if (showAdvanced) {
                    SettingsEditItem(
                        title = stringResource(R.string.title_pref_delay_test_url),
                        value = delayTestUrl,
                        help = stringResource(R.string.help_delay_test_url),
                        onValueChanged = { delayTestUrl = it }
                    )
                    SettingsEditItem(
                        title = stringResource(R.string.title_pref_real_ping_concurrency),
                        value = realPingConcurrency,
                        keyboardNumber = true,
                        help = stringResource(R.string.help_real_ping_concurrency),
                        onValueChanged = { realPingConcurrency = it }
                    )
                    SettingsEditItem(
                        title = stringResource(R.string.title_pref_ip_api_url),
                        value = ipApiUrl,
                        help = stringResource(R.string.help_ip_api_url),
                        onValueChanged = { ipApiUrl = it }
                    )
                }
            }

            SettingsSection(
                title = stringResource(R.string.title_tether),
                expanded = tetherExpanded,
                onExpandedChange = { tetherExpanded = it }
            ) {
                SettingsSwitchItem(
                    title = stringResource(R.string.title_tether_enabled),
                    summary = stringResource(R.string.summary_tether_enabled),
                    checked = tetherEnabled,
                    onCheckedChange = { tetherEnabled = it }
                )
                if (tetherEnabled) {
                    SettingsEditItem(
                        title = stringResource(R.string.title_tether_socks_port),
                        value = tetherSocksPort,
                        keyboardNumber = true,
                        help = stringResource(R.string.help_tether_socks_port),
                        onValueChanged = { tetherSocksPort = it.trim() }
                    )
                    SettingsEditItem(
                        title = stringResource(R.string.title_tether_http_port),
                        value = tetherHttpPort,
                        keyboardNumber = true,
                        help = stringResource(R.string.help_tether_http_port),
                        onValueChanged = { tetherHttpPort = it.trim() }
                    )
                }
            }
            SettingsSection(
                title = stringResource(R.string.title_autoping),
                expanded = autoPingExpanded,
                onExpandedChange = { autoPingExpanded = it }
            ) {
                SettingsListItem(
                    title = stringResource(R.string.title_autoping_mode),
                    entries = stringArrayResource(R.array.autoping_mode_entries).toList(),
                    values = AutoPing.Mode.entries.map { it.key },
                    selectedValue = autoPingMode,
                    help = stringResource(R.string.help_autoping_mode),
                    onSelected = { autoPingMode = it }
                )
                if (autoPingMode != AutoPing.Mode.OFF.key) {
                    SettingsListItem(
                        title = stringResource(R.string.title_autoping_method),
                        entries = stringArrayResource(R.array.autoping_method_entries).toList(),
                        values = AutoPing.Method.entries.map { it.key },
                        selectedValue = autoPingMethod,
                        help = stringResource(R.string.help_autoping_method),
                        onSelected = { autoPingMethod = it }
                    )
                    if (autoPingMethod != AutoPing.Method.HTTP.key) {
                        SettingsEditItem(
                            title = stringResource(R.string.title_autoping_target),
                            value = autoPingTarget,
                            help = stringResource(R.string.help_autoping_target),
                            onValueChanged = { autoPingTarget = it.trim() }
                        )
                    }
                    SettingsEditItem(
                        title = stringResource(R.string.title_autoping_interval),
                        value = autoPingInterval,
                        help = stringResource(R.string.help_autoping_interval),
                        onValueChanged = { autoPingInterval = it.trim() },
                        keyboardNumber = true
                    )
                    SettingsEditItem(
                        title = stringResource(R.string.title_autoping_timeout),
                        value = autoPingTimeout,
                        help = stringResource(R.string.help_autoping_timeout),
                        onValueChanged = { autoPingTimeout = it.trim() },
                        keyboardNumber = true
                    )
                    if (autoPingMode == AutoPing.Mode.RESET.key || autoPingMode == AutoPing.Mode.AGGRESSIVE.key) {
                        SettingsEditItem(
                            title = stringResource(R.string.title_autoping_fail_limit),
                            value = autoPingFailLimit,
                            help = stringResource(R.string.help_autoping_fail_limit),
                            onValueChanged = { autoPingFailLimit = it.trim() },
                            keyboardNumber = true
                        )
                    }
                }
            }
            SettingsSection(
                title = stringResource(R.string.title_mode_settings),
                expanded = modeSettingsExpanded,
                onExpandedChange = { modeSettingsExpanded = it }
            ) {
                SettingsListItem(
                    title = stringResource(R.string.title_mode),
                    entries = modeEntries,
                    values = modeValues,
                    selectedValue = mode,
                    help = stringResource(R.string.help_mode),
                    onSelected = { mode = it }
                )
                SettingsMenuItem(
                    title = stringResource(R.string.title_mode_help),
                    onClick = onModeHelpClicked
                )
                if ((showAdvanced || sshEngine != AppConfig.SSH_ENGINE_NATIVE) && !com.v2ray.ang.handler.Edition.isLite) {
                    SettingsListItem(
                        title = stringResource(R.string.title_ssh_engine),
                        entries = listOf(
                            stringResource(R.string.ssh_engine_native),
                            stringResource(R.string.ssh_engine_java)
                        ),
                        values = listOf(AppConfig.SSH_ENGINE_NATIVE, AppConfig.SSH_ENGINE_JAVA),
                        selectedValue = sshEngine,
                        help = stringResource(R.string.help_ssh_engine),
                        onSelected = { sshEngine = it }
                    )
                }
                if (showAdvanced || enableRootMode) SettingsSwitchItem(
                    title = stringResource(R.string.title_root_mode_enabled),
                    summary = stringResource(R.string.summary_root_mode_enabled),
                    checked = enableRootMode,
                    onCheckedChange = { newValue ->
                        if (newValue && !RootManager.cachedRoot()) {
                            viewModel.checkAndRequestRoot {
                                enableRootMode = true
                            }
                        } else {
                            enableRootMode = newValue
                        }
                    }
                )
                if (showAdvanced || lanSharing) SettingsSwitchItem(
                    title = stringResource(R.string.title_root_lan_sharing),
                    summary = stringResource(R.string.summary_root_lan_sharing),
                    checked = lanSharing,
                    onCheckedChange = { newValue ->
                        if (newValue && !RootManager.cachedRoot()) {
                            viewModel.checkAndRequestRoot {
                                lanSharing = true
                            }
                        } else {
                            lanSharing = newValue
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            NavigationBarsSpacer()
        }
    }
}
