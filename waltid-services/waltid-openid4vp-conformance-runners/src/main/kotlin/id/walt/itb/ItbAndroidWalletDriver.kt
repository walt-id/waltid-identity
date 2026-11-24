package id.walt.itb

import io.ktor.http.Url
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Carries wallet operations to the Android device used by the live runner. */
internal class ItbAndroidWalletDriver private constructor(
    private val socket: Socket,
    private val ioDispatcher: CoroutineDispatcher,
    private val operationTimeoutMillis: Long,
) : AutoCloseable by socket {
    val isClosed: Boolean get() = socket.isClosed
    private var sequence = 0

    suspend fun execute(interaction: ItbWalletInteraction): ItbWalletResult {
        val result = try {
            withSocketDeadline(socket, operationTimeoutMillis, ioDispatcher) {
                ItbDeviceWire.write(socket, buildJsonObject {
                    put("sequence", ++sequence); put("interaction", ItbDeviceWire.encode(interaction))
                })
                ItbDeviceWire.read(socket).also {
                    check(it["sequence"]?.jsonPrimitive?.intOrNull == sequence) { "Uncorrelated device response" }
                }
            }
        } catch (error: Exception) {
            close() // Closing cancels pending device work; a broken channel is never retried.
            throw error
        }
        if (result["success"]?.jsonPrimitive?.booleanOrNull != true) {
            result["tokenFailure"]?.let { throw ItbTokenFailure.decode(it.jsonObject) }
            val trace = result["trace"]?.jsonArray.orEmpty().mapNotNull {
                it.jsonPrimitive.content.takeIf { value -> value.matches(Regex("[A-Za-z0-9_.$:<>-]{1,240}")) }
            }.take(20)
            System.err.println("Android wallet failure locations: ${trace.joinToString()}")
            val code = result["code"]?.jsonPrimitive?.content
            if (code != null && code.matches(Regex("[a-z0-9_]{1,80}"))) throw ItbWalletRejection(code)
            error("The Android wallet operation failed")
        }
        return Json.decodeFromJsonElement(ItbWalletResult.serializer(), result.getValue("observation"))
    }

    companion object {
        /** Closing the socket must not depend on a blocked OutputStream.write returning. */
        internal suspend fun <T> withSocketDeadline(
            socket: Socket, timeoutMillis: Long, ioDispatcher: CoroutineDispatcher, operation: suspend () -> T,
        ): T =
            withTimeout(timeoutMillis) {
                suspendCancellableCoroutine { continuation ->
                    val workerScope = CoroutineScope(ioDispatcher + SupervisorJob())
                    val worker = workerScope.launch {
                        try {
                            continuation.resume(operation())
                        } catch (error: Throwable) {
                            if (continuation.isActive) continuation.resumeWithException(error)
                        } finally {
                            workerScope.cancel()
                        }
                    }
                    continuation.invokeOnCancellation {
                        runCatching { socket.close() }
                        worker.cancel()
                    }
                }
            }

        suspend fun connect(
            port: Int, token: String, origin: Url, trustPem: String?, connectDispatcher: CoroutineDispatcher,
            provider: String = "android-native-biometric", operationTimeoutMillis: Long = 110_000,
        ): ItbAndroidWalletDriver {
            require(port in 1024..65535 && token.matches(Regex("[0-9a-f]{64}")))
            val socket = Socket()
            try {
                withContext(connectDispatcher) {
                    socket.connect(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 5_000)
                }
                withSocketDeadline(socket, 180_000, connectDispatcher) {
                    ItbDeviceWire.write(socket, buildJsonObject {
                        put("version", ItbDeviceWire.VERSION); put("token", token); put("origin", origin.toString())
                        trustPem?.let { put("trustPem", it) }
                    })
                    val response = ItbDeviceWire.read(socket)
                    check(response["ready"]?.jsonPrimitive?.booleanOrNull == true &&
                        response["provider"]?.jsonPrimitive?.content == provider) {
                        "Expected Android wallet driver is not ready"
                    }
                }
                return ItbAndroidWalletDriver(socket, connectDispatcher, operationTimeoutMillis)
            } catch (error: Exception) { socket.close(); throw error }
        }
    }
}
