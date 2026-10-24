package com.hitachi.kiosk.ui.qr

import kotlin.random.Random

/** Made-up EMVCo QRIS payloads (merchant presented mode) of a chosen length for the QR tests. */
internal object QrisSamples {
    private const val CRC_TAG_LENGTH = 8
    private const val MAX_VALUE_LENGTH = 99
    private const val TAG_OVERHEAD = 4
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    private fun tag(
        id: String,
        value: String,
    ) = id + value.length.toString().padStart(2, '0') + value

    private val core: String =
        tag("00", "01") +
            tag("01", "12") +
            tag("26", tag("00", "ID.CO.EXAMPLE.WWW") + tag("01", "936000000000000000") + tag("03", "UMI")) +
            tag("51", tag("00", "ID.CO.QRIS.WWW") + tag("02", "ID1020000004721") + tag("03", "UMI")) +
            tag("52", "5812") +
            tag("53", "360") +
            tag("54", "172700") +
            tag("58", "ID") +
            tag("59", "Urban Grill") +
            tag("60", "Jakarta") +
            tag("61", "10110") +
            tag("62", tag("01", "A1B2C3D4E5F6A7B8") + tag("07", "K03"))

    /** A QRIS payload exactly [length] characters long, ending with a CRC tag (the value is not a real CRC). */
    fun payload(length: Int): String {
        val random = Random(length)
        val sb = StringBuilder(core)
        var id = 80
        while (sb.length + CRC_TAG_LENGTH < length) {
            val room = length - CRC_TAG_LENGTH - sb.length
            var valueLength = minOf(MAX_VALUE_LENGTH, room - TAG_OVERHEAD)
            if (room - TAG_OVERHEAD - valueLength in 1 until TAG_OVERHEAD) valueLength -= TAG_OVERHEAD
            val filler = String(CharArray(valueLength) { ALPHABET[random.nextInt(ALPHABET.length)] })
            sb.append(tag((id++).toString(), filler))
        }
        sb.append("6304ABCD")
        check(sb.length == length) { "asked for $length, built ${sb.length}" }
        return sb.toString()
    }

    /** [length] digits, so the encoder picks numeric mode. */
    fun numeric(length: Int): String {
        val random = Random(length)
        return String(CharArray(length) { '0' + random.nextInt(10) })
    }
}
