package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.mdoc.proximity.ProximityCloseReason
import id.walt.mdoc.proximity.ProximityConnection
import id.walt.mdoc.proximity.ProximityError
import id.walt.mdoc.proximity.ProximityException
import id.walt.mdoc.proximity.ProximityTransportKind
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield

/** Strict holder-side HTTP/1.1 message framing over one Wi-Fi Aware TCP stream. */
internal class WifiAwareHttpConnection(
    private val raw: WifiAwareRawConnection,
    private val maximumMessageBytes: Int,
) : ProximityConnection {
    override val kind: ProximityTransportKind = ProximityTransportKind.WIFI_AWARE

    private val exchangeMutex = Mutex()
    private val closed = atomic(false)
    private var buffered = ByteArray(0)
    private var responseExpected = false

    init {
        require(maximumMessageBytes > 0)
    }

    override suspend fun receive(): ImmutableBytes? = exchangeMutex.withLock {
        ensureOpen()
        check(!responseExpected) { "A Wi-Fi Aware response is still pending" }
        try {
            val headerEnd = readHeaderEnd() ?: return@withLock null
            val header = buffered.copyOfRange(0, headerEnd)
            buffered = buffered.copyOfRange(headerEnd + HEADER_DELIMITER.size, buffered.size)
            val contentLength = parseRequestHeader(header)
            readBody(contentLength)
            val body = buffered.copyOfRange(0, contentLength)
            buffered = buffered.copyOfRange(contentLength, buffered.size)
            responseExpected = true
            ImmutableBytes.of(body)
        } catch (cancelled: CancellationException) {
            closeRaw(ProximityCloseReason.CANCELLED)
            throw cancelled
        } catch (failure: ProximityException) {
            closeRaw(ProximityCloseReason.PROTOCOL_ERROR)
            throw failure
        } catch (failure: Throwable) {
            closeRaw(ProximityCloseReason.PROTOCOL_ERROR)
            throw protocolFailure(failure.message ?: "Malformed Wi-Fi Aware HTTP request", failure)
        }
    }

    override suspend fun send(message: ImmutableBytes): Unit = exchangeMutex.withLock {
        ensureOpen()
        check(responseExpected) { "A Wi-Fi Aware response requires a preceding request" }
        require(message.size <= maximumMessageBytes) {
            "Wi-Fi Aware response exceeds the configured message limit"
        }
        val body = message.copy()
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Length: ")
            append(body.size)
            append("\r\nContent-Type: application/cbor\r\n\r\n")
        }.encodeToByteArray()
        try {
            raw.write(header + body)
            responseExpected = false
        } catch (cancelled: CancellationException) {
            closeRaw(ProximityCloseReason.CANCELLED)
            throw cancelled
        } catch (failure: Throwable) {
            closeRaw(ProximityCloseReason.PEER_DISCONNECTED)
            throw ProximityException(
                ProximityError.Transport(
                    "wifi_aware_socket_write_failed",
                    "The Wi-Fi Aware response could not be written",
                ),
                failure,
            )
        } finally {
            body.fill(0)
        }
    }

    override suspend fun close(reason: ProximityCloseReason) {
        closeRaw(reason)
    }

    private suspend fun readHeaderEnd(): Int? {
        while (true) {
            indexOf(buffered, HEADER_DELIMITER)?.let { return it }
            if (buffered.size >= MAXIMUM_HEADER_BYTES) {
                throw protocolFailure("Wi-Fi Aware HTTP headers exceed the supported limit")
            }
            val next = raw.read(minOf(READ_CHUNK_BYTES, MAXIMUM_HEADER_BYTES - buffered.size))
            if (next == null) {
                if (buffered.isEmpty()) return null
                throw protocolFailure("Wi-Fi Aware peer disconnected during HTTP headers")
            }
            if (next.isEmpty()) {
                yield()
            } else {
                buffered += next
            }
        }
    }

    private suspend fun readBody(length: Int) {
        while (buffered.size < length) {
            val next = raw.read(minOf(READ_CHUNK_BYTES, length - buffered.size))
                ?: throw protocolFailure("Wi-Fi Aware peer disconnected during the HTTP body")
            if (next.isEmpty()) yield() else buffered += next
        }
    }

    private fun parseRequestHeader(encoded: ByteArray): Int {
        require(encoded.all { byte -> byte == '\r'.code.toByte() || byte == '\n'.code.toByte() || byte.toInt() in 0x20..0x7e }) {
            "Wi-Fi Aware HTTP headers contain non-ASCII control bytes"
        }
        val lines = encoded.decodeToString().split("\r\n")
        require(lines.isNotEmpty() && lines.first() == "POST /mdoc HTTP/1.1") {
            "Wi-Fi Aware retrieval requires POST /mdoc HTTP/1.1"
        }
        require(lines.all { it.length <= MAXIMUM_HEADER_LINE_CHARS }) {
            "Wi-Fi Aware HTTP header line exceeds the supported limit"
        }
        val fields = linkedMapOf<String, MutableList<String>>()
        lines.drop(1).forEach { line ->
            require(line.isNotEmpty() && line.first() != ' ' && line.first() != '\t') {
                "Wi-Fi Aware HTTP headers must not be empty or folded"
            }
            val separator = line.indexOf(':')
            require(separator > 0) { "Wi-Fi Aware HTTP header is malformed" }
            val name = line.substring(0, separator)
            require(name.all(::isTokenCharacter)) { "Wi-Fi Aware HTTP header name is invalid" }
            fields.getOrPut(name.lowercase()) { mutableListOf() } += line.substring(separator + 1).trim()
        }
        require(fields["host"]?.singleOrNull()?.isNotEmpty() == true) {
            "Wi-Fi Aware HTTP request requires one Host header"
        }
        require(fields["transfer-encoding"] == null) {
            "Wi-Fi Aware retrieval does not support transfer encoding"
        }
        require(fields["content-type"]?.singleOrNull()?.equals("application/cbor", ignoreCase = true) == true) {
            "Wi-Fi Aware HTTP request requires application/cbor"
        }
        val lengthText = fields["content-length"]?.singleOrNull()
            ?: throw IllegalArgumentException("Wi-Fi Aware HTTP request requires one Content-Length header")
        require(lengthText.isNotEmpty() && lengthText.all(Char::isDigit)) {
            "Wi-Fi Aware HTTP Content-Length is invalid"
        }
        val length = lengthText.toLongOrNull()
            ?: throw IllegalArgumentException("Wi-Fi Aware HTTP Content-Length overflows")
        require(length in 1..maximumMessageBytes.toLong()) {
            "Wi-Fi Aware HTTP body is empty or exceeds the configured message limit"
        }
        return length.toInt()
    }

    private fun ensureOpen() = check(!closed.value) { "Wi-Fi Aware connection is closed" }

    private fun closeRaw(reason: ProximityCloseReason) {
        if (closed.compareAndSet(expect = false, update = true)) {
            buffered.fill(0)
            buffered = ByteArray(0)
            raw.close(reason)
        }
    }

    private fun protocolFailure(message: String, cause: Throwable? = null): ProximityException =
        ProximityException(ProximityError.Protocol("wifi_aware_http_invalid", message), cause)

    private companion object {
        val HEADER_DELIMITER = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
        const val MAXIMUM_HEADER_BYTES = 16 * 1024
        const val MAXIMUM_HEADER_LINE_CHARS = 4 * 1024
        const val READ_CHUNK_BYTES = 8 * 1024

        fun indexOf(bytes: ByteArray, pattern: ByteArray): Int? {
            if (bytes.size < pattern.size) return null
            for (start in 0..bytes.size - pattern.size) {
                if (pattern.indices.all { offset -> bytes[start + offset] == pattern[offset] }) return start
            }
            return null
        }

        fun isTokenCharacter(character: Char): Boolean =
            character in '0'..'9' || character in 'A'..'Z' || character in 'a'..'z' ||
                character in "!#$%&'*+-.^_`|~"
    }
}
