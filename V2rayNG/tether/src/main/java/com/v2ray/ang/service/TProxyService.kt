package com.v2ray.ang.service

/**
 * JNI entry points of libhev-socks5-tunnel.so. The library is built for Amier VPN and registers
 * its natives on `com/v2ray/ang/service/TProxyService` in JNI_OnLoad, so Amier Tether keeps this
 * exact class name and method set to reuse the same .so file.
 */
class TProxyService private constructor() {
    companion object {
        init {
            System.loadLibrary("hev-socks5-tunnel")
        }

        @JvmStatic
        @Suppress("FunctionName")
        external fun TProxyStartService(configPath: String, fd: Int): Boolean

        @JvmStatic
        @Suppress("FunctionName")
        external fun TProxyStopService(): Boolean

        @JvmStatic
        @Suppress("FunctionName")
        external fun TProxyIsRunning(): Boolean

        /** [tx packets, tx bytes, rx packets, rx bytes], or null when not running. */
        @JvmStatic
        @Suppress("FunctionName")
        external fun TProxyGetStats(): LongArray?
    }
}
