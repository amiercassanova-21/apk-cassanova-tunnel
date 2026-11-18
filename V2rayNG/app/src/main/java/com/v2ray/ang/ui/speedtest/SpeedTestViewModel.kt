package com.v2ray.ang.ui.speedtest

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.InternetSpeedTest
import com.v2ray.ang.handler.LocalProxyClient
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SpeedPhase { IDLE, PING, DOWNLOAD, UPLOAD, DONE, FAILED }

/**
 * [viaVpn] is null until checked. Rates are Mbps; null means not measured yet (or failed when
 * the matching phase is over). [liveMbps] is the running rate of the current phase.
 */
data class SpeedTestUiState(
    val phase: SpeedPhase = SpeedPhase.IDLE,
    val viaVpn: Boolean? = null,
    val pingMs: Long? = null,
    val downMbps: Double? = null,
    val upMbps: Double? = null,
    val liveMbps: Double = 0.0,
    val usedBytes: Long = 0,
) {
    val running: Boolean get() = phase == SpeedPhase.PING || phase == SpeedPhase.DOWNLOAD || phase == SpeedPhase.UPLOAD
}

class SpeedTestViewModel(application: Application) : BaseViewModel(application) {

    private val _state = MutableStateFlow(SpeedTestUiState())
    val state: StateFlow<SpeedTestUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun start() {
        if (_state.value.running) return
        job?.cancel()
        _state.value = SpeedTestUiState(phase = SpeedPhase.PING)
        job = viewModelScope.launch(Dispatchers.IO) {
            val proxyPort = LocalProxyClient.port()
            _state.update { it.copy(viaVpn = proxyPort != null) }
            val client = InternetSpeedTest.client(proxyPort)
            try {
                val ping = InternetSpeedTest.ping(client)
                _state.update { it.copy(pingMs = ping, phase = SpeedPhase.DOWNLOAD) }
                if (ping == null) {
                    _state.update { it.copy(phase = SpeedPhase.FAILED) }
                    return@launch
                }
                val down = InternetSpeedTest.download(client) { mbps -> _state.update { it.copy(liveMbps = mbps) } }
                _state.update {
                    it.copy(downMbps = down.mbps, usedBytes = down.bytes, liveMbps = 0.0, phase = SpeedPhase.UPLOAD)
                }
                val up = InternetSpeedTest.upload(client) { mbps -> _state.update { it.copy(liveMbps = mbps) } }
                _state.update {
                    it.copy(upMbps = up.mbps, usedBytes = it.usedBytes + up.bytes, liveMbps = 0.0, phase = SpeedPhase.DONE)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Speed test failed", e)
                _state.update { it.copy(phase = SpeedPhase.FAILED, liveMbps = 0.0) }
            } finally {
                client.dispatcher.cancelAll()
                client.dispatcher.executorService.shutdown()
                client.connectionPool.evictAll()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.update { if (it.running) it.copy(phase = SpeedPhase.IDLE, liveMbps = 0.0) else it }
    }
}
