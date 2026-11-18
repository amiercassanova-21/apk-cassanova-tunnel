package com.v2ray.ang.handler

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil

/**
 * Cassanova: the phone's own "Private DNS" in strict mode (a hostname set in system settings)
 * sends DNS straight to that host over TLS, around the VPN; on some phones (seen on vivo and
 * Xiaomi) the connection then fails. "Automatic" mode does not cause this, so only the
 * strict hostname is reported.
 */
object PrivateDnsCheck {

    /**
     * The strict Private DNS hostname of the network this app uses, or null when it is off,
     * automatic, or unknown (Android below 9 has no Private DNS). The app itself is excluded
     * from its VPN, so this is the underlying Wi-Fi/mobile network even while connected.
     */
    fun strictHost(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return strictHostP(context)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun strictHostP(context: Context): String? = try {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        cm?.getLinkProperties(cm.activeNetwork)?.privateDnsServerName?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        LogUtil.w(AppConfig.TAG, "Private DNS check failed", e)
        null
    }
}
