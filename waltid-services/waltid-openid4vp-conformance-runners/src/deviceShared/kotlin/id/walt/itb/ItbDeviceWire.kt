package id.walt.itb

import io.ktor.http.Url
import io.ktor.http.URLProtocol
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.*
import java.io.EOFException
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Test-only, bounded messages over loopback + adb forwarding. No private-key signing RPC. */
internal object ItbDeviceWire {
    private const val MAX_BYTES = 1024 * 1024

    fun approvedOrigin(value: String): Url = Url(value).also {
        require(it.protocol == URLProtocol.HTTPS && it.host == "dev-i4mlab.aegean.gr" && it.port == 443 &&
            it.user == null && it.password == null && it.encodedPath in setOf("", "/") &&
            it.parameters.isEmpty() && it.fragment.isEmpty()) { "Only the approved ITB tenant is supported" }
    }

    suspend fun read(socket: Socket): JsonObject = withContext(Dispatchers.IO) {
        socket.soTimeout = 250
        suspend fun bytes(size: Int): ByteArray {
            val result = ByteArray(size)
            var offset = 0
            while (offset < size) {
                coroutineContext.ensureActive()
                val count = try { socket.getInputStream().read(result, offset, size - offset) }
                    catch (_: SocketTimeoutException) { continue }
                if (count < 0) throw EOFException("ITB device channel disconnected")
                offset += count
            }
            return result
        }
        val size = ByteBuffer.wrap(bytes(4)).int
        require(size in 1..MAX_BYTES) { "Invalid ITB device frame size" }
        Json.parseToJsonElement(bytes(size).decodeToString(throwOnInvalidSequence = true)).jsonObject
    }

    suspend fun write(socket: Socket, value: JsonObject) = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        val bytes = value.toString().encodeToByteArray()
        require(bytes.size in 1..MAX_BYTES)
        socket.getOutputStream().apply {
            write(ByteBuffer.allocate(4).putInt(bytes.size).array())
            write(bytes)
            flush()
        }
    }

    fun authenticate(request: JsonObject, expected: String) {
        require(expected.matches(Regex("[0-9a-f]{64}"))) { "A fresh 256-bit device token is required" }
        val received = request["token"]?.jsonPrimitive?.content ?: ""
        require(MessageDigest.isEqual(received.encodeToByteArray(), expected.encodeToByteArray())) {
            "ITB device authentication failed"
        }
        require(request["version"]?.jsonPrimitive?.intOrNull == 1) { "Unsupported device protocol" }
    }

    fun encode(interaction: ItbWalletInteraction): JsonObject = buildJsonObject {
        when (interaction) {
            is ItbWalletInteraction.Offer -> {
                put("kind", "offer"); put("url", interaction.url.toString())
                interaction.transactionCode?.let { put("transactionCode", it) }
            }
            is ItbWalletInteraction.Presentation -> { put("kind", "presentation"); put("url", interaction.url.toString()) }
            is ItbWalletInteraction.DigitalCredentials -> {
                put("kind", "dc"); put("endpoint", interaction.endpoint.toString())
                put("validationSession", interaction.validationSession); put("profile", interaction.profile)
                put("protocol", interaction.protocol)
                interaction.payment?.let { payment -> put("payment", buildJsonObject {
                    put("attestationType", payment.attestationType); put("merchant", payment.merchant)
                    put("payeeId", payment.payeeId); put("currency", payment.currency)
                    put("amount", payment.amount); put("transactionId", payment.transactionId)
                }) }
            }
        }
    }

    fun decode(value: JsonObject): ItbWalletInteraction {
        fun JsonObject.text(name: String) = getValue(name).jsonPrimitive.content
        return when (value.text("kind")) {
            "offer" -> ItbWalletInteraction.Offer(Url(value.text("url")), value["transactionCode"]?.jsonPrimitive?.content)
            "presentation" -> ItbWalletInteraction.Presentation(Url(value.text("url")))
            "dc" -> ItbWalletInteraction.DigitalCredentials(
                Url(value.text("endpoint")), value.text("validationSession"), value.text("profile"), value.text("protocol"),
                value["payment"]?.jsonObject?.let { ItbWalletInteraction.Payment(
                    it.text("attestationType"), it.text("merchant"), it.text("payeeId"),
                    it.text("currency"), it.text("amount"), it.text("transactionId"),
                ) },
            )
            else -> error("Unsupported wallet operation")
        }
    }

    /** EOF cancels an in-flight wallet operation, including an outstanding native prompt. */
    suspend fun serve(socket: Socket, execute: suspend (ItbWalletInteraction) -> Unit) = coroutineScope {
        val scope = this
        val requests = Channel<JsonObject>()
        val reader = launch {
            try { while (true) requests.send(read(socket)) }
            catch (error: Exception) {
                if (isActive && !socket.isClosed) scope.cancel("ITB device channel ended", error)
            }
            finally { requests.close() }
        }
        try {
            var expected = 1
            for (request in requests) {
                require(request["sequence"]?.jsonPrimitive?.intOrNull == expected++) { "Out-of-order wallet operation" }
                require(expected <= 101) { "Device run exceeds its bounded operation count" }
                val result = try {
                    execute(decode(request.getValue("interaction").jsonObject))
                    coroutineContext.ensureActive()
                    buildJsonObject { put("success", true) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    // Never send exception messages, credentials or protocol values to the host report.
                    buildJsonObject {
                        put("success", false)
                        put("failure", error::class.simpleName ?: "WalletFailure")
                        put("trace", buildJsonArray {
                            generateSequence(error as Throwable?) { it.cause }.take(5).forEach { cause ->
                                add(cause.javaClass.name)
                                cause.stackTrace.filter { it.className.startsWith("id.walt") }.take(3).forEach {
                                    add("${it.className}.${it.methodName}:${it.lineNumber}")
                                }
                            }
                        })
                        if (error is ItbWalletRejection) put("code", error.code)
                    }
                }
                write(socket, JsonObject(result + ("sequence" to request.getValue("sequence"))))
            }
        } finally {
            socket.close()
            withContext(NonCancellable) { reader.cancelAndJoin() }
        }
    }
}
