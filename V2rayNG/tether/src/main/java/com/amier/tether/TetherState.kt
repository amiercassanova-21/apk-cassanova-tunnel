package com.amier.tether

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the screen and the notification show; written by [TetherVpnService]. */
sealed interface TetherStatus {
    data object Off : TetherStatus

    /** Looking for the proxy; [lost] when a working proxy just went away (traffic is held). */
    data class Searching(val lost: Boolean) : TetherStatus

    data class Connected(val target: ProxyTarget, val sinceMillis: Long, val bytes: Long) : TetherStatus
}

object TetherState {
    private val _status = MutableStateFlow<TetherStatus>(TetherStatus.Off)
    val status: StateFlow<TetherStatus> = _status.asStateFlow()

    fun set(status: TetherStatus) {
        _status.value = status
    }
}

/** The user's choices: automatic detection, or a fixed address. */
data class TetherSettings(val auto: Boolean, val host: String, val port: String)

object TetherPrefs {
    private const val FILE = "tether"
    private const val KEY_AUTO = "auto"
    private const val KEY_HOST = "host"
    private const val KEY_PORT = "port"

    fun load(context: Context): TetherSettings {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return TetherSettings(
            auto = p.getBoolean(KEY_AUTO, true),
            host = p.getString(KEY_HOST, "").orEmpty(),
            port = p.getString(KEY_PORT, "").orEmpty(),
        )
    }

    fun save(context: Context, settings: TetherSettings) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTO, settings.auto)
            .putString(KEY_HOST, settings.host.trim())
            .putString(KEY_PORT, settings.port.trim())
            .apply()
    }
}
