package id.walt.openid4vp.conformance.adapter

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*

/**
 * VCI Wallet Conformance Test Adapter
 *
 * Bridges the OpenID conformance suite (acting as credential issuer) with wallet-api2.
 *
 * ## Flow
 *
 * 1. Conformance suite POSTs credential offer to `/credential-offer`
 * 2. Adapter parses offer and determines grant type (pre-auth or auth code)
 * 3. For pre-auth: directly calls wallet-api2 `/receive` endpoint
 * 4. For auth code: initiates OAuth flow via `/receive/authorization-url`
 * 5. User completes OAuth login (manual browser interaction)
 * 6. Callback at `/callback` completes token exchange and credential fetch
 *
 * ## Endpoints
 *
 * - `GET/POST /credential-offer` - Receives credential offers from conformance suite
 * - `GET /callback` - OAuth authorization callback
 * - `GET /health` - Health check
 *
 * ## Configuration
 *
 * - Default wallet-api2 URL: `http://127.0.0.1:7005`
 * - Default adapter port: 7007
 * - Binds to 0.0.0.0 for Docker container accessibility
 *
 * @param walletApiUrl Base URL of wallet-api2 service
 * @param adapterPort Port to listen on
 * @param walletId Optional existing wallet ID (auto-creates if null)
 * @param testDid Optional DID to use for credential requests
 * @param testKeyId Optional key ID to use for proof signing
 */
class VciWalletConformanceAdapter(
    private val walletApiUrl: String = "http://127.0.0.1:7005",
    private val adapterPort: Int = 7007,
    private var walletId: String? = null,
    private val testDid: String? = null,
    private val testKeyId: String? = null
) {

    private var server: EmbeddedServer<*, *>? = null
    private var httpClient: HttpClient? = null

    /** Pending authorization code flows awaiting callback */
    private val pendingAuthFlows = mutableMapOf<String, PendingAuthFlow>()

    /** Get the credential offer endpoint URL for conformance suite configuration */
    fun getCredentialOfferEndpoint(): String = "http://127.0.0.1:$adapterPort/credential-offer"

    /** Get the redirect URI for OAuth callbacks */
    fun getRedirectUri(): String = "http://127.0.0.1:$adapterPort/callback"

    /**
     * Start the adapter server.
     *
     * @param client HTTP client for wallet-api2 calls
     */
    suspend fun start(client: HttpClient) {
        this.httpClient = client

        println("[VCI Adapter] Starting on port $adapterPort")
        println("[VCI Adapter] Wallet API: $walletApiUrl")

        // Setup wallet with static key if not provided
        if (walletId == null) {
            walletId = setupTestWallet(client)
        }

        println("[VCI Adapter] Wallet ID: $walletId")
        println("[VCI Adapter] Credential Offer: ${getCredentialOfferEndpoint()}")
        println("[VCI Adapter] Redirect URI: ${getRedirectUri()}")

        server = embeddedServer(CIO, port = adapterPort, host = "0.0.0.0") {
            routing {
                get("/health") {
                    call.respondText("VCI Wallet Conformance Adapter is running")
                }

                post("/credential-offer") { handleCredentialOffer(call) }
                get("/credential-offer") { handleCredentialOffer(call) }
                get("/callback") { handleAuthCallback(call) }
            }
        }.start(wait = false)

        println("[VCI Adapter] Started successfully")
    }

    /** Stop the adapter server */
    fun stop() {
        println("[VCI Adapter] Stopping...")
        server?.stop(1000, 2000)
        server = null
        httpClient = null
        pendingAuthFlows.clear()
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Request Handlers
    // ─────────────────────────────────────────────────────────────────────────────

    private suspend fun handleCredentialOffer(call: ApplicationCall) {
        val client = httpClient ?: run {
            call.respond(HttpStatusCode.InternalServerError, "HTTP client not initialized")
            return
        }

        try {
            println("[VCI Adapter] Received credential offer")

            // Extract offer from query params or body
            val offer = extractOffer(call)
            if (offer == null) {
                println("[VCI Adapter] No offer provided - availability check")
                call.respond(HttpStatusCode.OK, "Credential offer endpoint ready")
                return
            }

            // Parse JSON offer to determine grant type
            val offerJson = parseOfferJson(offer)
            if (offerJson == null) {
                // URI format - try direct claim
                val result = claimCredentialPreAuth(client, offer)
                call.respond(HttpStatusCode.OK, "Processed: ${result.message}")
                return
            }

            // Determine grant type and route accordingly
            val grants = offerJson["grants"]?.jsonObject
            val hasPreAuthCode = grants?.containsKey("urn:ietf:params:oauth:grant-type:pre-authorized_code") == true
            val hasAuthCode = grants?.containsKey("authorization_code") == true

            println("[VCI Adapter] Grants - preAuth: $hasPreAuthCode, authCode: $hasAuthCode")

            when {
                hasPreAuthCode -> {
                    val result = claimCredentialPreAuth(client, offer)
                    call.respond(HttpStatusCode.OK, "Pre-auth claim: ${result.message}")
                }
                hasAuthCode -> {
                    val result = initiateAuthCodeFlow(client, offer)
                    if (result.authorizationUrl != null) {
                        // Store state for callback
                        result.state?.let { state ->
                            pendingAuthFlows[state] = PendingAuthFlow(
                                state = state,
                                codeVerifier = result.codeVerifier,
                                tokenEndpoint = result.tokenEndpoint ?: error("Missing tokenEndpoint in auth response")
                            )
                        }
                        call.respond(HttpStatusCode.OK, buildJsonObject {
                            put("status", "authorization_required")
                            put("authorization_url", result.authorizationUrl)
                            put("state", result.state)
                        }.toString())
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, "Auth flow failed: ${result.error}")
                    }
                }
                else -> {
                    call.respond(HttpStatusCode.BadRequest, "No supported grant type in offer")
                }
            }

        } catch (e: Exception) {
            println("[VCI Adapter] ERROR: ${e.message}")
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
        }
    }

    private suspend fun handleAuthCallback(call: ApplicationCall) {
        val client = httpClient ?: run {
            call.respond(HttpStatusCode.InternalServerError, "HTTP client not initialized")
            return
        }

        try {
            val code = call.request.queryParameters["code"]
            val state = call.request.queryParameters["state"]
            val error = call.request.queryParameters["error"]

            println("[VCI Adapter] Auth callback - code: ${code?.take(20)}..., state: $state, error: $error")

            if (error != null) {
                val errorDesc = call.request.queryParameters["error_description"]
                call.respond(HttpStatusCode.BadRequest, "Authorization error: $error - $errorDesc")
                return
            }

            if (code == null || state == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing code or state")
                return
            }

            val pendingFlow = pendingAuthFlows.remove(state)
            if (pendingFlow == null) {
                // Still show success page even if state not found
                call.respondText(authCompleteHtml("Code received, but no pending flow found"), ContentType.Text.Html)
                return
            }

            // Exchange code for token
            val tokenResult = exchangeCodeForToken(client, code, pendingFlow)
            call.respondText(
                authCompleteHtml(if (tokenResult.success) "Credential issuance complete" else "Token exchange failed: ${tokenResult.message}"),
                ContentType.Text.Html
            )

        } catch (e: Exception) {
            println("[VCI Adapter] ERROR: ${e.message}")
            call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Wallet API Calls
    // ─────────────────────────────────────────────────────────────────────────────

    private suspend fun claimCredentialPreAuth(client: HttpClient, offer: String): ClaimResult {
        return try {
            val url = "$walletApiUrl/wallet/$walletId/credentials/receive"
            println("[VCI Adapter] POST $url")

            val requestBody = buildOfferRequest(offer)
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }

            val body = response.bodyAsText()
            if (response.status.isSuccess()) {
                ClaimResult(true, body)
            } else {
                ClaimResult(false, "Status ${response.status}: $body")
            }
        } catch (e: Exception) {
            ClaimResult(false, "Exception: ${e.message}")
        }
    }

    private suspend fun initiateAuthCodeFlow(client: HttpClient, offer: String): AuthFlowResult {
        return try {
            val url = "$walletApiUrl/wallet/$walletId/credentials/receive/authorization-url"
            println("[VCI Adapter] POST $url")

            val requestBody = buildOfferRequest(offer, includeAuthParams = true)
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }

            val body = response.bodyAsText()
            println("[VCI Adapter] Response: ${response.status} - $body")

            if (response.status.isSuccess()) {
                val result = Json.parseToJsonElement(body).jsonObject
                AuthFlowResult(
                    authorizationUrl = result["authorizationUrl"]?.jsonPrimitive?.content,
                    state = result["state"]?.jsonPrimitive?.content,
                    codeVerifier = result["codeVerifier"]?.jsonPrimitive?.content,
                    tokenEndpoint = result["tokenEndpoint"]?.jsonPrimitive?.content
                )
            } else {
                AuthFlowResult(error = "Status ${response.status}: $body")
            }
        } catch (e: Exception) {
            AuthFlowResult(error = "Exception: ${e.message}")
        }
    }

    private suspend fun exchangeCodeForToken(client: HttpClient, code: String, flow: PendingAuthFlow): TokenResult {
        return try {
            val url = "$walletApiUrl/wallet/$walletId/credentials/receive/exchange-code"
            println("[VCI Adapter] POST $url")

            val requestBody = buildJsonObject {
                put("tokenEndpoint", flow.tokenEndpoint)
                put("code", code)
                flow.codeVerifier?.let { put("codeVerifier", it) }
                put("clientId", "wallet-conformance-test")
                put("redirectUri", getRedirectUri())
            }

            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }

            val body = response.bodyAsText()
            if (response.status.isSuccess()) {
                TokenResult(success = true, message = "Token obtained")
            } else {
                TokenResult(success = false, message = "Status ${response.status}: $body")
            }
        } catch (e: Exception) {
            TokenResult(success = false, message = "Exception: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Wallet Setup
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Setup a test wallet with static key for conformance testing.
     *
     * Wallets need a static key for signing credential proofs. This method
     * checks for existing wallets with static keys or creates a new one.
     */
    private suspend fun setupTestWallet(client: HttpClient): String {
        println("[VCI Adapter] Setting up test wallet...")

        // Check for existing wallet with static key
        val walletsResponse = client.get("$walletApiUrl/wallet")
        val wallets = Json.parseToJsonElement(walletsResponse.bodyAsText()).jsonArray

        for (wallet in wallets) {
            val id = wallet.jsonPrimitive.content
            val infoResponse = client.get("$walletApiUrl/wallet/$id")
            val info = Json.parseToJsonElement(infoResponse.bodyAsText()).jsonObject
            if (info["hasStaticKey"]?.jsonPrimitive?.booleanOrNull == true) {
                println("[VCI Adapter] Using existing wallet: $id")
                return id
            }
        }

        // Create new wallet with embedded EC P-256 static key
        println("[VCI Adapter] Creating new wallet with static key...")
        val createRequest = """
            {
                "staticKey": {
                    "type": "jwk",
                    "jwk": {
                        "kty": "EC",
                        "crv": "P-256",
                        "x": "d5KVpCdze-46QteHfgAswRurlSYUylJ1JntvcbaZ__Y",
                        "y": "uqvaPeOm7SGsdXr34frqkJGAz8tHmR0EmpsSbfqgwDA",
                        "d": "c6TUFwkoQ8QMiz1wZ-4BqJJzvD56RRlcgn0R-XKqQjk"
                    }
                }
            }
        """.trimIndent()

        val createResponse = client.post("$walletApiUrl/wallet") {
            contentType(ContentType.Application.Json)
            setBody(createRequest)
        }
        val createResult = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        val newWalletId = createResult["walletId"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Failed to create wallet: ${createResponse.bodyAsText()}")

        println("[VCI Adapter] Created wallet: $newWalletId")

        // Generate additional key and DID
        client.post("$walletApiUrl/wallet/$newWalletId/keys/generate") {
            contentType(ContentType.Application.Json)
            setBody("""{"keyType": "secp256r1"}""")
        }

        client.post("$walletApiUrl/wallet/$newWalletId/dids/create") {
            contentType(ContentType.Application.Json)
            setBody("""{"method": "key"}""")
        }

        return newWalletId
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────────

    private suspend fun extractOffer(call: ApplicationCall): String? {
        return call.request.queryParameters["credential_offer"]
            ?: call.request.queryParameters["credential_offer_uri"]
            ?: call.receiveText().takeIf { it.isNotBlank() }
    }

    private fun parseOfferJson(offer: String): JsonObject? {
        return if (offer.trimStart().startsWith("{")) {
            Json.parseToJsonElement(offer).jsonObject
        } else null
    }

    private fun buildOfferRequest(offer: String, includeAuthParams: Boolean = false): JsonObject {
        val offerJsonObject = parseOfferJson(offer)
        return buildJsonObject {
            if (offerJsonObject != null) {
                put("offerJson", offerJsonObject)
            } else {
                put("offerUrl", offer)
            }
            put("clientId", "wallet-conformance-test")
            put("redirectUri", getRedirectUri())
            if (includeAuthParams) {
                put("usePkce", true)
            }
            testDid?.let { put("did", it) }
            testKeyId?.let { put("keyId", it) }
        }
    }

    private fun authCompleteHtml(message: String): String = """
        <!DOCTYPE html>
        <html>
        <head><title>Authorization Complete</title></head>
        <body>
            <h1>Authorization Complete</h1>
            <p>$message</p>
            <p>You can close this window.</p>
        </body>
        </html>
    """.trimIndent()

    // ─────────────────────────────────────────────────────────────────────────────
    // Data Classes
    // ─────────────────────────────────────────────────────────────────────────────

    private data class PendingAuthFlow(
        val state: String,
        val codeVerifier: String?,
        val tokenEndpoint: String
    )

    private data class ClaimResult(val success: Boolean, val message: String)

    private data class AuthFlowResult(
        val authorizationUrl: String? = null,
        val state: String? = null,
        val codeVerifier: String? = null,
        val tokenEndpoint: String? = null,
        val error: String? = null
    )

    private data class TokenResult(val success: Boolean, val message: String)
}
