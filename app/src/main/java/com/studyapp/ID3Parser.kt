package com.studyapp

import android.content.res.AssetManager
import java.io.InputStream

object ID3Parser {

    data class Tags(val title: String?, val artist: String?)

    fun parse(fileName: String, stream: InputStream): Tags {
        val bytes = stream.readBytes()
        if (bytes.size < 10) return Tags(null, null)

        return if (bytes.size >= 3 &&
            bytes[0] == 0x49.toByte() &&
            bytes[1] == 0x44.toByte() &&
            bytes[2] == 0x33.toByte()
        ) {
            parseID3v2(bytes)
        } else {
            parseID3v1(bytes, fileName)
        }
    }

    private fun parseID3v2(bytes: ByteArray): Tags {
        var offset = 10
        if (bytes.size < offset) return Tags(null, null)

        val rawSize = ((bytes[6].toInt() and 0x7F) shl 21) or
                ((bytes[7].toInt() and 0x7F) shl 14) or
                ((bytes[8].toInt() and 0x7F) shl 7) or
                (bytes[9].toInt() and 0x7F)
        var tagEnd = offset + rawSize
        if (tagEnd > bytes.size) tagEnd = bytes.size

        val majorVer = bytes[3].toInt() and 0xFF
        var title: String? = null
        var artist: String? = null

        while (offset + 10 <= tagEnd) {
            val frameId = when (majorVer) {
                2 -> String(bytes, offset, 3)
                else -> String(bytes, offset, 4)
            }
            val frameSize = when (majorVer) {
                2 -> ((bytes[offset + 3].toInt() and 0xFF) shl 16) or
                        ((bytes[offset + 4].toInt() and 0xFF) shl 8) or
                        (bytes[offset + 5].toInt() and 0xFF)
                else -> ((bytes[offset + 4].toInt() and 0xFF) shl 24) or
                        ((bytes[offset + 5].toInt() and 0xFF) shl 16) or
                        ((bytes[offset + 6].toInt() and 0xFF) shl 8) or
                        (bytes[offset + 7].toInt() and 0xFF)
            }
            if (frameSize <= 0 || frameSize > tagEnd - offset - 10) break

            val dataOffset = offset + (if (majorVer == 2) 6 else 10)
            val dataSize = frameSize - (if (majorVer == 2) 0 else 0)
            if (dataOffset + dataSize > bytes.size) break

            val id = when (majorVer) {
                2 -> when (frameId) { "TT2" -> "TIT2"; "TP1" -> "TPE1"; else -> frameId }
                else -> frameId
            }

            when (id) {
                "TIT2" -> title = extractString(bytes, dataOffset, dataSize)
                "TPE1" -> artist = extractString(bytes, dataOffset, dataSize)
            }

            offset += (if (majorVer == 2) 6 else 10) + frameSize
        }
        return Tags(title, artist)
    }

    private fun extractString(bytes: ByteArray, offset: Int, size: Int): String {
        if (offset >= bytes.size || size <= 1) return ""
        val encoding = bytes[offset].toInt() and 0xFF
        val start = offset + 1
        val end = minOf(start + size - 1, bytes.size)
        if (start >= end) return ""
        return when (encoding) {
            1, 2 -> {
                var nullFound = false
                val filtered = (start until end).filter { i ->
                    if (bytes[i] == 0.toByte() && !nullFound) { nullFound = true; false }
                    else !nullFound
                }.map { bytes[it] }.toByteArray()
                String(filtered, Charsets.UTF_16)
            }
            3 -> String(bytes, start, end - start, Charsets.UTF_8)
            else -> String(bytes, start, end - start, Charsets.ISO_8859_1)
        }.trim()
    }

    private fun parseID3v1(bytes: ByteArray, fileName: String): Tags {
        if (bytes.size < 128) return Tags(null, null)
        val tagStart = bytes.size - 128
        if (bytes[tagStart].toInt() != 0x54 || bytes[tagStart + 1].toInt() != 0x41 || bytes[tagStart + 2].toInt() != 0x47) {
            return Tags(null, null)
        }
        val title = String(bytes, tagStart + 3, 30, Charsets.ISO_8859_1).trim { it <= ' ' }
        val artist = String(bytes, tagStart + 33, 30, Charsets.ISO_8859_1).trim { it <= ' ' }
        return Tags(title.ifEmpty { null }, artist.ifEmpty { null })
    }
}
