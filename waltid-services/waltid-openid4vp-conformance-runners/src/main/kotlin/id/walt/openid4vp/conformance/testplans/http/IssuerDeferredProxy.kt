package id.walt.openid4vp.conformance.testplans.http

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import id.walt.openid4vp.conformance.testplans.DeferredProxyEvent
import id.walt.openid4vp.conformance.testplans.deferredOfferKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.time.Duration
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.zip.ZipException

/** Test-only proxy. It never manufactures credential responses or validates tokens. */
class IssuerDeferredProxy(
    private val upstream: URI,
    port: Int,
    private val evidenceFile: Path,
    private val requestTimeout: Duration = Duration.ofSeconds(30),
    bindHost: String = "127.0.0.1",
) : AutoCloseable {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER).build()
    private val offers = ConcurrentHashMap.newKeySet<String>()
    private val executor = Executors.newFixedThreadPool(16)
    private val server = HttpServer.create(InetSocketAddress(bindHost, port), 0).apply { this.executor = this@IssuerDeferredProxy.executor }
    val port: Int get() = server.address.port

    init {
        require(upstream.scheme == "http" && upstream.host in setOf("127.0.0.1", "localhost")) {
            "The conformance proxy upstream must be the local OSS issuer"
        }
        Files.createDirectories(evidenceFile.toAbsolutePath().parent)
        Files.writeString(evidenceFile, "")
        server.createContext("/") { exchange ->
            try { forward(exchange) } catch (exception: Exception) {
                // Never include request data or upstream exception messages in the response/log.
                val route = exchange.requestURI.path.takeIf { it in observedPaths } ?: "<other>"
                System.err.println("Issuer conformance proxy failed: ${exchange.requestMethod} $route (${exception::class.simpleName})")
                exchange.runCatching { sendResponseHeaders(502, -1) }
            } finally { exchange.close() }
        }
    }

    fun start() = server.start()

    @Synchronized
    private fun record(key: String, event: String) {
        Files.writeString(evidenceFile, Json.encodeToString(DeferredProxyEvent(key, event)) + "\n", APPEND)
    }

    private fun forward(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        val body = exchange.requestBody.readAllBytes()
        val request = HttpRequest.newBuilder(URI.create(upstream.toString().trimEnd('/') + exchange.requestURI.toASCIIString()))
            .timeout(requestTimeout).method(exchange.requestMethod, HttpRequest.BodyPublishers.ofByteArray(body))
        val excluded = hopHeaders + exchange.requestHeaders["Connection"].orEmpty().flatMap { it.split(',') }.map { it.trim().lowercase() }
        exchange.requestHeaders.forEach { (name, values) ->
            if (name.lowercase() !in excluded) values.forEach { request.header(name, it) }
        }
        if (exchange.requestHeaders.getFirst("X-Forwarded-Host") == null) {
            exchange.requestHeaders.getFirst("Host")?.let { request.header("X-Forwarded-Host", it) }
        }
        val response = client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray())

        if (exchange.requestMethod == "POST" && path == "/issuer2/credential-offers" && response.statusCode() == 201) {
            val deferred = Json.parseToJsonElement(body.decodeToString()).jsonObject["deferred"]?.jsonPrimitive?.booleanOrNull == true
            if (deferred) {
                // Cloudflare adds Accept-Encoding even when the management client did not.
                // Decode a copy for correlation; forward the original body and encoding headers.
                val receipt = decodeManagementReceipt(response.body(), response.headers().allValues("Content-Encoding"))
                val id = Json.parseToJsonElement(receipt.decodeToString()).jsonObject.getValue("offerId").jsonPrimitive.content
                val key = deferredOfferKey(id)
                offers.add(key)
                record(key, "registered")
            }
        }

        if (exchange.requestMethod == "POST" && path in setOf("/openid4vci/credential", "/openid4vci/deferred_credential")) {
            // The issuer has already verified this token. Decode only for test correlation.
            val subject = sessionSubject(exchange.requestHeaders.getFirst("Authorization"))
            val key = subject?.let(::deferredOfferKey)
            if (key != null && key in offers) {
                if (path == "/openid4vci/credential" && response.statusCode() == 202) {
                    record(key, "initial202")
                    val approval = try {
                        client.send(
                            HttpRequest.newBuilder(upstream.resolve("/issuer2/credential-offers/$subject/deferred/complete"))
                                .timeout(requestTimeout).POST(HttpRequest.BodyPublishers.noBody()).build(),
                            HttpResponse.BodyHandlers.discarding(),
                        )
                    } catch (exception: Exception) {
                        record(key, "failed")
                        throw exception
                    }
                    record(key, if (approval.statusCode() == 204) "approved204" else "failed")
                } else if (path == "/openid4vci/deferred_credential" && response.statusCode() == 200) {
                    record(key, "collected200")
                }
            } else if (path == "/openid4vci/credential" && response.statusCode() == 202) {
                // Uncorrelated deferred traffic cannot silently bypass approval/evidence checks.
                error("Unregistered deferred offer")
            }
        }

        val responseExcluded = hopHeaders + response.headers().allValues("Connection").flatMap { it.split(',') }.map { it.trim().lowercase() }
        response.headers().map().forEach { (name, values) ->
            if (name.lowercase() !in responseExcluded) exchange.responseHeaders[name] = values
        }
        val noBody = exchange.requestMethod == "HEAD" || response.statusCode() in setOf(204, 304)
        exchange.sendResponseHeaders(response.statusCode(), if (noBody || response.body().isEmpty()) -1 else response.body().size.toLong())
        if (!noBody) exchange.responseBody.write(response.body())
    }

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
        client.close()
    }

    companion object {
        private val hopHeaders = setOf("host", "connection", "content-length", "transfer-encoding", "keep-alive", "proxy-authenticate", "proxy-authorization", "te", "trailer", "upgrade", "expect")
        private val observedPaths = setOf("/issuer2/credential-offers", "/openid4vci/credential", "/openid4vci/deferred_credential")

        private fun decodeManagementReceipt(body: ByteArray, encodings: List<String>): ByteArray =
            encodings.flatMap { it.split(',') }.map { it.trim().lowercase() }.asReversed().fold(body) { bytes, encoding ->
                when (encoding) {
                    "", "identity" -> bytes
                    "gzip" -> GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readAllBytes() }
                    "deflate" -> try {
                        inflateManagementReceipt(bytes, raw = false)
                    } catch (_: ZipException) {
                        // Ktor sends raw DEFLATE; other upstreams may use a zlib wrapper.
                        inflateManagementReceipt(bytes, raw = true)
                    }
                    else -> error("Unsupported management receipt content encoding")
                }
            }

        private fun inflateManagementReceipt(bytes: ByteArray, raw: Boolean): ByteArray {
            val inflater = Inflater(raw)
            return try {
                InflaterInputStream(ByteArrayInputStream(bytes), inflater).use { it.readAllBytes() }
            } finally {
                inflater.end()
            }
        }

        internal fun sessionSubject(authorization: String?): String? = runCatching {
            val token = requireNotNull(authorization).substringAfter(' ')
            Json.parseToJsonElement(Base64.getUrlDecoder().decode(token.split('.')[1]).decodeToString())
                .jsonObject.getValue("sub").jsonPrimitive.content.also {
                    require(it.matches(Regex("[A-Za-z0-9_-]+")))
                }
        }.getOrNull()

        @JvmStatic
        fun main(args: Array<String>) {
            require(args.size in 3..4) { "Usage: IssuerDeferredProxy <upstream> <listen-port> <evidence-file> [bind-host]" }
            val proxy = IssuerDeferredProxy(URI.create(args[0]), args[1].toInt(), Path.of(args[2]), bindHost = args.getOrElse(3) { "127.0.0.1" })
            Runtime.getRuntime().addShutdownHook(Thread { proxy.close() })
            proxy.start()
            println("Issuer conformance proxy listening on port ${proxy.port}")
        }
    }
}
