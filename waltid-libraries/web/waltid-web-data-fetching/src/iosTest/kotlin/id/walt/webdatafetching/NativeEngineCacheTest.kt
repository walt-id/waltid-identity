@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package id.walt.webdatafetching

import id.walt.webdatafetching.engines.NativeEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.cinterop.*
import kotlinx.coroutines.test.runTest
import platform.Foundation.*
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.posix.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class NativeEngineCacheTest {
    @Test
    fun nativeClientDoesNotPersistCredentialResponses() = runTest(timeout = 30.seconds) {
        val cacheDirectory = NSTemporaryDirectory() + "wallet-http-cache-" + NSUUID().UUIDString
        val previousCache = NSURLCache.sharedURLCache
        val cache = NSURLCache(
            memoryCapacity = 1024uL,
            diskCapacity = 32uL * 1024uL * 1024uL,
            directoryURL = NSURL.fileURLWithPath(cacheDirectory),
        )
        NSURLCache.setSharedURLCache(cache)
        val markers = listOf("CONTROL-CREDENTIAL", "IMMEDIATE-CREDENTIAL", "DEFERRED-CREDENTIAL")
        val server = LoopbackCredentialServer(markers.map { "{\"credentials\":[{\"credential\":\"$it-${"x".repeat(65536)}\"}]}" })
        try {
            // Positive control: the unmodified Darwin configuration persists this POST body.
            val controlClient = HttpClient(Darwin)
            try {
                assertTrue(controlClient.postCredential(server.url).contains(markers[0]))
            } finally {
                controlClient.close()
            }
            // Foundation writes on its own queue; use real time, independent of runTest's clock.
            for (attempt in 1..50) {
                if (cacheContains(cacheDirectory, markers[0])) break
                NSThread.sleepForTimeInterval(0.1)
            }
            assertTrue(cacheContains(cacheDirectory, markers[0]), "Positive control did not persist a body")

            val client = NativeEngine.getHttpClient {}
            try {
                for ((index, marker) in markers.drop(1).withIndex()) {
                    val endpoint = if (index == 0) server.url else server.url.replace("/credential", "/deferred")
                    assertTrue(client.postCredential(endpoint).contains(marker))
                }
            } finally {
                client.close()
            }
            NSThread.sleepForTimeInterval(1.0)
            for (marker in markers.drop(1)) {
                assertFalse(cacheContains(cacheDirectory, marker), "Native client persisted $marker")
            }
        } finally {
            server.close()
            NSURLCache.setSharedURLCache(previousCache)
            NSFileManager.defaultManager.removeItemAtPath(cacheDirectory, null)
        }
    }

    private suspend fun HttpClient.postCredential(endpoint: String): String = post(endpoint) {
        contentType(ContentType.Application.Json)
        bearerAuth("SYNTHETIC-CACHE-TEST")
        setBody("{}")
    }.bodyAsText()

    private fun cacheContains(directory: String, marker: String): Boolean {
        val markerBytes = marker.encodeToByteArray()
        val needle = markerBytes.usePinned { NSData.create(bytes = it.addressOf(0), length = markerBytes.size.toULong()) }
        val entries = NSFileManager.defaultManager.enumeratorAtPath(directory) ?: return false
        while (true) {
            val entry = entries.nextObject() as? String ?: return false
            val data = NSData.dataWithContentsOfFile("$directory/$entry") ?: continue
            val found = data.rangeOfData(needle, options = 0uL, range = NSMakeRange(0uL, data.length))
                .useContents { location != NSNotFound.toULong() }
            if (found) return true
        }
    }
}

/** A local HTTP fixture deliberately omitting Cache-Control. No external issuer or real credentials. */
private class LoopbackCredentialServer(responses: List<String>) {
    private val listener = socket(AF_INET, SOCK_STREAM, 0)
    val url: String

    init {
        check(listener >= 0)
        val port = memScoped {
            val address = alloc<sockaddr_in> {
                sin_len = sizeOf<sockaddr_in>().toUByte()
                sin_family = AF_INET.toUByte()
                sin_port = 0u
                sin_addr.ptr.reinterpret<UByteVar>().let { bytes ->
                    bytes[0] = 127u
                    bytes[1] = 0u
                    bytes[2] = 0u
                    bytes[3] = 1u
                }
            }
            check(bind(listener, address.ptr.reinterpret(), sizeOf<sockaddr_in>().toUInt()) == 0)
            check(listen(listener, 4) == 0)
            val size = alloc<socklen_tVar> { value = sizeOf<sockaddr_in>().toUInt() }
            check(getsockname(listener, address.ptr.reinterpret(), size.ptr) == 0)
            val bytes = address.ptr.reinterpret<UByteVar>()
            (bytes[2].toInt() shl 8) or bytes[3].toInt()
        }
        url = "http://127.0.0.1:$port/credential"
        dispatch_async(dispatch_get_global_queue(0, 0u)) {
            for (body in responses) {
                val connection = accept(listener, null, null)
                if (connection < 0) break
                try {
                    memScoped {
                        val noSigpipe = alloc<IntVar> { value = 1 }
                        setsockopt(connection, SOL_SOCKET, SO_NOSIGPIPE, noSigpipe.ptr, sizeOf<IntVar>().toUInt())
                        val buffer = ByteArray(4096)
                        var request = ""
                        buffer.usePinned { pinned ->
                            while (true) {
                                val read = recv(connection, pinned.addressOf(0), buffer.size.toULong(), 0).toInt()
                                if (read <= 0) break
                                request += buffer.decodeToString(endIndex = read)
                                val headerEnd = request.indexOf("\r\n\r\n")
                                if (headerEnd >= 0) {
                                    val length = Regex("Content-Length: (\\d+)", RegexOption.IGNORE_CASE)
                                        .find(request.substring(0, headerEnd))?.groupValues?.get(1)?.toInt() ?: 0
                                    if (request.length >= headerEnd + 4 + length) break
                                }
                            }
                        }
                    }
                    val headers = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/json\r\n" +
                        "Content-Length: ${body.length}\r\n" +
                        "Connection: close\r\n\r\n"
                    val bytes = (headers + body).encodeToByteArray()
                    bytes.usePinned { pinned ->
                        var sent = 0
                        while (sent < bytes.size) {
                            val count = send(connection, pinned.addressOf(sent), (bytes.size - sent).toULong(), 0).toInt()
                            if (count <= 0) break
                            sent += count
                        }
                    }
                } finally {
                    platform.posix.close(connection)
                }
            }
        }
    }

    fun close() {
        shutdown(listener, SHUT_RDWR)
        platform.posix.close(listener)
    }
}
