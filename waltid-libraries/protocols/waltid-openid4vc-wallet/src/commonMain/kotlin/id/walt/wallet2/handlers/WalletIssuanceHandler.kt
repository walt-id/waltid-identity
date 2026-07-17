package id.walt.wallet2.handlers

import id.walt.credentials.CredentialParser
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.Key
import id.walt.openid4vci.CryptographicBindingMethod
import id.walt.openid4vci.clientauth.ClientAuthenticationMethods
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.openid4vci.offers.CredentialOffer
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
import id.waltid.openid4vci.wallet.attestation.ClientAttestationAssembler
import id.waltid.openid4vci.wallet.attestation.ClientAttestationHeaders
import id.waltid.openid4vci.wallet.metadata.IssuerMetadataResolver
import id.waltid.openid4vci.wallet.metadata.OfferedCredentialResolver
import id.waltid.openid4vci.wallet.oauth.ClientConfiguration
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}
private const val DEFAULT_CLIENT_ID = "eudiw-abca"

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
    val clientId: String = DEFAULT_CLIENT_ID,

    /** redirect_uri registered with the authorization server (auth-code flows only). */
    val redirectUri: Url = Url("openid://"),

    /**
     * Additional HTTP headers to attach to the token request, e.g. the attestation-based client
     * authentication headers `OAuth-Client-Attestation` / `OAuth-Client-Attestation-PoP`
     * (OpenID4VCI 1.0 §Token Endpoint; [@!I-D.ietf-oauth-attestation-based-client-auth]).
     *
     * This is a manual escape hatch. Prefer passing a ClientAttestationAssembler to the handler so
     * the library can create attestation headers from authorization server metadata and the wallet key.
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
    val preAuthorizedCode: String? = null,
    val txCodeRequired: Boolean,
    val credentialEndpoint: Url,
    val offeredCredentials: List<String>,
    val tokenEndpoint: Url? = null,
)

/**
 * Complete credential-offer resolution retained between review and issuance.
 *
 * @property summary App-facing metadata derived from the resolution.
 * @property offer Exact parsed credential offer, including its grants.
 * @property issuerMetadata Credential issuer metadata used to validate the offered configurations.
 * @property authorizationServerMetadata Authorization server metadata used for the token request.
 * @property offeredCredentials Offered configurations resolved against [issuerMetadata].
 */
private class ResolvedIssuanceOffer(
    val summary: ResolveOfferResult,
    val offer: CredentialOffer,
    val issuerMetadata: CredentialIssuerMetadata,
    val authorizationServerMetadata: AuthorizationServerMetadata,
    val offeredCredentials: List<OfferedCredentialResolver.ResolvedCredentialOffer>,
)

@Serializable
data class RequestTokenRequest(
    val tokenEndpoint: Url,
    val preAuthorizedCode: String,
    val credentialIssuer: String? = null,
    val txCode: String? = null,
    val clientId: String = DEFAULT_CLIENT_ID,
    val redirectUri: Url = Url("openid://"),
    val tokenRequestHeaders: Map<String, String> = emptyMap(),
    val anonymousPreAuthorizedCode: Boolean = false,
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
    val clientId: String = DEFAULT_CLIENT_ID,
    /**
     * When true, [WalletIssuanceHandler.fetchCredential] stores the fetched
     * credential(s) when called with a wallet. Defaults to false (stateless).
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
    val clientId: String = DEFAULT_CLIENT_ID,
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
    val credentialConfigurationId: String,
    val credentialIssuerBaseUrl: String,
)

@Serializable
data class ExchangeCodeRequest(
    val code: String,
    /** Used to resolve AS metadata, including token endpoint, issuer, and token auth methods. */
    val credentialIssuerBaseUrl: String,
    val codeVerifier: String? = null,
    val clientId: String = DEFAULT_CLIENT_ID,
    val redirectUri: Url = Url("openid://"),
    val tokenRequestHeaders: Map<String, String> = emptyMap(),
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

    private const val MAX_PREVIEWED_OFFERS = 16
    private val lenientJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val previewedOffers = LinkedHashMap<OfferPreviewCacheKey, ResolvedIssuanceOffer>()
    private val previewedOffersMutex = Mutex()

    /** HTTP redirect status codes that the wallet should follow for credential/nonce endpoints. */
    private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)

    /**
     * Shared [HttpClient] for all issuance step functions. Lazily initialized on first use.
     *
     * The client is created and configured by [WebDataFetcher] (default Native engine - Java on
     * JVM, with TLS 1.3 - plus centrally-managed request/logging configuration, including lenient
     * JSON content negotiation) rather than constructed directly with the platform default engine.
     *
     * Using a shared lazy instance avoids creating a new connection pool on every isolated-step
     * call (resolveOffer, requestToken, etc.), which would be wasteful.
     *
     * The full receive flow and auth-code flow accept an httpClient parameter so tests and the
     * Enterprise can inject a custom client; they fall back to this shared instance by default.
     */
    private val httpClient: HttpClient by lazy {
        WebDataFetcher(WebDataFetcherId.WALLET2_ISSUANCE_HANDLER).httpClient
    }

    @Deprecated("Use the shared httpClient property", replaceWith = ReplaceWith("httpClient"))
    private fun defaultHttpClient(): HttpClient = httpClient

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
     * When a matching [previewOffer] resolution is retained for [wallet], this flow reuses that
     * exact offer and metadata. Failed attempts retain the preview for retry; successful completion
     * removes it. Calls without a retained preview resolve [request] normally.
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
    ): Flow<StoredCredential> = receiveCredentialFlow(
        wallet = wallet,
        request = request,
        attestationAssembler = attestationAssembler,
        onEvent = onEvent,
        httpClient = httpClient,
        onDeferredTransactionId = onDeferredTransactionId,
        onCredentialStored = {},
    )

    fun receiveCredentialFlow(
        wallet: Wallet,
        request: ReceiveCredentialRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = defaultHttpClient(),
        onDeferredTransactionId: suspend (credentialConfigurationId: String, transactionId: String) -> Unit = { _, _ -> },
        onCredentialStored: suspend (StoredCredential) -> Unit,
    ): Flow<StoredCredential> = channelFlow {
        val key = wallet.resolveKey(request.key, request.keyId)
            ?: error("No key available: wallet has no keyStores, no staticKey, no inline key, and no keyId was specified")
        val did = request.did ?: wallet.defaultDid()

        val clientConfig = ClientConfiguration(
            clientId = request.clientId,
            redirectUris = listOf(request.redirectUri.toString())
        )
        val tokenBuilder = TokenRequestBuilder(clientConfig, httpClient)
        val proofBuilder = JwtProofBuilder()

        // 1. Resolve the offer source, or reuse the exact resolution shown during preview.
        log.trace { "Parsing offer string: ${request.getEffectiveOfferString().take(120)}..." }
        val cacheKey = offerPreviewCacheKey(wallet, request)
        val previewedOffer = previewedOffersMutex.withLock { previewedOffers[cacheKey] }
        val resolvedOffer = previewedOffer ?: resolveIssuanceOffer(
            request.toResolveOfferRequest(),
            httpClient,
        )

        // 2. Reuse issuer metadata and offered configurations from that resolution.
        val offer = resolvedOffer.offer
        val issuerMetadata = resolvedOffer.issuerMetadata
        val offeredCredentials = resolvedOffer.offeredCredentials
        val asMetadata = resolvedOffer.authorizationServerMetadata
        log.trace { "Resolved offer: issuer=${offer.credentialIssuer}, configIds=${offer.credentialConfigurationIds}" }
        onEvent(WalletSessionEvent.issuance_offer_resolved)

        log.debug { "Offer contains ${offeredCredentials.size} credential(s)" }

        // 3. Pre-authorized code grant only (auth-code handled by separate flow)
        val preAuthGrant = offer.grants?.preAuthorizedCode
            ?: error("Only pre-authorized code grant is currently supported. Offer grants: ${offer.grants}")
        log.trace { "Using pre-authorized code grant" }

        // 4. Request token
        val tokenEndpoint = asMetadata.tokenEndpoint
            ?: error("Authorization server metadata contains no token_endpoint")
        log.trace { "Requesting token from $tokenEndpoint" }

        val attestationHeaders = buildTokenEndpointAttestationHeaders(
            asMetadata = asMetadata,
            clientId = request.clientId,
            attestationAssembler = attestationAssembler,
            resolveInstanceKey = { key },
            onAttestationObtained = { onEvent(WalletSessionEvent.issuance_attestation_obtained) },
        )

        val anonymousPreAuthorizedCode =
            asMetadata.preAuthorizedGrantAnonymousAccessSupported == true &&
                    request.tokenRequestHeaders.isEmpty() &&
                    attestationHeaders == null

        val tokenResponse = tokenBuilder.exchangePreAuthorizedCode(
            tokenEndpoint = tokenEndpoint,
            preAuthorizedCode = preAuthGrant.preAuthorizedCode,
            txCode = request.txCode,
            additionalHeaders = request.tokenRequestHeaders,
            attestationHeaders = attestationHeaders,
            anonymous = anonymousPreAuthorizedCode,
        )
        log.trace { "Token obtained, c_nonce=${tokenResponse.c_nonce}" }
        onEvent(WalletSessionEvent.issuance_token_obtained)

        // 5. Fetch c_nonce
        val cNonce = issuerMetadata.nonceEndpoint
            ?.let { fetchCNonce(httpClient, it) }
            ?: tokenResponse.c_nonce
        log.trace { "Using nonce: $cNonce (from ${if (issuerMetadata.nonceEndpoint != null) "nonce endpoint" else "token response"})" }

        val credentialEndpoint = issuerMetadata.credentialEndpoint
        log.trace { "Credential endpoint: $credentialEndpoint" }

        // 6. Issue each offered credential
        for (offeredCredential in offeredCredentials) {
            log.trace { "Issuing credential configId=${offeredCredential.credentialConfigurationId}, format=${offeredCredential.configuration.format}" }
            val proofs = if (offeredCredential.configuration.proofTypesSupported?.isNotEmpty() == true) {
                val nonce = cNonce
                    ?: error("Issuer requires proof but did not provide a c_nonce")
                log.trace { "Building proof JWT, did=$did, nonce=$nonce" }
                val preferJwkBinding =
                    shouldPreferJwkBinding(offeredCredential.configuration.cryptographicBindingMethodsSupported)
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
                onCredentialStored(entry)
                onEvent(WalletSessionEvent.issuance_credential_stored)
                send(entry)
            }
        }

        onEvent(WalletSessionEvent.issuance_completed)
        if (previewedOffer != null) {
            previewedOffersMutex.withLock {
                if (previewedOffers[cacheKey] === previewedOffer) {
                    previewedOffers.remove(cacheKey)
                }
            }
        }
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
    ): ReceiveCredentialResult = receiveCredential(
        wallet = wallet,
        request = request,
        attestationAssembler = attestationAssembler,
        onEvent = onEvent,
        httpClient = httpClient,
        onCredentialStored = {},
    )

    suspend fun receiveCredential(
        wallet: Wallet,
        request: ReceiveCredentialRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = defaultHttpClient(),
        onCredentialStored: suspend (StoredCredential) -> Unit,
    ): ReceiveCredentialResult {
        val ids = mutableListOf<String>()
        val deferredIds = mutableMapOf<String, String>()
        receiveCredentialFlow(
            wallet,
            request,
            attestationAssembler,
            onEvent,
            httpClient,
            onDeferredTransactionId = { configId, txId -> deferredIds[configId] = txId },
            onCredentialStored = onCredentialStored,
        ).collect { ids += it.id }
        return ReceiveCredentialResult(credentialIds = ids, deferredTransactionIds = deferredIds)
    }

    /**
     * Resolves an offer for review and retains the complete resolution for [receiveCredential].
     *
     * The bounded preview cache is scoped by wallet ID and offer source. While retained, a matching
     * receive reuses the exact parsed offer, issuer metadata, authorization server metadata, and
     * offered configurations instead of fetching them again.
     *
     * @param wallet Wallet that will receive the reviewed offer.
     * @param request Credential offer URL or inline offer JSON to resolve.
     * @param httpClient HTTP client used for offer and metadata resolution.
     * @return Review metadata derived from the retained resolution.
     */
    suspend fun previewOffer(
        wallet: Wallet,
        request: ResolveOfferRequest,
        httpClient: HttpClient = defaultHttpClient(),
    ): ResolveOfferResult {
        val resolvedOffer = resolveIssuanceOffer(request, httpClient)
        previewedOffersMutex.withLock {
            if (previewedOffers.size >= MAX_PREVIEWED_OFFERS) {
                previewedOffers.remove(previewedOffers.keys.first())
            }
            previewedOffers[offerPreviewCacheKey(wallet, request)] = resolvedOffer
        }
        return resolvedOffer.summary
    }

    private suspend fun resolveIssuanceOffer(
        request: ResolveOfferRequest,
        httpClient: HttpClient = defaultHttpClient(),
    ): ResolvedIssuanceOffer {
        val offer = resolveOffer(request, httpClient)
        val metadataResolver = IssuerMetadataResolver(httpClient)
        val issuerMetadata = metadataResolver.resolveCredentialIssuerMetadata(offer.credentialIssuer)
        val asMetadata = metadataResolver.resolveAuthorizationServerMetadataWithFallback(issuerMetadata)
        val offeredCredentials = OfferedCredentialResolver.resolveOfferedCredentials(offer, issuerMetadata)
        return ResolvedIssuanceOffer(
            summary = ResolveOfferResult(
                credentialIssuer = offer.credentialIssuer,
                credentialConfigurationIds = offer.credentialConfigurationIds,
                grantType = offer.grants?.preAuthorizedCode?.let { "pre-authorized_code" }
                    ?: offer.grants?.authorizationCode?.let { "authorization_code" },
                preAuthorizedCode = offer.grants?.preAuthorizedCode?.preAuthorizedCode,
                txCodeRequired = offer.grants?.preAuthorizedCode?.txCode != null,
                tokenEndpoint = asMetadata.tokenEndpoint?.let { Url(it) },
                credentialEndpoint = Url(issuerMetadata.credentialEndpoint),
                offeredCredentials = offeredCredentials.map { it.credentialConfigurationId },
            ),
            offer = offer,
            issuerMetadata = issuerMetadata,
            authorizationServerMetadata = asMetadata,
            offeredCredentials = offeredCredentials,
        )
    }

    private data class OfferPreviewCacheKey(
        val walletId: String,
        val offerSource: String,
    )

    private fun offerPreviewCacheKey(wallet: Wallet, request: ResolveOfferRequest): OfferPreviewCacheKey =
        OfferPreviewCacheKey(walletId = wallet.id, offerSource = request.getEffectiveOfferString())

    private fun offerPreviewCacheKey(wallet: Wallet, request: ReceiveCredentialRequest): OfferPreviewCacheKey =
        OfferPreviewCacheKey(walletId = wallet.id, offerSource = request.getEffectiveOfferString())

    private fun ReceiveCredentialRequest.toResolveOfferRequest(): ResolveOfferRequest =
        ResolveOfferRequest(offerUrl = offerUrl, offerJson = offerJson)

    // ---------------------------------------------------------------------------
    // Isolated step handlers
    // ---------------------------------------------------------------------------

    /**
     * Resolves offer metadata without retaining it for a later issuance call.
     *
     * Use [previewOffer] when user review and subsequent issuance must use the same resolution.
     *
     * @param request Credential offer URL or inline offer JSON to resolve.
     * @return Resolved offer, issuer, endpoint, credential, and transaction-code metadata.
     */
    suspend fun resolveOffer(request: ResolveOfferRequest): ResolveOfferResult =
        resolveIssuanceOffer(request).summary

    suspend fun requestToken(request: RequestTokenRequest): RequestTokenResult =
        requestToken(
            request = request,
            attestationHeaders = null,
            anonymousPreAuthorizedCode = request.anonymousPreAuthorizedCode,
        )

    suspend fun requestToken(
        wallet: Wallet,
        request: RequestTokenRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        httpClient: HttpClient = defaultHttpClient(),
        onAttestationObtained: suspend () -> Unit = {},
    ): RequestTokenResult {
        val credentialIssuer = request.credentialIssuer?.takeIf { it.isNotBlank() }
        val asMetadata = credentialIssuer?.let {
            val metadataResolver = IssuerMetadataResolver(httpClient)
            val issuerMetadata = metadataResolver.resolveCredentialIssuerMetadata(it)
            metadataResolver.resolveAuthorizationServerMetadataWithFallback(issuerMetadata)
        }
        val attestationHeaders = asMetadata?.let {
            buildTokenEndpointAttestationHeaders(
                asMetadata = it,
                clientId = request.clientId,
                attestationAssembler = attestationAssembler,
                resolveInstanceKey = { wallet.resolveKey() },
                onAttestationObtained = onAttestationObtained,
            )
        }
        val anonymousPreAuthorizedCode =
            request.anonymousPreAuthorizedCode ||
                (asMetadata?.preAuthorizedGrantAnonymousAccessSupported == true &&
                    request.tokenRequestHeaders.isEmpty() &&
                    attestationHeaders == null)

        return requestToken(
            request = request,
            attestationHeaders = attestationHeaders,
            anonymousPreAuthorizedCode = anonymousPreAuthorizedCode,
            httpClient = httpClient,
        )
    }

    private suspend fun requestToken(
        request: RequestTokenRequest,
        attestationHeaders: ClientAttestationHeaders?,
        anonymousPreAuthorizedCode: Boolean,
        httpClient: HttpClient = defaultHttpClient(),
    ): RequestTokenResult {
        val clientConfig = ClientConfiguration(
            clientId = request.clientId,
            redirectUris = listOf(request.redirectUri.toString())
        )
        val tokenResponse = TokenRequestBuilder(clientConfig, httpClient).exchangePreAuthorizedCode(
            tokenEndpoint = request.tokenEndpoint.toString(),
            preAuthorizedCode = request.preAuthorizedCode,
            txCode = request.txCode,
            additionalHeaders = request.tokenRequestHeaders,
            attestationHeaders = attestationHeaders,
            anonymous = anonymousPreAuthorizedCode,
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

    /**
     * Fetches credentials and applies [FetchCredentialRequest.storeInWallet] consistently for
     * every server adapter. Use the stateless overload when no wallet is available.
     */
    suspend fun fetchCredential(wallet: Wallet, request: FetchCredentialRequest): FetchCredentialResult =
        fetchCredential(request).also { result ->
            if (request.storeInWallet) {
                result.rawCredentials.forEach { wallet.parseAndStore(it) }
            }
        }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

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
    ): StoredCredential = parseAndStore(
        rawCredential = issuedCredential.credential.let {
            if (it is JsonPrimitive) it.content else it.toString()
        },
        label = label,
    )

    private suspend fun Wallet.parseAndStore(
        rawCredential: String,
        label: String? = null,
    ): StoredCredential {
        val (_, parsed) = CredentialParser.detectAndParse(rawCredential)
        return StoredCredential(
            id = Uuid.random().toString(),
            credential = parsed,
            label = label,
            addedAt = Clock.System.now()
        ).also { addCredential(it) }
    }

    private suspend fun buildTokenEndpointAttestationHeaders(
        asMetadata: AuthorizationServerMetadata,
        clientId: String,
        attestationAssembler: ClientAttestationAssembler?,
        resolveInstanceKey: suspend () -> Key?,
        onAttestationObtained: suspend () -> Unit = {},
    ): ClientAttestationHeaders? {
        val assembler = attestationAssembler ?: return null
        if (!asMetadata.supportsAttestationBasedClientAuthentication()) return null

        log.debug { "Issuer supports attestation-based client auth, building attestation headers" }
        val key = resolveInstanceKey()
            ?: error("No key available for client attestation")
        val headers = assembler.buildAttestationHeaders(
            instanceKey = key,
            clientId = clientId,
            audience = asMetadata.issuer,
        )
        onAttestationObtained()
        return headers
    }

    private fun AuthorizationServerMetadata.supportsAttestationBasedClientAuthentication(): Boolean =
        tokenEndpointAuthMethodsSupported?.contains(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH) == true

    private suspend fun resolveAuthorizationCodeAuthorizationServerMetadata(
        credentialIssuerBaseUrl: String,
        httpClient: HttpClient,
    ): AuthorizationServerMetadata {
        val metadataResolver = IssuerMetadataResolver(httpClient)
        val issuerMetadata = metadataResolver.resolveCredentialIssuerMetadata(credentialIssuerBaseUrl)
        return metadataResolver.resolveAuthorizationServerMetadataWithFallback(issuerMetadata)
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
        val asMetadata =
            IssuerMetadataResolver(httpClient).resolveAuthorizationServerMetadataWithFallback(issuerMetadata)

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
            credentialConfigurationId = credentialConfigurationId,
            credentialIssuerBaseUrl = offer.credentialIssuer,
        )
    }

    /**
     * Step 2 of auth-code grant: exchange the authorization code for a token.
     * Wraps [TokenRequestBuilder.exchangeAuthorizationCode].
     */
    suspend fun exchangeCode(request: ExchangeCodeRequest): RequestTokenResult {
        val httpClient = defaultHttpClient()
        val credentialIssuerBaseUrl = request.credentialIssuerBaseUrl.takeIf { it.isNotBlank() }
            ?: error("credentialIssuerBaseUrl must be provided")
        val asMetadata = resolveAuthorizationCodeAuthorizationServerMetadata(credentialIssuerBaseUrl, httpClient)
        return exchangeCode(
            request = request,
            tokenEndpoint = asMetadata.tokenEndpoint
                ?: error("Authorization server metadata contains no token_endpoint"),
            attestationHeaders = null,
            httpClient = httpClient,
        )
    }

    suspend fun exchangeCode(
        wallet: Wallet,
        request: ExchangeCodeRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        httpClient: HttpClient = defaultHttpClient(),
        onAttestationObtained: suspend () -> Unit = {},
    ): RequestTokenResult {
        val credentialIssuerBaseUrl = request.credentialIssuerBaseUrl.takeIf { it.isNotBlank() }
            ?: error("credentialIssuerBaseUrl must be provided")
        val asMetadata = resolveAuthorizationCodeAuthorizationServerMetadata(credentialIssuerBaseUrl, httpClient)
        val tokenEndpoint = asMetadata.tokenEndpoint
            ?: error("Authorization server metadata contains no token_endpoint")
        val attestationHeaders = buildTokenEndpointAttestationHeaders(
            asMetadata = asMetadata,
            clientId = request.clientId,
            attestationAssembler = attestationAssembler,
            resolveInstanceKey = { wallet.resolveKey() },
            onAttestationObtained = onAttestationObtained,
        )
        return exchangeCode(
            request = request,
            tokenEndpoint = tokenEndpoint,
            attestationHeaders = attestationHeaders,
            httpClient = httpClient,
        )
    }

    private suspend fun exchangeCode(
        request: ExchangeCodeRequest,
        tokenEndpoint: String,
        attestationHeaders: ClientAttestationHeaders?,
        httpClient: HttpClient = defaultHttpClient(),
    ): RequestTokenResult {
        val clientConfig = ClientConfiguration(
            clientId = request.clientId,
            redirectUris = listOf(request.redirectUri.toString())
        )
        val tokenResponse = TokenRequestBuilder(clientConfig, httpClient).exchangeAuthorizationCode(
            tokenEndpoint = tokenEndpoint,
            code = request.code,
            codeVerifier = request.codeVerifier,
            additionalHeaders = request.tokenRequestHeaders,
            attestationHeaders = attestationHeaders,
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
        httpClient: HttpClient = defaultHttpClient(),
    ): Flow<StoredCredential> = pollDeferredFlow(
        wallet = wallet,
        request = request,
        onEvent = onEvent,
        httpClient = httpClient,
        beforeCredentialsStored = {},
        onCredentialStored = {},
    )

    fun pollDeferredFlow(
        wallet: Wallet,
        request: PollDeferredRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = defaultHttpClient(),
        beforeCredentialsStored: suspend (Int) -> Unit = {},
    ): Flow<StoredCredential> = pollDeferredFlow(
        wallet = wallet,
        request = request,
        onEvent = onEvent,
        httpClient = httpClient,
        beforeCredentialsStored = beforeCredentialsStored,
        onCredentialStored = {},
    )

    fun pollDeferredFlow(
        wallet: Wallet,
        request: PollDeferredRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = defaultHttpClient(),
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit,
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

        if (rawCredentials.isNotEmpty()) beforeCredentialsStored(rawCredentials.size)

        for (issuedCredential in rawCredentials) {
            val entry = wallet.parseAndStore(issuedCredential)
            onCredentialStored(entry)
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
     *   3. [receiveCredentialAuthCodeFlow] - exchange code + issue credentials
     *
     * This function handles step 3 only, continuing from an authorization code.
     */
    fun receiveCredentialAuthCodeFlow(
        wallet: Wallet,
        code: String,
        codeVerifier: String?,
        credentialIssuerBaseUrl: String,
        credentialEndpoint: Url,
        credentialConfigurationId: String,
        nonceEndpoint: String? = null,
        clientId: String = DEFAULT_CLIENT_ID,
        redirectUri: Url = Url("openid://"),
        /** Inline key for proof-of-possession; takes precedence over the wallet's stores. */
        inlineKey: DirectSerializedKey? = null,
        /** Inline DID for holder binding; defaults to the wallet's default DID. */
        inlineDid: String? = null,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = defaultHttpClient()
    ): Flow<StoredCredential> = receiveCredentialAuthCodeFlow(
        wallet = wallet,
        code = code,
        codeVerifier = codeVerifier,
        credentialIssuerBaseUrl = credentialIssuerBaseUrl,
        credentialEndpoint = credentialEndpoint,
        credentialConfigurationId = credentialConfigurationId,
        nonceEndpoint = nonceEndpoint,
        clientId = clientId,
        redirectUri = redirectUri,
        inlineKey = inlineKey,
        inlineDid = inlineDid,
        attestationAssembler = attestationAssembler,
        onEvent = onEvent,
        httpClient = httpClient,
        onCredentialStored = {},
    )

    fun receiveCredentialAuthCodeFlow(
        wallet: Wallet,
        code: String,
        codeVerifier: String?,
        credentialIssuerBaseUrl: String,
        credentialEndpoint: Url,
        credentialConfigurationId: String,
        nonceEndpoint: String? = null,
        clientId: String = DEFAULT_CLIENT_ID,
        redirectUri: Url = Url("openid://"),
        inlineKey: DirectSerializedKey? = null,
        inlineDid: String? = null,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = defaultHttpClient(),
        onCredentialStored: suspend (StoredCredential) -> Unit,
    ): Flow<StoredCredential> = channelFlow {
        val key = wallet.resolveKey(inlineKey)
            ?: error("No key available for proof-of-possession")
        val did = inlineDid ?: wallet.defaultDid()

        // Exchange code for token
        val exchangeRequest = ExchangeCodeRequest(
            code = code,
            codeVerifier = codeVerifier,
            clientId = clientId,
            redirectUri = redirectUri,
            credentialIssuerBaseUrl = credentialIssuerBaseUrl,
        )
        val tokenResult = exchangeCode(
            wallet = wallet,
            request = exchangeRequest,
            attestationAssembler = attestationAssembler,
            httpClient = httpClient,
            onAttestationObtained = { onEvent(WalletSessionEvent.issuance_attestation_obtained) },
        )
        onEvent(WalletSessionEvent.issuance_token_obtained)

        // Sign proof — nonce from nonce endpoint or token response (never fall back to access_token)
        val cNonce = nonceEndpoint?.let { fetchCNonce(httpClient, it) }
            ?: tokenResult.cNonce
            ?: error("Issuer did not provide a c_nonce (neither via nonce endpoint nor token response)")
        val proofBuilder = JwtProofBuilder()
        val proofs = proofBuilder.buildProof(key, credentialIssuerBaseUrl, cNonce, did)
        onEvent(WalletSessionEvent.issuance_proof_signed)

        // Fetch credential
        val fetchResult = fetchCredential(
            FetchCredentialRequest(
                credentialEndpoint = credentialEndpoint,
                accessToken = tokenResult.accessToken,
                credentialConfigurationId = credentialConfigurationId,
                proofJwt = proofs.jwt?.firstOrNull(),
                clientId = clientId
            )
        )
        onEvent(WalletSessionEvent.issuance_credential_received)

        for (rawString in fetchResult.rawCredentials) {
            val (_, parsed) = CredentialParser.detectAndParse(rawString)
            val entry = StoredCredential(
                id = Uuid.random().toString(),
                credential = parsed,
                addedAt = Clock.System.now()
            )
            wallet.addCredential(entry)
            onCredentialStored(entry)
            onEvent(WalletSessionEvent.issuance_credential_stored)
            send(entry)
        }
        onEvent(WalletSessionEvent.issuance_completed)
    }
}
