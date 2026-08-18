package id.walt.openid4vp.conformance.adapter

import id.walt.crypto.keys.jwk.JWKKey
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * HTTP bridge between the OpenID conformance suite and Wallet API2.
 *
 * It never constructs PAR, token, nonce, proof, or credential requests itself.
 * Those requests are made through Wallet API2 so the suite evaluates the wallet
 * implementation rather than conformance harness behaviour.
 */
class VciWalletConformanceAdapter(
    private val walletApiUrl: String = "http://127.0.0.1:7006",
    private val adapterPort: Int = 7007,
    private val publicBaseUrl: String,
    private val clientId: String = "wallet-conformance-test",
    private val txCode: String = "123456",
    private val clientAttestationIssuer: String,
    private val clientAttesterJwk: JsonObject,
    private var walletId: String? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val pendingAuthorizationFlows = ConcurrentHashMap<String, PendingAuthorizationFlow>()
    private var server: EmbeddedServer<*, *>? = null
    private var httpClient: HttpClient? = null
    private var ownsWallet = false

    fun credentialOfferEndpoint(): String = "$publicBaseUrl/credential-offer"

    fun callbackUrl(): String = "$publicBaseUrl/callback"

    suspend fun start(client: HttpClient) {
        check(server == null) { "VCI wallet conformance adapter is already running" }
        httpClient = client
        walletId = walletId ?: createTestWallet(client).also { ownsWallet = true }

        println("[VCI wallet adapter] Wallet API: $walletApiUrl")
        println("[VCI wallet adapter] Wallet ID: $walletId")
        println("[VCI wallet adapter] Local wallet attestation endpoint: http://127.0.0.1:$adapterPort/wallet-instance-attestation/jwk")
        println("[VCI wallet adapter] Public credential-offer endpoint: ${credentialOfferEndpoint()}")
        println("[VCI wallet adapter] Callback URL: ${callbackUrl()}")

        server = embeddedServer(CIO, host = "0.0.0.0", port = adapterPort) {
            routing {
                get("/health") { call.respondText("ok") }
                post("/wallet-instance-attestation/jwk") { issueWalletInstanceAttestation(call) }
                get("/credential-offer") { receiveCredentialOffer(call) }
                post("/credential-offer") { receiveCredentialOffer(call) }
                get("/callback") { receiveAuthorizationCallback(call) }
            }
        }.start(wait = false)
    }

    suspend fun close() {
        server?.stop(1_000, 2_000)
        server = null
        pendingAuthorizationFlows.clear()

        val client = httpClient
        val id = walletId
        if (client != null && id != null && ownsWallet) {
            try {
                val response = client.deleteWallet(id)
                check(response.status.value in 200..299 || response.status == HttpStatusCode.NotFound) {
                    "Wallet API2 returned ${response.status} while deleting conformance wallet $id"
                }
            } catch (error: Throwable) {
                println("[VCI wallet adapter] Failed to delete conformance wallet $id: ${error.message}")
            }
        }

        walletId = null
        ownsWallet = false
        httpClient = null
    }

    private suspend fun receiveCredentialOffer(call: ApplicationCall) {
        val client = requireNotNull(httpClient) { "Adapter HTTP client is not initialized" }
        val source = call.offerSource() ?: run {
            call.respondText("Missing credential_offer or credential_offer_uri", status = HttpStatusCode.BadRequest)
            return
        }

        try {
            val resolvedOffer = resolveOffer(client, source)
            when (resolvedOffer.grantType) {
                "pre-authorized_code", "pre_authorization_code" -> {
                    val credentialCount = receivePreAuthorizedCredential(client, source)
                    call.respondText("Credential issuance completed; stored credentials=$credentialCount")
                }
                "authorization_code" -> startAuthorizationCodeFlow(client, source, resolvedOffer, call)
                else -> error("Unsupported credential-offer grant type '${resolvedOffer.grantType}'")
            }
        } catch (error: Throwable) {
            val message = "Wallet failed to process credential offer: ${error.message}"
            println("[VCI wallet adapter] $message")
            call.respondText(message, status = HttpStatusCode.BadGateway)
        }
    }

    /**
     * Local conformance-only wallet-attestation endpoint.
     *
     * Wallet API2 posts its generated wallet-instance public JWK here. The adapter
     * signs an OAuth client attestation with the test attester key that the suite
     * configured as trusted for this plan.
     */
    private suspend fun issueWalletInstanceAttestation(call: ApplicationCall) {
        val request = runCatching { json.parseToJsonElement(call.receiveText()).jsonObject }.getOrElse {
            call.respondText("Expected JSON containing a wallet-instance JWK", status = HttpStatusCode.BadRequest)
            return
        }
        val walletInstanceJwk = request["jwk"] as? JsonObject ?: run {
            call.respondText("Missing wallet-instance JWK", status = HttpStatusCode.BadRequest)
            return
        }

        val now = System.currentTimeMillis() / 1_000
        val header = buildJsonObject {
            put("typ", "oauth-client-attestation+jwt")
            put("alg", clientAttesterJwk.string("alg") ?: "ES256")
            clientAttesterJwk.string("kid")?.let { put("kid", it) }
            clientAttesterJwk["x5c"]?.let { put("x5c", it) }
        }
        val payload = buildJsonObject {
            put("iss", clientAttestationIssuer)
            put("sub", clientId)
            put("iat", now)
            put("nbf", now)
            put("exp", now + 300)
            put("cnf", buildJsonObject { put("jwk", walletInstanceJwk) })
        }
        val signingKey = JWKKey.importJWK(clientAttesterJwk.toString()).getOrThrow()
        val attestation = signingKey.signJws(payload.toString().toByteArray(), header)

        call.respondText(
            buildJsonObject { put("walletInstanceAttestation", attestation) }.toString(),
            ContentType.Application.Json,
        )
    }

    private suspend fun startAuthorizationCodeFlow(
        client: HttpClient,
        source: OfferSource,
        resolvedOffer: ResolvedOffer,
        call: ApplicationCall,
    ) {
        val response = client.post(walletUrl("credentials/receive/authorization-url")) {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                putOfferSource(source)
                put("clientId", clientId)
                put("redirectUri", callbackUrl())
                put("usePkce", true)
            }.toString())
        }
        val body = response.bodyAsText()
        require(response.status.value in 200..299) {
            "Wallet API2 authorization-url returned ${response.status}: ${body.take(1_000)}"
        }

        val authorization = json.parseToJsonElement(body).jsonObject
        val state = authorization.string("state") ?: error("Wallet API2 authorization-url response omitted state")
        val authorizationUrl = authorization.string("authorizationUrl")
            ?: error("Wallet API2 authorization-url response omitted authorizationUrl")
        val issuer = authorization.string("credentialIssuerBaseUrl")
            ?: error("Wallet API2 authorization-url response omitted credentialIssuerBaseUrl")
        val configurationId = authorization.string("credentialConfigurationId")
            ?: resolvedOffer.credentialConfigurationId

        pendingAuthorizationFlows[state] = PendingAuthorizationFlow(
            codeVerifier = authorization.string("codeVerifier"),
            credentialIssuerBaseUrl = issuer,
            credentialEndpoint = resolvedOffer.credentialEndpoint,
            credentialConfigurationId = configurationId,
        )
        call.respondRedirect(authorizationUrl)
    }

    private suspend fun receivePreAuthorizedCredential(client: HttpClient, source: OfferSource): Int {
        val response = client.post(walletUrl("credentials/receive")) {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                putOfferSource(source)
                put("clientId", clientId)
                put("redirectUri", callbackUrl())
                put("txCode", txCode)
            }.toString())
        }
        val body = response.bodyAsText()
        require(response.status.value in 200..299) {
            "Wallet API2 pre-authorized issuance returned ${response.status}: ${body.take(1_000)}"
        }
        val credentialIds = json.parseToJsonElement(body).jsonObject["credentialIds"]?.jsonArray.orEmpty()
        require(credentialIds.isNotEmpty()) { "Wallet API2 pre-authorized issuance returned no credential ids" }
        return credentialIds.size
    }

    private suspend fun receiveAuthorizationCallback(call: ApplicationCall) {
        val error = call.request.queryParameters["error"]
        if (error != null) {
            call.respondText(
                "Authorization server returned $error: ${call.request.queryParameters["error_description"].orEmpty()}",
                status = HttpStatusCode.BadRequest,
            )
            return
        }

        val code = call.request.queryParameters["code"] ?: run {
            call.respondText("Missing authorization code", status = HttpStatusCode.BadRequest)
            return
        }
        val state = call.request.queryParameters["state"] ?: run {
            call.respondText("Missing authorization state", status = HttpStatusCode.BadRequest)
            return
        }
        val flow = pendingAuthorizationFlows.remove(state) ?: run {
            call.respondText("Unknown authorization state", status = HttpStatusCode.BadRequest)
            return
        }
        val client = requireNotNull(httpClient) { "Adapter HTTP client is not initialized" }

        try {
            val accessToken = exchangeCode(client, code, flow)
            val nonce = requestNonce(client, flow.credentialIssuerBaseUrl)
            val proof = signProof(client, flow.credentialIssuerBaseUrl, nonce)
            val credentialCount = fetchCredential(client, flow, accessToken, proof)
            call.respondText("Credential issuance completed; stored credentials=$credentialCount")
        } catch (exception: Throwable) {
            val message = "Wallet failed to complete authorization-code issuance: ${exception.message}"
            println("[VCI wallet adapter] $message")
            call.respondText(message, status = HttpStatusCode.BadGateway)
        }
    }

    private suspend fun exchangeCode(client: HttpClient, code: String, flow: PendingAuthorizationFlow): String {
        val response = client.post(walletUrl("credentials/receive/exchange-code")) {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("code", code)
                put("credentialIssuerBaseUrl", flow.credentialIssuerBaseUrl)
                flow.codeVerifier?.let { put("codeVerifier", it) }
                put("clientId", clientId)
                put("redirectUri", callbackUrl())
            }.toString())
        }
        val body = response.bodyAsText()
        require(response.status.value in 200..299) {
            "Wallet API2 exchange-code returned ${response.status}: ${body.take(1_000)}"
        }
        return json.parseToJsonElement(body).jsonObject.string("accessToken")
            ?: error("Wallet API2 exchange-code response omitted accessToken")
    }

    private suspend fun requestNonce(client: HttpClient, credentialIssuerBaseUrl: String): String? {
        val response = client.post(walletUrl("credentials/receive/request-nonce")) {
            contentType(ContentType.Application.Json)
            setBody("""{"credentialIssuer":"$credentialIssuerBaseUrl"}""")
        }
        val body = response.bodyAsText()
        require(response.status.value in 200..299) {
            "Wallet API2 request-nonce returned ${response.status}: ${body.take(1_000)}"
        }
        return json.parseToJsonElement(body).jsonObject.string("nonce")
    }

    private suspend fun signProof(client: HttpClient, credentialIssuerBaseUrl: String, nonce: String?): String {
        val response = client.post(walletUrl("credentials/receive/sign-proof")) {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("issuerUrl", credentialIssuerBaseUrl)
                nonce?.let { put("nonce", it) }
            }.toString())
        }
        val body = response.bodyAsText()
        require(response.status.value in 200..299) {
            "Wallet API2 sign-proof returned ${response.status}: ${body.take(1_000)}"
        }
        return json.parseToJsonElement(body).jsonObject.string("proofJwt")
            ?: error("Wallet API2 sign-proof response omitted proofJwt")
    }

    private suspend fun fetchCredential(
        client: HttpClient,
        flow: PendingAuthorizationFlow,
        accessToken: String,
        proof: String,
    ): Int {
        val response = client.post(walletUrl("credentials/receive/fetch-credential")) {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("credentialEndpoint", flow.credentialEndpoint)
                put("accessToken", accessToken)
                put("credentialConfigurationId", flow.credentialConfigurationId)
                put("proofJwt", proof)
                put("clientId", clientId)
                put("storeInWallet", true)
            }.toString())
        }
        val body = response.bodyAsText()
        require(response.status.value in 200..299) {
            "Wallet API2 fetch-credential returned ${response.status}: ${body.take(1_000)}"
        }
        val credentials = json.parseToJsonElement(body).jsonObject["rawCredentials"]?.jsonArray.orEmpty()
        require(credentials.isNotEmpty()) { "Wallet API2 fetch-credential returned no credentials" }
        return credentials.size
    }

    private suspend fun resolveOffer(client: HttpClient, source: OfferSource): ResolvedOffer {
        val response = client.post(walletUrl("credentials/receive/resolve-offer")) {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { putOfferSource(source) }.toString())
        }
        val body = response.bodyAsText()
        require(response.status.value in 200..299) {
            "Wallet API2 resolve-offer returned ${response.status}: ${body.take(1_000)}"
        }
        val resolved = json.parseToJsonElement(body).jsonObject
        return ResolvedOffer(
            grantType = resolved.string("grantType"),
            credentialEndpoint = resolved.string("credentialEndpoint")
                ?: error("Wallet API2 resolve-offer response omitted credentialEndpoint"),
            credentialConfigurationId = resolved["credentialConfigurationIds"]?.jsonArray
                ?.firstOrNull()?.jsonPrimitive?.content
                ?: error("Wallet API2 resolve-offer response omitted credentialConfigurationIds"),
        )
    }

    private suspend fun createTestWallet(client: HttpClient): String {
        val response = client.post("$walletApiUrl/wallet") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {"staticKey":{"type":"jwk","jwk":{"kty":"EC","crv":"P-256","x":"d5KVpCdze-46QteHfgAswRurlSYUylJ1JntvcbaZ__Y","y":"uqvaPeOm7SGsdXr34frqkJGAz8tHmR0EmpsSbfqgwDA","d":"c6TUFwkoQ8QMiz1wZ-4BqJJzvD56RRlcgn0R-XKqQjk","kid":"wallet-static-key"}}}
                """.trimIndent()
            )
        }
        val body = response.bodyAsText()
        require(response.status.value in 200..299) {
            "Wallet API2 wallet creation returned ${response.status}: ${body.take(1_000)}"
        }
        return json.parseToJsonElement(body).jsonObject.string("walletId")
            ?: error("Wallet API2 wallet creation response omitted walletId")
    }

    private suspend fun ApplicationCall.offerSource(): OfferSource? {
        request.queryParameters["credential_offer"]?.let { offer ->
            return OfferSource.InlineJson(offer)
        }
        request.queryParameters["credential_offer_uri"]?.let { offerUri ->
            return OfferSource.Url("openid-credential-offer://?credential_offer_uri=${offerUri.encodeURLParameter()}")
        }
        if (request.httpMethod.value.equals("POST", ignoreCase = true)) {
            val body = try {
                receiveText().trim()
            } catch (_: Throwable) {
                ""
            }
            if (body.isNotEmpty()) {
                if (body.startsWith("{")) {
                    val payload = json.parseToJsonElement(body).jsonObject
                    payload["credential_offer"]?.let { offer ->
                        val nestedOffer = if (offer is JsonObject) offer.toString() else offer.jsonPrimitive.content
                        return OfferSource.InlineJson(nestedOffer)
                    }
                    val nestedOfferUri = payload.string("credential_offer_uri")
                    if (nestedOfferUri != null) {
                        return OfferSource.Url("openid-credential-offer://?credential_offer_uri=${nestedOfferUri.encodeURLParameter()}")
                    }
                    return OfferSource.InlineJson(body)
                }

                val formParameters = body.split('&').associate { pair ->
                    pair.split('=', limit = 2).let { parts ->
                        URLDecoder.decode(parts.first(), StandardCharsets.UTF_8) to
                            URLDecoder.decode(parts.getOrElse(1) { "" }, StandardCharsets.UTF_8)
                    }
                }
                formParameters["credential_offer"]?.let { return OfferSource.InlineJson(it) }
                formParameters["credential_offer_uri"]?.let {
                    return OfferSource.Url("openid-credential-offer://?credential_offer_uri=${it.encodeURLParameter()}")
                }
                return OfferSource.Url(body)
            }
        }
        return null
    }

    private fun JsonObjectBuilder.putOfferSource(source: OfferSource) {
        when (source) {
            is OfferSource.InlineJson -> put("offerJson", json.parseToJsonElement(source.value).jsonObject)
            is OfferSource.Url -> put("offerUrl", source.value)
        }
    }

    private fun walletUrl(path: String): String = "$walletApiUrl/wallet/${requireNotNull(walletId)}/$path"

    private suspend fun HttpClient.deleteWallet(id: String) = delete("$walletApiUrl/wallet/$id")

    private fun JsonObject.string(name: String): String? =
        get(name)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private sealed interface OfferSource {
        data class InlineJson(val value: String) : OfferSource
        data class Url(val value: String) : OfferSource
    }

    private data class ResolvedOffer(
        val grantType: String?,
        val credentialEndpoint: String,
        val credentialConfigurationId: String,
    )

    private data class PendingAuthorizationFlow(
        val codeVerifier: String?,
        val credentialIssuerBaseUrl: String,
        val credentialEndpoint: String,
        val credentialConfigurationId: String,
    )

}
