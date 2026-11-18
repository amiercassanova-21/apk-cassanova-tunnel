package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Cassanova: internet speed test (ping, download, upload) against Cloudflare's speed test
 * endpoints. Goes through the local proxy when connected (see [LocalProxyClient]), so it
 * measures the tunnel. Each transfer phase runs [STREAMS] parallel requests for at most
 * [PHASE_MS] or until its data cap is used, to keep quota use bounded.
 */
object InternetSpeedTest {

    private const val DOWN_URL = "https://speed.cloudflare.com/__down?bytes="
    private const val UP_URL = "https://speed.cloudflare.com/__up"

    const val STREAMS = 4
    const val PHASE_MS = 10_000L

    /** The first part of each phase (TCP ramp-up, proxy buffers) is left out of the rate. */
    const val WARMUP_MS = 2_000L
    const val DOWN_CAP_BYTES = 150L * 1024 * 1024
    const val UP_CAP_BYTES = 50L * 1024 * 1024
    private const val DOWN_REQUEST_BYTES = 25L * 1024 * 1024
    private const val UP_REQUEST_BYTES = 4L * 1024 * 1024
    private const val PING_COUNT = 6
    private const val PROGRESS_MS = 250L
    private const val TIMEOUT_MS = 15_000L

    fun client(proxyPort: Int?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (proxyPort != null) LocalProxyClient.useProxy(builder, proxyPort)
        return builder.build()
    }

    /** Megabits per second for [bytes] in [nanos]; 0 for an empty interval. */
    fun mbps(bytes: Long, nanos: Long): Double =
        if (nanos <= 0 || bytes <= 0) 0.0 else bytes * 8.0 / 1_000_000.0 / (nanos / 1e9)

    /**
     * The rate of a phase: from the warm-up mark to the end when the phase lasted past the
     * warm-up, otherwise over the whole phase.
     */
    fun phaseRate(totalBytes: Long, totalNanos: Long, warmBytes: Long?, warmNanos: Long?): Double =
        if (warmBytes != null && warmNanos != null && totalNanos > warmNanos) {
            mbps(totalBytes - warmBytes, totalNanos - warmNanos)
        } else {
            mbps(totalBytes, totalNanos)
        }

    fun median(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        return sorted[(sorted.size - 1) / 2]
    }

    /** Median round trip of small requests (the first one, which opens the connection, is skipped). */
    suspend fun ping(client: OkHttpClient): Long? = withContext(Dispatchers.IO) {
        val times = ArrayList<Long>()
        repeat(PING_COUNT) { i ->
            ensureActive()
            val start = System.nanoTime()
            val ok = try {
                client.newCall(Request.Builder().url(DOWN_URL + "0").build()).execute().use { it.isSuccessful }
            } catch (e: IOException) {
                LogUtil.w(AppConfig.TAG, "Speed test: ping request failed", e)
                false
            }
            if (ok && i > 0) times.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start))
        }
        median(times)
    }

    /** Result of a phase; [mbps] is null when no data moved at all. */
    data class Phase(val mbps: Double?, val bytes: Long)

    suspend fun download(client: OkHttpClient, onProgress: (Double) -> Unit): Phase =
        runPhase(DOWN_CAP_BYTES, onProgress) { counter, deadline, calls ->
            val call = client.newCall(Request.Builder().url(DOWN_URL + DOWN_REQUEST_BYTES).build())
            calls.add(call)
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val source = response.body?.source() ?: throw IOException("No body")
                val buffer = okio.Buffer()
                while (System.nanoTime() < deadline && counter.get() < DOWN_CAP_BYTES) {
                    val n = source.read(buffer, 64 * 1024)
                    if (n < 0) break
                    counter.addAndGet(n)
                    buffer.clear()
                }
            }
        }

    private val octetStream = "application/octet-stream".toMediaType()
    private val uploadChunk = ByteArray(64 * 1024) { (it * 31 + 7).toByte() }

    suspend fun upload(client: OkHttpClient, onProgress: (Double) -> Unit): Phase =
        runPhase(UP_CAP_BYTES, onProgress) { counter, deadline, calls ->
            val body = object : RequestBody() {
                override fun contentType() = octetStream
                override fun contentLength() = UP_REQUEST_BYTES
                override fun writeTo(sink: BufferedSink) {
                    var left = UP_REQUEST_BYTES
                    while (left > 0) {
                        if (System.nanoTime() >= deadline || counter.get() >= UP_CAP_BYTES) {
                            throw IOException("Phase over")
                        }
                        val n = minOf(left, uploadChunk.size.toLong()).toInt()
                        sink.write(uploadChunk, 0, n)
                        sink.flush()
                        counter.addAndGet(n.toLong())
                        left -= n
                    }
                }
            }
            val call = client.newCall(Request.Builder().url(UP_URL).post(body).build())
            calls.add(call)
            call.execute().close()
        }

    /**
     * Runs [STREAMS] workers that repeat [request] until the phase deadline or [cap], reporting
     * the running rate every [PROGRESS_MS]. Calls still open at the end are cancelled.
     */
    private suspend fun runPhase(
        cap: Long,
        onProgress: (Double) -> Unit,
        request: (counter: AtomicLong, deadline: Long, calls: ConcurrentLinkedQueue<Call>) -> Unit,
    ): Phase = withContext(Dispatchers.IO) {
        val counter = AtomicLong()
        val calls = ConcurrentLinkedQueue<Call>()
        val start = System.nanoTime()
        val deadline = start + TimeUnit.MILLISECONDS.toNanos(PHASE_MS)
        val warmAt = start + TimeUnit.MILLISECONDS.toNanos(WARMUP_MS)
        val workers = (1..STREAMS).map {
            launch {
                while (isActive && System.nanoTime() < deadline && counter.get() < cap) {
                    try {
                        request(counter, deadline, calls)
                    } catch (e: IOException) {
                        if (System.nanoTime() < deadline && counter.get() < cap) {
                            LogUtil.w(AppConfig.TAG, "Speed test: transfer request failed", e)
                            delay(300)
                        }
                    }
                }
            }
        }
        var warmBytes: Long? = null
        var warmNanos: Long? = null
        try {
            while (System.nanoTime() < deadline && counter.get() < cap && workers.any { it.isActive }) {
                delay(PROGRESS_MS)
                val now = System.nanoTime()
                if (warmBytes == null && now >= warmAt) {
                    warmBytes = counter.get()
                    warmNanos = now - start
                }
                onProgress(mbps(counter.get(), now - start))
            }
        } finally {
            calls.forEach { it.cancel() }
            workers.forEach { it.cancel() }
        }
        val total = counter.get()
        val elapsed = minOf(System.nanoTime(), deadline) - start
        Phase(if (total > 0) phaseRate(total, elapsed, warmBytes, warmNanos) else null, total)
    }
}
