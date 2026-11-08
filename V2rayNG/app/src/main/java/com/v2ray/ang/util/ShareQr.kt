package com.v2ray.ang.util

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Cassanova: QR codes of the share sheet (pure ZXing, unit tested). Error correction Q keeps the
 * code readable with the small badge drawn over its centre, from a screenshot or another phone.
 */
object ShareQr {
    /** Badge size as a fraction of the QR width; well inside what level Q can repair. */
    const val BADGE_FRACTION = 0.16f

    /** Above this many characters the code gets dense: the sheet suggests copying the link. */
    const val DENSE_LENGTH = 600

    fun matrix(text: String, size: Int): BitMatrix {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to Charsets.UTF_8.name(),
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.Q,
            EncodeHintType.MARGIN to 2,
        )
        return QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    }

    fun isDense(text: String): Boolean = text.length > DENSE_LENGTH
}
