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
    
    /** Stored credential offers awaiting user action */
    private val pendingOffers = mutableMapOf<String, String>()
    
    /** Generate unique offer ID */
    private fun generateOfferId(): String = java.util.UUID.randomUUID().toString().take(8)

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

                post("/credential-offer") { handleCredentialOfferApi(call) }
                get("/credential-offer") { handleCredentialOfferDirectPage(call) }
                get("/offer/:offerId") { handleOfferPage(call) }
                post("/start-issuance") { handleStartIssuance(call) }
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
        pendingOffers.clear()
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Request Handlers
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Fetch credential format from issuer metadata
     */
    private suspend fun getCredentialFormat(credentialIssuerUrl: String, configurationId: String): String? {
        return try {
            val metadataUrl = "$credentialIssuerUrl/.well-known/openid-credential-issuer"
            val response = httpClient?.get(metadataUrl)
            if (response?.status?.isSuccess() == true) {
                val metadata = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                val configurations = metadata["credential_configurations_supported"]?.jsonObject
                val config = configurations?.get(configurationId)?.jsonObject
                config?.get("format")?.jsonPrimitive?.content
            } else null
        } catch (e: Exception) {
            println("[VCI Adapter] Could not fetch credential format (${e::class.simpleName})")
            null
        }
    }

    /**
     * GET /credential-offer - Direct link with query parameter (from conformance suite browser view)
     */
    private suspend fun handleCredentialOfferDirectPage(call: ApplicationCall) {
        try {
            val offerParam = call.request.queryParameters["credential_offer"]
            if (offerParam == null) {
                call.respondText("Missing credential_offer parameter", status = HttpStatusCode.BadRequest)
                return
            }

            // Parse the offer to extract issuer info
            val offerJson = try {
                Json.parseToJsonElement(offerParam).jsonObject
            } catch (e: Exception) {
                call.respondText("Invalid JSON in credential_offer: ${e.message}", status = HttpStatusCode.BadRequest)
                return
            }

            val credentialIssuer = offerJson["credential_issuer"]?.jsonPrimitive?.content ?: "Unknown Issuer"
            val configurationIds = offerJson["credential_configuration_ids"]?.jsonArray?.map { 
                it.jsonPrimitive.content 
            } ?: emptyList()

            // Fetch actual formats from issuer metadata
            val credentialsWithFormats = configurationIds.map { configId ->
                val format = getCredentialFormat(credentialIssuer, configId)
                if (format != null) {
                    "$configId (format: $format)"
                } else {
                    configId
                }
            }.joinToString(", ")

            val credentials = if (credentialsWithFormats.isNotEmpty()) credentialsWithFormats else "Unknown"

            val hasAuthCode = offerJson["grants"]?.jsonObject?.containsKey("authorization_code") == true
            val hasPreAuth = offerJson["grants"]?.jsonObject?.containsKey("urn:ietf:params:oauth:grant-type:pre-authorized_code") == true

            val grantType = when {
                hasAuthCode -> "Authorization Code (OAuth)"
                hasPreAuth -> "Pre-Authorized Code"
                else -> "Unknown"
            }

            // Return HTML page with start button
            call.respondText(credentialOfferPageHtml(credentialIssuer, credentials, grantType, offerParam), ContentType.Text.Html)

        } catch (e: Exception) {
            println("[VCI Adapter] Error rendering offer page (${e::class.simpleName})")
            call.respondText("Error: ${e.message}", status = HttpStatusCode.InternalServerError)
        }
    }

    /**
     * GET /offer/:offerId - Show user-friendly page with one-click start button
     */
    private suspend fun handleOfferPage(call: ApplicationCall) {
        try {
            val offerId = call.parameters["offerId"] ?: run {
                call.respondText("Missing offer ID", status = HttpStatusCode.BadRequest)
                return
            }
            
            val offer = pendingOffers[offerId] ?: run {
                call.respondText("Offer not found or already processed", status = HttpStatusCode.NotFound)
                return
            }

            // Parse the offer to extract issuer info
            val offerJson = try {
                Json.parseToJsonElement(offer).jsonObject
            } catch (e: Exception) {
                call.respondText("Invalid JSON in credential offer: ${e.message}", status = HttpStatusCode.BadRequest)
                return
            }

            val credentialIssuer = offerJson["credential_issuer"]?.jsonPrimitive?.content ?: "Unknown Issuer"
            val configurationIds = offerJson["credential_configuration_ids"]?.jsonArray?.map { 
                it.jsonPrimitive.content 
            } ?: emptyList()

            // Fetch actual formats from issuer metadata
            val credentialsWithFormats = configurationIds.map { configId ->
                val format = getCredentialFormat(credentialIssuer, configId)
                if (format != null) {
                    "$configId (format: $format)"
                } else {
                    configId
                }
            }.joinToString(", ")

            val credentials = if (credentialsWithFormats.isNotEmpty()) credentialsWithFormats else "Unknown"

            val hasAuthCode = offerJson["grants"]?.jsonObject?.containsKey("authorization_code") == true
            val hasPreAuth = offerJson["grants"]?.jsonObject?.containsKey("urn:ietf:params:oauth:grant-type:pre-authorized_code") == true

            val grantType = when {
                hasAuthCode -> "Authorization Code (OAuth)"
                hasPreAuth -> "Pre-Authorized Code"
                else -> "Unknown"
            }

            // Return HTML page with start button
            call.respondText(credentialOfferPageHtml(credentialIssuer, credentials, grantType, offer), ContentType.Text.Html)

        } catch (e: Exception) {
            println("[VCI Adapter] Error rendering offer page (${e::class.simpleName})")
            call.respondText("Error: ${e.message}", status = HttpStatusCode.InternalServerError)
        }
    }

    /**
     * POST /start-issuance - Process the credential offer and start issuance
     */
    private suspend fun handleStartIssuance(call: ApplicationCall) {
        val client = httpClient ?: run {
            call.respond(HttpStatusCode.InternalServerError, "HTTP client not initialized")
            return
        }

        try {
            val formParams = call.receiveParameters()
            val offer = formParams["offer"] ?: run {
                call.respondText("Missing offer parameter", status = HttpStatusCode.BadRequest)
                return
            }

            println("[VCI Adapter] Starting issuance from user click")
            
            // Remove from pending offers (one-time use)
            pendingOffers.values.remove(offer)

            // Parse and process the offer
            val offerJson = Json.parseToJsonElement(offer).jsonObject
            val grants = offerJson["grants"]?.jsonObject
            val hasPreAuthCode = grants?.containsKey("urn:ietf:params:oauth:grant-type:pre-authorized_code") == true
            val hasAuthCode = grants?.containsKey("authorization_code") == true

            when {
                hasPreAuthCode -> {
                    val result = claimCredentialPreAuth(client, offer)
                    call.respondText(
                        issuanceResultHtml(result.success, result.message),
                        ContentType.Text.Html
                    )
                }
                hasAuthCode -> {
                    val result = initiateAuthCodeFlow(client, offer)
                    if (result.authorizationUrl != null) {
                        result.state?.let { state ->
                            pendingAuthFlows[state] = PendingAuthFlow(
                                state = state,
                                codeVerifier = result.codeVerifier,
                                tokenEndpoint = result.tokenEndpoint ?: error("Missing tokenEndpoint"),
                                asIssuerUrl = result.asIssuerUrl,
                                credentialEndpoint = result.credentialEndpoint,
                                credentialIssuerUrl = result.credentialIssuerUrl,
                                credentialConfigurationId = result.credentialConfigurationId,
                                nonceEndpoint = result.nonceEndpoint
                            )
                        }
                        // Redirect to authorization URL
                        call.respondRedirect(result.authorizationUrl)
                    } else {
                        call.respondText(
                            issuanceResultHtml(false, "Auth flow failed: ${result.error}"),
                            ContentType.Text.Html
                        )
                    }
                }
                else -> {
                    call.respondText(
                        issuanceResultHtml(false, "No supported grant type in offer"),
                        ContentType.Text.Html
                    )
                }
            }

        } catch (e: Exception) {
            println("[VCI Adapter] Error starting issuance (${e::class.simpleName})")
            call.respondText(
                issuanceResultHtml(false, "Error: ${e.message}"),
                ContentType.Text.Html
            )
        }
    }

    /**
     * POST /credential-offer - API endpoint for programmatic access
     */
    private suspend fun handleCredentialOfferApi(call: ApplicationCall) {
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
                    // Store offer and return URL for user to visit
                    val offerId = generateOfferId()
                    pendingOffers[offerId] = offer
                    val offerUrl = "http://127.0.0.1:$adapterPort/offer/$offerId"
                    
                    println("[VCI Adapter] Stored authorization-code offer for browser continuation")
                    
                    call.respond(HttpStatusCode.OK, buildJsonObject {
                        put("status", "ready")
                        put("message", "Open the offer URL in your browser to continue")
                        put("offer_url", offerUrl)
                    }.toString())
                }
                else -> {
                    call.respond(HttpStatusCode.BadRequest, "No supported grant type in offer")
                }
            }

        } catch (e: Exception) {
            println("[VCI Adapter] Credential-offer handling failed (${e::class.simpleName})")
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

            println("[VCI Adapter] Auth callback received - code: ${code != null}, state: ${state != null}, error: ${error != null}")

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
            
            if (tokenResult.success && tokenResult.accessToken != null && pendingFlow.credentialEndpoint != null) {
                // Now fetch the credential
                val credResult = fetchCredential(
                    client = client,
                    accessToken = tokenResult.accessToken,
                    credentialEndpoint = pendingFlow.credentialEndpoint,
                    credentialConfigurationId = pendingFlow.credentialConfigurationId ?: "org.iso.18013.5.1.mDL",
                    credentialIssuerUrl = pendingFlow.credentialIssuerUrl,
                    nonceEndpoint = pendingFlow.nonceEndpoint
                )
                call.respondText(
                    authCompleteHtml(if (credResult.success) "Credential received: ${credResult.message}" else "Credential fetch failed: ${credResult.message}"),
                    ContentType.Text.Html
                )
            } else {
                call.respondText(
                    authCompleteHtml(if (tokenResult.success) "Token obtained but missing data for credential fetch" else "Token exchange failed: ${tokenResult.message}"),
                    ContentType.Text.Html
                )
            }

        } catch (e: Exception) {
            println("[VCI Adapter] Authorization callback handling failed (${e::class.simpleName})")
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
            println("[VCI Adapter] Authorization initialization status: ${response.status}")

            if (response.status.isSuccess()) {
                val result = Json.parseToJsonElement(body).jsonObject
                AuthFlowResult(
                    authorizationUrl = result["authorizationUrl"]?.jsonPrimitive?.content,
                    state = result["state"]?.jsonPrimitive?.content,
                    codeVerifier = result["codeVerifier"]?.jsonPrimitive?.content,
                    tokenEndpoint = result["tokenEndpoint"]?.jsonPrimitive?.content,
                    asIssuerUrl = result["asIssuerUrl"]?.jsonPrimitive?.content,
                    credentialEndpoint = result["credentialEndpoint"]?.jsonPrimitive?.content,
                    credentialIssuerUrl = result["credentialIssuerUrl"]?.jsonPrimitive?.content,
                    credentialConfigurationId = result["credentialConfigurationId"]?.jsonPrimitive?.content,
                    nonceEndpoint = result["nonceEndpoint"]?.jsonPrimitive?.content
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
                // Include AS issuer URL for client_assertion aud claim
                flow.asIssuerUrl?.let { put("asIssuerUrl", it) }
            }

            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }

            val body = response.bodyAsText()
            println("[VCI Adapter] Token exchange status: ${response.status}")
            if (response.status.isSuccess()) {
                val result = Json.parseToJsonElement(body).jsonObject
                TokenResult(
                    success = true,
                    message = "Token obtained",
                    accessToken = result["accessToken"]?.jsonPrimitive?.content,
                )
            } else {
                TokenResult(success = false, message = "Status ${response.status}")
            }
        } catch (e: Exception) {
            TokenResult(success = false, message = "Exception: ${e.message}")
        }
    }

    private suspend fun fetchCredential(
        client: HttpClient,
        accessToken: String,
        credentialEndpoint: String,
        credentialConfigurationId: String,
        credentialIssuerUrl: String?,
        nonceEndpoint: String?
    ): ClaimResult {
        return try {
            val url = "$walletApiUrl/wallet/$walletId/credentials/receive/fetch-credential"
            println("[VCI Adapter] POST $url")
            println("[VCI Adapter] Strict nonce endpoint configured: ${nonceEndpoint != null}")

            // Let the wallet API fetch a fresh nonce and sign the proof with the wallet key.
            val requestBody = buildJsonObject {
                put("credentialEndpoint", credentialEndpoint)
                put("accessToken", accessToken)
                put("credentialConfigurationId", credentialConfigurationId)
                nonceEndpoint?.let { put("nonceEndpoint", it) }
                credentialIssuerUrl?.let { put("credentialIssuerUrl", it) }
            }

            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }

            val body = response.bodyAsText()
            println("[VCI Adapter] Fetch response status: ${response.status}")
            
            if (response.status.isSuccess()) {
                ClaimResult(true, "Credential received")
            } else {
                ClaimResult(false, "Status ${response.status}")
            }
        } catch (e: Exception) {
            ClaimResult(false, "Exception: ${e.message}")
        }
    }

    private suspend fun signKeyProof(client: HttpClient, nonce: String, issuerUrl: String): String? {
        return try {
            val url = "$walletApiUrl/wallet/$walletId/credentials/receive/sign-proof"
            println("[VCI Adapter] POST $url")
            println("[VCI Adapter] Sign proof request prepared")

            val requestBody = buildJsonObject {
                put("issuerUrl", issuerUrl)
                put("nonce", nonce)
            }

            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }

            val body = response.bodyAsText()
            println("[VCI Adapter] Sign proof response status: ${response.status}")
            
            if (response.status.isSuccess()) {
                val result = Json.parseToJsonElement(body).jsonObject
                val proofJwt = result["proofJwt"]?.jsonPrimitive?.content
                println("[VCI Adapter] Sign proof response contained proof: ${proofJwt != null}")
                proofJwt
            } else {
                println("[VCI Adapter] Sign proof failed: ${response.status}")
                null
            }
        } catch (e: Exception) {
            println("[VCI Adapter] Sign proof failed (${e::class.simpleName})")
            null
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
                        "d": "c6TUFwkoQ8QMiz1wZ-4BqJJzvD56RRlcgn0R-XKqQjk",
                        "kid": "wallet-static-key"
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

    private fun credentialOfferPageHtml(issuer: String, credentials: String, grantType: String, offerJson: String): String = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Credential Offer</title>
            <style>
                body { font-family: Arial, sans-serif; max-width: 600px; margin: 50px auto; padding: 20px; }
                h1 { color: #333; }
                .info { background: #f5f5f5; padding: 15px; border-radius: 5px; margin: 20px 0; }
                .info-row { margin: 10px 0; }
                .label { font-weight: bold; color: #666; }
                button { 
                    background: #007bff; 
                    color: white; 
                    border: none; 
                    padding: 15px 30px; 
                    font-size: 16px; 
                    border-radius: 5px; 
                    cursor: pointer;
                    width: 100%;
                }
                button:hover { background: #0056b3; }
            </style>
        </head>
        <body>
            <h1>🎫 Credential Offer Received</h1>
            <div class="info">
                <div class="info-row"><span class="label">Issuer:</span> $issuer</div>
                <div class="info-row"><span class="label">Credentials:</span> $credentials</div>
                <div class="info-row"><span class="label">Grant Type:</span> $grantType</div>
            </div>
            <form method="POST" action="/start-issuance">
                <input type="hidden" name="offer" value="${offerJson.replace("\"", "&quot;")}" />
                <button type="submit">🚀 Start Issuance</button>
            </form>
        </body>
        </html>
    """.trimIndent()

    private fun issuanceResultHtml(success: Boolean, message: String): String = """
        <!DOCTYPE html>
        <html>
        <head><title>Issuance Result</title>
            <style>
                body { font-family: Arial, sans-serif; max-width: 600px; margin: 50px auto; padding: 20px; text-align: center; }
                .success { color: #28a745; }
                .error { color: #dc3545; }
            </style>
        </head>
        <body>
            <h1 class="${if (success) "success" else "error"}">${if (success) "✅ Success" else "❌ Error"}</h1>
            <p>$message</p>
        </body>
        </html>
    """.trimIndent()

    private fun authCompleteHtml(message: String): String = """
        <!DOCTYPE html>
        <html>
        <head><title>Authorization Complete</title>
            <style>
                body { font-family: Arial, sans-serif; max-width: 600px; margin: 50px auto; padding: 20px; text-align: center; }
            </style>
        </head>
        <body>
            <h1>✅ Authorization Complete</h1>
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
        val tokenEndpoint: String,
        val asIssuerUrl: String?,
        val credentialEndpoint: String?,
        val credentialIssuerUrl: String?,
        val credentialConfigurationId: String?,
        val nonceEndpoint: String?
    )

    private data class ClaimResult(val success: Boolean, val message: String)

    private data class AuthFlowResult(
        val authorizationUrl: String? = null,
        val state: String? = null,
        val codeVerifier: String? = null,
        val tokenEndpoint: String? = null,
        val asIssuerUrl: String? = null,
        val credentialEndpoint: String? = null,
        val credentialIssuerUrl: String? = null,
        val credentialConfigurationId: String? = null,
        val nonceEndpoint: String? = null,
        val error: String? = null
    )

    private data class TokenResult(
        val success: Boolean,
        val message: String,
        val accessToken: String? = null,
    )
}
