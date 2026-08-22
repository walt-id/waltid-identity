package id.walt.wallet2.handlers

import id.walt.credentials.CredentialParser
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.selectJwsAlgorithm
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.did.dids.DidService
import id.walt.openid4vci.CryptographicBindingMethod
import id.walt.openid4vci.clientauth.ClientAuthenticationMethods
import id.walt.openid4vci.errors.CredentialError
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.issuer.ProofType
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.openid4vci.offers.CredentialOffer
import id.walt.openid4vci.offers.TxCode
import id.walt.openid4vci.prooftypes.Proofs
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.wallet2.data.*
import id.walt.wallet2.handlers.WalletIssuanceHandler.exchangeCode
import id.walt.wallet2.handlers.WalletIssuanceHandler.pollDeferredFlow
import id.walt.wallet2.handlers.WalletIssuanceHandler.resolveOffer
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import id.waltid.openid4vci.wallet.authorization.AuthorizationRequestBuilder
import id.waltid.openid4vci.wallet.authorization.PushedAuthorizationRequestExecutor
import id.waltid.openid4vci.wallet.attestation.ClientAttestationAssembler
import id.waltid.openid4vci.wallet.attestation.ClientAttestationHeaders
import id.waltid.openid4vci.wallet.clientauth.ClientAssertionBuilder
import id.waltid.openid4vci.wallet.dpop.DPOP_HEADER
import id.waltid.openid4vci.wallet.dpop.DPOP_NONCE_ATTEMPTS
import id.waltid.openid4vci.wallet.dpop.DPOP_NONCE_HEADER
import id.waltid.openid4vci.wallet.dpop.USE_DPOP_NONCE
import id.waltid.openid4vci.wallet.metadata.IssuerMetadataResolver
import id.waltid.openid4vci.wallet.metadata.CredentialIssuerMetadataTrustResolver
import id.waltid.openid4vci.wallet.metadata.OfferedCredentialResolver
import id.waltid.openid4vci.wallet.metadata.ResolvedCredentialIssuerMetadata
import id.waltid.openid4vci.wallet.nonce.NonceRequestBuilder
import id.waltid.openid4vci.wallet.oauth.ClientConfiguration
import id.waltid.openid4vci.wallet.offer.CredentialOfferParser
import id.waltid.openid4vci.wallet.offer.CredentialOfferResolver
import id.waltid.openid4vci.wallet.proof.JwtProofBuilder
import id.waltid.openid4vci.wallet.proof.ProofKeyBinding
import id.waltid.openid4vci.wallet.token.ClientAssertionFactory
import id.waltid.openid4vci.wallet.token.TokenRequestBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.jvm.JvmInline
import kotlin.time.Clock
import kotlin.uuid.Uuid
import id.walt.crypto2.keys.Key as Crypto2Key

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
     * and the wallet's stores. Prefer [keyId] for store-backed / crypto2 keys so the wallet can
     * resolve a signing-capable [WalletKeyStoreEntry] (e.g. Enterprise `keyReference.path`).
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
    val tokenRequestHeaders: Map<String, String> = emptyMap(),

    /**
     * Optional arbitrary metadata to store alongside the received credential(s).
     * This metadata is passed through to [StoredCredential.metadata] when credentials are stored.
     */
    val metadata: JsonObject? = null,
) : CredentialOfferSource {
    init {
        checkOfferSource()
    }
}

/** Opaque identifier binding an issuance action to one reviewed credential-offer resolution. */
@Serializable
@JvmInline
value class IssuancePreviewHandle(val value: String) {
    init {
        require(value.isNotBlank()) { "Issuance preview handle must not be blank" }
    }

    override fun toString(): String = "IssuancePreviewHandle(<redacted>)"
}

/**
 * Input for receiving credentials from a reviewed preview.
 *
 * The offer source is intentionally absent: [previewHandle] is the only authority for the exact
 * offer and metadata resolution the user reviewed.
 */
@Serializable
data class ReceiveCredentialFromPreviewRequest(
    val previewHandle: IssuancePreviewHandle,
    val key: DirectSerializedKey? = null,
    val keyId: String? = null,
    val did: String? = null,
    val txCode: String? = null,
    val clientId: String = DEFAULT_CLIENT_ID,
    val redirectUri: Url = Url("openid://"),
    val tokenRequestHeaders: Map<String, String> = emptyMap(),
)

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
    init {
        checkOfferSource()
    }
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
    val nonceEndpoint: Url? = null,
)

/** A credential-offer preview and the opaque handle required to act on that review. */
data class IssuancePreview(
    val handle: IssuancePreviewHandle,
    val offer: ResolveOfferResult,
)

/**
 * Typed metadata for an offer retained between review and issuance.
 *
 * Unlike [ResolveOfferResult], this preview-only result is not serialized as a shared REST response.
 * It exposes the protocol models already resolved for the retained issuance snapshot so mobile
 * consumers can project them into platform-safe API models without parsing raw JSON or refetching.
 *
 * @property previewHandle Opaque handle required to act on this retained preview.
 * @property resolvedIssuerMetadata Canonical issuer-metadata resolution retained for this preview.
 * @property offeredCredentials Offered credential configurations resolved against
 * [resolvedIssuerMetadata].metadata.
 * @property transactionCode Canonical OpenID4VCI transaction-code metadata, when required.
 */
data class WalletOfferPreviewResult(
    val previewHandle: IssuancePreviewHandle,
    val resolvedIssuerMetadata: ResolvedCredentialIssuerMetadata,
    val offeredCredentials: List<OfferedCredentialResolver.ResolvedCredentialOffer>,
    val transactionCode: TxCode?,
)

/**
 * Stateless, richer variant of [ResolveOfferResult].
 *
 * Combines the app-facing [ResolveOfferResult] summary (grant type, endpoints, pre-authorized code,
 * transaction-code requirement) with the already-resolved protocol metadata so callers can render an
 * issuer/credential preview without a second resolution and without retaining a preview handle.
 *
 * Unlike [WalletOfferPreviewResult] this does not create or store a preview handle, so it is suited to
 * stateless "resolve for display" endpoints where issuance is completed by re-sending the offer.
 *
 * @property summary App-facing offer summary (identical to [resolveOffer]'s result).
 * @property resolvedIssuerMetadata Canonical issuer-metadata resolution returned with this result.
 * @property offeredCredentials Offered credential configurations resolved against
 * [resolvedIssuerMetadata].metadata.
 * @property transactionCode Canonical OpenID4VCI transaction-code metadata, when required.
 */
data class WalletOfferResolution(
    val summary: ResolveOfferResult,
    val resolvedIssuerMetadata: ResolvedCredentialIssuerMetadata,
    val offeredCredentials: List<OfferedCredentialResolver.ResolvedCredentialOffer>,
    val transactionCode: TxCode?,
)

/**
 * Complete credential-offer resolution retained between review and issuance.
 *
 * @property summary App-facing metadata derived from the resolution.
 * @property offer Exact parsed credential offer, including its grants.
 * @property resolvedIssuerMetadata Canonical issuer-metadata resolution used to validate the
 * offered configurations.
 * @property authorizationServerMetadata Authorization server metadata used for the token request.
 * @property offeredCredentials Offered configurations resolved against
 * [resolvedIssuerMetadata].metadata.
 */
internal class ResolvedIssuanceOffer(
    val source: ResolveOfferRequest,
    val summary: ResolveOfferResult,
    val offer: CredentialOffer,
    val resolvedIssuerMetadata: ResolvedCredentialIssuerMetadata,
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
    val expiresIn: Long? = null,
    /**
     * OAuth `token_type`, e.g. `DPoP` or `Bearer`.
     *
     * RFC 9449 Section 7.1: a DPoP-typed access token must be presented with a per-request proof, and
     * a Bearer token must not be. Dropping this made the authorization-code flow request credentials
     * without a proof even after the token endpoint had issued it a DPoP token.
     */
    val tokenType: String? = null,
) {
    override fun toString(): String =
        "RequestTokenResult(accessToken=<redacted>, expiresIn=$expiresIn, tokenType=$tokenType)"
}

@Serializable
data class RequestNonceRequest(
    val credentialIssuer: Url,
)

@Serializable
data class RequestNonceResult(
    val nonce: String?,
) {
    override fun toString(): String = "RequestNonceResult(nonce=<redacted>)"
}

@Serializable
data class SignProofRequest(
    val issuerUrl: Url,
    /**
     * Credential configuration id whose `proof_types_supported` constrains the proof algorithm.
     * Resolved against the issuer metadata at [issuerUrl].
     */
    val credentialConfigurationId: String,
    val nonce: String? = null,
    /** Inline key to sign the proof with; takes precedence over [keyId]. */
    val key: DirectSerializedKey? = null,
    val keyId: String? = null,
    val did: String? = null,
) {
    init {
        require(credentialConfigurationId.isNotBlank()) {
            "credentialConfigurationId must not be blank"
        }
    }
}

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
     *
     * When storing, pass [credentialIssuerBaseUrl] (and optionally [metadata]/[label])
     * so issuer display metadata and credential labels are persisted the same way as
     * the full pre-authorized receive path.
     */
    val storeInWallet: Boolean = false,
    /**
     * Credential issuer base URL used to resolve issuer metadata when [storeInWallet] is true.
     * Typically the same value returned by offer resolution / authorization-url generation.
     */
    val credentialIssuerBaseUrl: String? = null,
    /** Optional sidecar metadata merged with resolved issuer display when storing. */
    val metadata: JsonObject? = null,
    /** Optional credential label override; otherwise derived from credential configuration display. */
    val label: String? = null,
)

@Serializable
data class FetchCredentialResult(
    val rawCredentials: List<String>
)

/**
 * Completes the authorization-code grant in one call: exchanges [code] for an access token, builds a
 * proof of possession, fetches the credential(s) and stores them in the wallet.
 *
 * The caller still drives the browser redirect and therefore holds [code], [codeVerifier] and the
 * endpoints that `POST /credentials/receive/authorization-url` returned - those isolated steps remain
 * the caller's responsibility. Everything after the redirect is handled here, mirroring what
 * `credentials/receive` does for the pre-authorized code grant.
 */
@Serializable
data class ReceiveAuthorizedCredentialRequest(
    /** Authorization code from the redirect callback. */
    val code: String,
    /** PKCE verifier returned by `authorization-url`; required whenever PKCE was used. */
    val codeVerifier: String? = null,
    /** Credential Issuer Identifier, used to re-resolve issuer and authorization server metadata. */
    val credentialIssuer: String,
    val credentialEndpoint: Url,
    val credentialConfigurationId: String,
    /** Issuer nonce endpoint, when it advertises one; validated against issuer metadata. */
    val nonceEndpoint: Url? = null,
    val clientId: String = DEFAULT_CLIENT_ID,
    val redirectUri: Url = Url("openid://"),
    /**
     * Sender constrain the token and credential requests with DPoP (RFC 9449).
     *
     * Opt-in, because there is no metadata that says whether the *credential endpoint* accepts
     * DPoP-bound tokens - `dpop_signing_alg_values_supported` describes the authorization server only.
     * Enabling it on that signal alone broke working issuance: an authorization server advertising DPoP
     * duly issued a DPoP-bound token, and the Credential Issuer then rejected it as `invalid_token`
     * because its credential endpoint expects a Bearer token.
     *
     * Turn it on for issuers known to accept DPoP end to end, such as FAPI 2.0 / HAIP deployments.
     */
    val useDpop: Boolean = false,

    /** Inline holder key for proof of possession; takes precedence over [keyId]. */
    val key: DirectSerializedKey? = null,
    val keyId: String? = null,
    /** Holder DID; when absent the proof is bound to the raw JWK. */
    val did: String? = null,
    /** Optional metadata stored alongside the received credential(s). */
    val metadata: JsonObject? = null,
    /** Optional label override; otherwise derived from the credential configuration display. */
    val label: String? = null,
) {
    init {
        require(code.isNotBlank()) { "Authorization code must not be blank" }
        require(credentialIssuer.isNotBlank()) { "credentialIssuer must not be blank" }
        require(credentialConfigurationId.isNotBlank()) { "credentialConfigurationId must not be blank" }
    }
}

/**
 * A sanitized failure returned by an OpenID4VCI Credential Endpoint.
 *
 * The response body is parsed into [credentialError] when it follows the OID4VCI error shape;
 * raw response content, access tokens, and proof material are deliberately not retained.
 */
class CredentialEndpointException(
    val statusCode: Int,
    val credentialError: CredentialError? = null,
) : Exception(
    buildString {
        append("Credential endpoint returned HTTP ").append(statusCode)
        credentialError?.error?.let { append(" (").append(it).append(')') }
    }
) {
    val isInvalidNonce: Boolean
        get() = credentialError?.error == CredentialErrorCodes.INVALID_NONCE
}

// Deferred issuance types

@Serializable
data class PollDeferredRequest(
    /** The deferred credential endpoint URL from the issuer's metadata. */
    val deferredCredentialEndpoint: Url,
    /** The transaction_id received when the credential was deferred. */
    val transactionId: String,
    /** Access token from the original token response. */
    val accessToken: String,
    /**
     * Credential issuer base URL used to resolve issuer metadata when storing the deferred credential.
     * Pass the same issuer URL used for the original offer so `issuerDisplay` and labels are persisted.
     */
    val credentialIssuerBaseUrl: String? = null,
    /** Credential configuration id used to derive the stored credential label from issuer metadata. */
    val credentialConfigurationId: String? = null,
    /** Optional sidecar metadata merged with resolved issuer display when storing. */
    val metadata: JsonObject? = null,
    /** Optional credential label override; otherwise derived from credential configuration display. */
    val label: String? = null,
)

// Auth-code grant isolated steps

@Serializable
data class GenerateAuthorizationUrlRequest(
    override val offerUrl: Url? = null,
    override val offerJson: JsonObject? = null,
    val clientId: String = DEFAULT_CLIENT_ID,
    val redirectUri: Url = Url("openid://"),
    val usePkce: Boolean = true,
    /**
     * Request the credential by OAuth `scope` instead of `authorization_details`.
     *
     * OID4VCI 1.0 Section 5.1.2 defines both, as alternatives. `authorization_details` is the default
     * because it names the credential configuration directly; scope-based authorization needs the
     * issuer to publish a `scope` on the configuration, and some profiles (HAIP among them) require it.
     */
    val useScope: Boolean = false,
) : CredentialOfferSource {
    init {
        checkOfferSource()
    }
}

@Serializable
data class GenerateAuthorizationUrlResult(
    val authorizationUrl: Url,
    val state: String,
    val codeVerifier: String? = null,
    val credentialConfigurationId: String,
    val credentialIssuerBaseUrl: String,
    val nonceEndpoint: Url? = null,
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
    private val crypto2Runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val lenientJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val previewedOffers = PreviewSessionStore<ResolvedIssuanceOffer>(sessionName = "Issuance")

    /** Existing redirect handling for credential endpoint POSTs; nonce requests never use this path. */
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
            val inlineOffer = lenientJson.decodeFromString<CredentialOffer>(source.getEffectiveOfferString())
            CredentialOfferResolver(httpClient).resolveCredentialOffer(credentialOffer = inlineOffer, credentialOfferUri = null)
        } else {
            val req = CredentialOfferParser.parseCredentialOfferUrl(source.getEffectiveOfferString())
            CredentialOfferResolver(httpClient).resolveCredentialOffer(
                credentialOffer = req.credentialOffer,
                credentialOfferUri = req.credentialOfferUri
            )
        }

    /** Immediate, non-previewed pre-authorized-code issuance flow. */
    fun receiveCredentialFlow(
        wallet: Wallet,
        request: ReceiveCredentialRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        /**
         * Called whenever the issuer defers a credential.
         * [credentialConfigurationId] identifies which credential was deferred;
         * [transactionId] should be stored and passed to [pollDeferredFlow] later.
         */
        onDeferredTransactionId: suspend (credentialConfigurationId: String, transactionId: String) -> Unit = { _, _ -> },
        /** Called with the exact response batch size before any credential of that batch is persisted. */
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
        metadataTrustResolver: CredentialIssuerMetadataTrustResolver? = null,
    ): Flow<StoredCredential> = receiveCredentialFlowInternal(
        wallet = wallet,
        request = request,
        resolvedOffer = null,
        attestationAssembler = attestationAssembler,
        onEvent = onEvent,
        httpClient = httpClient,
        onDeferredTransactionId = onDeferredTransactionId,
        beforeCredentialsStored = beforeCredentialsStored,
        onCredentialStored = onCredentialStored,
        metadataTrustResolver = metadataTrustResolver,
    )

    /**
     * Reviewed pre-authorized-code issuance flow.
     *
     * Failed attempts retain the selected preview for retry. Successful completion consumes it.
     */
    fun receiveCredentialFlow(
        wallet: Wallet,
        request: ReceiveCredentialFromPreviewRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        onDeferredTransactionId: suspend (credentialConfigurationId: String, transactionId: String) -> Unit = { _, _ -> },
        /** Called with the exact response batch size before any credential of that batch is persisted. */
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
    ): Flow<StoredCredential> = channelFlow {
        previewedOffers.useRetainingOnFailure(
            walletId = wallet.id,
            id = request.previewHandle.value,
        ) { resolvedOffer ->
            receiveCredentialFlowInternal(
                wallet = wallet,
                request = request.toReceiveCredentialRequest(resolvedOffer.source),
                resolvedOffer = resolvedOffer,
                attestationAssembler = attestationAssembler,
                onEvent = onEvent,
                httpClient = httpClient,
                onDeferredTransactionId = onDeferredTransactionId,
                beforeCredentialsStored = beforeCredentialsStored,
                onCredentialStored = onCredentialStored,
                metadataTrustResolver = null,
            ).collect(::send)
        }
    }

    private fun receiveCredentialFlowInternal(
        wallet: Wallet,
        request: ReceiveCredentialRequest,
        resolvedOffer: ResolvedIssuanceOffer?,
        attestationAssembler: ClientAttestationAssembler?,
        onEvent: suspend (WalletSessionEvent) -> Unit,
        httpClient: HttpClient,
        onDeferredTransactionId: suspend (credentialConfigurationId: String, transactionId: String) -> Unit,
        beforeCredentialsStored: suspend (Int) -> Unit,
        onCredentialStored: suspend (StoredCredential) -> Unit,
        metadataTrustResolver: CredentialIssuerMetadataTrustResolver?,
    ): Flow<StoredCredential> = channelFlow {
        val keyMaterial = request.key?.key?.let { WalletKeyStoreEntry(it.getKeyId(), it, null) }
            ?: wallet.resolveKeyMaterial(request.keyId, setOf(KeyUsage.SIGN))
            // The previous wording claimed the wallet had no key stores, which misreports the common
            // case of a store that exists but is empty - resolveKeyMaterial also returns null when a
            // named key is absent, or when the keys present do not permit signing.
            ?: error(
                request.keyId?.let { "Wallet '${wallet.id}' has no key '$it' usable for signing" }
                    ?: "Wallet '${wallet.id}' holds no key usable for signing: none of its " +
                    "${wallet.keyStores.size} key store(s) contained a key permitting KeyUsage.SIGN, " +
                    "there is no static key, and neither an inline key nor a keyId was supplied"
            )
        val did = request.did ?: wallet.defaultDid()
        val requestMetadata = request.metadata

        val clientConfig = ClientConfiguration(
            clientId = request.clientId,
            redirectUris = listOf(request.redirectUri.toString())
        )
        val tokenBuilder = TokenRequestBuilder(clientConfig, httpClient)
        val proofBuilder = JwtProofBuilder()

        // 1. Resolve the offer source, or use the exact resolution selected by its preview handle.
        log.trace { "Parsing offer string: ${request.getEffectiveOfferString().take(120)}..." }
        val effectiveResolvedOffer = resolvedOffer ?: resolveIssuanceOffer(
            request.toResolveOfferRequest(),
            httpClient,
            metadataTrustResolver,
        )

        // 2. Reuse issuer metadata and offered configurations from that resolution.
        val offer = effectiveResolvedOffer.offer
        val issuerMetadata = effectiveResolvedOffer.resolvedIssuerMetadata.metadata
        val offeredCredentials = effectiveResolvedOffer.offeredCredentials
        val asMetadata = effectiveResolvedOffer.authorizationServerMetadata
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

        val attestationHeaders = buildClientAttestationHeaders(
            asMetadata = asMetadata,
            clientId = request.clientId,
            attestationAssembler = attestationAssembler,
            resolveInstanceKey = { keyMaterial.crypto2AttestationKey() },
            onAttestationObtained = { onEvent(WalletSessionEvent.issuance_attestation_obtained) },
        )

        // private_key_jwt (RFC 7523). Engaged from authorization server metadata for the same reason
        // attestation is: the wallet cannot know out of band which method a given issuer requires.
        // Attestation wins when both are advertised, because it additionally attests the wallet
        // instance rather than only proving key control.
        val clientAssertionFactory = clientAssertionFactory(
            asMetadata = asMetadata,
            clientId = request.clientId,
            keyMaterial = keyMaterial,
        ).takeIf { attestationHeaders == null }

        val anonymousPreAuthorizedCode =
            asMetadata.preAuthorizedGrantAnonymousAccessSupported == true &&
                    request.tokenRequestHeaders.isEmpty() &&
                    attestationHeaders == null &&
                    clientAssertionFactory == null

        // Sender constraining (RFC 9449): used when the authorization server advertises DPoP *and*
        // the wallet key can sign one of the advertised algorithms. Otherwise a plain Bearer token
        // is requested - DPoP is optional for the wallet, so an unusable key must not fail issuance.
        val dpopAlgorithms = usableDpopAlgorithms(asMetadata, keyMaterial)

        // OpenID4VCI 1.0 §6.3: only forward a tx_code when the offer's grant requested one;
        // issuers now reject an unsolicited tx_code instead of ignoring it.
        val effectiveTxCode = request.txCode?.takeIf { preAuthGrant.txCode != null }

        val tokenResponse = tokenBuilder.exchangePreAuthorizedCode(
            tokenEndpoint = tokenEndpoint,
            preAuthorizedCode = preAuthGrant.preAuthorizedCode,
            txCode = effectiveTxCode,
            additionalHeaders = request.tokenRequestHeaders,
            attestationHeaders = attestationHeaders,
            anonymous = anonymousPreAuthorizedCode,
            dpopProofFactory = dpopAlgorithms?.let { algorithms ->
                { endpoint: String, nonce: String? ->
                    buildDpopProof(keyMaterial, algorithms, endpoint, nonce = nonce)
                }
            },
            clientAssertionFactory = clientAssertionFactory,
        )
        log.trace { "Token obtained" }
        onEvent(WalletSessionEvent.issuance_token_obtained)

        // A DPoP-typed access token must carry a per-request proof; a Bearer token must not.
        val credentialDpop = dpopAlgorithmsForToken(tokenResponse.token_type, dpopAlgorithms)
            ?.let { DpopRequestContext(it, keyMaterial) }

        val credentialEndpoint = issuerMetadata.credentialEndpoint
        log.trace { "Credential endpoint: $credentialEndpoint" }

        // 5. Issue each offered credential with a fresh nonce when proof is required.
        for (offeredCredential in offeredCredentials) {
            log.trace { "Issuing credential configId=${offeredCredential.credentialConfigurationId}, format=${offeredCredential.configuration.format}" }
            val jwtProofAlgorithms = supportedJwtProofAlgorithms(offeredCredential.configuration.proofTypesSupported)
            val buildProof: (suspend (String?) -> String?)? =
                if (jwtProofAlgorithms != null) {
                    { nonce ->
                        log.trace { "Building credential proof JWT" }
                        val preferJwkBinding = shouldPreferJwkBinding(
                            offeredCredential.configuration.cryptographicBindingMethodsSupported
                        )
                        buildJwtProof(
                            proofBuilder = proofBuilder,
                            keyMaterial = keyMaterial,
                            audience = offer.credentialIssuer,
                            nonce = nonce,
                            did = did?.takeUnless { preferJwkBinding },
                            acceptedAlgorithms = jwtProofAlgorithms,
                        ).jwt?.firstOrNull()
                    }
                } else null

            val credentialResponse = requestCredentialWithNonceRetry(
                request = FetchCredentialRequest(
                    credentialEndpoint = Url(credentialEndpoint),
                    accessToken = tokenResponse.access_token,
                    credentialConfigurationId = offeredCredential.credentialConfigurationId,
                ),
                nonceEndpoint = issuerMetadata.nonceEndpoint,
                httpClient = httpClient,
                buildProof = buildProof,
                onProofGenerated = { onEvent(WalletSessionEvent.issuance_proof_signed) },
                dpop = credentialDpop,
            )
            onEvent(WalletSessionEvent.issuance_credential_received)

            val rawCredentials = credentialResponse.credentials

            if (rawCredentials == null) {
                // Deferred issuance: the issuer accepted the request but will issue the credential later.
                // The transactionId can be used to poll the deferred credential endpoint.
                val transactionId = credentialResponse.transactionId
                log.info { "Deferred issuance: credential for '${offeredCredential.credentialConfigurationId}' will be available later" }
                if (transactionId != null) {
                    onDeferredTransactionId(offeredCredential.credentialConfigurationId, transactionId)
                }
                onEvent(WalletSessionEvent.issuance_deferred)
                continue
            }

            if (rawCredentials.isNotEmpty()) beforeCredentialsStored(rawCredentials.size)
            for (issuedCredential in rawCredentials) {
                val entry = wallet.parseAndStore(
                    issuedCredential,
                    label = offeredCredential.configuration.credentialMetadata?.display?.firstOrNull()?.name,
                    metadata = storedCredentialDisplayMetadata(
                        issuerMetadata = issuerMetadata,
                        credentialConfigurationId = offeredCredential.credentialConfigurationId,
                        requestMetadata = requestMetadata,
                    ),
                )
                onCredentialStored(entry)
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
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        /** Called with the exact response batch size before any credential of that batch is persisted. */
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
        metadataTrustResolver: CredentialIssuerMetadataTrustResolver? = null,
    ): ReceiveCredentialResult {
        val ids = mutableListOf<String>()
        val deferredIds = mutableMapOf<String, String>()
        receiveCredentialFlow(
            wallet = wallet,
            request = request,
            attestationAssembler = attestationAssembler,
            onEvent = onEvent,
            httpClient = httpClient,
            onDeferredTransactionId = { configId, txId -> deferredIds[configId] = txId },
            beforeCredentialsStored = beforeCredentialsStored,
            onCredentialStored = onCredentialStored,
            metadataTrustResolver = metadataTrustResolver,
        ).collect { ids += it.id }
        return ReceiveCredentialResult(credentialIds = ids, deferredTransactionIds = deferredIds)
    }

    /** Receives credentials using exactly the offer resolution selected by [request]. */
    suspend fun receiveCredential(
        wallet: Wallet,
        request: ReceiveCredentialFromPreviewRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        /** Called with the exact response batch size before any credential of that batch is persisted. */
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
    ): ReceiveCredentialResult {
        val ids = mutableListOf<String>()
        val deferredIds = mutableMapOf<String, String>()
        receiveCredentialFlow(
            wallet = wallet,
            request = request,
            attestationAssembler = attestationAssembler,
            onEvent = onEvent,
            httpClient = httpClient,
            onDeferredTransactionId = { configId, txId -> deferredIds[configId] = txId },
            beforeCredentialsStored = beforeCredentialsStored,
            onCredentialStored = onCredentialStored,
        ).collect { ids += it.id }
        return ReceiveCredentialResult(credentialIds = ids, deferredTransactionIds = deferredIds)
    }

    /**
     * Resolves an offer for review and retains the complete resolution for [receiveCredential].
     *
     * The returned opaque handle selects this exact parsed offer, issuer metadata, authorization
     * server metadata, and offered configuration set for a later reviewed receive action.
     *
     * @param wallet Wallet that will receive the reviewed offer.
     * @param request Credential offer URL or inline offer JSON to resolve.
     * @param httpClient HTTP client used for offer and metadata resolution.
     * @return Review metadata and the opaque handle required to act on it.
     */
    suspend fun previewOffer(
        wallet: Wallet,
        request: ResolveOfferRequest,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        metadataTrustResolver: CredentialIssuerMetadataTrustResolver? = null,
    ): WalletOfferPreviewResult {
        val resolvedOffer = resolveIssuanceOffer(request, httpClient, metadataTrustResolver)
        val previewHandle = IssuancePreviewHandle(
            previewedOffers.create(walletId = wallet.id, value = resolvedOffer)
        )
        return WalletOfferPreviewResult(
            previewHandle = previewHandle,
            resolvedIssuerMetadata = resolvedOffer.resolvedIssuerMetadata,
            offeredCredentials = resolvedOffer.offeredCredentials,
            transactionCode = resolvedOffer.offer.grants?.preAuthorizedCode?.txCode,
        )
    }

    /** Explicitly discards a reviewed issuance preview without contacting the issuer. */
    suspend fun discardPreview(wallet: Wallet, handle: IssuancePreviewHandle) {
        previewedOffers.discard(walletId = wallet.id, id = handle.value)
    }

    /** Clears every issuance preview and tombstone owned by [wallet] during wallet deletion. */
    suspend fun clearPreviews(wallet: Wallet) {
        previewedOffers.clearWallet(wallet.id)
    }

    private suspend fun resolveIssuanceOffer(
        request: ResolveOfferRequest,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        metadataTrustResolver: CredentialIssuerMetadataTrustResolver? = null,
    ): ResolvedIssuanceOffer {
        val offer = resolveOffer(request, httpClient)
        val metadataResolver = IssuerMetadataResolver(httpClient, metadataTrustResolver)
        val resolvedIssuerMetadata = metadataResolver.resolveCredentialIssuerMetadata(offer.credentialIssuer)
        val issuerMetadata = resolvedIssuerMetadata.metadata
        val asMetadata = metadataResolver.resolveAuthorizationServerMetadataWithFallback(issuerMetadata)
        val offeredCredentials = OfferedCredentialResolver.resolveOfferedCredentials(offer, issuerMetadata)
        return ResolvedIssuanceOffer(
            source = request,
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
                nonceEndpoint = issuerMetadata.nonceEndpoint?.let { Url(it) },
            ),
            offer = offer,
            resolvedIssuerMetadata = resolvedIssuerMetadata,
            authorizationServerMetadata = asMetadata,
            offeredCredentials = offeredCredentials,
        )
    }

    private fun ReceiveCredentialRequest.toResolveOfferRequest(): ResolveOfferRequest =
        ResolveOfferRequest(offerUrl = offerUrl, offerJson = offerJson)

    private fun ReceiveCredentialFromPreviewRequest.toReceiveCredentialRequest(
        source: ResolveOfferRequest,
    ): ReceiveCredentialRequest = ReceiveCredentialRequest(
        offerUrl = source.offerUrl,
        offerJson = source.offerJson,
        key = key,
        keyId = keyId,
        did = did,
        txCode = txCode,
        clientId = clientId,
        redirectUri = redirectUri,
        tokenRequestHeaders = tokenRequestHeaders,
    )

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

    /**
     * Resolves an offer and returns the summary together with the resolved issuer and offered-credential
     * metadata, without retaining a preview handle.
     *
     * Use this for stateless "resolve for display" flows that render an issuer/credential preview and then
     * complete issuance by re-sending the offer (e.g. via the pre-authorized or authorization-code endpoints).
     *
     * @param request Credential offer URL or inline offer JSON to resolve.
     * @param httpClient HTTP client used for offer and metadata resolution.
     * @param metadataTrustResolver Optional trust boundary for signed Credential Issuer Metadata. When
     * absent, only unsigned metadata is accepted.
     * @return Resolved offer summary, issuer metadata resolution, offered credentials, and transaction code.
     */
    suspend fun resolveOfferDetailed(
        request: ResolveOfferRequest,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        metadataTrustResolver: CredentialIssuerMetadataTrustResolver? = null,
    ): WalletOfferResolution {
        val resolved = resolveIssuanceOffer(request, httpClient, metadataTrustResolver)
        return WalletOfferResolution(
            summary = resolved.summary,
            resolvedIssuerMetadata = resolved.resolvedIssuerMetadata,
            offeredCredentials = resolved.offeredCredentials,
            transactionCode = resolved.offer.grants?.preAuthorizedCode?.txCode,
        )
    }

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
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        onAttestationObtained: suspend () -> Unit = {},
    ): RequestTokenResult {
        val credentialIssuer = request.credentialIssuer?.takeIf { it.isNotBlank() }
        val asMetadata = credentialIssuer?.let {
            val metadataResolver = IssuerMetadataResolver(httpClient)
            val issuerMetadata = metadataResolver.resolveCredentialIssuerMetadata(it).metadata
            metadataResolver.resolveAuthorizationServerMetadataWithFallback(issuerMetadata)
        }
        val attestationHeaders = asMetadata?.let {
            buildClientAttestationHeaders(
                asMetadata = it,
                clientId = request.clientId,
                attestationAssembler = attestationAssembler,
                resolveInstanceKey = {
                    wallet.resolveKeyMaterial(null, setOf(KeyUsage.SIGN))?.crypto2AttestationKey()
                },
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
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
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
            expiresIn = tokenResponse.expires_in,
            tokenType = tokenResponse.token_type,
        )
    }

    suspend fun requestNonce(
        request: RequestNonceRequest,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
    ): RequestNonceResult {
        val issuerMetadata = IssuerMetadataResolver(httpClient)
            .resolveCredentialIssuerMetadata(request.credentialIssuer.toString()).metadata
        return RequestNonceResult(
            nonce = requestProofNonce(
                httpClient = httpClient,
                issuerMetadata = issuerMetadata,
            )
        )
    }

    suspend fun signProof(
        wallet: Wallet,
        request: SignProofRequest,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
    ): SignProofResult {
        val keyMaterial = request.key?.key?.let { WalletKeyStoreEntry(it.getKeyId(), it, null) }
            ?: wallet.resolveKeyMaterial(request.keyId, setOf(KeyUsage.SIGN))
            ?: error("No key available for signing proof")
        val issuerMetadata = IssuerMetadataResolver(httpClient)
            .resolveCredentialIssuerMetadata(request.issuerUrl.toString()).metadata
        val configuration = issuerMetadata.credentialConfigurationsSupported[request.credentialConfigurationId]
            ?: error(
                "Unknown credential configuration '${request.credentialConfigurationId}' " +
                        "for issuer '${issuerMetadata.credentialIssuer}'"
            )
        val acceptedAlgorithms = supportedJwtProofAlgorithms(configuration.proofTypesSupported)
            ?: error(
                "Credential configuration '${request.credentialConfigurationId}' " +
                        "does not advertise JWT proof types"
            )
        val preferJwkBinding = shouldPreferJwkBinding(configuration.cryptographicBindingMethodsSupported)
        val proofs = buildJwtProof(
            proofBuilder = JwtProofBuilder(),
            keyMaterial = keyMaterial,
            audience = issuerMetadata.credentialIssuer,
            nonce = request.nonce,
            did = request.did?.takeUnless { preferJwkBinding },
            acceptedAlgorithms = acceptedAlgorithms,
        )
        return SignProofResult(proofJwt = proofs.jwt?.firstOrNull() ?: error("Proof signing produced no JWT"))
    }

    suspend fun fetchCredential(request: FetchCredentialRequest): FetchCredentialResult =
        fetchCredential(request, httpClient)

    internal suspend fun fetchCredential(
        request: FetchCredentialRequest,
        httpClient: HttpClient,
    ): FetchCredentialResult {
        val credentialResponse = requestCredential(request, httpClient)
        val rawCredentials = credentialResponse.credentials
            ?.map { it.credential.let { c -> if (c is JsonPrimitive) c.content else c.toString() } }
            ?: error("Credential response contained no credentials")
        return FetchCredentialResult(rawCredentials = rawCredentials)
    }

    private suspend fun requestCredential(
        request: FetchCredentialRequest,
        httpClient: HttpClient,
        dpop: DpopRequestContext? = null,
    ): CredentialResponse {
        // Build JSON manually to avoid Proofs serialization issue
        val credentialRequestJson = buildJsonObject {
            put("credential_configuration_id", request.credentialConfigurationId)
            request.proofJwt?.let { jwt ->
                putJsonObject("proofs") {
                    put("jwt", buildJsonArray { add(JsonPrimitive(jwt)) })
                }
            }
        }
        val endpoint = request.credentialEndpoint.toString()
        val scheme = if (dpop != null) "DPoP" else "Bearer"
        var dpopNonce: String? = null

        repeat(DPOP_NONCE_ATTEMPTS) { attempt ->
            val proof = dpop?.let {
                buildDpopProof(
                    keyMaterial = it.keyMaterial,
                    algorithms = it.algorithms,
                    endpoint = endpoint,
                    accessToken = request.accessToken,
                    nonce = dpopNonce,
                )
            }
            val response = postFollowingRedirects(httpClient, endpoint) {
                header(HttpHeaders.Authorization, "$scheme ${request.accessToken}")
                proof?.let { header(DPOP_HEADER, it) }
                contentType(ContentType.Application.Json)
                setBody(credentialRequestJson.toString())
            }
            if (response.status.isSuccess()) return response.body()

            // Read the DPoP signals before the body: oauthErrorCode() consumes it.
            val suppliedNonce = response.headers[DPOP_NONCE_HEADER]
            val oauthError = response.oauthErrorCode()
            if (
                attempt == 0 &&
                proof != null &&
                oauthError == USE_DPOP_NONCE &&
                !suppliedNonce.isNullOrBlank()
            ) {
                log.debug { "Credential endpoint demanded a DPoP nonce; retrying once with it" }
                dpopNonce = suppliedNonce
                return@repeat
            }

            // try-catch rather than runCatching: the body read suspends, and runCatching would
            // swallow CancellationException.
            val credentialError = try {
                lenientJson.decodeFromString<CredentialError>(response.bodyAsText())
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            throw CredentialEndpointException(
                statusCode = response.status.value,
                credentialError = credentialError,
            )
        }
        error("DPoP nonce retry exhausted for the credential endpoint")
    }

    /**
     * Fetches a credential with a freshly generated proof, retrying once only when the issuer
     * explicitly rejects that proof with the OID4VCI `invalid_nonce` error.
     *
     * Isolated fetch callers deliberately do not use this helper: they own the separate
     * request-nonce and sign-proof steps and therefore must handle [CredentialEndpointException]
     * themselves.
     */
    internal suspend fun requestCredentialWithNonceRetry(
        request: FetchCredentialRequest,
        nonceEndpoint: String?,
        httpClient: HttpClient,
        buildProof: (suspend (String?) -> String?)?,
        onProofGenerated: suspend () -> Unit = {},
        dpop: DpopRequestContext? = null,
    ): CredentialResponse {
        suspend fun fetchWithFreshProof(): CredentialResponse {
            val proofJwt = buildProof?.invoke(requestProofNonce(httpClient, nonceEndpoint))
            onProofGenerated()
            return requestCredential(request.copy(proofJwt = proofJwt), httpClient, dpop)
        }

        return try {
            fetchWithFreshProof()
        } catch (error: CredentialEndpointException) {
            if (!error.isInvalidNonce || buildProof == null || nonceEndpoint == null) {
                throw error
            }
            log.info { "Credential issuer rejected the proof nonce; obtaining a fresh nonce and retrying once" }
            fetchWithFreshProof()
        }
    }

    /**
     * Fetches credentials and applies [FetchCredentialRequest.storeInWallet] consistently for
     * every server adapter. Use the stateless overload when no wallet is available.
     *
     * When [FetchCredentialRequest.storeInWallet] is true, pass
     * [FetchCredentialRequest.credentialIssuerBaseUrl] so issuer display metadata and labels
     * are persisted like the full receive path.
     */
    suspend fun fetchCredential(
        wallet: Wallet,
        request: FetchCredentialRequest,
        httpClient: HttpClient = defaultHttpClient(),
        /** Called with the exact response batch size before any credential of that batch is persisted. */
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
    ): FetchCredentialResult =
        fetchCredential(request, httpClient).also { result ->
            if (request.storeInWallet) {
                if (request.credentialIssuerBaseUrl.isNullOrBlank() && request.metadata == null && request.label == null) {
                    log.warn {
                        "storeInWallet=true without credentialIssuerBaseUrl/metadata/label; " +
                                "issuer display metadata and labels will not be persisted"
                    }
                }
                val storage = resolveCredentialStorageContext(
                    credentialIssuerBaseUrl = request.credentialIssuerBaseUrl,
                    credentialConfigurationId = request.credentialConfigurationId,
                    requestMetadata = request.metadata,
                    labelOverride = request.label,
                )
                if (result.rawCredentials.isNotEmpty()) beforeCredentialsStored(result.rawCredentials.size)
                result.rawCredentials.forEach { raw ->
                    onCredentialStored(
                        wallet.parseAndStore(
                            rawCredential = raw,
                            label = storage.label,
                            metadata = storage.metadata,
                        )
                    )
                }
            }
        }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Builds a [ClientConfiguration] from the common clientId/redirectUri pair.
     * Extracted to eliminate four identical constructions across issuance functions.
     */
    /**
     * Perform a pushed authorization request (RFC 9126) and return the browser URL for it.
     *
     * The resulting authorization request carries **only** `client_id` and `request_uri`. Anything
     * else is a finding: RFC 9126 Section 4 and FAPI 2.0 Security Profile Section 5.3.3.2 both
     * require it, on the grounds that duplicated parameters can leak into browser history and logs.
     *
     * [keyMaterial] is what makes this possible at all - the endpoint is client authenticated. When it
     * is absent the request is still pushed, but unauthenticated, which an authorization server
     * demanding `private_key_jwt` will reject; callers that have a wallet should use the
     * [generateAuthorizationUrl] overload that takes one.
     */
    private suspend fun pushAuthorizationRequest(
        request: GenerateAuthorizationUrlRequest,
        offer: CredentialOffer,
        issuerMetadata: CredentialIssuerMetadata,
        asMetadata: AuthorizationServerMetadata,
        authorizationEndpoint: String,
        authBuilder: AuthorizationRequestBuilder,
        credentialConfigurationId: String,
        parEndpoint: String,
        keyMaterial: WalletKeyStoreEntry?,
        attestationAssembler: ClientAttestationAssembler?,
    ): GenerateAuthorizationUrlResult {
        // Binds the eventual access token to this wallet's key at authorization time (RFC 9449
        // Section 10). Only offered when the key can actually sign an advertised algorithm.
        val dpopJkt = keyMaterial
            ?.takeIf { usableDpopAlgorithms(asMetadata, it) != null }
            ?.jwkThumbprint()

        val pushed = authBuilder.buildPushedAuthorizationRequestStateForCredentialConfigurations(
            credentialConfigurationIds = listOf(credentialConfigurationId),
            issuerState = offer.grants?.authorizationCode?.issuerState,
            usePKCE = request.usePkce,
            metadata = asMetadata,
            redirectUri = request.redirectUri.toString(),
            dpopJkt = dpopJkt,
            credentialIssuerLocations = issuerMetadata.authorizationDetailLocations(),
            scope = if (request.useScope) {
                issuerMetadata.requireCredentialScope(credentialConfigurationId)
            } else {
                null
            },
        )

        // A pushed authorization request authenticates the client exactly as the token request does
        // (RFC 9126 Section 2), so it needs the same credential. Under HAIP that is the only one on
        // offer: the authorization server advertises attest_jwt_client_auth alone and requires PAR, so
        // without this the flow cannot authenticate at all and the suite answers 401.
        val attestationHeaders = buildClientAttestationHeaders(
            asMetadata = asMetadata,
            clientId = request.clientId,
            attestationAssembler = attestationAssembler,
            resolveInstanceKey = { keyMaterial?.crypto2AttestationKey() },
        )

        val response = PushedAuthorizationRequestExecutor.execute(
            httpClient = httpClient,
            parEndpoint = parEndpoint,
            parameters = pushed.parameters,
            // Mutually exclusive with the attestation headers: presenting two client credentials is
            // what "token_endpoint_auth_methods_supported: [attest_jwt_client_auth]" excludes.
            clientAssertionFactory = keyMaterial
                ?.takeIf { attestationHeaders == null }
                ?.let { material ->
                    clientAssertionFactory(
                        asMetadata = asMetadata,
                        clientId = request.clientId,
                        keyMaterial = material,
                    )
                },
            attestationHeaders = attestationHeaders,
        )

        return GenerateAuthorizationUrlResult(
            authorizationUrl = Url(
                URLBuilder(authorizationEndpoint).apply {
                    parameters.append("client_id", request.clientId)
                    parameters.append("request_uri", response.requestUri)
                }.buildString()
            ),
            state = pushed.state,
            codeVerifier = pushed.pkceData?.codeVerifier,
            credentialConfigurationId = credentialConfigurationId,
            credentialIssuerBaseUrl = offer.credentialIssuer,
            nonceEndpoint = issuerMetadata.nonceEndpoint?.let { Url(it) },
        )
    }

    /**
     * `locations` to put in each `openid_credential` authorization detail, or `null` when it may be
     * omitted.
     *
     * OID4VCI 1.0 Section 5.1.1 makes it mandatory once the Credential Issuer advertises
     * `authorization_servers`, because a single authorization server can serve several issuers and the
     * grant would otherwise be ambiguous. The value is the Credential Issuer Identifier itself.
     */
    /**
     * The `scope` the issuer publishes for [credentialConfigurationId].
     *
     * Fails loudly rather than silently falling back to `authorization_details`: a caller that asked
     * for scope-based authorization against an issuer that publishes no scope has a configuration
     * problem, and quietly sending something else would surface later as an opaque authorization
     * error.
     */
    private fun CredentialIssuerMetadata.requireCredentialScope(credentialConfigurationId: String): String =
        requireNotNull(credentialConfigurationsSupported[credentialConfigurationId]?.scope) {
            "Credential configuration '$credentialConfigurationId' publishes no scope, so this " +
                    "credential cannot be requested by scope (OID4VCI 1.0 Section 5.1.2)"
        }

    private fun CredentialIssuerMetadata.authorizationDetailLocations(): List<String>? =
        authorizationServers?.takeIf { it.isNotEmpty() }?.let { listOf(credentialIssuer) }

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
        metadata: JsonObject? = null,
    ): StoredCredential = parseAndStore(
        rawCredential = issuedCredential.credential.let {
            if (it is JsonPrimitive) it.content else it.toString()
        },
        label = label,
        metadata = metadata,
    )

    private suspend fun Wallet.parseAndStore(
        rawCredential: String,
        label: String? = null,
        metadata: JsonObject? = null,
    ): StoredCredential {
        val (_, parsed) = CredentialParser.detectAndParse(rawCredential)
        return StoredCredential(
            id = Uuid.random().toString(),
            credential = parsed,
            label = label,
            addedAt = Clock.System.now(),
            metadata = metadata,
        ).also { addCredential(it) }
    }

    /**
     * Merges issuer and credential configuration display into sidecar metadata.
     */
    private fun mergeIssuerDisplayMetadata(
        issuerMetadata: CredentialIssuerMetadata,
        requestMetadata: JsonObject? = null,
        credentialConfigurationId: String? = null,
    ): JsonObject? = storedCredentialDisplayMetadata(
        issuerMetadata = issuerMetadata,
        credentialConfigurationId = credentialConfigurationId,
        requestMetadata = requestMetadata,
    )

    private fun credentialConfigurationLabel(
        issuerMetadata: CredentialIssuerMetadata?,
        credentialConfigurationId: String?,
    ): String? =
        credentialConfigurationId
            ?.let { issuerMetadata?.credentialConfigurationsSupported?.get(it) }
            ?.credentialMetadata
            ?.display
            ?.firstOrNull()
            ?.name

    private data class CredentialStorageContext(
        val label: String? = null,
        val metadata: JsonObject? = null,
    )

    /**
     * Resolves label + sidecar metadata for any receive→store path.
     * When [credentialIssuerBaseUrl] is provided, issuer metadata is fetched so
     * `issuerDisplay` and configuration display labels match the full receive path.
     */
    private suspend fun resolveCredentialStorageContext(
        credentialIssuerBaseUrl: String?,
        credentialConfigurationId: String?,
        requestMetadata: JsonObject? = null,
        labelOverride: String? = null,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        issuerMetadata: CredentialIssuerMetadata? = null,
    ): CredentialStorageContext {
        val resolvedIssuerMetadata = issuerMetadata
            ?: credentialIssuerBaseUrl?.let {
                IssuerMetadataResolver(httpClient).resolveCredentialIssuerMetadata(it).metadata
            }
        return CredentialStorageContext(
            label = labelOverride
                ?: credentialConfigurationLabel(resolvedIssuerMetadata, credentialConfigurationId),
            metadata = resolvedIssuerMetadata
                ?.let {
                    mergeIssuerDisplayMetadata(
                        issuerMetadata = it,
                        requestMetadata = requestMetadata,
                        credentialConfigurationId = credentialConfigurationId,
                    )
                }
                ?: requestMetadata,
        )
    }

    /**
     * Builds a `private_key_jwt` client-assertion factory when the authorization server advertises
     * that method, or null otherwise.
     *
     * The wallet's own signing key is used as the client credential: a wallet acting as its own
     * OAuth client has no separate registered secret, and the authorization server holds the public
     * half of exactly this key from registration.
     *
     * `aud` is the authorization server's issuer identifier, which FAPI 2.0 §5.3.3.1 requires;
     * plain RFC 7523 §3 would also allow the token endpoint, but the issuer satisfies both.
     *
     * The returned factory signs a new assertion on every call so each carries a fresh `jti`.
     */
    private fun clientAssertionFactory(
        asMetadata: AuthorizationServerMetadata,
        clientId: String,
        keyMaterial: WalletKeyStoreEntry,
    ): ClientAssertionFactory? {
        val supported = asMetadata.tokenEndpointAuthMethodsSupported
            ?.contains(ClientAuthenticationMethods.PRIVATE_KEY_JWT) == true
        if (!supported) return null
        return {
            ClientAssertionBuilder().buildAssertion(
                key = keyMaterial.requireCrypto2SigningKey(),
                clientId = clientId,
                audience = asMetadata.issuer,
                supportedAlgorithms = asMetadata.tokenEndpointAuthSigningAlgValuesSupported,
            )
        }
    }

    /**
     * Client attestation headers for any request that authenticates this client to the authorization
     * server - the token request and the pushed authorization request alike. Both take the
     * authorization server's issuer as the PoP audience (OAuth 2.0 Attestation-Based Client
     * Authentication Section 5.2), so one builder serves both.
     */
    private suspend fun buildClientAttestationHeaders(
        asMetadata: AuthorizationServerMetadata,
        clientId: String,
        attestationAssembler: ClientAttestationAssembler?,
        resolveInstanceKey: suspend () -> Crypto2Key?,
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

    private suspend fun WalletKeyStoreEntry.crypto2AttestationKey(): Crypto2Key? =
        crypto2Key ?: legacyKey?.let { migrateLocalJwk(it) }?.let { crypto2Runtime.restore(it) }

    private fun AuthorizationServerMetadata.supportsAttestationBasedClientAuthentication(): Boolean =
        tokenEndpointAuthMethodsSupported?.contains(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH) == true

    private suspend fun resolveAuthorizationCodeAuthorizationServerMetadata(
        credentialIssuerBaseUrl: String,
        httpClient: HttpClient,
    ): AuthorizationServerMetadata {
        val metadataResolver = IssuerMetadataResolver(httpClient)
        val issuerMetadata = metadataResolver.resolveCredentialIssuerMetadata(credentialIssuerBaseUrl).metadata
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

    private suspend fun requestProofNonce(
        httpClient: HttpClient,
        issuerMetadata: CredentialIssuerMetadata,
    ): String? = requestProofNonce(httpClient, issuerMetadata.nonceEndpoint)

    private suspend fun requestProofNonce(
        httpClient: HttpClient,
        nonceEndpoint: String?,
    ): String? = nonceEndpoint?.let {
        NonceRequestBuilder(httpClient).requestNonce(it).cNonce
    }

    private fun shouldPreferJwkBinding(
        methods: Set<CryptographicBindingMethod>?
    ): Boolean {
        if (methods.isNullOrEmpty()) return false
        val supportsJwk = methods.any {
            it is CryptographicBindingMethod.Jwk || it is CryptographicBindingMethod.CoseKey
        }
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
    /**
     * Step 1 of the auth-code grant, with the wallet available so the request can be client
     * authenticated.
     *
     * Prefer this over the [wallet]-less overload: only this one can perform a pushed authorization
     * request (RFC 9126), because PAR needs a key to authenticate the client and to bind the DPoP
     * proof (`dpop_jkt`). An authorization server that advertises
     * `require_pushed_authorization_requests` rejects a plain authorization request outright.
     */
    suspend fun generateAuthorizationUrl(
        wallet: Wallet,
        request: GenerateAuthorizationUrlRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
    ): GenerateAuthorizationUrlResult = generateAuthorizationUrl(
        request = request,
        keyMaterial = wallet.resolveKeyMaterial(null, setOf(KeyUsage.SIGN)),
        attestationAssembler = attestationAssembler,
    )

    suspend fun generateAuthorizationUrl(request: GenerateAuthorizationUrlRequest): GenerateAuthorizationUrlResult =
        generateAuthorizationUrl(request = request, keyMaterial = null)

    private suspend fun generateAuthorizationUrl(
        request: GenerateAuthorizationUrlRequest,
        keyMaterial: WalletKeyStoreEntry?,
        attestationAssembler: ClientAttestationAssembler? = null,
    ): GenerateAuthorizationUrlResult {
        val offer = resolveOffer(request, httpClient)
        val issuerMetadata = IssuerMetadataResolver(httpClient).resolveCredentialIssuerMetadata(offer.credentialIssuer).metadata
        val asMetadata =
            IssuerMetadataResolver(httpClient).resolveAuthorizationServerMetadataWithFallback(issuerMetadata)

        val authorizationEndpoint = asMetadata.authorizationEndpoint
            ?: error("Authorization server has no authorization_endpoint")

        val clientConfig = clientConfig(request.clientId, request.redirectUri)
        val authBuilder = AuthorizationRequestBuilder(clientConfig)
        val credentialConfigurationId = offer.credentialConfigurationIds.first()

        // Engaged only when the authorization server *requires* PAR, not merely advertises an
        // endpoint. RFC 9126 makes PAR optional for the client, and pushing to an endpoint that does
        // not expect this client's authentication fails the whole flow - an issuer that advertises the
        // endpoint but does not require it answered our pushed request with HTTP 401 and broke
        // authorization-code issuance that previously worked. Same failure mode as engaging DPoP on
        // advertisement alone.
        val parEndpoint = asMetadata.pushedAuthorizationRequestEndpoint
            ?.takeIf { asMetadata.requirePushedAuthorizationRequests == true }
        require(asMetadata.requirePushedAuthorizationRequests != true || parEndpoint != null) {
            "Authorization server requires PAR but advertises no pushed_authorization_request_endpoint"
        }
        if (parEndpoint != null) {
            return pushAuthorizationRequest(
                request = request,
                offer = offer,
                issuerMetadata = issuerMetadata,
                asMetadata = asMetadata,
                authorizationEndpoint = authorizationEndpoint,
                authBuilder = authBuilder,
                credentialConfigurationId = credentialConfigurationId,
                parEndpoint = parEndpoint,
                keyMaterial = keyMaterial,
                attestationAssembler = attestationAssembler,
            )
        }

        val authRequest = authBuilder.buildAuthorizationRequest(
            authorizationEndpoint = authorizationEndpoint,
            credentialConfigurationId = credentialConfigurationId,
            issuerState = offer.grants?.authorizationCode?.issuerState,
            usePKCE = request.usePkce,
            metadata = asMetadata,
            scope = if (request.useScope) {
                issuerMetadata.requireCredentialScope(credentialConfigurationId)
            } else {
                null
            },
        )
        return GenerateAuthorizationUrlResult(
            authorizationUrl = Url(authRequest.url),
            state = authRequest.state,
            codeVerifier = authRequest.pkceData?.codeVerifier,
            credentialConfigurationId = credentialConfigurationId,
            credentialIssuerBaseUrl = offer.credentialIssuer,
            nonceEndpoint = issuerMetadata.nonceEndpoint?.let { Url(it) },
        )
    }

    /**
     * Step 2 of auth-code grant: exchange the authorization code for a token.
     * Wraps [TokenRequestBuilder.exchangeAuthorizationCode].
     */
    suspend fun exchangeCode(request: ExchangeCodeRequest): RequestTokenResult {
        val httpClient = httpClient
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
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        onAttestationObtained: suspend () -> Unit = {},
        /**
         * Key to client authenticate and sender constrain this exchange with, when the caller already
         * resolved one.
         *
         * Must be the same key the subsequent credential request proves possession of: a DPoP access
         * token is bound to the `jkt` of the key that requested it (RFC 9449 Section 6), so resolving
         * the wallet default here while the flow proceeds with a `keyReference`-selected key binds the
         * token to one key and proves possession of another - which the credential endpoint correctly
         * rejects as `invalid_token`.
         */
        keyMaterial: WalletKeyStoreEntry? = null,
        /** See [ReceiveAuthorizedCredentialRequest.useDpop]. Client authentication is unaffected. */
        useDpop: Boolean = false,
    ): RequestTokenResult {
        val credentialIssuerBaseUrl = request.credentialIssuerBaseUrl.takeIf { it.isNotBlank() }
            ?: error("credentialIssuerBaseUrl must be provided")
        val asMetadata = resolveAuthorizationCodeAuthorizationServerMetadata(credentialIssuerBaseUrl, httpClient)
        val tokenEndpoint = asMetadata.tokenEndpoint
            ?: error("Authorization server metadata contains no token_endpoint")
        val attestationHeaders = buildClientAttestationHeaders(
            asMetadata = asMetadata,
            clientId = request.clientId,
            attestationAssembler = attestationAssembler,
            resolveInstanceKey = {
                wallet.resolveKeyMaterial(null, setOf(KeyUsage.SIGN))?.crypto2AttestationKey()
            },
            onAttestationObtained = onAttestationObtained,
        )
        return exchangeCode(
            request = request,
            tokenEndpoint = tokenEndpoint,
            attestationHeaders = attestationHeaders,
            httpClient = httpClient,
            asMetadata = asMetadata,
            keyMaterial = keyMaterial ?: wallet.resolveKeyMaterial(null, setOf(KeyUsage.SIGN)),
            useDpop = useDpop,
        )
    }

    private suspend fun exchangeCode(
        request: ExchangeCodeRequest,
        tokenEndpoint: String,
        attestationHeaders: ClientAttestationHeaders?,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        /**
         * Authorization server metadata and wallet key, needed to client authenticate and sender
         * constrain the token request. Both optional so the key-less overload still works, but a
         * caller with a wallet should always supply them - see [exchangeCode].
         */
        asMetadata: AuthorizationServerMetadata? = null,
        keyMaterial: WalletKeyStoreEntry? = null,
        useDpop: Boolean = false,
    ): RequestTokenResult {
        val clientConfig = ClientConfiguration(
            clientId = request.clientId,
            redirectUris = listOf(request.redirectUri.toString())
        )

        // Mirrors the pre-authorized-code exchange. Attestation wins over private_key_jwt when both
        // are advertised, because it additionally attests the wallet instance rather than only proving
        // key control.
        val clientAssertionFactory = if (attestationHeaders == null && asMetadata != null && keyMaterial != null) {
            clientAssertionFactory(asMetadata = asMetadata, clientId = request.clientId, keyMaterial = keyMaterial)
        } else {
            null
        }

        // Sender constraining (RFC 9449), engaged only when the key can sign an advertised algorithm;
        // DPoP is optional for the wallet, so an unusable key must fall back to Bearer, not fail.
        val senderConstraining = if (useDpop && asMetadata != null && keyMaterial != null) {
            usableDpopAlgorithms(asMetadata, keyMaterial)?.let { algorithms -> keyMaterial to algorithms }
        } else {
            null
        }

        val tokenResponse = TokenRequestBuilder(clientConfig, httpClient).exchangeAuthorizationCode(
            tokenEndpoint = tokenEndpoint,
            code = request.code,
            codeVerifier = request.codeVerifier,
            additionalHeaders = request.tokenRequestHeaders,
            attestationHeaders = attestationHeaders,
            dpopProofFactory = senderConstraining?.let { (key, algorithms) ->
                { endpoint: String, nonce: String? ->
                    buildDpopProof(key, algorithms, endpoint, nonce = nonce)
                }
            },
            clientAssertionFactory = clientAssertionFactory,
        )
        return RequestTokenResult(
            accessToken = tokenResponse.access_token,
            expiresIn = tokenResponse.expires_in,
            tokenType = tokenResponse.token_type,
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
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        /** Called with the exact response batch size before any credential of that batch is persisted. */
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
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
                log.info { "Deferred credential not yet ready" }
                return@channelFlow
            }
            error("Deferred credential endpoint returned ${response.status}: $body")
        }

        val credentialResponse = response.body<CredentialResponse>()
        val rawCredentials = credentialResponse.credentials
            ?: error("Deferred credential response contained no credentials")

        val storage = resolveCredentialStorageContext(
            credentialIssuerBaseUrl = request.credentialIssuerBaseUrl,
            credentialConfigurationId = request.credentialConfigurationId,
            requestMetadata = request.metadata,
            labelOverride = request.label,
            httpClient = httpClient,
        )

        if (rawCredentials.isNotEmpty()) beforeCredentialsStored(rawCredentials.size)

        for (issuedCredential in rawCredentials) {
            val entry = wallet.parseAndStore(
                issuedCredential,
                label = storage.label,
                metadata = storage.metadata,
            )
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
     *
     * Key selection: [key] (explicit override) else [keyReference] via
     * `wallet.resolveKeyMaterial(keyReference, SIGN)`, else the wallet default signing key.
     * Pass store-backed key ids (e.g. Enterprise `keyReference.path`) as [keyReference] so
     * crypto2-only backends remain usable; do not convert referenced keys into [DirectSerializedKey].
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
        /** Inline key for proof-of-possession; takes precedence over [keyReference] and wallet stores. */
        key: DirectSerializedKey? = null,
        /** Store key id for proof-of-possession; ignored when [key] is provided. */
        keyReference: String? = null,
        /** Inline DID for holder binding; defaults to the wallet's default DID. */
        did: String? = null,
        /** Optional sidecar metadata merged with resolved issuer display when storing. */
        metadata: JsonObject? = null,
        /**
         * Sender constrain the token and credential requests with DPoP (RFC 9449); see
         * [ReceiveAuthorizedCredentialRequest.useDpop] for why this is opt-in rather than derived from
         * the authorization server's advertised algorithms.
         */
        useDpop: Boolean = false,
        /** Optional credential label override; otherwise derived from credential configuration display. */
        label: String? = null,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        /** Called with the exact response batch size before any credential of that batch is persisted. */
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
    ): Flow<StoredCredential> = channelFlow {
        val keyMaterial = key?.key?.let { WalletKeyStoreEntry(it.getKeyId(), it, null) }
            ?: wallet.resolveKeyMaterial(keyReference, setOf(KeyUsage.SIGN))
            ?: error("No key available for proof-of-possession")
        val holderDid = did ?: wallet.defaultDid()

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
            keyMaterial = keyMaterial,
            useDpop = useDpop,
        )
        onEvent(WalletSessionEvent.issuance_token_obtained)

        // Resolve issuer metadata again at continuation time and use only its advertised nonce endpoint.
        val issuerMetadata = IssuerMetadataResolver(httpClient)
            .resolveCredentialIssuerMetadata(credentialIssuerBaseUrl).metadata
        nonceEndpoint?.let { expected ->
            require(expected == issuerMetadata.nonceEndpoint) {
                "Provided nonce endpoint does not match credential issuer metadata"
            }
        }
        val proofBuilder = JwtProofBuilder()
        val credentialConfiguration = issuerMetadata.credentialConfigurationsSupported[credentialConfigurationId]
        // The proof must use an algorithm the issuer advertises for this configuration.
        val jwtProofAlgorithms = supportedJwtProofAlgorithms(credentialConfiguration?.proofTypesSupported)
        val credentialResponse = requestCredentialWithNonceRetry(
            request = FetchCredentialRequest(
                credentialEndpoint = credentialEndpoint,
                accessToken = tokenResult.accessToken,
                credentialConfigurationId = credentialConfigurationId,
                clientId = clientId,
            ),
            nonceEndpoint = issuerMetadata.nonceEndpoint,
            httpClient = httpClient,
            buildProof = { nonce ->
                val preferJwkBinding = shouldPreferJwkBinding(
                    credentialConfiguration?.cryptographicBindingMethodsSupported
                )
                buildJwtProof(
                    proofBuilder = proofBuilder,
                    keyMaterial = keyMaterial,
                    audience = credentialIssuerBaseUrl,
                    nonce = nonce,
                    did = holderDid?.takeUnless { preferJwkBinding },
                    acceptedAlgorithms = jwtProofAlgorithms,
                ).jwt?.firstOrNull()
            },
            onProofGenerated = { onEvent(WalletSessionEvent.issuance_proof_signed) },
            // RFC 9449 Section 7.1: a DPoP-typed access token has to be presented with a fresh
            // per-request proof at the credential endpoint too, not only at the token endpoint. The
            // pre-authorized-code flow already did this; omitting it here meant the issuer answered a
            // successfully DPoP-bound token exchange with "Couldn't find DPoP Proof header".
            dpop = if (!useDpop) null else dpopAlgorithmsForToken(
                tokenType = tokenResult.tokenType ?: "Bearer",
                advertisedAlgorithms = usableDpopAlgorithms(
                    resolveAuthorizationCodeAuthorizationServerMetadata(credentialIssuerBaseUrl, httpClient),
                    keyMaterial,
                ),
            )?.let { algorithms -> DpopRequestContext(algorithms, keyMaterial) },
        )
        onEvent(WalletSessionEvent.issuance_credential_received)

        val rawCredentials = credentialResponse.credentials
            ?.map { it.credential.let { credential -> if (credential is JsonPrimitive) credential.content else credential.toString() } }
            ?: error("Credential response contained no credentials")

        val storage = resolveCredentialStorageContext(
            credentialIssuerBaseUrl = credentialIssuerBaseUrl,
            credentialConfigurationId = credentialConfigurationId,
            requestMetadata = metadata,
            labelOverride = label,
            httpClient = httpClient,
            issuerMetadata = issuerMetadata,
        )

        if (rawCredentials.isNotEmpty()) beforeCredentialsStored(rawCredentials.size)

        for (rawString in rawCredentials) {
            val entry = wallet.parseAndStore(
                rawCredential = rawString,
                label = storage.label,
                metadata = storage.metadata,
            )
            onCredentialStored(entry)
            onEvent(WalletSessionEvent.issuance_credential_stored)
            send(entry)
        }
        onEvent(WalletSessionEvent.issuance_completed)
    }

    /**
     * Collects [receiveCredentialAuthCodeFlow] into a result, so the authorization-code grant has the
     * same single-call shape over HTTP that [receiveCredential] gives the pre-authorized code grant.
     *
     * Deferred issuance is not reported here: [receiveCredentialAuthCodeFlow] does not handle a `202`
     * response, so a deferring issuer surfaces through the credential endpoint error instead. Poll
     * such credentials with [pollDeferredFlow].
     */
    suspend fun receiveCredentialAuthCode(
        wallet: Wallet,
        request: ReceiveAuthorizedCredentialRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
    ): ReceiveCredentialResult {
        val ids = mutableListOf<String>()
        receiveCredentialAuthCodeFlow(
            wallet = wallet,
            code = request.code,
            codeVerifier = request.codeVerifier,
            credentialIssuerBaseUrl = request.credentialIssuer,
            credentialEndpoint = request.credentialEndpoint,
            credentialConfigurationId = request.credentialConfigurationId,
            useDpop = request.useDpop,
            nonceEndpoint = request.nonceEndpoint?.toString(),
            clientId = request.clientId,
            redirectUri = request.redirectUri,
            key = request.key,
            keyReference = request.keyId,
            did = request.did,
            metadata = request.metadata,
            label = request.label,
            attestationAssembler = attestationAssembler,
            onEvent = onEvent,
            httpClient = httpClient,
        ).collect { ids += it.id }
        return ReceiveCredentialResult(credentialIds = ids)
    }

    /**
     * Builds a JWT proof through the proof-builder contracts - no proof assembly happens here.
     *
     * [nonce] is null whenever the Credential Issuer advertises no Nonce Endpoint; the builder then
     * omits the `nonce` claim. The legacy branch is only reached for keys that cannot be represented
     * in crypto2 (remote v1 KMS keys, secp256k1) and goes away with the legacy key API.
     */
    private suspend fun buildJwtProof(
        proofBuilder: JwtProofBuilder,
        keyMaterial: WalletKeyStoreEntry,
        audience: String,
        nonce: String?,
        did: String?,
        acceptedAlgorithms: Set<String>? = null,
    ): Proofs {
        val binding = did
            ?.let { ProofKeyBinding.KeyId(DidService.resolveAuthenticationMethodId(it, keyMaterial.keyId)) }
            ?: ProofKeyBinding.Jwk
        val effectiveCrypto2Key = keyMaterial.crypto2Key
            ?: keyMaterial.legacyKey?.let { migrateLocalJwk(it) }?.let { crypto2Runtime.restore(it) }
        return effectiveCrypto2Key?.let {
            proofBuilder.buildProof(
                key = it,
                algorithm = it.selectJwsAlgorithm(acceptedAlgorithms),
                audience = audience,
                nonce = nonce,
                binding = binding,
            )
        } ?: run {
            val legacyKey = requireNotNull(keyMaterial.legacyKey) {
                "Key '${keyMaterial.keyId}' has no usable signing representation"
            }
            acceptedAlgorithms?.let {
                require(legacyKey.keyType.jwsAlg in it) {
                    "Issuer does not support proof algorithm ${legacyKey.keyType.jwsAlg}"
                }
            }
            proofBuilder.buildProof(
                key = legacyKey,
                audience = audience,
                nonce = nonce,
                binding = binding,
            )
        }
    }
}

internal fun supportedJwtProofAlgorithms(proofTypes: Map<String, ProofType>?): Set<String>? {
    if (proofTypes.isNullOrEmpty()) return null
    return requireNotNull(proofTypes["jwt"]) {
        "Issuer requires an unsupported proof type: ${proofTypes.keys}"
    }.proofSigningAlgValuesSupported
}
