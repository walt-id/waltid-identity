package id.walt.itb

import io.ktor.http.Url
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/** Opt-in operator fixture. The forwarded device executes whole wallet operations with a native key. */
internal class ItbAndroidWalletDriver private constructor(private val socket: Socket) : AutoCloseable by socket {
    private var sequence = 0

    suspend fun execute(interaction: ItbWalletInteraction) {
        val result = try {
            withTimeout(110_000) {
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
            val trace = result["trace"]?.jsonArray.orEmpty().mapNotNull {
                it.jsonPrimitive.content.takeIf { value -> value.matches(Regex("[A-Za-z0-9_.$:<>-]{1,240}")) }
            }.take(20)
            System.err.println("Native wallet failure locations: ${trace.joinToString()}")
            val code = result["code"]?.jsonPrimitive?.content
            if (code != null && code.matches(Regex("[a-z_]{1,80}"))) throw ItbWalletRejection(code)
            error("The native wallet operation failed")
        }
    }

    companion object {
        suspend fun connect(
            port: Int, token: String, origin: Url, trustPem: String, connectDispatcher: CoroutineDispatcher,
        ): ItbAndroidWalletDriver {
            require(port in 1024..65535 && token.matches(Regex("[0-9a-f]{64}")))
            val socket = Socket()
            try {
                withContext(connectDispatcher) {
                    socket.connect(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 5_000)
                }
                withTimeout(30_000) {
                    ItbDeviceWire.write(socket, buildJsonObject {
                        put("version", 1); put("token", token); put("origin", origin.toString())
                        put("trustPem", trustPem)
                    })
                    val response = ItbDeviceWire.read(socket)
                    check(response["ready"]?.jsonPrimitive?.booleanOrNull == true &&
                        response["provider"]?.jsonPrimitive?.content == "android-native-biometric") {
                        "Native device fixture is not ready"
                    }
                }
                return ItbAndroidWalletDriver(socket)
            } catch (error: Exception) { socket.close(); throw error }
        }
    }
}
