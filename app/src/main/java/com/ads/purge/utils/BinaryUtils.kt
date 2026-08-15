package com.ads.purge.utils

object BinaryUtils {

    fun bytesToHex(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): String {
        val sb = StringBuilder()
        for (i in offset until minOf(offset + length, bytes.size)) {
            sb.append(String.format("%02X ", bytes[i]))
        }
        return sb.toString().trim()
    }

    fun hexToBytes(hex: String): ByteArray {
        val cleaned = hex.replace(" ", "").replace("\n", "")
        val len = cleaned.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(cleaned[i], 16) shl 4) +
                    Character.digit(cleaned[i + 1], 16)).toByte()
        }
        return data
    }

    fun readLe32(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    fun writeLe32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((value shr 16) and 0xFF).toByte()
        bytes[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }
}
