package com.v2ray.ang.util

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShareQrTest {

    private val plain = "vless://3f2a9c1e-7b4d-4e8a-9a51-2c6d0e8f1b77@sgdo9.cassanova.example.id:443" +
        "?encryption=none&security=tls&sni=sgdo9.cassanova.example.id&type=ws&host=sgdo9.cassanova.example.id&path=%2Fvless#09%20vless"

    // A locked link is base64 of nonce + AES-GCM(plain) + tag: about 4/3 of the plain link plus 40 chars.
    private val locked = "cslock://" + java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString(ByteArray(plain.length + 28) { (it * 37 + 11).toByte() })

    private val sshPayload = "ssh://admin09:secret@sgdo9.example.id:80?mode=payload&payload=" +
        "GET%20%2F%20HTTP%2F1.1%5Bcrlf%5DHost%3A%20edu.example.com%5Bcrlf%5D%5Bcrlf%5DPATCH%20%2F%20HTTP%2F1.1".repeat(3) + "#09%20ssh"

    /** Same reader setup as QRCodeDecoder.syncDecodeQRCode (gallery images). */
    private fun decode(width: Int, height: Int, pixels: IntArray): String? {
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.CHARACTER_SET to "UTF-8",
        )
        val source = RGBLuminanceSource(width, height, pixels)
        return try {
            QRCodeReader().decode(BinaryBitmap(GlobalHistogramBinarizer(source)), hints).text
        } catch (e: NotFoundException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    /** The QR as the sheet shows it: [size] px, black on white, with the dark centre badge. */
    private fun render(text: String, size: Int): IntArray {
        val m = ShareQr.matrix(text, size)
        val px = IntArray(size * size) { i -> if (m.get(i % size, i / size)) 0xff000000.toInt() else 0xffffffff.toInt() }
        val badge = (size * ShareQr.BADGE_FRACTION).toInt()
        val from = (size - badge) / 2
        for (y in from until from + badge) for (x in from until from + badge) {
            val border = x < from + 3 || y < from + 3 || x >= from + badge - 3 || y >= from + badge - 3
            px[y * size + x] = if (border) 0xffffffff.toInt() else 0xff0e1220.toInt()
        }
        return px
    }

    /** A phone screenshot: dark app background, the white card, the QR at [qrPx] px. */
    private fun screenshot(text: String, qrPx: Int): Triple<Int, Int, IntArray> {
        val w = 1080
        val h = 2400
        val shot = IntArray(w * h) { 0xff161b2d.toInt() }
        val qr = render(text, qrPx)
        val left = (w - qrPx) / 2
        val top = 900
        for (y in top - 30 until top + qrPx + 90) for (x in left - 30 until left + qrPx + 30) shot[y * w + x] = 0xffffffff.toInt()
        for (y in 0 until qrPx) System.arraycopy(qr, y * qrPx, shot, (top + y) * w + left, qrPx)
        return Triple(w, h, shot)
    }

    @Test
    fun badgeDoesNotBreakTheCode() {
        listOf(plain, locked, sshPayload).forEach { text ->
            assertEquals(text, decode(800, 800, render(text, 800)), text.take(12))
        }
    }

    @Test
    fun screenshotOfTheSheetIsReadable() {
        // 240 dp on a 2.75x screen is 660 px; a small 2x screen gives 480 px.
        listOf(660, 480).forEach { qrPx ->
            listOf(plain, locked, sshPayload).forEach { text ->
                val (w, h, shot) = screenshot(text, qrPx)
                assertEquals(text, decode(w, h, shot), "${text.take(12)} at $qrPx px")
            }
        }
    }

    @Test
    fun longLinksAreFlaggedDense() {
        assertFalse(ShareQr.isDense(plain))
        assertTrue(ShareQr.isDense("x".repeat(ShareQr.DENSE_LENGTH + 1)))
    }
}
