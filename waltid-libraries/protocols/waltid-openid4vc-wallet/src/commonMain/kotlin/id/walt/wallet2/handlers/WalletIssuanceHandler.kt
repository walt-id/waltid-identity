package id.walt.wallet2.handlers

import id.walt.credentials.CredentialParser
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.Key
import id.walt.openid4vci.CryptographicBindingMethod
import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.requests.credential.DefaultCredentialRequest
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletSessionEvent
import id.walt.wallet2.handlers.WalletIssuanceHandler.exchangeCode
import id.walt.wallet2.handlers.WalletIssuanceHandler.generateAuthorizationUrl
import id.walt.wallet2.handlers.WalletIssuanceHandler.pollDeferredFlow
import id.walt.wallet2.handlers.WalletIssuanceHandler.receiveCredentialFlow
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import id.waltid.openid4vci.wallet.metadata.IssuerMetadataResolver
import id.waltid.openid4vci.wallet.metadata.OfferedCredentialResolver
import id.waltid.openid4vci.wallet.oauth.ClientConfiguration
import id.waltid.openid4vci.wallet.attestation.ClientAttestationAssembler
import id.waltid.openid4vci.wallet.offer.CredentialOfferParser
import id.waltid.openid4vci.wallet.offer.CredentialOfferResolver
import id.waltid.openid4vci.wallet.proof.JwtProofBuilder
import id.waltid.openid4vci.wallet.token.TokenRequestBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

// ---------------------------------------------------------------------------
// Shared offer-source contract
// ---------------------------------------------------------------------------

/**
 * Common contract for request types that carry a credential offer, either as a URL
 * (openid-credential-offer://...) or as inline JSON. Exactly one must be non-null.
 *
 * Eliminates duplicated [offerUrl]/[offerJson] mutual-exclusivity checks and
 * [getEffectiveOfferString] across [ReceiveCredentialRequest], [ResolveOfferRequest],
 * and [GenerateAuthorizationUrlRequest].
 */
interface CredentialOfferSource {
    val offerUrl: Url?
    val offerJson: JsonObject?

    fun getEffectiveOfferString(): String =
        offerUrl?.toString() ?: offerJson?.toString() ?: error("No offer source available")
}

/** Validates the mutual exclusivity of [offerUrl] and [offerJson]. Call from `init {}` blocks. */
fun CredentialOfferSource.checkOfferSource() {
    check(offerUrl != null || offerJson != null) { "Either offerUrl or offerJson must be provided" }
    check(offerUrl == null || offerJson == null) { "Only one of offerUrl or offerJson may be provided, not both" }
}

// ---------------------------------------------------------------------------
// Request / response types
// ---------------------------------------------------------------------------

/**
 * Input for the full pre-authorized-code issuance flow.
 *
 * Exactly one of [offerUrl] or [offerJson] must be non-null.
 */
@Serializable
data class ReceiveCredentialRequest(
    /**
     * A credential offer URL (openid-credential-offer://...).
     * Provide this when the offer arrives as a URL (QR code, deep link).
     */
    override val offerUrl: Url? = null,

    /**
     * A credential offer as a parsed JSON object.
     * Provide this when the offer arrives as inline JSON.
     */
    override val offerJson: JsonObject? = null,

    /**
     * Inline key to use for proof-of-possession.
     *
     * When provided, this serialized key is used directly and takes precedence over [keyId]
     * and the wallet's stores. This supports store-less / referenced-elsewhere callers that
     * supply the key material inline (e.g. the Enterprise resolving a `keyReference` Target to a
     * concrete key before delegating).
     */
    val key: DirectSerializedKey? = null,

    /**
     * ID of the key to use for proof-of-possession (resolved from the wallet's key stores).
     * Ignored when [key] is provided. Defaults to the wallet's default key.
     */
    val keyId: String? = null,

    /** DID to use as the credential subject / holder binding. Defaults to wallet's default DID. */
    val did: String? = null,

    /** Transaction code (PIN) required by some pre-authorized code flows. */
    val txCode: String? = null,

    /** OAuth 2.0 client_id presented to the authorization server. */
    val clientId: String = "wallet-client",

    /** redirect_uri registered with the authorization server (auth-code flows only). */
    val redirectUri: Url = Url("openid://"),

    /**
     * Additional HTTP headers to attach to the token request, e.g. the attestation-based client
     * authentication headers `OAuth-Client-Attestation` / `OAuth-Client-Attestation-PoP`
     * (OpenID4VCI 1.0 §Token Endpoint; [@!I-D.ietf-oauth-attestation-based-client-auth]).
     *
     * Building the attestation + PoP JWTs is the caller's responsibility (the wallet provider /
     * Enterprise holds the stored client attestation); this handler only forwards the resulting
     * headers to the token request.
     */
    val tokenRequestHeaders: Map<String, String> = emptyMap()
) : CredentialOfferSource {
    init { checkOfferSource() }
}

/** Result of a completed issuance flow. */
@Serializable
data class ReceiveCredentialResult(
    /** All credentials that were successfully issued and stored. */
    val credentialIds: List<String>,
    /**
     * Transaction IDs for credentials deferred by the issuer.
     * Each entry maps a credential configuration ID to the transaction ID
     * that should be used with [WalletIssuanceHandler.pollDeferredFlow] to
     * retrieve the credential once it becomes available.
     */
    val deferredTransactionIds: Map<String, String> = emptyMap()
)

// Isolated step types

@Serializable
data class ResolveOfferRequest(
    override val offerUrl: Url? = null,
    override val offerJson: JsonObject? = null
) : CredentialOfferSource {
    init { checkOfferSource() }
}

@Serializable
data class ResolveOfferResult(
    val credentialIssuer: String,
    val credentialConfigurationIds: List<String>,
    val grantType: String?,
    val txCodeRequired: Boolean,
    val credentialEndpoint: Url,
    val offeredCredentials: List<String>,
    /**
     * Token endpoint URL for the pre-authorized code grant.
     * Null for auth-code grant offers or when AS metadata has no token endpoint.
     * Pass directly to [RequestTokenRequest.tokenEndpoint].
     */
    val tokenEndpoint: Url?,
    /**
     * Pre-authorized code from the offer grant.
     * Null for auth-code grant offers.
     * Pass directly to [RequestTokenRequest.preAuthorizedCode].
     */
    val preAuthorizedCode: String?,
)

@Serializable
data class RequestTokenRequest(
    val tokenEndpoint: Url,
    val preAuthorizedCode: String,
    val txCode: String? = null,
    val clientId: String = "wallet-client",
    val redirectUri: Url = Url("openid://")
)

@Serializable
data class RequestTokenResult(
    val accessToken: String,
    val cNonce: String? = null,
    val expiresIn: Long? = null
)

@Serializable
data class SignProofRequest(
    val issuerUrl: Url,
    val nonce: String,
    /** Inline key to sign the proof with; takes precedence over [keyId]. */
    val key: DirectSerializedKey? = null,
    val keyId: String? = null,
    val did: String? = null
)

@Serializable
data class SignProofResult(
    val proofJwt: String
)

@Serializable
data class FetchCredentialRequest(
    val credentialEndpoint: Url,
    val accessToken: String,
    val credentialConfigurationId: String,
    val proofJwt: String? = null,
    val clientId: String = "wallet-client",
    /**
     * When true, the server-side route handler automatically stores the fetched
     * credential(s) in the wallet after retrieval. Defaults to false (stateless).
     * This field is ignored by [WalletIssuanceHandler.fetchCredential] itself.
     */
    val storeInWallet: Boolean = false,
)

@Serializable
data class FetchCredentialResult(
    val rawCredentials: List<String>
)

// Deferred issuance types

@Serializable
data class PollDeferredRequest(
    /** The deferred credential endpoint URL from the issuer's metadata. */
    val deferredCredentialEndpoint: Url,
    /** The transaction_id received when the credential was deferred. */
    val transactionId: String,
    /** Access token from the original token response. */
    val accessToken: String
)

// Auth-code grant isolated steps

@Serializable
data class GenerateAuthorizationUrlRequest(
    override val offerUrl: Url? = null,
    override val offerJson: JsonObject? = null,
    val clientId: String = "wallet-client",
    val redirectUri: Url = Url("openid://"),
    val usePkce: Boolean = true
) : CredentialOfferSource {
    init { checkOfferSource() }
}

@Serializable
data class GenerateAuthorizationUrlResult(
    val authorizationUrl: Url,
    val state: String,
    val codeVerifier: String? = null,
    val credentialConfigurationId: String
)

@Serializable
data class ExchangeCodeRequest(
    val tokenEndpoint: Url,
    val code: String,
    val codeVerifier: String? = null,
    val clientId: String = "wallet-client",
    val redirectUri: Url = Url("openid://")
)

// ---------------------------------------------------------------------------
// Handler
// ---------------------------------------------------------------------------

/**
 * OpenID4VCI 1.0 credential issuance logic.
 *
 * Orchestrates the wallet-side steps using waltid-openid4vci-wallet primitives.
 * Returns a [Flow] of [StoredCredential] for the full flow so callers can
 * react to each credential as it arrives (useful for streaming UIs).
 */
object WalletIssuanceHandler {

    /** Lenient JSON for decoding library responses that may contain unknown fields. */
    private val lenientJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** HTTP redirect status codes that the wallet should follow for credential/nonce endpoints. */
    private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)

    /**
     * Creates the [HttpClient] used for the issuance flow.
     *
     * The client is created and configured by [WebDataFetcher] (default Native engine - Java on
     * JVM, with TLS 1.3 - plus centrally-managed request/logging configuration, including lenient
     * JSON content negotiation) rather than constructed directly with the platform default engine.
     * The collaborators ([IssuerMetadataResolver], [CredentialOfferResolver], [TokenRequestBuilder])
     * and the direct credential/nonce/deferred POST calls continue to consume the raw [HttpClient].
     */
    private fun defaultHttpClient(): HttpClient =
        WebDataFetcher(WebDataFetcherId.WALLET2_ISSUANCE_HANDLER).httpClient

    /**
     * Resolves a credential offer from any [CredentialOfferSource], handling both inline JSON
     * and URL (openid-credential-offer://...) forms. Extracted to eliminate three identical
     * if/else blocks across [receiveCredentialFlow], [resolveOffer], and [generateAuthorizationUrl].
     */
    private suspend fun resolveOffer(source: CredentialOfferSource, httpClient: HttpClient) =
        if (source.offerJson != null) {
            val inlineOffer = lenientJson.decodeFromString<id.walt.openid4vci.offers.CredentialOffer>(source.getEffectiveOfferString())
            CredentialOfferResolver(httpClient).resolveCredentialOffer(credentialOffer = inlineOffer, credentialOfferUri = null)
        } else {
            val req = CredentialOfferParser.parseCredentialOfferUrl(source.getEffectiveOfferString())
            CredentialOfferResolver(httpClient).resolveCredentialOffer(credentialOffer = req.credentialOffer, credentialOfferUri = req.credentialOfferUri)
        }

    /**
     * Builds a proof JWT, choosing DID-based binding when [did] is non-null, or JWK-inclusion
     * otherwise. Extracted to eliminate three identical `if (did != null)` proof-building blocks.
     */
    private suspend fun JwtProofBuilder.buildProof(key: Key, issuer: String, nonce: String, did: String?) =
        if (did != null) buildJwtProof(key, issuer, nonce, keyId = did)
        else buildJwtProof(key, issuer, nonce, includeJwk = true)

    /**
     * Full pre-authorized-code issuance flow, emitting each stored credential
     * as a [Flow] element as soon as it is received and stored.
     *
     * [onEvent] is called at each lifecycle step for progress reporting.
     */
    fun receiveCredentialFlow(
        wallet: Wallet,
        request: ReceiveCredentialRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = defaultHttpClient(),
        /**
         * Called whenever the issuer defers a credential.
         * [credentialConfigurationId] identifies which credential was deferred;
         * [transactionId] should be stored and passed to [pollDeferredFlow] later.
         */
        onDeferredTransactionId: suspend (credentialConfigurationId: String, transactionId: String) -> Unit = { _, _ -> },
    ): Flow<StoredCredential> = channelFlow {
        val key = wallet.resolveKey(request.key, request.keyId)
            ?: error("No key available: wallet has no keyStores, no staticKey, no inline key, and no keyId was specified")
        val did = request.did ?: wallet.defaultDid()

        val clientConfig = clientConfig(request.clientId, request.redirectUri)
        val metadataResolver = IssuerMetadataResolver(httpClient)
        val tokenBuilder = TokenRequestBuilder(clientConfig, httpClient)
        val proofBuilder = JwtProofBuilder()

        // 1. Parse and resolve offer
        // When offerJson is provided it's already the raw JSON object - decode directly instead of
        // parsing as a URL (which would fail since JSON is not a valid openid-credential-offer:// URL).
        log.trace { "Parsing offer string: ${request.getEffectiveOfferString().take(120)}..." }
        val offer = resolveOffer(request, httpClient)
        log.trace { "Resolved offer: issuer=${offer.credentialIssuer}, configIds=${offer.credentialConfigurationIds}" }
        onEvent(WalletSessionEvent.issuance_offer_resolved)

        // 2. Fetch issuer metadata
        log.trace { "Fetching issuer metadata from ${offer.credentialIssuer}" }
        val issuerMetadata = metadataResolver.resolveCredentialIssuerMetadata(offer.credentialIssuer)
        val offeredCredentials = OfferedCredentialResolver.resolveOfferedCredentials(offer, issuerMetadata)
        log.debug { "Offer contains ${offeredCredentials.size} credential(s)" }

        // 3. Pre-authorized code grant only (auth-code handled by separate flow)
        val preAuthGrant = offer.grants?.preAuthorizedCode
            ?: error("Only pre-authorized code grant is currently supported. Offer grants: ${offer.grants}")
        log.trace { "Using pre-authorized code grant" }

        // 4. Request token
        val asMetadata = metadataResolver.resolveAuthorizationServerMetadataWithFallback(issuerMetadata)
        val tokenEndpoint = asMetadata.tokenEndpoint
            ?: error("Authorization server metadata contains no token_endpoint")
        log.trace { "Requesting token from $tokenEndpoint" }

        val attestationHeaders = if (attestationAssembler != null &&
            asMetadata.tokenEndpointAuthMethodsSupported?.contains("attest_jwt_client_auth") == true
        ) {
            log.debug { "Issuer requires attestation-based client auth, building attestation headers" }
            val asIssuer = asMetadata.issuer ?: tokenEndpoint
            val headers = attestationAssembler.buildAttestationHeaders(key, request.clientId, asIssuer)
            onEvent(WalletSessionEvent.issuance_attestation_obtained)
            headers
        } else null

        val effectiveTxCode = request.txCode
            ?: preAuthGrant.txCode?.value?.content

        val tokenResponse = tokenBuilder.exchangePreAuthorizedCode(
            tokenEndpoint = tokenEndpoint,
            preAuthorizedCode = preAuthGrant.preAuthorizedCode,
            txCode = effectiveTxCode,
            additionalHeaders = request.tokenRequestHeaders,
            attestationHeaders = attestationHeaders,
        )
        log.trace { "Token obtained, c_nonce=${tokenResponse.c_nonce}" }
        onEvent(WalletSessionEvent.issuance_token_obtained)

        // 5. Fetch c_nonce
        val cNonce = issuerMetadata.nonceEndpoint
            ?.let { fetchCNonce(httpClient, it) }
            ?: tokenResponse.c_nonce
        log.trace { "Using nonce: $cNonce (from ${if (issuerMetadata.nonceEndpoint != null) "nonce endpoint" else "token response"})" }

        val credentialEndpoint = issuerMetadata.credentialEndpoint
            ?: error("Issuer metadata contains no credential_endpoint")
        log.trace { "Credential endpoint: $credentialEndpoint" }

        // 6. Issue each offered credential
        for (offeredCredential in offeredCredentials) {
            log.trace { "Issuing credential configId=${offeredCredential.credentialConfigurationId}, format=${offeredCredential.configuration.format}" }
            val proofs = if (offeredCredential.configuration.proofTypesSupported?.isNotEmpty() == true) {
                val nonce = cNonce
                    ?: error("Issuer requires proof but did not provide a c_nonce")
                log.trace { "Building proof JWT, did=$did, nonce=$nonce" }
                val preferJwkBinding = shouldPreferJwkBinding(offeredCredential.configuration.cryptographicBindingMethodsSupported)
                if (did != null && !preferJwkBinding) {
                    proofBuilder.buildJwtProof(key, offer.credentialIssuer, nonce, keyId = did)
                } else {
                    proofBuilder.buildJwtProof(key, offer.credentialIssuer, nonce, includeJwk = true)
                }
            } else null
            log.trace { "Proof JWT present: ${proofs?.jwt?.firstOrNull()?.take(40)}..." }
            onEvent(WalletSessionEvent.issuance_proof_signed)

            // Build the credential request JSON manually to ensure proofs serializes correctly.
            // Using DefaultCredentialRequest + setBody risks proofs being serialized as null
            // due to Proofs.additional: Map<String,JsonElement> interacting with encodeDefaults=false.
            val credentialRequestJson = buildJsonObject {
                put("credential_configuration_id", offeredCredential.credentialConfigurationId)
                proofs?.jwt?.firstOrNull()?.let { jwt ->
                    putJsonObject("proofs") {
                        put("jwt", buildJsonArray { add(JsonPrimitive(jwt)) })
                    }
                }
            }
            log.trace { "Posting to credential endpoint: ${credentialRequestJson.toString().take(200)}" }

            val credentialResponse = postFollowingRedirects(httpClient, credentialEndpoint) {
                header(HttpHeaders.Authorization, "Bearer ${tokenResponse.access_token}")
                contentType(ContentType.Application.Json)
                setBody(credentialRequestJson.toString())
            }.let { response ->
                if (!response.status.isSuccess()) {
                    error("Credential endpoint returned ${response.status}: ${response.bodyAsText()}")
                }
                response.body<CredentialResponse>()
            }
            onEvent(WalletSessionEvent.issuance_credential_received)

            val rawCredentials = credentialResponse.credentials

            if (rawCredentials == null) {
                // Deferred issuance: the issuer accepted the request but will issue the credential later.
                // The transactionId can be used to poll the deferred credential endpoint.
                val transactionId = credentialResponse.transactionId
                log.info { "Deferred issuance: credential for '${offeredCredential.credentialConfigurationId}' will be available later. transactionId=$transactionId" }
                if (transactionId != null) {
                    onDeferredTransactionId(offeredCredential.credentialConfigurationId, transactionId)
                }
                onEvent(WalletSessionEvent.issuance_deferred)
                continue
            }

            for (issuedCredential in rawCredentials) {
                val entry = wallet.parseAndStore(issuedCredential, label = offeredCredential.configuration.credentialMetadata?.display?.firstOrNull()?.name)
                onEvent(WalletSessionEvent.issuance_credential_stored)
                send(entry)
            }
        }

        onEvent(WalletSessionEvent.issuance_completed)
    }

    /**
     * Convenience wrapper that collects [receiveCredentialFlow] into a result.
     * Deferred credential transaction IDs are included in [ReceiveCredentialResult.deferredTransactionIds].
     */
    suspend fun receiveCredential(
        wallet: Wallet,
        request: ReceiveCredentialRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = defaultHttpClient()
    ): ReceiveCredentialResult {
        val ids = mutableListOf<String>()
        val deferredIds = mutableMapOf<String, String>()
        receiveCredentialFlow(
            wallet, request, attestationAssembler, onEvent, httpClient,
            onDeferredTransactionId = { configId, txId -> deferredIds[configId] = txId }
        ).collect { ids += it.id }
        return ReceiveCredentialResult(credentialIds = ids, deferredTransactionIds = deferredIds)
    }

    // ---------------------------------------------------------------------------
    // Isolated step handlers
    // ---------------------------------------------------------------------------

    suspend fun resolveOffer(request: ResolveOfferRequest): ResolveOfferResult {
        val httpClient = defaultHttpClient()
        val offer = resolveOffer(request, httpClient)
        val issuerMetadata = IssuerMetadataResolver(httpClient).resolveCredentialIssuerMetadata(offer.credentialIssuer)
        val offeredCredentials = OfferedCredentialResolver.resolveOfferedCredentials(offer, issuerMetadata)
        val preAuthGrant = offer.grants?.preAuthorizedCode
        val asMetadata = if (preAuthGrant != null) {
            runCatching { IssuerMetadataResolver(httpClient).resolveAuthorizationServerMetadataWithFallback(issuerMetadata) }.getOrNull()
        } else null
        return ResolveOfferResult(
            credentialIssuer = offer.credentialIssuer,
            credentialConfigurationIds = offer.credentialConfigurationIds,
            grantType = preAuthGrant?.let { "pre-authorized_code" }
                ?: offer.grants?.authorizationCode?.let { "authorization_code" },
            txCodeRequired = preAuthGrant?.txCode != null,
            credentialEndpoint = Url(issuerMetadata.credentialEndpoint
                ?: error("Issuer metadata contains no credential_endpoint")),
            offeredCredentials = offeredCredentials.map { it.credentialConfigurationId },
            tokenEndpoint = asMetadata?.tokenEndpoint?.let { Url(it) },
            preAuthorizedCode = preAuthGrant?.preAuthorizedCode,
        )
    }

    suspend fun requestToken(request: RequestTokenRequest): RequestTokenResult {
        val httpClient = defaultHttpClient()
        val clientConfig = clientConfig(request.clientId, request.redirectUri)
        val tokenResponse = TokenRequestBuilder(clientConfig, httpClient).exchangePreAuthorizedCode(
            tokenEndpoint = request.tokenEndpoint.toString(),
            preAuthorizedCode = request.preAuthorizedCode,
            txCode = request.txCode
        )
        return RequestTokenResult(
            accessToken = tokenResponse.access_token,
            cNonce = tokenResponse.c_nonce,
            expiresIn = tokenResponse.expires_in
        )
    }

    suspend fun signProof(wallet: Wallet, request: SignProofRequest): SignProofResult {
        val key = wallet.resolveKey(request.key, request.keyId)
            ?: error("No key available for signing proof")
        val proofBuilder = JwtProofBuilder()
        val proofs = proofBuilder.buildProof(key, request.issuerUrl.toString(), request.nonce, request.did)
        return SignProofResult(proofJwt = proofs.jwt?.firstOrNull() ?: error("Proof signing produced no JWT"))
    }

    suspend fun fetchCredential(request: FetchCredentialRequest): FetchCredentialResult {
        val httpClient = defaultHttpClient()
        // Build JSON manually to avoid Proofs serialization issue
        val credentialRequestJson = buildJsonObject {
            put("credential_configuration_id", request.credentialConfigurationId)
            request.proofJwt?.let { jwt ->
                putJsonObject("proofs") {
                    put("jwt", buildJsonArray { add(JsonPrimitive(jwt)) })
                }
            }
        }
        val response = postFollowingRedirects(httpClient, request.credentialEndpoint.toString()) {
            header(HttpHeaders.Authorization, "Bearer ${request.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(credentialRequestJson.toString())
        }
        if (!response.status.isSuccess()) {
            error("Credential endpoint returned ${response.status}: ${response.bodyAsText()}")
        }
        val credentialResponse = response.body<CredentialResponse>()
        val rawCredentials = credentialResponse.credentials
            ?.map { it.credential.let { c -> if (c is JsonPrimitive) c.content else c.toString() } }
            ?: error("Credential response contained no credentials")
        return FetchCredentialResult(rawCredentials = rawCredentials)
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    // resolveKey is now wallet.resolveKey(inlineKey, keyId) - see Wallet.resolveKey

    /**
     * Builds a [ClientConfiguration] from the common clientId/redirectUri pair.
     * Extracted to eliminate four identical constructions across issuance functions.
     */
    private fun clientConfig(clientId: String, redirectUri: Url) =
        ClientConfiguration(clientId = clientId, redirectUris = listOf(redirectUri.toString()))

    /**
     * Parses a raw issued credential JSON element, creates a [StoredCredential], stores it in
     * the wallet, and returns it. Extracted to eliminate duplication between [receiveCredentialFlow]
     * and [pollDeferredFlow].
     */
    private suspend fun Wallet.parseAndStore(
        issuedCredential: id.walt.openid4vci.responses.credential.IssuedCredential,
        label: String? = null,
    ): StoredCredential {
        val rawString = issuedCredential.credential.let {
            if (it is JsonPrimitive) it.content else it.toString()
        }
        val (_, parsed) = CredentialParser.detectAndParse(rawString)
        return StoredCredential(
            id = Uuid.random().toString(),
            credential = parsed,
            label = label,
            addedAt = Clock.System.now()
        ).also { addCredential(it) }
    }

    private suspend fun postFollowingRedirects(
        httpClient: HttpClient,
        url: String,
        block: HttpRequestBuilder.() -> Unit
    ): HttpResponse {
        var response = httpClient.post(url, block)
        if (response.status.value in REDIRECT_STATUS_CODES) {
            val location = response.headers[HttpHeaders.Location]
            if (location != null) {
                log.debug { "Following redirect to: $location" }
                check(isSameOrigin(url, location)) {
                    "Cross-origin redirect from $url to $location is not supported for wallet POST requests"
                }
                response = httpClient.post(location, block)
            }
        }
        return response
    }

    private suspend fun fetchCNonce(httpClient: HttpClient, nonceEndpoint: String): String? =
        runCatching {
            val response = postFollowingRedirects(httpClient, nonceEndpoint) {
                contentType(ContentType.Application.Json)
            }
            if (response.status.isSuccess()) {
                response.body<JsonObject>()["c_nonce"]
                    ?.let { (it as? JsonPrimitive)?.content }
            } else null
        }.getOrNull()

    private fun shouldPreferJwkBinding(
        methods: Set<CryptographicBindingMethod>?
    ): Boolean {
        if (methods.isNullOrEmpty()) return false
        val supportsJwk = methods.any { it is CryptographicBindingMethod.Jwk }
        val supportsDid = methods.any { it is CryptographicBindingMethod.Did }
        return supportsJwk && !supportsDid
    }

    private fun isSameOrigin(source: String, target: String): Boolean {
        val sourceUrl = Url(source)
        val targetUrl = Url(target)
        return sourceUrl.protocol == targetUrl.protocol &&
            sourceUrl.host == targetUrl.host &&
            sourceUrl.port == targetUrl.port
    }

    // ---------------------------------------------------------------------------
    // Authorization-code grant isolated steps
    // ---------------------------------------------------------------------------

    /**
     * Step 1 of auth-code grant: resolve the offer and generate the authorization URL.
     * The caller (mobile app / browser) must then redirect to [GenerateAuthorizationUrlResult.authorizationUrl]
     * and capture the `code` from the redirect callback before calling [exchangeCode].
     */
    suspend fun generateAuthorizationUrl(request: GenerateAuthorizationUrlRequest): GenerateAuthorizationUrlResult {
        val httpClient = defaultHttpClient()
        val offer = resolveOffer(request, httpClient)
        val issuerMetadata = IssuerMetadataResolver(httpClient).resolveCredentialIssuerMetadata(offer.credentialIssuer)
        val asMetadata = IssuerMetadataResolver(httpClient).resolveAuthorizationServerMetadataWithFallback(issuerMetadata)

        val authorizationEndpoint = asMetadata.authorizationEndpoint
            ?: error("Authorization server has no authorization_endpoint")

        val clientConfig = clientConfig(request.clientId, request.redirectUri)
        val authBuilder = id.waltid.openid4vci.wallet.authorization.AuthorizationRequestBuilder(clientConfig)
        val credentialConfigurationId = offer.credentialConfigurationIds.first()

        val authRequest = authBuilder.buildAuthorizationRequest(
            authorizationEndpoint = authorizationEndpoint,
            credentialConfigurationId = credentialConfigurationId,
            issuerState = offer.grants?.authorizationCode?.issuerState,
            usePKCE = request.usePkce,
            metadata = asMetadata
        )
        return GenerateAuthorizationUrlResult(
            authorizationUrl = Url(authRequest.url),
            state = authRequest.state,
            codeVerifier = authRequest.pkceData?.codeVerifier,
            credentialConfigurationId = credentialConfigurationId
        )
    }

    /**
     * Step 2 of auth-code grant: exchange the authorization code for a token.
     * Wraps [TokenRequestBuilder.exchangeAuthorizationCode].
     */
    suspend fun exchangeCode(request: ExchangeCodeRequest): RequestTokenResult {
        val httpClient = defaultHttpClient()
        val clientConfig = clientConfig(request.clientId, request.redirectUri)
        val tokenResponse = TokenRequestBuilder(clientConfig, httpClient).exchangeAuthorizationCode(
            tokenEndpoint = request.tokenEndpoint.toString(),
            code = request.code,
            codeVerifier = request.codeVerifier
        )
        return RequestTokenResult(
            accessToken = tokenResponse.access_token,
            cNonce = tokenResponse.c_nonce,
            expiresIn = tokenResponse.expires_in
        )
    }

    // ---------------------------------------------------------------------------
    // Deferred issuance polling
    // ---------------------------------------------------------------------------

    /**
     * Polls the deferred credential endpoint for a previously deferred credential.
     *
     * Per OpenID4VCI §9, the wallet sends a POST to the deferred credential endpoint
     * with the transaction_id. The issuer responds with the credential when ready,
     * or with an `issuance_pending` error if not yet available.
     *
     * On success the credential is stored in the wallet's credential store.
     */
        fun pollDeferredFlow(
        wallet: Wallet,
        request: PollDeferredRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = defaultHttpClient()
    ): Flow<StoredCredential> = channelFlow {
        val response = httpClient.post(request.deferredCredentialEndpoint.toString()) {
            header(HttpHeaders.Authorization, "Bearer ${request.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("transaction_id", JsonPrimitive(request.transactionId))
            })
        }

        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            // issuance_pending is a spec-defined recoverable error - the wallet should retry later.
            // Parse the error JSON rather than substring-matching the raw body, which would be fragile.
            val errorCode = runCatching {
                Json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
            }.getOrNull()
            if (errorCode == "issuance_pending") {
                log.info { "Deferred credential not yet ready for transactionId=${request.transactionId}" }
                return@channelFlow
            }
            error("Deferred credential endpoint returned ${response.status}: $body")
        }

        val credentialResponse = response.body<CredentialResponse>()
        val rawCredentials = credentialResponse.credentials
            ?: error("Deferred credential response contained no credentials")

        for (issuedCredential in rawCredentials) {
            val entry = wallet.parseAndStore(issuedCredential)
            onEvent(WalletSessionEvent.issuance_credential_stored)
            send(entry)
        }
        onEvent(WalletSessionEvent.issuance_completed)
    }

    // ---------------------------------------------------------------------------
    // Auth-code grant full flow
    // ---------------------------------------------------------------------------

    /**
     * Full authorization-code grant issuance flow.
     *
     * This flow requires user interaction (browser redirect) between steps 2 and 3,
     * so it cannot be a single blocking call. Instead it is split into:
     *   1. [generateAuthorizationUrl] — get the URL to redirect the user to
     *   2. (caller handles browser redirect and captures the `code` callback)
     *   3. [receiveCredentialAuthCode] — exchange code + issue credentials
     *
     * This function handles step 3 only, continuing from an authorization code.
     */
        fun receiveCredentialAuthCodeFlow(
        wallet: Wallet,
        tokenEndpoint: Url,
        code: String,
        codeVerifier: String?,
        credentialIssuer: String,
        credentialEndpoint: Url,
        credentialConfigurationId: String,
        nonceEndpoint: String? = null,
        clientId: String = "wallet-client",
        redirectUri: Url = Url("openid://"),
        /** Inline key for proof-of-possession; takes precedence over the wallet's stores. */
        inlineKey: DirectSerializedKey? = null,
        /** Inline DID for holder binding; defaults to the wallet's default DID. */
        inlineDid: String? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = defaultHttpClient()
    ): Flow<StoredCredential> = channelFlow {
        val key = wallet.resolveKey(inlineKey)
            ?: error("No key available for proof-of-possession")
        val did = inlineDid ?: wallet.defaultDid()

        // Exchange code for token
        val tokenResult = exchangeCode(ExchangeCodeRequest(
            tokenEndpoint = tokenEndpoint,
            code = code,
            codeVerifier = codeVerifier,
            clientId = clientId,
            redirectUri = redirectUri
        ))
        onEvent(WalletSessionEvent.issuance_token_obtained)

        // Sign proof — nonce from nonce endpoint or token response (never fall back to access_token)
        val cNonce = nonceEndpoint?.let { fetchCNonce(httpClient, it) }
            ?: tokenResult.cNonce
            ?: error("Issuer did not provide a c_nonce (neither via nonce endpoint nor token response)")
        val proofBuilder = JwtProofBuilder()
        val proofs = proofBuilder.buildProof(key, credentialIssuer, cNonce, did)
        onEvent(WalletSessionEvent.issuance_proof_signed)

        // Fetch credential
        val fetchResult = fetchCredential(FetchCredentialRequest(
            credentialEndpoint = credentialEndpoint,
            accessToken = tokenResult.accessToken,
            credentialConfigurationId = credentialConfigurationId,
            proofJwt = proofs.jwt?.firstOrNull(),
            clientId = clientId
        ))
        onEvent(WalletSessionEvent.issuance_credential_received)

        for (rawString in fetchResult.rawCredentials) {
            val (_, parsed) = CredentialParser.detectAndParse(rawString)
            val entry = StoredCredential(
                id = Uuid.random().toString(),
                credential = parsed,
                addedAt = Clock.System.now()
            )
            wallet.addCredential(entry)
            onEvent(WalletSessionEvent.issuance_credential_stored)
            send(entry)
        }
        onEvent(WalletSessionEvent.issuance_completed)
    }
}
