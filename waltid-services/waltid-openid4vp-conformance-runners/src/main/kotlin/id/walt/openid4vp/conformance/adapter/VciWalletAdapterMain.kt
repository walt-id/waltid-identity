package id.walt.openid4vp.conformance.adapter

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 * Standalone runner for VCI Wallet Conformance Adapter.
 *
 * Use this for manual testing with the OpenID conformance suite:
 *
 * ```bash
 * ./gradlew :waltid-services:waltid-openid4vp-conformance-runners:runVciWalletAdapter
 * ```
 *
 * Or run directly from IDE.
 *
 * ## Prerequisites
 *
 * - wallet-api2 running on port 7005
 * - Conformance suite Docker containers running
 *
 * ## Environment Variables
 *
 * - `WALLET_API_URL` - wallet-api2 URL (default: http://127.0.0.1:7005)
 * - `ADAPTER_PORT` - Adapter port (default: 7007)
 */
fun main() = runBlocking {
    println("═".repeat(60))
    println(" VCI Wallet Conformance Adapter - Standalone Mode")
    println("═".repeat(60))

    val walletApiUrl = System.getenv("WALLET_API_URL") ?: "http://127.0.0.1:7005"
    val adapterPort = System.getenv("ADAPTER_PORT")?.toIntOrNull() ?: 7007
    val hostIp = getHostIp()

    println("""
        |
        | Configuration:
        |   Wallet API: $walletApiUrl
        |   Adapter Port: $adapterPort
        |   Host IP: $hostIp
        |
    """.trimMargin())

    val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
    }

    val adapter = VciWalletConformanceAdapter(
        walletApiUrl = walletApiUrl,
        adapterPort = adapterPort
    )

    try {
        adapter.start(httpClient)

        println("""
            |
            |═══════════════════════════════════════════════════════════════
            | Adapter is running! Press Ctrl+C to stop.
            |
            | Local health check:
            |   curl http://127.0.0.1:$adapterPort/health
            |
            | For conformance suite configuration:
            |   credential_offer_endpoint: http://$hostIp:$adapterPort/credential-offer
            |
            | Browser 'Visit' button will open:
            |   http://$hostIp:$adapterPort/credential-offer?...
            |═══════════════════════════════════════════════════════════════
        """.trimMargin())

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(Thread {
            println("\nShutting down...")
            adapter.stop()
            httpClient.close()
        })

        // Block forever
        Thread.currentThread().join()

    } catch (e: Exception) {
        println("ERROR: ${e.message}")
        e.printStackTrace()
        adapter.stop()
        httpClient.close()
    }
}

/**
 * Get the host's IP address for Docker container access.
 * On Linux, `host.docker.internal` doesn't work, so we need the actual IP.
 */
private fun getHostIp(): String {
    return try {
        val process = ProcessBuilder("sh", "-c", "ip route get 1.1.1.1 | awk '{print \$7; exit}'")
            .redirectErrorStream(true)
            .start()
        val ip = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        if (ip.isNotEmpty() && ip.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
            ip
        } else {
            "127.0.0.1"
        }
    } catch (_: Exception) {
        "127.0.0.1"
    }
}
