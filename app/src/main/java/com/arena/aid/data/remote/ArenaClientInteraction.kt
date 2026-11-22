package com.arena.aid.data.remote

import java.security.SecureRandom
import kotlin.math.ceil

/**
 * Exact Kotlin port of arena.ai's `encodeClientInteraction` ("cx" field, version 2)
 * and UUIDv7 generator used by `/nextjs-api/stream/create-evaluation`.
 */
object ArenaClientInteraction {
    private const val VERSION = 2
    private const val BASE64_URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private val layoutBits = intArrayOf(
        2,  // submitKind (1 = button)
        2,  // submitTrusted (2 = true)
        4,  // untrustedUiEvents (0)
        8,  // charsTyped
        4,  // insertMulti (0)
        4,  // pasteCount (0)
        1,  // pasteWithoutModifier (0)
        1,  // composition (0)
        4,  // otherInputTypes (0)
        3,  // docOwnProps (0)
        1,  // coarsePointer (1 = true on mobile)
        16, // integrity (0)
        3,  // pressBucket (2 = lt50)
        2,  // pressureBucket (2 = half)
        2,  // movesBeforeClick (1 = 1to3)
        2,  // maxCoalesced (1 = 1)
        2,  // heartbeatGapBucket (0 = lt2s)
        2,  // visibilityChangesBucket (0 = 0)
        1,  // focusVisContradiction (0)
        16, // visitTag
        16  // minuteTag
    )

    private val totalBits = layoutBits.sum()
    private val payloadBytes = ceil(totalBits / 8.0).toInt()
    private val blobBytes = 3 + payloadBytes

    fun encode(charsTyped: Int, sessionId: String): String {
        val salt = fnv1a16(sessionId.ifBlank { "local" })
        val minuteTag = ((System.currentTimeMillis() / 60_000L).toInt()) and 0xFFFF
        val visitTag = fnv1a16(sessionId)

        val fieldValues = intArrayOf(
            1, // submitKind = "button"
            2, // submitTrusted = true
            0, // untrustedUiEvents
            charsTyped.coerceIn(1, 255),
            0, // insertMulti
            0, // pasteCount
            0, // pasteWithoutModifier
            0, // composition
            0, // otherInputTypes
            0, // docOwnProps
            1, // coarsePointer = true
            0, // integrity
            2, // pressBucket = "lt50"
            2, // pressureBucket = "half"
            1, // movesBeforeClick = "1to3"
            1, // maxCoalesced = "1"
            0, // heartbeatGapBucket = "lt2s"
            0, // visibilityChangesBucket = "0"
            0, // focusVisContradiction = false
            visitTag,
            minuteTag
        )

        var seed = (0x5bd1e995 xor (salt and 0xFFFF))
        fun nextRand(): Int {
            seed += 0x6d2b79f5
            var e = imul(seed xor (seed ushr 15), 1 or seed)
            e = e xor (e + imul(e xor (e ushr 7), 61 or e))
            return (e xor (e ushr 14))
        }

        val order = IntArray(layoutBits.size) { it }
        for (i in order.size - 1 downTo 1) {
            val r = (nextRand().toLong() and 0xFFFFFFFFL)
            val j = (r % (i + 1)).toInt()
            val tmp = order[i]
            order[i] = order[j]
            order[j] = tmp
        }

        val mask = ByteArray(payloadBytes)
        for (i in 0 until payloadBytes) {
            mask[i] = (nextRand() and 0xFF).toByte()
        }

        val payload = ByteArray(payloadBytes)
        var bitPos = 0
        for (idx in order) {
            val bits = layoutBits[idx]
            val value = fieldValues[idx]
            for (b in bits - 1 downTo 0) {
                if (((value ushr b) and 1) != 0) {
                    val byteIdx = bitPos shr 3
                    payload[byteIdx] = (payload[byteIdx].toInt() or (128 ushr (bitPos and 7))).toByte()
                }
                bitPos++
            }
        }

        for (i in 0 until payloadBytes) {
            payload[i] = (payload[i].toInt() xor mask[i].toInt()).toByte()
        }

        val out = ByteArray(blobBytes)
        out[0] = VERSION.toByte()
        out[1] = ((salt ushr 8) and 0xFF).toByte()
        out[2] = (salt and 0xFF).toByte()
        System.arraycopy(payload, 0, out, 3, payloadBytes)

        return toBase64Url(out)
    }

    private fun fnv1a16(input: String): Int {
        var h = 0x811c9dc5.toInt()
        for (ch in input) {
            h = h xor ch.code
            h = imul(h, 0x01000193)
        }
        return (h xor (h ushr 16)) and 0xFFFF
    }

    private fun imul(a: Int, b: Int): Int = a * b

    private fun toBase64Url(bytes: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else null
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else null
            val triple = (b0 shl 16) or ((b1 ?: 0) shl 8) or (b2 ?: 0)
            sb.append(BASE64_URL[(triple ushr 18) and 63])
            sb.append(BASE64_URL[(triple ushr 12) and 63])
            if (b1 != null) sb.append(BASE64_URL[(triple ushr 6) and 63])
            if (b2 != null) sb.append(BASE64_URL[triple and 63])
            i += 3
        }
        return sb.toString()
    }

    fun uuidV7(): String {
        val r = SecureRandom()
        val b = ByteArray(16).also { r.nextBytes(it) }
        val ts = System.currentTimeMillis()
        for (i in 0..5) b[i] = (ts shr (8 * (5 - i))).toByte()
        b[6] = ((b[6].toInt() and 0x0F) or 0x70).toByte()
        b[8] = ((b[8].toInt() and 0x3F) or 0x80).toByte()
        val hex = b.joinToString("") { "%02x".format(it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}
