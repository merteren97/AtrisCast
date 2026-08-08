package com.atrishub.atriscast.airplay

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

object RtspRequestParser {
    fun read(input: InputStream): RtspRequest? {
        val buffered = if (input is BufferedInputStream) input else BufferedInputStream(input)
        val headerBytes = readHeaders(buffered) ?: return null
        val headerText = headerBytes.toString(Charsets.ISO_8859_1)
        val lines = headerText.split("\r\n")
        val requestLine = lines.firstOrNull()?.trim().orEmpty()
        val parts = requestLine.split(' ', limit = 3)
        if (parts.size != 3) return null

        val headers = linkedMapOf<String, String>()
        lines.drop(1).filter { it.isNotBlank() }.forEach { line ->
            val separator = line.indexOf(':')
            if (separator > 0) {
                headers[line.substring(0, separator).trim()] = line.substring(separator + 1).trim()
            }
        }

        val contentLength = headers.entries
            .firstOrNull { it.key.equals("Content-Length", true) }
            ?.value?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        require(contentLength <= MAX_BODY_BYTES) {
            "RTSP body exceeds $MAX_BODY_BYTES bytes"
        }

        val body = ByteArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val count = buffered.read(body, read, contentLength - read)
            if (count < 0) break
            read += count
        }

        return RtspRequest(parts[0], parts[1], parts[2], headers, if (read == body.size) body else body.copyOf(read))
    }

    private fun readHeaders(input: InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        var state = 0
        while (out.size() < MAX_HEADER_BYTES) {
            val b = input.read()
            if (b < 0) return if (out.size() == 0) null else out.toByteArray()
            out.write(b)
            state = when {
                state == 0 && b == '\r'.code -> 1
                state == 1 && b == '\n'.code -> 2
                state == 2 && b == '\r'.code -> 3
                state == 3 && b == '\n'.code -> return out.toByteArray()
                b == '\r'.code -> 1
                else -> 0
            }
        }
        throw IllegalArgumentException("RTSP header exceeds $MAX_HEADER_BYTES bytes")
    }

    private const val MAX_HEADER_BYTES = 64 * 1024
    private const val MAX_BODY_BYTES = 4 * 1024 * 1024
}
