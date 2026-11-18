package com.v2ray.ang.ui.settings

import android.app.Application
import android.content.Intent
import android.provider.Settings
import com.v2ray.ang.AppConfig
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.R
import com.v2ray.ang.handler.DnsLeakCheck
import com.v2ray.ang.handler.DnsPresets
import com.v2ray.ang.handler.LocalProxyClient
import com.v2ray.ang.handler.DnsSpeedTest
import com.v2ray.ang.handler.PrivateDnsCheck
import com.v2ray.ang.root.RootManager
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One row of the DNS test; [id] is a preset id or [DnsTestRow.CUSTOM_ID]. */
data class DnsTestRow(
    val id: String,
    val host: String?,
    val done: Boolean = false,
    val millis: Long? = null,
    val adsBlocked: Boolean? = null,
) {
    companion object {
        const val CUSTOM_ID = "custom"
    }
}

/** DNS leak check dialog; [needVpn] when the VPN is not connected, [result] once done. */
data class DnsLeakUiState(
    val visible: Boolean = false,
    val running: Boolean = false,
    val needVpn: Boolean = false,
    val result: DnsLeakCheck.Result? = null,
)

/** [viaVpn] is null until the test has checked for the local proxy. */
data class DnsTestUiState(
    val visible: Boolean = false,
    val running: Boolean = false,
    val viaVpn: Boolean? = null,
    val rows: List<DnsTestRow> = emptyList(),
)

class SettingsViewModel(application: Application) : BaseViewModel(application) {

    private val _systemVpnSettingsAvailable = MutableStateFlow(false)
    val systemVpnSettingsAvailable = _systemVpnSettingsAvailable.asStateFlow()

    private val _privateDnsHost = MutableStateFlow<String?>(null)

    /** Strict Private DNS hostname of the phone, null when off or automatic. */
    val privateDnsHost: StateFlow<String?> = _privateDnsHost.asStateFlow()

    suspend fun refreshPrivateDns() {
        _privateDnsHost.value = withContext(Dispatchers.IO) { PrivateDnsCheck.strictHost(getApplication<Application>()) }
    }

    private val _dnsTest = MutableStateFlow(DnsTestUiState())
    val dnsTest: StateFlow<DnsTestUiState> = _dnsTest.asStateFlow()
    private var dnsTestJob: Job? = null

    /**
     * Times every preset, plus [customUrl] (the current DoH address when it is not a preset).
     * Rows fill in one by one; when all are done they are sorted fastest first.
     */
    fun startDnsTest(customUrl: String?) {
        dnsTestJob?.cancel()
        val targets = DnsPresets.ALL.map { it.id to it.remoteDns } +
            listOfNotNull(customUrl?.let { DnsTestRow.CUSTOM_ID to it })
        _dnsTest.value = DnsTestUiState(
            visible = true,
            running = true,
            rows = targets.map { (id, url) -> DnsTestRow(id, DnsPresets.hostOf(url)) },
        )
        dnsTestJob = viewModelScope.launch(Dispatchers.IO) {
            val proxyPort = DnsSpeedTest.localProxyPort()
            _dnsTest.update { it.copy(viaVpn = proxyPort != null) }
            val client = DnsSpeedTest.client(proxyPort)
            try {
                for ((id, url) in targets) {
                    ensureActive()
                    val m = DnsSpeedTest.measure(client, url)
                    ensureActive()
                    _dnsTest.update { state ->
                        state.copy(rows = state.rows.map {
                            if (it.id == id) it.copy(done = true, millis = m.millis, adsBlocked = m.adsBlocked) else it
                        })
                    }
                }
            } finally {
                client.dispatcher.executorService.shutdown()
                client.connectionPool.evictAll()
            }
            _dnsTest.update { state ->
                state.copy(running = false, rows = state.rows.sortedBy { it.millis ?: Long.MAX_VALUE })
            }
        }
    }

    private val _dnsLeak = MutableStateFlow(DnsLeakUiState())
    val dnsLeak: StateFlow<DnsLeakUiState> = _dnsLeak.asStateFlow()
    private var dnsLeakJob: Job? = null

    fun startDnsLeakCheck() {
        dnsLeakJob?.cancel()
        _dnsLeak.value = DnsLeakUiState(visible = true, running = true)
        dnsLeakJob = viewModelScope.launch(Dispatchers.IO) {
            val proxyPort = LocalProxyClient.port()
            if (proxyPort == null) {
                _dnsLeak.value = DnsLeakUiState(visible = true, needVpn = true)
                return@launch
            }
            val result = DnsLeakCheck.run(proxyPort)
            ensureActive()
            _dnsLeak.value = DnsLeakUiState(visible = true, result = result)
        }
    }

    fun closeDnsLeakCheck() {
        dnsLeakJob?.cancel()
        dnsLeakJob = null
        _dnsLeak.value = DnsLeakUiState()
    }

    fun closeDnsTest() {
        dnsTestJob?.cancel()
        dnsTestJob = null
        _dnsTest.value = DnsTestUiState()
    }

    suspend fun refreshSystemVpnSettingsAvailability() {
        _systemVpnSettingsAvailable.value = withContext(Dispatchers.IO) {
            // Android exposes the VPN page, not a direct link to the Always-on VPN switch.
            Intent(Settings.ACTION_VPN_SETTINGS).resolveActivity(getApplication<Application>().packageManager) != null
        }
    }

    /**
     * Checks for root access and requests it if necessary.
     * Updates [isLoading] during the process.
     */
    fun checkAndRequestRoot(onSuccess: () -> Unit) {
        launchLoading {
            val hasRoot = withContext(Dispatchers.IO) {
                RootManager.refresh()
            }
            if (hasRoot) {
                onSuccess()
            } else {
                toastError(R.string.toast_root_required)
            }
        }
    }

    /**
     * Validates if the given string is a valid observatory duration.
     * Shows error toast if invalid.
     * @return The trimmed value if valid, null otherwise.
     */
    fun validateObservatoryDuration(value: String): String? {
        val duration = value.trim()
        return if (AppConfig.OBSERVATORY_DURATION_PATTERN.matches(duration)) {
            duration
        } else {
            toastError(R.string.toast_invalid_observatory_duration)
            null
        }
    }

    /**
     * Validates if the given string is a valid observatory sampling value.
     * Shows error toast if invalid.
     * @return The value if valid, null otherwise.
     */
    fun validateObservatorySampling(value: String): String? {
        val sampling = value.trim().toIntOrNull()?.takeIf { it > 0 }
        return if (sampling != null) {
            sampling.toString()
        } else {
            toastError(R.string.toast_invalid_observatory_sampling)
            null
        }
    }
}
