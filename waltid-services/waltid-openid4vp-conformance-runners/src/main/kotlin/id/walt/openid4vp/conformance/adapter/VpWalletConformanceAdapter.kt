package id.walt.openid4vp.conformance.adapter

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*

/**
 * Wallet Conformance Test Adapter
 * 
 * Bridges the OpenID conformance suite (verifier) with the walt.id wallet API.
 * 
 * The conformance suite expects a wallet to:
 * 1. Receive authorization requests via URL scheme (openid4vp://)
 * 2. Fetch the request from request_uri
 * 3. Process and generate VP response
 * 4. POST response to verifier's response_uri
 * 
 * This adapter simulates a wallet app by:
 * - Exposing an HTTP endpoint that conformance suite can invoke
 * - Building the full authorization URL from query parameters
 * - Calling wallet API's /present endpoint for full flow
 * - The wallet API handles everything: fetch, match, sign, submit
 * 
 * This is TEST INFRASTRUCTURE, not a production wallet implementation.
 */
class VpWalletConformanceAdapter(
    private val walletApiUrl: String = "http://127.0.0.1:7005",
    private val adapterPort: Int = 7006,
    private val walletId: String = resolveWalletId()
) {
    companion object {
        private fun resolveWalletId(): String {
            // Priority: env var > file > fallback
            System.getenv("CONFORMANCE_WALLET_ID")?.takeIf { it.isNotBlank() }?.let { return it }

            // Try reading from file written by setup script
            runCatching {
                java.io.File("/tmp/conformance-wallet-id.txt").readText().trim()
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let {
                println("[Adapter] Read wallet ID from /tmp/conformance-wallet-id.txt: $it")
                return it
            }

            return "conformance-test-wallet"  // Triggers auto-discovery
        }
    }
    
    private var server: EmbeddedServer<*, *>? = null
    
    /**
     * Start the adapter server
     */
    fun start(httpClient: HttpClient) {
        println("[Adapter] Starting Wallet Conformance Adapter on port $adapterPort")
        println("[Adapter] Wallet API URL: $walletApiUrl")
        println("[Adapter] Using wallet ID: $walletId")
        
        server = embeddedServer(CIO, port = adapterPort) {
            routing {
                // Health check
                get("/health") {
                    call.respondText("Wallet Conformance Adapter is running")
                }
                
                // OpenID4VP authorization endpoint
                // This is what the conformance suite will call
                get("/openid4vp/authorize") {
                    handleAuthorizationRequest(call, httpClient)
                }
                
                // Alternative: handle as POST (some verifiers might POST)
                post("/openid4vp/authorize") {
                    handleAuthorizationRequest(call, httpClient)
                }
            }
        }.start(wait = false)
        
        println("[Adapter] Started successfully")
        println("[Adapter] Authorization endpoint: http://127.0.0.1:$adapterPort/openid4vp/authorize")
    }
    
    /**
     * Stop the adapter server
     */
    fun stop() {
        println("[Adapter] Stopping...")
        server?.stop(1000, 2000)
        server = null
    }
    
    /**
     * Handle authorization request from conformance suite
     */
    private suspend fun handleAuthorizationRequest(call: ApplicationCall, httpClient: HttpClient) {
        try {
            println("[Adapter] Received authorization request")
            println("[Adapter] Query parameters: ${call.request.queryParameters}")

            // Get available wallet ID dynamically if not configured
            val effectiveWalletId = if (walletId == "conformance-test-wallet") {
                // Auto-discover: find a wallet with credentials
                val walletsResponse = httpClient.get("$walletApiUrl/wallet")
                val wallets = Json.parseToJsonElement(walletsResponse.bodyAsText()).jsonArray
                if (wallets.isEmpty()) {
                    println("[Adapter] ERROR: No wallets available")
                    call.respond(HttpStatusCode.InternalServerError, "No wallets available in wallet API")
                    return
                }

                // Find first wallet that has credentials
                var foundWalletId: String? = null
                for (walletJson in wallets) {
                    val wId = walletJson.jsonPrimitive.content
                    val credsResponse = httpClient.get("$walletApiUrl/wallet/$wId/credentials")
                    val creds = Json.parseToJsonElement(credsResponse.bodyAsText()).jsonArray
                    if (creds.isNotEmpty()) {
                        foundWalletId = wId
                        println("[Adapter] Found wallet with ${creds.size} credential(s): $wId")
                        break
                    }
                }

                if (foundWalletId == null) {
                    println("[Adapter] ERROR: No wallets with credentials found")
                    call.respond(HttpStatusCode.InternalServerError,
                        "No wallets with credentials found. Run setup-test-wallet.sh first.")
                    return
                }
                foundWalletId
            } else {
                walletId
            }
            
            // Extract parameters
            val requestUri = call.parameters["request_uri"]
            val request = call.parameters["request"]
            val clientId = call.parameters["client_id"]
            val requestUriMethod = call.parameters["request_uri_method"]

            println("[Adapter] Parameters:")
            println("[Adapter]   client_id: $clientId")
            println("[Adapter]   request_uri: $requestUri")
            println("[Adapter]   request_uri_method: $requestUriMethod")

            // Build the full authorization URL to pass to wallet API
            // The wallet API expects the full openid4vp:// URL
            val fullAuthUrl = buildString {
                append("openid4vp://")
                append("?client_id=")
                append(java.net.URLEncoder.encode(clientId ?: "", "UTF-8"))
                if (requestUri != null) {
                    append("&request_uri=")
                    append(java.net.URLEncoder.encode(requestUri, "UTF-8"))
                }
                if (request != null) {
                    append("&request=")
                    append(java.net.URLEncoder.encode(request, "UTF-8"))
                }
                if (requestUriMethod != null) {
                    append("&request_uri_method=")
                    append(requestUriMethod)
                }
            }
            
            println("[Adapter] Built authorization URL: ${fullAuthUrl.take(150)}...")
            
            // Call wallet API's full presentation endpoint
            // This handles: fetch request -> match credentials -> sign -> submit to verifier
            println("[Adapter] Calling wallet API /present endpoint for wallet: $effectiveWalletId")
            val presentResponse = httpClient.post("$walletApiUrl/wallet/$effectiveWalletId/credentials/present") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("requestUrl", fullAuthUrl)
                })
            }
            
            val responseBody = presentResponse.bodyAsText()
            println("[Adapter] Wallet API response: ${presentResponse.status}")
            println("[Adapter] Response body: ${responseBody.take(500)}")
            
            if (presentResponse.status.isSuccess()) {
                println("[Adapter] Presentation completed successfully")
                // Return the wallet's response which may contain redirect_to for fragment-based flows
                call.respond(presentResponse.status, responseBody)
            } else {
                println("[Adapter] ERROR: Wallet API returned ${presentResponse.status}")
                // Pass through the wallet's error response - important for negative test detection
                call.respond(presentResponse.status, responseBody)
            }
            
        } catch (e: Exception) {
            println("[Adapter] ERROR: ${e.message}")
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
        }
    }
}
