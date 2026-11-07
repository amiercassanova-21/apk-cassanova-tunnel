package com.v2ray.ang.handler

import android.content.Context
import android.os.Build
import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import java.io.File

/**
 * The Cassanova connection log ("Log Koneksi"): a small text file in the app's files folder.
 * The core daemon process writes it (connection steps, SSH engine lines, server banner, auto
 * ping), the UI process reads it. It does not depend on Logcat or the app log level, so it is
 * always filled. Secrets (passwords, payloads, locked configs) are never written here.
 *
 * Line format: `<epochMillis>\t<kind>\t<escaped text>`; see [Kind].
 */
object ConnLog {
    private const val FILE_NAME = "connection_log.txt"
    private const val MAX_LINES = 500
    private const val TRIM_AT = 600

    /** I info, O success, W warning, E error, B server banner (HTML), S session start. */
    enum class Kind(val code: Char) {
        INFO('I'), OK('O'), WARN('W'), ERROR('E'), BANNER('B'), SESSION('S');

        companion object {
            fun from(code: Char): Kind? = entries.firstOrNull { it.code == code }
        }
    }

    data class Line(val time: Long, val kind: Kind, val text: String)

    private val lock = Any()
    private var file: File? = null
    private var linesWritten = 0

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    /** Daemon: starts a new session (clears the old log) with the device header. */
    fun begin(context: Context) {
        synchronized(lock) {
            val f = file(context)
            file = f
            try {
                f.writeText("")
            } catch (_: Exception) {
            }
            linesWritten = 0
        }
        write(Kind.SESSION, "")
        info("${if (BuildConfig.LITE) "Amier VPN Lite" else "Amier VPN"} ${BuildConfig.VERSION_NAME} (build ${BuildConfig.CASSANOVA_BUILD})")
        info("${Build.MANUFACTURER} ${Build.MODEL}")
        info("Android ${Build.VERSION.RELEASE} API-${Build.VERSION.SDK_INT} (${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()})")
    }

    /** Daemon: attaches to the current log file without clearing it (e.g. after a reload). */
    fun attach(context: Context) {
        synchronized(lock) {
            if (file == null) file = file(context)
        }
    }

    fun info(text: String) = write(Kind.INFO, text)
    fun ok(text: String) = write(Kind.OK, text)
    fun warn(text: String) = write(Kind.WARN, text)
    fun error(text: String) = write(Kind.ERROR, text)
    fun banner(html: String) = write(Kind.BANNER, html)

    private fun write(kind: Kind, text: String) {
        // Mirrors to Logcat at a level the app log setting never filters out.
        if (kind != Kind.SESSION && kind != Kind.BANNER) Log.println(Log.WARN, AppConfig.TAG, "CONN: $text")
        synchronized(lock) {
            val f = file ?: return
            try {
                f.appendText(encode(Line(System.currentTimeMillis(), kind, text)) + "\n")
                linesWritten++
                if (linesWritten >= TRIM_AT) {
                    val kept = f.readLines().takeLast(MAX_LINES)
                    f.writeText(kept.joinToString("\n", postfix = "\n"))
                    linesWritten = kept.size
                }
            } catch (_: Exception) {
            }
        }
    }

    /** UI: every line of the current log, oldest first. */
    fun read(context: Context): List<Line> = try {
        file(context).takeIf { it.exists() }?.readLines()?.mapNotNull(::decode) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    /** UI: clears the log. */
    fun clear(context: Context) {
        try {
            file(context).writeText("")
        } catch (_: Exception) {
        }
    }

    // ---------- pure helpers (unit tested) ----------

    fun encode(line: Line): String = "${line.time}\t${line.kind.code}\t${escape(line.text)}"

    fun decode(raw: String): Line? {
        val first = raw.indexOf('\t')
        if (first <= 0 || raw.length < first + 3 || raw[first + 2] != '\t') return null
        val time = raw.substring(0, first).toLongOrNull() ?: return null
        val kind = Kind.from(raw[first + 1]) ?: return null
        return Line(time, kind, unescape(raw.substring(first + 3)))
    }

    fun escape(text: String): String =
        text.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

    fun unescape(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                when (text[i + 1]) {
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    '\\' -> out.append('\\')
                    else -> out.append(c).append(text[i + 1])
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    /** The banner of the current session and the session's start time, or null. */
    fun sessionBanner(lines: List<Line>): Pair<Long, String>? {
        val session = lines.lastOrNull { it.kind == Kind.SESSION } ?: return null
        val banner = lines.lastOrNull { it.kind == Kind.BANNER && it.time >= session.time } ?: return null
        return session.time to banner.text
    }
}
