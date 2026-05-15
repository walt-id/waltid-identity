package id.walt.wallet2.handlers

import id.walt.credentials.CredentialParser
import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.requests.credential.DefaultCredentialRequest
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletSessionEvent
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
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

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
    val offerUrl: Url? = null,

    /**
     * A credential offer as a parsed JSON object.
     * Provide this when the offer arrives as inline JSON.
     */
    val offerJson: JsonObject? = null,

    /** ID of the key to use for proof-of-possession. Defaults to wallet's default key. */
    val keyId: String? = null,

    /** DID to use as the credential subject / holder binding. Defaults to wallet's default DID. */
    val did: String? = null,

    /** Transaction code (PIN) required by some pre-authorized code flows. */
    val txCode: String? = null,

    /** OAuth 2.0 client_id presented to the authorization server. */
    val clientId: String = "wallet-client",

    /** redirect_uri registered with the authorization server (auth-code flows only). */
    val redirectUri: Url = Url("openid://")
) {
    init {
        check(offerUrl != null || offerJson != null) {
            "Either offerUrl or offerJson must be provided"
        }
        check(offerUrl == null || offerJson == null) {
            "Only one of offerUrl or offerJson may be provided, not both"
        }
    }

    fun getEffectiveOfferString(): String =
        offerUrl?.toString() ?: offerJson?.toString() ?: error("No offer source available")
}

/** Result of a completed issuance flow. */
@Serializable
data class ReceiveCredentialResult(
    /** All credentials that were successfully issued and stored. */
    val credentialIds: List<String>
)

// Isolated step types

@Serializable
data class ResolveOfferRequest(
    val offerUrl: Url? = null,
    val offerJson: JsonObject? = null
) {
    init {
        check(offerUrl != null || offerJson != null) {
            "Either offerUrl or offerJson must be provided"
        }
        check(offerUrl == null || offerJson == null) {
            "Only one of offerUrl or offerJson may be provided, not both"
        }
    }

    fun getEffectiveOfferString(): String =
        offerUrl?.toString() ?: offerJson?.toString() ?: error("No offer source available")
}

@Serializable
data class ResolveOfferResult(
    val credentialIssuer: String,
    val credentialConfigurationIds: List<String>,
    val grantType: String?,
    val txCodeRequired: Boolean,
    val credentialEndpoint: Url,
    val offeredCredentials: List<String>
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
    val cNonce: String?,
    val expiresIn: Int?
)

@Serializable
data class SignProofRequest(
    val issuerUrl: Url,
    val nonce: String,
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
    val clientId: String = "wallet-client"
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
    val offerUrl: Url? = null,
    val offerJson: JsonObject? = null,
    val clientId: String = "wallet-client",
    val redirectUri: Url = Url("openid://"),
    val usePkce: Boolean = true
) {
    init {
        check(offerUrl != null || offerJson != null) {
            "Either offerUrl or offerJson must be provided"
        }
        check(offerUrl == null || offerJson == null) {
            "Only one of offerUrl or offerJson may be provided, not both"
        }
    }

    fun getEffectiveOfferString(): String =
        offerUrl?.toString() ?: offerJson?.toString() ?: error("No offer source available")
}

@Serializable
data class GenerateAuthorizationUrlResult(
    val authorizationUrl: Url,
    val state: String,
    val codeVerifier: String?,
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
@OptIn(ExperimentalUuidApi::class)
object WalletIssuanceHandler {

    private val lenientJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private fun defaultHttpClient() = HttpClient {
        install(ContentNegotiation) { json(lenientJson) }
    }

    /**
     * Full pre-authorized-code issuance flow, emitting each stored credential
     * as a [Flow] element as soon as it is received and stored.
     *
     * [onEvent] is called at each lifecycle step for progress reporting.
     */
    fun receiveCredentialFlow(
        wallet: Wallet,
        request: ReceiveCredentialRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = defaultHttpClient()
    ): Flow<StoredCredential> = channelFlow {
        val key = resolveKey(wallet, request.keyId)
            ?: error("No key available: wallet has no keyStores, no staticKey, and no keyId was specified")
        val did = request.did ?: wallet.defaultDid()

        val clientConfig = ClientConfiguration(
            clientId = request.clientId,
            redirectUris = listOf(request.redirectUri.toString())
        )
        val metadataResolver = IssuerMetadataResolver(httpClient)
        val tokenBuilder = TokenRequestBuilder(clientConfig, httpClient)
        val proofBuilder = JwtProofBuilder()

        // 1. Parse and resolve offer
        val offerString = request.getEffectiveOfferString()
        log.trace { "Parsing offer string: ${offerString.take(120)}..." }
        val offerRequest = CredentialOfferParser.parseCredentialOfferUrl(offerString)
        val offer = CredentialOfferResolver(httpClient).resolveCredentialOffer(
            credentialOffer = offerRequest.credentialOffer,
            credentialOfferUri = offerRequest.credentialOfferUri
        )
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
        val tokenResponse = tokenBuilder.exchangePreAuthorizedCode(
            tokenEndpoint = tokenEndpoint,
            preAuthorizedCode = preAuthGrant.preAuthorizedCode,
            txCode = request.txCode
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
                val nonce = cNonce ?: tokenResponse.access_token
                log.trace { "Building proof JWT, did=$did, nonce=$nonce" }
                if (did != null) {
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

            val credentialResponse = httpClient.post(credentialEndpoint) {
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
                // TODO: store the transactionId for later polling via a dedicated deferred-issuance endpoint
                onEvent(WalletSessionEvent.issuance_deferred)
                continue
            }

            for (issuedCredential in rawCredentials) {
                val rawString = issuedCredential.credential.let {
                    if (it is JsonPrimitive) it.content else it.toString()
                }
                val (_, parsed) = CredentialParser.detectAndParse(rawString)
                val entry = StoredCredential(
                    id = Uuid.random().toString(),
                    credential = parsed,
                    label = offeredCredential.configuration.credentialMetadata?.display?.firstOrNull()?.name,
                    addedAt = Clock.System.now()
                )
                wallet.addCredential(entry)
                onEvent(WalletSessionEvent.issuance_credential_stored)
                send(entry)
            }
        }

        onEvent(WalletSessionEvent.issuance_completed)
    }

    /**
     * Convenience wrapper that collects [receiveCredentialFlow] into a result.
     */
    suspend fun receiveCredential(
        wallet: Wallet,
        request: ReceiveCredentialRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = defaultHttpClient()
    ): ReceiveCredentialResult {
        val ids = mutableListOf<String>()
        receiveCredentialFlow(wallet, request, onEvent, httpClient).collect { ids += it.id }
        return ReceiveCredentialResult(credentialIds = ids)
    }

    // ---------------------------------------------------------------------------
    // Isolated step handlers
    // ---------------------------------------------------------------------------

    suspend fun resolveOffer(request: ResolveOfferRequest): ResolveOfferResult {
        val httpClient = defaultHttpClient()
        val offerRequest = CredentialOfferParser.parseCredentialOfferUrl(request.getEffectiveOfferString())
        val offer = CredentialOfferResolver(httpClient).resolveCredentialOffer(
            credentialOffer = offerRequest.credentialOffer,
            credentialOfferUri = offerRequest.credentialOfferUri
        )
        val issuerMetadata = IssuerMetadataResolver(httpClient).resolveCredentialIssuerMetadata(offer.credentialIssuer)
        val offeredCredentials = OfferedCredentialResolver.resolveOfferedCredentials(offer, issuerMetadata)
        return ResolveOfferResult(
            credentialIssuer = offer.credentialIssuer,
            credentialConfigurationIds = offer.credentialConfigurationIds,
            grantType = offer.grants?.preAuthorizedCode?.let { "pre-authorized_code" }
                ?: offer.grants?.authorizationCode?.let { "authorization_code" },
            txCodeRequired = offer.grants?.preAuthorizedCode?.txCode != null,
            credentialEndpoint = Url(issuerMetadata.credentialEndpoint
                ?: error("Issuer metadata contains no credential_endpoint")),
            offeredCredentials = offeredCredentials.map { it.credentialConfigurationId }
        )
    }

    suspend fun requestToken(request: RequestTokenRequest): RequestTokenResult {
        val httpClient = defaultHttpClient()
        val clientConfig = ClientConfiguration(
            clientId = request.clientId,
            redirectUris = listOf(request.redirectUri.toString())
        )
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
        val key = resolveKey(wallet, request.keyId)
            ?: error("No key available for signing proof")
        val proofBuilder = JwtProofBuilder()
        val proofs = if (request.did != null) {
            proofBuilder.buildJwtProof(key, request.issuerUrl.toString(), request.nonce, keyId = request.did)
        } else {
            proofBuilder.buildJwtProof(key, request.issuerUrl.toString(), request.nonce, includeJwk = true)
        }
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
        val response = httpClient.post(request.credentialEndpoint.toString()) {
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

    private suspend fun resolveKey(wallet: Wallet, keyId: String?) = when {
        keyId != null -> wallet.findKey(keyId)
            ?: error("Key '$keyId' not found in any wallet key store")
        else -> wallet.defaultKey()
    }

    private suspend fun fetchCNonce(httpClient: HttpClient, nonceEndpoint: String): String? =
        runCatching {
            val response = httpClient.post(nonceEndpoint) {
                contentType(ContentType.Application.Json)
            }
            if (response.status.isSuccess()) {
                response.body<JsonObject>()["c_nonce"]
                    ?.let { (it as? JsonPrimitive)?.content }
            } else null
        }.getOrNull()

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
        val offerString = request.getEffectiveOfferString()
        val offerRequest = CredentialOfferParser.parseCredentialOfferUrl(offerString)
        val offer = CredentialOfferResolver(httpClient).resolveCredentialOffer(
            credentialOffer = offerRequest.credentialOffer,
            credentialOfferUri = offerRequest.credentialOfferUri
        )
        val issuerMetadata = IssuerMetadataResolver(httpClient).resolveCredentialIssuerMetadata(offer.credentialIssuer)
        val asMetadata = IssuerMetadataResolver(httpClient).resolveAuthorizationServerMetadataWithFallback(issuerMetadata)

        val authorizationEndpoint = asMetadata.authorizationEndpoint
            ?: error("Authorization server has no authorization_endpoint")

        val clientConfig = ClientConfiguration(
            clientId = request.clientId,
            redirectUris = listOf(request.redirectUri.toString())
        )
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
        val clientConfig = ClientConfiguration(
            clientId = request.clientId,
            redirectUris = listOf(request.redirectUri.toString())
        )
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
    @OptIn(ExperimentalUuidApi::class)
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
            // issuance_pending is a spec-defined recoverable error — the wallet should retry later
            if (body.contains("issuance_pending")) {
                log.info { "Deferred credential not yet ready for transactionId=${request.transactionId}" }
                return@channelFlow
            }
            error("Deferred credential endpoint returned ${response.status}: $body")
        }

        val credentialResponse = response.body<CredentialResponse>()
        val rawCredentials = credentialResponse.credentials
            ?: error("Deferred credential response contained no credentials")

        for (issuedCredential in rawCredentials) {
            val rawString = issuedCredential.credential.let {
                if (it is JsonPrimitive) it.content else it.toString()
            }
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
    @OptIn(ExperimentalUuidApi::class)
    fun receiveCredentialAuthCodeFlow(
        wallet: Wallet,
        tokenEndpoint: Url,
        code: String,
        codeVerifier: String?,
        credentialEndpoint: Url,
        credentialConfigurationId: String,
        clientId: String = "wallet-client",
        redirectUri: Url = Url("openid://"),
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = defaultHttpClient()
    ): Flow<StoredCredential> = channelFlow {
        val key = wallet.defaultKey()
            ?: error("No key available for proof-of-possession")
        val did = wallet.defaultDid()

        // Exchange code for token
        val tokenResult = exchangeCode(ExchangeCodeRequest(
            tokenEndpoint = tokenEndpoint,
            code = code,
            codeVerifier = codeVerifier,
            clientId = clientId,
            redirectUri = redirectUri
        ))
        onEvent(WalletSessionEvent.issuance_token_obtained)

        // Sign proof
        val cNonce = tokenResult.cNonce ?: tokenResult.accessToken
        val proofBuilder = JwtProofBuilder()
        val proofs = if (did != null) {
            proofBuilder.buildJwtProof(key, credentialEndpoint.host, cNonce, keyId = did)
        } else {
            proofBuilder.buildJwtProof(key, credentialEndpoint.host, cNonce, includeJwk = true)
        }
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
