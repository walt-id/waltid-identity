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

    /**
     * Authorization codes captured at [getRedirectUri], keyed by the `state` that produced them.
     *
     * The authorization leg is driven by simply GETting the authorization URL with a redirect-following
     * client: the authorization server's redirect lands back on this adapter's own callback, which
     * records the code here. That is the real redirect path a browser would take, and it avoids
     * needing a second HTTP client configured without redirect following.
     */
    private val authorizationCodesByState = mutableMapOf<String, String>()

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
        authorizationCodesByState.clear()
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
            println("[VCI Adapter] Could not fetch format: ${e.message}")
            null
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
            val hasPreAuthCode = grants?.containsKey(PRE_AUTHORIZED_CODE_GRANT) == true
            val hasAuthCode = grants?.containsKey("authorization_code") == true

            println("[VCI Adapter] Grants - preAuth: $hasPreAuthCode, authCode: $hasAuthCode")

            when {
                hasPreAuthCode -> {
                    val result = claimCredentialPreAuth(client, offer)
                    call.respond(HttpStatusCode.OK, "Pre-auth claim: ${result.message}")
                }

                hasAuthCode -> {
                    val result = claimCredentialAuthCode(client, offer)
                    call.respond(HttpStatusCode.OK, "Auth-code claim: ${result.message}")
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

    /**
     * Redirect target of the authorization request.
     *
     * Only records the code against its `state`; the exchange itself is Wallet2's job via
     * `POST /credentials/receive/authorized`. Nothing renders a page here because no human is
     * involved - the caller that GET the authorization URL is the one following this redirect.
     */
    private suspend fun handleAuthCallback(call: ApplicationCall) {
        val code = call.request.queryParameters["code"]
        val state = call.request.queryParameters["state"]
        val error = call.request.queryParameters["error"]

        println("[VCI Adapter] Auth callback - code: ${code?.take(12)}…, state: $state, error: $error")

        when {
            error != null -> {
                val description = call.request.queryParameters["error_description"]
                call.respondText("Authorization error: $error - $description", status = HttpStatusCode.BadRequest)
            }

            code == null || state == null ->
                call.respondText("Missing code or state", status = HttpStatusCode.BadRequest)

            else -> {
                authorizationCodesByState[state] = code
                call.respondText("Authorization code received")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Wallet API Calls
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Completes the authorization-code grant by delegating every protocol step to Wallet2.
     *
     * The adapter drives only the browser leg the suite expects a user to perform: it asks the wallet
     * for an authorization URL, GETs it so the authorization server's redirect lands on
     * [handleAuthCallback], then hands the captured code back. Discovery, PKCE, the token exchange,
     * proof of possession and credential storage all happen inside the wallet - mirroring how the
     * pre-authorized grant delegates to `POST /credentials/receive`.
     *
     * This replaces per-field orchestration in the adapter that had drifted out of sync with the
     * wallet's DTOs (it read `tokenEndpoint`/`cNonce` fields that no longer exist and omitted
     * required ones), and which therefore never completed a single flow.
     */
    private suspend fun claimCredentialAuthCode(client: HttpClient, offer: String): ClaimResult {
        val offerSource = buildOfferSource(offer)

        // 1. Endpoints the wallet needs back on the final call; authorization-url does not return them.
        val resolveResponse = client.post("$walletApiUrl/wallet/$walletId/credentials/receive/resolve-offer") {
            contentType(ContentType.Application.Json)
            setBody(offerSource.toString())
        }
        if (!resolveResponse.status.isSuccess()) {
            return ClaimResult(false, "resolve-offer ${resolveResponse.status}: ${resolveResponse.bodyAsText()}")
        }
        val resolved = Json.parseToJsonElement(resolveResponse.bodyAsText()).jsonObject
        val credentialIssuer = resolved["credentialIssuer"]?.jsonPrimitive?.content
            ?: return ClaimResult(false, "resolve-offer returned no credentialIssuer")
        val credentialEndpoint = resolved["credentialEndpoint"]?.jsonPrimitive?.content
            ?: return ClaimResult(false, "resolve-offer returned no credentialEndpoint")
        val nonceEndpoint = resolved["nonceEndpoint"]?.jsonPrimitive?.contentOrNull

        // 2. Authorization URL, PKCE verifier and state.
        val authUrlResponse = client.post("$walletApiUrl/wallet/$walletId/credentials/receive/authorization-url") {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    offerSource.entries.forEach { (key, value) -> put(key, value) }
                    put("clientId", CLIENT_ID)
                    put("redirectUri", getRedirectUri())
                    put("usePkce", true)
                }.toString()
            )
        }
        if (!authUrlResponse.status.isSuccess()) {
            return ClaimResult(false, "authorization-url ${authUrlResponse.status}: ${authUrlResponse.bodyAsText()}")
        }
        val authorization = Json.parseToJsonElement(authUrlResponse.bodyAsText()).jsonObject
        val authorizationUrl = authorization["authorizationUrl"]?.jsonPrimitive?.content
            ?: return ClaimResult(false, "authorization-url returned no authorizationUrl")
        val state = authorization["state"]?.jsonPrimitive?.content
            ?: return ClaimResult(false, "authorization-url returned no state")
        val codeVerifier = authorization["codeVerifier"]?.jsonPrimitive?.contentOrNull
        val credentialConfigurationId = authorization["credentialConfigurationId"]?.jsonPrimitive?.content
            ?: return ClaimResult(false, "authorization-url returned no credentialConfigurationId")

        // 3. The browser leg, followed by hand. Ktor's HttpRedirect refuses to follow an HTTPS ->
        // HTTP downgrade, and the authorization server is HTTPS while this adapter's redirect URI is
        // plain HTTP on loopback, so an automatic follow silently stops at the 303.
        println("[VCI Adapter] Following authorization request")
        val code = followAuthorization(client, authorizationUrl).getOrElse { return ClaimResult(false, it.message ?: "authorization failed") }

        // 4. Everything after the redirect is the wallet's job.
        val url = "$walletApiUrl/wallet/$walletId/credentials/receive/authorized"
        println("[VCI Adapter] POST $url")
        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("code", code)
                    codeVerifier?.let { put("codeVerifier", it) }
                    put("credentialIssuer", credentialIssuer)
                    put("credentialEndpoint", credentialEndpoint)
                    put("credentialConfigurationId", credentialConfigurationId)
                    nonceEndpoint?.let { put("nonceEndpoint", it) }
                    put("clientId", CLIENT_ID)
                    put("redirectUri", getRedirectUri())
                    testDid?.let { put("did", it) }
                    testKeyId?.let { put("keyId", it) }
                    // The suite drives sender_constrain=dpop and requires a DPoP proof at both the
                    // token and credential endpoints. Opt in explicitly: the wallet defaults to off,
                    // because an authorization server advertising DPoP does not imply the Credential
                    // Issuer's credential endpoint accepts DPoP-bound tokens.
                    put("useDpop", true)
                }.toString()
            )
        }
        val body = response.bodyAsText()
        return if (response.status.isSuccess()) {
            ClaimResult(true, body)
        } else {
            ClaimResult(false, "Status ${response.status}: $body")
        }
    }

    /**
     * Walks the authorization server's redirect chain until it lands on [getRedirectUri], returning
     * the authorization code from that final query.
     *
     * Done by hand rather than by letting the HTTP client follow: the authorization server is HTTPS
     * and this adapter's redirect URI is plain HTTP on loopback, and Ktor's `HttpRedirect` refuses
     * HTTPS -> HTTP downgrades, so an automatic follow stops at the redirect without reporting why.
     *
     * A chain longer than [MAX_AUTHORIZATION_REDIRECTS] means the server wants interaction this
     * adapter cannot supply, which is reported rather than looped on.
     */
    private suspend fun followAuthorization(client: HttpClient, authorizationUrl: String): Result<String> {
        var current = authorizationUrl

        repeat(MAX_AUTHORIZATION_REDIRECTS) {
            val response = client.get(current)
            val location = response.headers[HttpHeaders.Location]
                ?: return Result.failure(
                    IllegalStateException(
                        "authorization endpoint answered ${response.status} without a Location: " +
                                response.bodyAsText().take(300),
                    )
                )

            val target = URLBuilder(current).apply { takeFrom(location) }.build()
            if ("${target.protocol.name}://${target.host}:${target.port}${target.encodedPath}" == getRedirectUri()) {
                target.parameters["error"]?.let { error ->
                    return Result.failure(
                        IllegalStateException(
                            "authorization failed: $error ${target.parameters["error_description"].orEmpty()}",
                        )
                    )
                }
                return target.parameters["code"]
                    ?.let { Result.success(it) }
                    ?: Result.failure(IllegalStateException("redirect to $location carried no code"))
            }

            println("[VCI Adapter] Authorization redirect -> $location")
            current = target.toString()
        }

        return Result.failure(
            IllegalStateException(
                "authorization did not reach ${getRedirectUri()} within $MAX_AUTHORIZATION_REDIRECTS redirects",
            )
        )
    }

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

        // Generate the signing key and a DID. "backend" is the sealed-class discriminator of
        // TypedKeyGenerationRequest - without it the wallet answers 400 and, since these responses
        // were previously unchecked, the wallet silently ended up with no key at all.
        val keyResponse = client.post("$walletApiUrl/wallet/$newWalletId/keys/generate") {
            contentType(ContentType.Application.Json)
            setBody("""{"backend": "jwk", "keyType": "secp256r1"}""")
        }
        check(keyResponse.status.isSuccess()) {
            "Failed to generate wallet key: ${keyResponse.status} ${keyResponse.bodyAsText()}"
        }

        val didResponse = client.post("$walletApiUrl/wallet/$newWalletId/dids/create") {
            contentType(ContentType.Application.Json)
            setBody("""{"method": "key"}""")
        }
        check(didResponse.status.isSuccess()) {
            "Failed to create wallet DID: ${didResponse.status} ${didResponse.bodyAsText()}"
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

    /**
     * Just the offer source, as `resolve-offer` and `authorization-url` accept it.
     *
     * Both reject unknown fields, so the key/DID/tx-code extras that [buildOfferRequest] adds for
     * `credentials/receive` must not be sent here.
     */
    private fun buildOfferSource(offer: String): JsonObject = buildJsonObject {
        val offerJsonObject = parseOfferJson(offer)
        if (offerJsonObject != null) put("offerJson", offerJsonObject) else put("offerUrl", offer)
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
            offerJsonObject?.let(::extractTransactionCode)?.let { put("txCode", it) }
        }
    }

    /**
     * Transaction code (PIN) for the pre-authorized code grant.
     *
     * OpenID4VCI carries only the code's *shape* in `tx_code`, never its value - a real user reads
     * the value from the issuer out of band. The conformance suite embeds it in the human-readable
     * description ("Input the one-time code: <123456> for testing purposes") precisely so that an
     * automated wallet can recover it, so parsing the description is the intended automation and
     * survives the suite replacing its currently hardcoded value.
     */
    private fun extractTransactionCode(offer: JsonObject): String? =
        offer["grants"]?.jsonObject
            ?.get(PRE_AUTHORIZED_CODE_GRANT)?.jsonObject
            ?.get("tx_code")?.jsonObject
            ?.get("description")?.jsonPrimitive?.contentOrNull
            ?.let { TX_CODE_IN_DESCRIPTION.find(it)?.groupValues?.get(1) }

    /** Outcome of delegating a grant to the wallet; [message] carries the wallet's own response body. */
    private data class ClaimResult(val success: Boolean, val message: String)

    private companion object {
        /** Client identifier the VCI wallet plans register with the conformance suite. */
        const val CLIENT_ID = "wallet-conformance-test"

        /** Redirects tolerated on the authorization leg before concluding interaction is required. */
        const val MAX_AUTHORIZATION_REDIRECTS = 5

        /** OpenID4VCI 1.0 pre-authorized code grant key in a credential offer's `grants` object. */
        const val PRE_AUTHORIZED_CODE_GRANT = "urn:ietf:params:oauth:grant-type:pre-authorized_code"

        /** Matches the `<code>` the conformance suite embeds in its `tx_code` description. */
        val TX_CODE_IN_DESCRIPTION = Regex("<([^>]+)>")
    }
}
