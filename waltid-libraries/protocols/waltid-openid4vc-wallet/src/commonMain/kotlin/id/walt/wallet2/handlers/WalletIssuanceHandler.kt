@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.handlers

import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.MdocsCredential
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.selectJwsAlgorithm
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.did.dids.DidService
import id.walt.openid4vci.clientauth.ClientAuthenticationMethods
import id.walt.openid4vci.clientauth.attestation.ClientAttestationHeaders.CLIENT_ATTESTATION_CHALLENGE
import id.walt.openid4vci.errors.CredentialError
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.issuer.ProofTypeMetadata
import id.walt.openid4vci.metadata.issuer.KeyAttestationsRequired
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.openid4vci.offers.CredentialOffer
import id.walt.openid4vci.offers.TxCode
import id.walt.openid4vci.requests.authorization.AuthorizationDetail
import id.walt.openid4vci.requests.notification.NotificationEvent
import id.waltid.openid4vci.wallet.credential.CredentialIssuanceTarget
import id.waltid.openid4vci.wallet.credential.validateCredentialResponse
import id.waltid.openid4vci.wallet.credential.CredentialRequestBuilder
import id.walt.openid4vci.proofs.ProofType
import id.walt.openid4vci.proofs.Proofs
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
import id.waltid.openid4vci.wallet.attestation.WalletAttestationChallengeRequestBuilder
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
import id.waltid.openid4vci.wallet.token.ClientAttestationHeadersFactory
import id.waltid.openid4vci.wallet.token.DPoPProofFactory
import id.waltid.openid4vci.wallet.token.TokenRequestBuilder
import id.waltid.openid4vci.wallet.token.TokenResponseHeadersHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlin.jvm.JvmInline
import kotlin.time.Clock
import kotlin.uuid.Uuid
import id.walt.crypto2.keys.Key as Crypto2Key

private val log = KotlinLogging.logger {}

const val Wallet2DefaultClientId = "eudiw-abca"
private const val DEFAULT_CLIENT_ID = Wallet2DefaultClientId

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
     * and the wallet's stores. Storage requires wallet-resolved holder keys: use [keyId], the
     * wallet default, or explicit [credentials] with stored holder key IDs. An inline key cannot
     * be used as a stored credential's holder, even when its public material matches a wallet key.
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
    /** Omit to receive one instance of every selected/offered configuration. */
    val credentials: List<WalletCredentialSelection>? = null,
) : CredentialOfferSource {
    /** Retains the released constructor contract. */
    constructor(
        offerUrl: Url? = null,
        offerJson: JsonObject? = null,
        key: DirectSerializedKey? = null,
        keyId: String? = null,
        did: String? = null,
        txCode: String? = null,
        clientId: String = DEFAULT_CLIENT_ID,
        redirectUri: Url = Url("openid://"),
        tokenRequestHeaders: Map<String, String> = emptyMap(),
        metadata: JsonObject? = null,
    ) : this(
        offerUrl, offerJson, key, keyId, did, txCode, clientId, redirectUri, tokenRequestHeaders, metadata,
        credentials = null,
    )

    /** Retains the released copy contract and preserves the added fields. */
    fun copy(
        offerUrl: Url? = this.offerUrl,
        offerJson: JsonObject? = this.offerJson,
        key: DirectSerializedKey? = this.key,
        keyId: String? = this.keyId,
        did: String? = this.did,
        txCode: String? = this.txCode,
        clientId: String = this.clientId,
        redirectUri: Url = this.redirectUri,
        tokenRequestHeaders: Map<String, String> = this.tokenRequestHeaders,
        metadata: JsonObject? = this.metadata,
    ): ReceiveCredentialRequest = ReceiveCredentialRequest(
        offerUrl, offerJson, key, keyId, did, txCode, clientId, redirectUri, tokenRequestHeaders, metadata,
        credentials = this.credentials,
    )

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
    /** Omit to receive one instance of every selected/offered configuration. */
    val credentials: List<WalletCredentialSelection>? = null,
) {
    /** Retains the released constructor contract. */
    constructor(
        previewHandle: IssuancePreviewHandle,
        key: DirectSerializedKey? = null,
        keyId: String? = null,
        did: String? = null,
        txCode: String? = null,
        clientId: String = DEFAULT_CLIENT_ID,
        redirectUri: Url = Url("openid://"),
        tokenRequestHeaders: Map<String, String> = emptyMap(),
    ) : this(
        previewHandle, key, keyId, did, txCode, clientId, redirectUri, tokenRequestHeaders, credentials = null,
    )

    /** Retains the released copy contract and preserves the added fields. */
    fun copy(
        previewHandle: IssuancePreviewHandle = this.previewHandle,
        key: DirectSerializedKey? = this.key,
        keyId: String? = this.keyId,
        did: String? = this.did,
        txCode: String? = this.txCode,
        clientId: String = this.clientId,
        redirectUri: Url = this.redirectUri,
        tokenRequestHeaders: Map<String, String> = this.tokenRequestHeaders,
    ): ReceiveCredentialFromPreviewRequest = ReceiveCredentialFromPreviewRequest(
        previewHandle, key, keyId, did, txCode, clientId, redirectUri, tokenRequestHeaders,
        credentials = this.credentials,
    )
}


/** Result of a completed issuance flow. */
@Serializable
data class ReceiveCredentialResult(
    /** All credentials that were successfully issued and stored. */
    val credentialIds: List<String>,
    /**
     * Configuration IDs mapped to issuer transaction IDs for [WalletIssuanceHandler.pollDeferredFlow].
     */
    val deferredTransactionIds: Map<String, String> = emptyMap()
) {
    /** Fails with complete progress when the released map cannot represent this outcome. */
    constructor(result: ReceiveCredentialsResult) : this(result.credentialIds, result.releasedDeferredTransactions())
}

/** Result of a completed issuance flow. */
@Serializable
data class ReceiveCredentialsResult(
    /** All credentials that were successfully issued and stored. */
    val credentialIds: List<String>,
    /**
     * Deferred targets with the holder bindings required to resume each request.
     * Each entry retains its configuration, optional dataset identifier and transaction ID.
     * Resume the opaque wallet continuation when available.
     */
    val deferredCredentials: List<DeferredCredentialTransaction> = emptyList(),
    /** A stopped target; earlier stored/deferred results remain valid. Do not redeem the grant again. */
    val failure: CredentialIssuanceFailure? = null,
    /** Local-save recovery after a validated response; resume its handle instead of redeeming the grant again. */
    val storageOutcome: WalletIssuanceOutcome.Failed? = null,
) {
    init {
        require(storageOutcome == null || (failure != null && credentialIds.containsAll(storageOutcome.storedCredentialIds))) {
            "Storage recovery must preserve its failure and committed IDs in the full-flow result"
        }
    }

}

/** Retains the target and holder selections for one deferred Credential Request. */
@Serializable
data class DeferredCredentialTransaction(
    val credentialConfigurationId: String,
    val credentialIdentifier: String? = null,
    val transactionId: String,
    val holderBindings: List<CredentialHolderBinding>,
    val intervalSeconds: Long,
    val proofRequired: Boolean = false,
    /** Opaque wallet-scoped continuation. Access tokens and sender-key context stay in the wallet. */
    val deferredCredentialId: String? = null,
) {
    init {
        require(credentialConfigurationId.isNotBlank())
        require(credentialIdentifier == null || credentialIdentifier.isNotBlank())
        require(transactionId.isNotBlank())
        require(holderBindings.isNotEmpty())
        require(intervalSeconds > 0)
        require(deferredCredentialId == null || deferredCredentialId.isNotBlank())
    }
}

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
    /** Select configurations and let metadata determine the authorization parameters. */
    val credentialConfigurationIds: List<String>? = null,
    val authorizationDetails: List<AuthorizationDetail>? = null,
    val scope: String? = null,
) {
    /** Retains the released constructor contract. */
    constructor(
        tokenEndpoint: Url,
        preAuthorizedCode: String,
        credentialIssuer: String? = null,
        txCode: String? = null,
        clientId: String = DEFAULT_CLIENT_ID,
        redirectUri: Url = Url("openid://"),
        tokenRequestHeaders: Map<String, String> = emptyMap(),
        anonymousPreAuthorizedCode: Boolean = false,
    ) : this(
        tokenEndpoint, preAuthorizedCode, credentialIssuer, txCode, clientId, redirectUri, tokenRequestHeaders,
        anonymousPreAuthorizedCode, credentialConfigurationIds = null, authorizationDetails = null, scope = null,
    )

    /** Retains the released copy contract and preserves the added fields. */
    fun copy(
        tokenEndpoint: Url = this.tokenEndpoint,
        preAuthorizedCode: String = this.preAuthorizedCode,
        credentialIssuer: String? = this.credentialIssuer,
        txCode: String? = this.txCode,
        clientId: String = this.clientId,
        redirectUri: Url = this.redirectUri,
        tokenRequestHeaders: Map<String, String> = this.tokenRequestHeaders,
        anonymousPreAuthorizedCode: Boolean = this.anonymousPreAuthorizedCode,
    ): RequestTokenRequest = RequestTokenRequest(
        tokenEndpoint, preAuthorizedCode, credentialIssuer, txCode, clientId, redirectUri, tokenRequestHeaders,
        anonymousPreAuthorizedCode, credentialConfigurationIds = this.credentialConfigurationIds,
        authorizationDetails = this.authorizationDetails, scope = this.scope,
    )

    init {
        require(authorizationDetails == null || scope == null) { "Use authorizationDetails or scope, not both" }
        if (credentialConfigurationIds != null) {
            require(credentialConfigurationIds.isNotEmpty() && !credentialIssuer.isNullOrBlank()) {
                "Automatic token authorization requires credentialIssuer and selected configurations"
            }
            require(authorizationDetails == null && scope == null) {
                "Do not combine selected configurations with explicit authorization parameters"
            }
        }
    }
}

/** Released token result; its successful serialized shape is fixed. */
@Serializable
data class RequestTokenResult(
    val accessToken: String,
    val expiresIn: Long? = null,
    /** OAuth token_type. Preserve it when presenting the token (RFC 9449 Section 7.1). */
    val tokenType: String? = null,
) {
    override fun toString(): String =
        "RequestTokenResult(accessToken=<redacted>, expiresIn=$expiresIn, tokenType=$tokenType)"
}

/** Token plus the authorization server's granted configurations, datasets and scopes. */
@Serializable
data class RequestTokenDetailedResult(
    val accessToken: String,
    val expiresIn: Long? = null,
    val tokenType: String? = null,
    val authorizationDetails: List<AuthorizationDetail>? = null,
    val scope: String? = null,
) {
    internal fun toReleasedResult(): RequestTokenResult = RequestTokenResult(accessToken, expiresIn, tokenType)

    override fun toString(): String =
        "RequestTokenDetailedResult(accessToken=<redacted>, expiresIn=$expiresIn, tokenType=$tokenType)"
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
    /**
     * OAuth `client_id` written as the proof `iss` claim for client-bound token requests.
     * Leave unset for anonymous pre-authorized access (OpenID4VCI 1.0 Appendix F.1).
     */
    val clientId: String? = null,
) {
    init {
        require(credentialConfigurationId.isNotBlank()) {
            "credentialConfigurationId must not be blank"
        }
        require(clientId == null || clientId.isNotBlank()) { "clientId cannot be blank" }
    }
}

@Serializable
data class SignProofResult(
    val proofJwt: String
)

/** Input for signing one proof for each accepted holder. */
@Serializable
data class SignProofsRequest(
    val holderBindings: List<CredentialHolderBinding> = listOf(CredentialHolderBinding()),
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
    /**
     * OAuth `client_id` written as the proof `iss` claim for client-bound token requests.
     * Leave unset for anonymous pre-authorized access (OpenID4VCI 1.0 Appendix F.1).
     */
    val clientId: String? = null,
) {
    init {
        require(credentialConfigurationId.isNotBlank()) {
            "credentialConfigurationId must not be blank"
        }
        require(clientId == null || clientId.isNotBlank()) { "clientId cannot be blank" }
    }
}

@Serializable(with = SignProofsResult.Companion::class)
data class SignProofsResult(val proofs: Proofs) {
    init {
        val jwt = proofs.jwt
        require(!jwt.isNullOrEmpty() && jwt.all { it.isNotBlank() } &&
                proofs.diVp == null && proofs.attestation == null) { "Expected a non-empty collection of JWT proofs" }
    }

    /** Keeps the released single-proof wire shape without adding a second internal proof representation. */
    companion object : KSerializer<SignProofsResult> {
        override val descriptor = SignProofsWire.serializer().descriptor

        override fun serialize(encoder: Encoder, value: SignProofsResult) {
            val single = value.proofs.jwt?.singleOrNull()
            val wire = if (single != null) SignProofsWire(proofJwt = single) else SignProofsWire(proofs = value.proofs)
            encoder.encodeSerializableValue(SignProofsWire.serializer(), wire)
        }

        override fun deserialize(decoder: Decoder): SignProofsResult {
            val wire = decoder.decodeSerializableValue(SignProofsWire.serializer())
            require((wire.proofJwt == null) != (wire.proofs == null)) { "Expected proofJwt or proofs, not both" }
            return SignProofsResult(wire.proofs ?: Proofs(jwt = listOf(requireNotNull(wire.proofJwt))))
        }
    }
}

@Serializable
@SerialName("SignProofsResult")
private data class SignProofsWire(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val proofJwt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val proofs: Proofs? = null,
)


@Serializable
data class FetchCredentialRequest(
    val credentialEndpoint: Url,
    val accessToken: String,
    val credentialConfigurationId: String,
    /** Existing single-proof REST input. Mutually exclusive with [proofs]. */
    val proofJwt: String? = null,
    val clientId: String = DEFAULT_CLIENT_ID,
    /**
     * When true, [WalletIssuanceHandler.fetchCredential] stores the fetched
     * credential(s) when called with a wallet. Defaults to false (stateless).
     *
     * When storing, pass [credentialIssuerBaseUrl] (and optionally [metadata]/[label])
     * so issuer display metadata and credential labels are persisted the same way as
     * the full pre-authorized receive path. [holderBindings] must identify the stored wallet keys
     * used for [proofs]; inline holder keys are rejected, and copies require distinct public keys.
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
    /**
     * Wallet key used for the proof that the issuer binds into an mdoc MSO DeviceKey. Required when
     * [storeInWallet] stores an mdoc; it must identify the exact key used to create [proofs].
     */
    val keyId: String? = null,
    val proofs: Proofs? = null,
    val credentialIdentifier: String? = null,
    val holderBindings: List<CredentialHolderBinding> = listOf(CredentialHolderBinding()),
    /** Token type returned by the authorization server. */
    val tokenType: String = "Bearer",
    /** Key that sender-constrains the access token, independent of credential holder bindings. */
    val dpopKeyId: String? = null,
) {
    /** Retains the released constructor contract. */
    constructor(
        credentialEndpoint: Url,
        accessToken: String,
        credentialConfigurationId: String,
        proofJwt: String? = null,
        clientId: String = DEFAULT_CLIENT_ID,
        storeInWallet: Boolean = false,
        credentialIssuerBaseUrl: String? = null,
        metadata: JsonObject? = null,
        label: String? = null,
        keyId: String? = null,
    ) : this(
        credentialEndpoint, accessToken, credentialConfigurationId, proofJwt, clientId, storeInWallet,
        credentialIssuerBaseUrl, metadata, label, keyId, proofs = null, credentialIdentifier = null,
        holderBindings = listOf(CredentialHolderBinding()), tokenType = "Bearer", dpopKeyId = null,
    )

    /** Retains the released copy contract and preserves the added fields. */
    fun copy(
        credentialEndpoint: Url = this.credentialEndpoint,
        accessToken: String = this.accessToken,
        credentialConfigurationId: String = this.credentialConfigurationId,
        proofJwt: String? = this.proofJwt,
        clientId: String = this.clientId,
        storeInWallet: Boolean = this.storeInWallet,
        credentialIssuerBaseUrl: String? = this.credentialIssuerBaseUrl,
        metadata: JsonObject? = this.metadata,
        label: String? = this.label,
        keyId: String? = this.keyId,
    ): FetchCredentialRequest = FetchCredentialRequest(
        credentialEndpoint, accessToken, credentialConfigurationId, proofJwt, clientId, storeInWallet,
        credentialIssuerBaseUrl, metadata, label, keyId, proofs = this.proofs,
        credentialIdentifier = this.credentialIdentifier, holderBindings = this.holderBindings,
        tokenType = this.tokenType, dpopKeyId = this.dpopKeyId,
    )

    init {
        require(proofs == null || proofJwt == null) { "Provide proofs or proofJwt, not both" }
        require(proofJwt == null || proofJwt.isNotBlank()) { "proofJwt cannot be blank" }
        require(!storeInWallet || (holderBindings.isNotEmpty() && holderBindings.none { it.key != null })) {
            "Wallet storage requires non-empty wallet-owned holder bindings"
        }
        require(proofs == null || (proofs.diVp == null && proofs.attestation == null &&
            proofs.jwt?.let { it.isNotEmpty() && it.none(String::isBlank) } == true)) { "Supply a non-empty collection of JWT proofs" }
    }

    val effectiveProofs: Proofs? get() = proofs ?: proofJwt?.let { Proofs(jwt = listOf(it)) }
}

/** Released isolated-fetch result. Detailed progress is available from [FetchCredentialsResult]. */
@Serializable
data class FetchCredentialResult(
    val rawCredentials: List<String>,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val notificationId: String? = null,
) {
    constructor(result: FetchCredentialsResult) : this(result.releasedRawCredentials(), result.notificationId)
}

/** Complete isolated-fetch progress, including remote deferral and local-save recovery. */
@Serializable
data class FetchCredentialsResult(
    val rawCredentials: List<String> = emptyList(),
    val deferredCredential: DeferredCredentialTransaction? = null,
    /** Local save result, including committed IDs and retained handles when storage is incomplete. */
    val storageOutcome: WalletIssuanceOutcome? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val notificationId: String? = null,
) {
    init {
        require(rawCredentials.isNotEmpty() xor (deferredCredential != null)) {
            "A fetch result must contain credentials or a deferred transaction"
        }
        require(deferredCredential == null || storageOutcome == null) {
            "A deferred response cannot have local storage progress"
        }
        require(storageOutcome == null || storageOutcome is WalletIssuanceOutcome.Stored ||
                storageOutcome is WalletIssuanceOutcome.Failed) {
            "Local storage must either complete or retain its failure"
        }
    }
}

/** The released fetch result cannot represent this progress; do not repeat the issuer request. */
class CredentialFetchException(val result: FetchCredentialsResult) : Exception(
    "Credential fetch requires continuation; inspect result instead of fetching again"
)

private fun FetchCredentialsResult.releasedRawCredentials(): List<String> {
    if (deferredCredential != null || storageOutcome is WalletIssuanceOutcome.Failed) {
        throw CredentialFetchException(this)
    }
    return rawCredentials
}

/**
 * Isolated rejection of a credential that was fetched with [FetchCredentialRequest.storeInWallet] left false.
 *
 * Pass [notificationId] and [accessToken] from [FetchCredentialResult] / the token response. Supply
 * either [notificationEndpoint] or [credentialIssuerBaseUrl] so the wallet can resolve the issuer's
 * OpenID4VCI notification endpoint. This isolated step is Bearer-only, matching isolated fetch.
 */
@Serializable
data class RejectIssuedCredentialRequest(
    val notificationId: String,
    val accessToken: String,
    val credentialIssuerBaseUrl: String? = null,
    val notificationEndpoint: String? = null,
    val eventDescription: String? = null,
) {
    init {
        require(notificationId.isNotBlank()) { "notificationId must not be blank" }
        require(accessToken.isNotBlank()) { "accessToken cannot be blank" }
        require(!notificationEndpoint.isNullOrBlank() || !credentialIssuerBaseUrl.isNullOrBlank()) {
            "Either notificationEndpoint or credentialIssuerBaseUrl must be provided"
        }
    }

    override fun toString(): String =
        "RejectIssuedCredentialRequest(notificationId=$notificationId, accessToken=<redacted>, " +
            "credentialIssuerBaseUrl=$credentialIssuerBaseUrl, notificationEndpoint=$notificationEndpoint, " +
            "eventDescription=$eventDescription)"
}

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


/** Completes authorization for an explicit collection of accepted credential selections. */
@Serializable
data class ReceiveAuthorizedCredentialsRequest(
    /** Targets accepted during authorization, with the holder bindings for each credential. */
    val credentials: List<WalletCredentialSelection>,
    /** Authorization code from the redirect callback. */
    val code: String,
    /** PKCE verifier returned by `authorization-url`; required whenever PKCE was used. */
    val codeVerifier: String? = null,
    /** Credential Issuer Identifier, used to re-resolve issuer and authorization server metadata. */
    val credentialIssuer: String,
    val credentialEndpoint: Url,
    /** Issuer nonce endpoint, when it advertises one; validated against issuer metadata. */
    val nonceEndpoint: Url? = null,
    val clientId: String = DEFAULT_CLIENT_ID,
    val redirectUri: Url = Url("openid://"),
    /** Explicit sender constraint; see [ReceiveAuthorizedCredentialRequest.useDpop]. */
    val useDpop: Boolean = false,

    /** Inline default proof key; storage requires explicit wallet-resolved holders in [credentials]. */
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
        require(credentials.isNotEmpty()) { "credentials must not be empty" }
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
    /**
     * Wallet key used for the original proof of possession. Required when the deferred response
     * stores an mdoc; it must identify the exact key used for the original credential request.
     */
    val keyId: String? = null,
    /** Copy from the deferred result to retain single-instance proof-binding validation. */
    val proofRequired: Boolean = false,
    val holderBindings: List<CredentialHolderBinding> = listOf(CredentialHolderBinding()),
    /** Token type returned by the authorization server. */
    val tokenType: String = "Bearer",
    /** Key that sender-constrains the access token, independent of credential holder bindings. */
    val dpopKeyId: String? = null,
    /** Dataset identifier from the original deferred result, when provided by the issuer. */
    val credentialIdentifier: String? = null,
) {
    init {
        require(holderBindings.isNotEmpty() && holderBindings.none { it.key != null }) {
            "Wallet storage requires non-empty wallet-owned holder bindings"
        }
    }

    /** Retains the released constructor contract. */
    constructor(
        deferredCredentialEndpoint: Url,
        transactionId: String,
        accessToken: String,
        credentialIssuerBaseUrl: String? = null,
        credentialConfigurationId: String? = null,
        metadata: JsonObject? = null,
        label: String? = null,
        keyId: String? = null,
    ) : this(
        deferredCredentialEndpoint, transactionId, accessToken, credentialIssuerBaseUrl, credentialConfigurationId,
        metadata, label, keyId, proofRequired = false, holderBindings = listOf(CredentialHolderBinding()),
        tokenType = "Bearer", dpopKeyId = null, credentialIdentifier = null,
    )

    /** Retains the released copy contract and preserves the added fields. */
    fun copy(
        deferredCredentialEndpoint: Url = this.deferredCredentialEndpoint,
        transactionId: String = this.transactionId,
        accessToken: String = this.accessToken,
        credentialIssuerBaseUrl: String? = this.credentialIssuerBaseUrl,
        credentialConfigurationId: String? = this.credentialConfigurationId,
        metadata: JsonObject? = this.metadata,
        label: String? = this.label,
        keyId: String? = this.keyId,
    ): PollDeferredRequest = PollDeferredRequest(
        deferredCredentialEndpoint, transactionId, accessToken, credentialIssuerBaseUrl, credentialConfigurationId,
        metadata, label, keyId, proofRequired = this.proofRequired, holderBindings = this.holderBindings,
        tokenType = this.tokenType, dpopKeyId = this.dpopKeyId, credentialIdentifier = this.credentialIdentifier,
    )
}


/** A successful deferred poll that requires waiting before another attempt. */
@Serializable
data class DeferredCredentialPending(val transactionId: String, val intervalSeconds: Long) {
    init {
        require(transactionId.isNotBlank())
        require(intervalSeconds > 0)
    }
}

// Draft-15 §9.3: preserve the released polling contract, including its default interval.
internal fun CredentialResponse.validateLegacyDeferredPendingResponse(transactionId: String): CredentialResponse =
    copy(transactionId = this.transactionId ?: transactionId, interval = interval ?: 5)
        .validateCredentialResponse(HttpStatusCode.Accepted.value, transactionId)

@Serializable
data class PollDeferredResult(
    val credentialIds: List<String>,
    val pending: DeferredCredentialPending? = null,
    /** Retained local-save progress after a storage failure; resumption must not poll the issuer again. */
    val storageOutcome: WalletIssuanceOutcome.Failed? = null,
) {
    init {
        require(pending == null || (credentialIds.isEmpty() && storageOutcome == null))
        require(storageOutcome == null || storageOutcome.storedCredentialIds == credentialIds)
    }
}

/** A streaming poll stopped after receipt; the outcome retains committed IDs and local-save handles. */
class CredentialStorageException(val outcome: WalletIssuanceOutcome.Failed) : Exception(outcome.error.message)

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
data class GenerateBatchAuthorizationUrlRequest(
    /** Wallet-initiated issuance without an offer. Mutually exclusive with offerUrl/offerJson. */
    val credentialIssuer: String? = null,
    val credentialConfigurationIds: List<String>? = null,
    override val offerUrl: Url? = null,
    override val offerJson: JsonObject? = null,
    val clientId: String = DEFAULT_CLIENT_ID,
    val redirectUri: Url = Url("openid://"),
    val usePkce: Boolean = true,
) : CredentialOfferSource {
    init {
        require(credentialConfigurationIds == null || (credentialConfigurationIds.isNotEmpty() &&
                credentialConfigurationIds.all(String::isNotBlank) &&
                credentialConfigurationIds.distinct().size == credentialConfigurationIds.size)) {
            "Select non-empty, distinct credential configuration IDs"
        }
        if (credentialIssuer == null) checkOfferSource() else {
            require(offerUrl == null && offerJson == null)
            require(credentialIssuer.isNotBlank() && !credentialConfigurationIds.isNullOrEmpty())
        }
    }
}

@Serializable
data class GenerateBatchAuthorizationUrlResult(
    val authorizationUrl: Url,
    val state: String,
    val codeVerifier: String? = null,
    val credentialConfigurationIds: List<String>,
    val credentialIssuerBaseUrl: String,
    val nonceEndpoint: Url? = null,
) {
    init {
        require(credentialConfigurationIds.isNotEmpty() && credentialConfigurationIds.all(String::isNotBlank) &&
                credentialConfigurationIds.distinct().size == credentialConfigurationIds.size)
    }
}

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
    ): Flow<StoredCredential> = receiveCredentialFlow(
        wallet = wallet,
        request = request,
        attestationAssembler = attestationAssembler,
        onEvent = onEvent,
        httpClient = httpClient,
        onDeferredTarget = { target -> onDeferredTransactionId(target.credentialConfigurationId, target.transactionId) },
        beforeCredentialsStored = beforeCredentialsStored,
        onCredentialStored = onCredentialStored,
        metadataTrustResolver = metadataTrustResolver,
    )

    /** Streams issuance with the complete target and holder context for each deferred request. */
    fun receiveCredentialFlow(
        wallet: Wallet,
        request: ReceiveCredentialRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        /**
         * Called whenever the issuer defers a credential.
         * Each deferred target identifies its configuration and optional dataset;
         * [DeferredCredentialTransaction.transactionId] should be stored and passed to [pollDeferredFlow] later.
         */
        onDeferredTarget: suspend (DeferredCredentialTransaction) -> Unit,
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
        onDeferredCredential = onDeferredTarget,
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
    ): Flow<StoredCredential> = receiveCredentialFlow(
        wallet = wallet,
        request = request,
        attestationAssembler = attestationAssembler,
        onEvent = onEvent,
        httpClient = httpClient,
        onDeferredTarget = { target -> onDeferredTransactionId(target.credentialConfigurationId, target.transactionId) },
        beforeCredentialsStored = beforeCredentialsStored,
        onCredentialStored = onCredentialStored,
    )

    /** Streams issuance with the complete target and holder context for each deferred request. */
    fun receiveCredentialFlow(
        wallet: Wallet,
        request: ReceiveCredentialFromPreviewRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        onDeferredTarget: suspend (DeferredCredentialTransaction) -> Unit,
        /** Called with the exact response batch size before any credential of that batch is persisted. */
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
    ): Flow<StoredCredential> = channelFlow {
        previewedOffers.useRetainingOnFailure(
            walletId = wallet.id,
            id = request.previewHandle.value,
        ) { resolvedOffer ->
            try {
                receiveCredentialFlowInternal(
                    wallet = wallet,
                    request = request.toReceiveCredentialRequest(resolvedOffer.source),
                    resolvedOffer = resolvedOffer,
                    attestationAssembler = attestationAssembler,
                    onEvent = onEvent,
                    httpClient = httpClient,
                    onDeferredCredential = onDeferredTarget,
                    beforeCredentialsStored = beforeCredentialsStored,
                    onCredentialStored = onCredentialStored,
                    metadataTrustResolver = null,
                ).collect(::send)
            } catch (error: CredentialIssuanceException) {
                previewedOffers.discard(wallet.id, request.previewHandle.value)
                throw error
            }
        }
    }

    private fun receiveCredentialFlowInternal(
        wallet: Wallet,
        request: ReceiveCredentialRequest,
        resolvedOffer: ResolvedIssuanceOffer?,
        attestationAssembler: ClientAttestationAssembler?,
        onEvent: suspend (WalletSessionEvent) -> Unit,
        httpClient: HttpClient,
        onDeferredCredential: suspend (DeferredCredentialTransaction) -> Unit,
        beforeCredentialsStored: suspend (Int) -> Unit,
        onCredentialStored: suspend (StoredCredential) -> Unit,
        metadataTrustResolver: CredentialIssuerMetadataTrustResolver?,
    ): Flow<StoredCredential> = channelFlow {
        wallet.issuanceSessions(httpClient).ensureOpen()
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
        onEvent.emitSafely(WalletSessionEvent.issuance_offer_resolved)

        val selections = wallet.resolveCredentialSelections(request.credentials, offer.credentialConfigurationIds,
            issuerMetadata, keyMaterial, did)
        log.debug { "Offer contains ${offeredCredentials.size} credential(s)" }

        // 3. Pre-authorized code grant only (auth-code handled by separate flow)
        val preAuthGrant = offer.grants?.preAuthorizedCode
            ?: error("Only pre-authorized code grant is currently supported. Offer grants: ${offer.grants}")
        log.trace { "Using pre-authorized code grant" }

        // 4. Request token
        val tokenEndpoint = asMetadata.tokenEndpoint
            ?: error("Authorization server metadata contains no token_endpoint")
        log.trace { "Requesting token from $tokenEndpoint" }

        val tokenAttestation = tokenClientAttestation(
            asMetadata = asMetadata,
            clientId = request.clientId,
            attestationAssembler = attestationAssembler,
            resolveInstanceKey = { keyMaterial.crypto2AttestationKey() },
            onAttestationObtained = { onEvent.emitSafely(WalletSessionEvent.issuance_attestation_obtained) },
            httpClient = httpClient,
        )

        // private_key_jwt (RFC 7523). Engaged from authorization server metadata for the same reason
        // attestation is: the wallet cannot know out of band which method a given issuer requires.
        // Attestation wins when both are advertised, because it additionally attests the wallet
        // instance rather than only proving key control.
        val clientAssertionFactory = clientAssertionFactory(
            asMetadata = asMetadata,
            clientId = request.clientId,
            keyMaterial = keyMaterial,
        ).takeIf { tokenAttestation == null }

        val anonymousPreAuthorizedCode =
            asMetadata.preAuthorizedGrantAnonymousAccessSupported == true &&
                    request.tokenRequestHeaders.isEmpty() &&
                    tokenAttestation == null &&
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
            additionalParameters = CredentialRequestBuilder.preAuthorizedTokenParameters(
                issuerMetadata, selections.map { it.selection.credentialConfigurationId }, asMetadata,
            ),
            additionalHeaders = request.tokenRequestHeaders,
            anonymous = anonymousPreAuthorizedCode,
            dpopProofFactory = dpopAlgorithms?.let { algorithms ->
                { endpoint: String, nonce: String? ->
                    buildDpopProof(keyMaterial, algorithms, endpoint, nonce = nonce)
                }
            },
            clientAssertionFactory = clientAssertionFactory,
            onResponseHeaders = tokenAttestation?.onResponseHeaders ?: {},
            attestationHeadersFactory = tokenAttestation?.factory,
        )
        log.trace { "Token obtained" }
        onEvent.emitSafely(WalletSessionEvent.issuance_token_obtained)

        executeCredentialTargets(
            wallet, httpClient, issuerMetadata,
            grantedCredentialSelections(issuerMetadata, selections, tokenResponse.authorization_details, tokenResponse.scope),
            CredentialIssuanceAccess(tokenResponse.access_token, keyMaterial,
                dpopAlgorithmsForToken(tokenResponse.token_type, dpopAlgorithms),
                request.clientId.takeUnless { anonymousPreAuthorizedCode }),
            sessions = wallet.issuanceSessions(httpClient), requestMetadata = requestMetadata, onEvent = onEvent,
            beforeCredentialsStored = beforeCredentialsStored,
            onCredentialStored = { entry ->
                onCredentialStored(entry)
                onEvent.emitSafely(WalletSessionEvent.issuance_credential_stored)
                send(entry)
            },
            onDeferredCredential = onDeferredCredential,
        )

        onEvent.emitSafely(WalletSessionEvent.issuance_completed)
    }

    /**
     * Retains the released transaction-map result. Use [receiveCredentials] for detailed progress.
     * Partial failures or repeated deferred configurations throw [CredentialReceiveException] with that progress.
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
    ): ReceiveCredentialResult = receiveCredentials(
        wallet = wallet,
        request = request,
        attestationAssembler = attestationAssembler,
        onEvent = onEvent,
        httpClient = httpClient,
        beforeCredentialsStored = beforeCredentialsStored,
        onCredentialStored = onCredentialStored,
        metadataTrustResolver = metadataTrustResolver,
    ).let(::ReceiveCredentialResult)

    suspend fun receiveCredential(
        wallet: Wallet,
        request: ReceiveCredentialFromPreviewRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        /** Called with the exact response batch size before any credential of that batch is persisted. */
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
    ): ReceiveCredentialResult = receiveCredentials(
        wallet = wallet,
        request = request,
        attestationAssembler = attestationAssembler,
        onEvent = onEvent,
        httpClient = httpClient,
        beforeCredentialsStored = beforeCredentialsStored,
        onCredentialStored = onCredentialStored,
    ).let(::ReceiveCredentialResult)

    /** Collects stored IDs, every deferred target and any stopped-target/storage progress. */
    suspend fun receiveCredentials(
        wallet: Wallet,
        request: ReceiveCredentialRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        /** Called with the exact response batch size before any credential of that batch is persisted. */
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
        metadataTrustResolver: CredentialIssuerMetadataTrustResolver? = null,
    ): ReceiveCredentialsResult {
        val progress = IssuanceResultCollector(onCredentialStored)
        return progress.collect(receiveCredentialFlow(
            wallet = wallet,
            request = request,
            attestationAssembler = attestationAssembler,
            onEvent = onEvent,
            httpClient = httpClient,
            onDeferredTarget = progress::deferred,
            beforeCredentialsStored = beforeCredentialsStored,
            onCredentialStored = progress::stored,
            metadataTrustResolver = metadataTrustResolver,
        ))
    }

    /** Receives credentials using exactly the offer resolution selected by [request]. */
    suspend fun receiveCredentials(
        wallet: Wallet,
        request: ReceiveCredentialFromPreviewRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        /** Called with the exact response batch size before any credential of that batch is persisted. */
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
    ): ReceiveCredentialsResult {
        val progress = IssuanceResultCollector(onCredentialStored)
        return progress.collect(receiveCredentialFlow(
            wallet = wallet,
            request = request,
            attestationAssembler = attestationAssembler,
            onEvent = onEvent,
            httpClient = httpClient,
            onDeferredTarget = progress::deferred,
            beforeCredentialsStored = beforeCredentialsStored,
            onCredentialStored = progress::stored,
        ))
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
        credentials = credentials,
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
        requestTokenDetailed(request).toReleasedResult()

    suspend fun requestToken(
        wallet: Wallet,
        request: RequestTokenRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        onAttestationObtained: suspend () -> Unit = {},
    ): RequestTokenResult = requestTokenDetailed(wallet, request, attestationAssembler, httpClient, onAttestationObtained).toReleasedResult()

    suspend fun requestTokenDetailed(request: RequestTokenRequest): RequestTokenDetailedResult =
        requestTokenDetailed(
            request = request,
            tokenAttestation = null,
            anonymousPreAuthorizedCode = request.anonymousPreAuthorizedCode,
        )

    suspend fun requestTokenDetailed(
        wallet: Wallet,
        request: RequestTokenRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        onAttestationObtained: suspend () -> Unit = {},
    ): RequestTokenDetailedResult {
        val credentialIssuer = request.credentialIssuer?.takeIf { it.isNotBlank() }
        val asMetadata = credentialIssuer?.let {
            val metadataResolver = IssuerMetadataResolver(httpClient)
            val issuerMetadata = metadataResolver.resolveCredentialIssuerMetadata(it).metadata
            metadataResolver.resolveAuthorizationServerMetadataWithFallback(issuerMetadata)
        }
        val tokenAttestation = asMetadata?.let {
            tokenClientAttestation(
                asMetadata = it,
                clientId = request.clientId,
                attestationAssembler = attestationAssembler,
                resolveInstanceKey = {
                    wallet.resolveKeyMaterial(null, setOf(KeyUsage.SIGN))?.crypto2AttestationKey()
                },
                onAttestationObtained = onAttestationObtained,
                httpClient = httpClient,
            )
        }
        val anonymousPreAuthorizedCode =
            request.anonymousPreAuthorizedCode ||
                    (asMetadata?.preAuthorizedGrantAnonymousAccessSupported == true &&
                            request.tokenRequestHeaders.isEmpty() &&
                            tokenAttestation == null)

        return requestTokenDetailed(
            request = request,
            tokenAttestation = tokenAttestation,
            anonymousPreAuthorizedCode = anonymousPreAuthorizedCode,
            httpClient = httpClient,
        )
    }

    private suspend fun requestTokenDetailed(
        request: RequestTokenRequest,
        tokenAttestation: TokenClientAttestation?,
        anonymousPreAuthorizedCode: Boolean,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
    ): RequestTokenDetailedResult {
        val clientConfig = ClientConfiguration(
            clientId = request.clientId,
            redirectUris = listOf(request.redirectUri.toString())
        )
        val authorizationParameters = request.credentialConfigurationIds?.let { configurationIds ->
            val resolver = IssuerMetadataResolver(httpClient)
            val issuer = resolver.resolveCredentialIssuerMetadata(requireNotNull(request.credentialIssuer)).metadata
            val authorizationServer = resolver.resolveAuthorizationServerMetadataWithFallback(issuer)
            require(request.tokenEndpoint.toString() == authorizationServer.tokenEndpoint) {
                "Token endpoint does not match issuer metadata"
            }
            CredentialRequestBuilder.preAuthorizedTokenParameters(
                issuer, configurationIds, authorizationServer,
            )
        } ?: buildMap {
            request.authorizationDetails?.let { put("authorization_details", lenientJson.encodeToString(it)) }
            request.scope?.let { put("scope", it) }
        }
        val tokenResponse = TokenRequestBuilder(clientConfig, httpClient).exchangePreAuthorizedCode(
            tokenEndpoint = request.tokenEndpoint.toString(),
            preAuthorizedCode = request.preAuthorizedCode,
            txCode = request.txCode,
            additionalParameters = authorizationParameters,
            additionalHeaders = request.tokenRequestHeaders,
            anonymous = anonymousPreAuthorizedCode,
            dpopProofFactory = null,
            clientAssertionFactory = null,
            onResponseHeaders = tokenAttestation?.onResponseHeaders ?: {},
            attestationHeadersFactory = tokenAttestation?.factory,
        )
        return RequestTokenDetailedResult(
            accessToken = tokenResponse.access_token,
            expiresIn = tokenResponse.expires_in,
            tokenType = tokenResponse.token_type,
            authorizationDetails = tokenResponse.authorization_details,
            scope = tokenResponse.scope,
        )
    }

    suspend fun requestNonce(
        request: RequestNonceRequest,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
    ): RequestNonceResult {
        val issuerMetadata = IssuerMetadataResolver(httpClient)
            .resolveCredentialIssuerMetadata(request.credentialIssuer.toString()).metadata
        return RequestNonceResult(
            nonce = requestProofNonce(httpClient, issuerMetadata.nonceEndpoint)
        )
    }

    /** Signs one proof, preserving the released single-proof SDK contract. */
    suspend fun signProof(
        wallet: Wallet,
        request: SignProofRequest,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
    ): SignProofResult = SignProofResult(
        signProofs(wallet, SignProofsRequest(
            issuerUrl = request.issuerUrl, credentialConfigurationId = request.credentialConfigurationId,
            nonce = request.nonce, key = request.key, keyId = request.keyId, did = request.did, clientId = request.clientId,
        ), httpClient).proofs.jwt!!.single()
    )

    /** Signs the accepted holders through the shared proof preflight and builder. */
    suspend fun signProofs(
        wallet: Wallet,
        request: SignProofsRequest,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
    ): SignProofsResult {
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
        val bindings = wallet.resolveProofHolderBindings(request.holderBindings, issuerMetadata, configuration, keyMaterial, request.did)
        return SignProofsResult(buildProofCollection(wallet, bindings, configuration, issuerMetadata.credentialIssuer, request.nonce, request.clientId))
    }

    internal suspend fun buildProofCollection(
        wallet: Wallet,
        bindings: List<ResolvedCredentialHolderBinding>,
        configuration: id.walt.openid4vci.metadata.issuer.CredentialConfiguration,
        issuer: String,
        nonce: String?,
        clientId: String?,
    ): Proofs {
        val algorithms = requireNotNull(supportedJwtProofAlgorithms(configuration.proofTypesSupported, wallet.attachedKeyAttestationProvider() != null)) {
            "Credential configuration does not support JWT proofs"
        }
        return Proofs(jwt = bindings.map { binding ->
            buildJwtProof(JwtProofBuilder(), binding.material, issuer, nonce,
                binding.did,
                algorithms,
                clientId = clientId,
                keyAttestationsRequired = configuration.proofTypesSupported?.get("jwt")?.keyAttestationsRequired,
                keyAttestationProvider = wallet.attachedKeyAttestationProvider(),
            ).jwt!!.single()
        })
    }

    suspend fun fetchCredential(request: FetchCredentialRequest): FetchCredentialResult =
        FetchCredentialResult(fetchCredentials(request))

    /** Fetches complete progress without projecting away deferral or storage recovery. */
    suspend fun fetchCredentials(request: FetchCredentialRequest): FetchCredentialsResult =
        fetchCredentials(request, httpClient)

    internal suspend fun fetchCredentials(
        request: FetchCredentialRequest,
        httpClient: HttpClient,
        dpop: DpopRequestContext? = null,
    ): FetchCredentialsResult {
        require(request.tokenType.equals(if (dpop == null) "Bearer" else "DPoP", ignoreCase = true)) {
            "A DPoP token requires its sender-constraining key and a wallet"
        }
        val requestedCount = request.effectiveProofs?.jwt?.size ?: 1
        if (requestedCount > 1 || request.credentialIssuerBaseUrl != null) {
            val issuer = requireNotNull(request.credentialIssuerBaseUrl) {
                "credentialIssuerBaseUrl is required to validate batch support"
            }
            val metadata = IssuerMetadataResolver(httpClient).resolveCredentialIssuerMetadata(issuer).metadata
            require(request.credentialEndpoint.toString() == metadata.credentialEndpoint) { "Credential endpoint does not match issuer metadata" }
            require(request.credentialConfigurationId in metadata.credentialConfigurationsSupported) { "Unknown credential configuration" }
            CredentialRequestBuilder.validateBatchSize(metadata, requestedCount)
        }
        if (request.storeInWallet) require(request.holderBindings.size == requestedCount) {
            "Supply one holder binding for each proof when storing credentials"
        }
        val credentialResponse = requestCredential(request, httpClient, dpop)
        if (credentialResponse.transactionId != null) return FetchCredentialsResult(
            deferredCredential = DeferredCredentialTransaction(request.credentialConfigurationId,
                request.credentialIdentifier, requireNotNull(credentialResponse.transactionId),
                request.holderBindings.map { it.copy(key = null, keyId = it.keyId ?: request.keyId) },
                intervalSeconds = requireNotNull(credentialResponse.interval),
                proofRequired = request.effectiveProofs != null),
            notificationId = credentialResponse.notificationId,
        )
        val rawCredentials = credentialResponse.credentials
            ?.map { it.credential.let { c -> if (c is JsonPrimitive) c.content else c.toString() } }
            ?: error("Credential response contained no credentials")
        require(rawCredentials.isNotEmpty() && (request.effectiveProofs == null || rawCredentials.size <= requestedCount)) {
            "Invalid credential response count"
        }
        return FetchCredentialsResult(
            rawCredentials = rawCredentials,
            notificationId = credentialResponse.notificationId,
        )
    }

    private suspend fun requestCredential(
        request: FetchCredentialRequest,
        httpClient: HttpClient,
        dpop: DpopRequestContext? = null,
        onStage: (CredentialIssuanceStage) -> Unit = {},
    ): CredentialResponse {
        val credentialRequestJson = CredentialRequestBuilder.build(
            CredentialIssuanceTarget(request.credentialConfigurationId, request.credentialIdentifier), request.effectiveProofs?.jwt)
        return requestCredentialResponse(request.credentialEndpoint.toString(), request.accessToken,
            credentialRequestJson, httpClient, dpop, onStage = onStage)
    }

    private suspend fun requestCredentialResponse(
        endpoint: String,
        accessToken: String,
        body: JsonObject,
        httpClient: HttpClient,
        dpop: DpopRequestContext?,
        expectedTransactionId: String? = null,
        onStage: (CredentialIssuanceStage) -> Unit = {},
    ): CredentialResponse {
        val scheme = if (dpop != null) "DPoP" else "Bearer"
        var dpopNonce: String? = dpop?.nonce

        repeat(DPOP_NONCE_ATTEMPTS) { attempt ->
            val proof = dpop?.let {
                onStage(CredentialIssuanceStage.PROOF)
                buildDpopProof(
                    keyMaterial = it.keyMaterial,
                    algorithms = it.algorithms,
                    endpoint = endpoint,
                    accessToken = accessToken,
                    nonce = dpopNonce,
                )
            }
            onStage(CredentialIssuanceStage.REQUEST)
            val response = postFollowingRedirects(httpClient, endpoint) {
                header(HttpHeaders.Authorization, "$scheme ${accessToken}")
                proof?.let { header(DPOP_HEADER, it) }
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
            dpop?.nonce = response.headers[DPOP_NONCE_HEADER] ?: dpopNonce
            if (response.status.isSuccess()) {
                onStage(CredentialIssuanceStage.RESPONSE)
                return response.body<CredentialResponse>().validateCredentialResponse(response.status.value, expectedTransactionId)
            }

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

            // Retain draft-15 §9.3 pending responses accepted by the released polling API.
            if (expectedTransactionId != null && response.status == HttpStatusCode.BadRequest && oauthError == "issuance_pending") {
                onStage(CredentialIssuanceStage.RESPONSE)
                return lenientJson.decodeFromString<CredentialResponse>(response.bodyAsText())
                    .validateLegacyDeferredPendingResponse(expectedTransactionId)
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
        buildProof: (suspend (String?) -> Proofs?)?,
        onProofGenerated: suspend () -> Unit = {},
        dpop: DpopRequestContext? = null,
        onStage: (CredentialIssuanceStage) -> Unit = {},
    ): CredentialResponse {
        suspend fun fetchWithFreshProof(): CredentialResponse {
            val proofs = buildProof?.let {
                val nonce = requestProofNonce(httpClient, nonceEndpoint)
                onStage(CredentialIssuanceStage.PROOF)
                it(nonce)
            }
            onProofGenerated()
            return requestCredential(request.copy(proofs = proofs), httpClient, dpop, onStage)
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
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
    ): FetchCredentialResult = FetchCredentialResult(
        fetchCredentials(wallet, request, httpClient, beforeCredentialsStored, onCredentialStored)
    )

    /** Fetches and optionally stores credentials while retaining complete continuation details. */
    suspend fun fetchCredentials(
        wallet: Wallet,
        request: FetchCredentialRequest,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        /** Called with the exact response batch size before any credential of that batch is persisted. */
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
    ): FetchCredentialsResult {
        wallet.issuanceSessions(httpClient).ensureOpen()
        val dpop = resolveIsolatedDpop(wallet, request.tokenType, request.dpopKeyId, request.credentialIssuerBaseUrl, httpClient)
        if (!request.storeInWallet) return fetchCredentials(request, httpClient, dpop)
        val storage = resolveCredentialStorageContext(
            credentialIssuerBaseUrl = request.credentialIssuerBaseUrl,
            credentialConfigurationId = request.credentialConfigurationId,
            requestMetadata = request.metadata,
            labelOverride = request.label,
            httpClient = httpClient,
        )
        val bindings = wallet.resolveStoredCredentialBindings(
            request.holderBindings, request.keyId, proofRequired = request.effectiveProofs != null,
        )
        val result = fetchCredentials(request, httpClient, dpop)
        if (result.deferredCredential != null) return result
        val target = IssuerNotificationTarget(
            notificationEndpoint = storage.notificationEndpoint,
            notificationId = result.notificationId,
            accessToken = request.accessToken,
            tokenType = request.tokenType,
            dpopProofFactory = dpop?.toProofFactory(request.accessToken),
        )
        return try {
            val prepared = wallet.prepareIssuedCredentials(result.rawCredentials, bindings, storage.label, storage.metadata,
                proofRequired = request.effectiveProofs != null,
                holderBindingKnown = request.keyId != null || request.holderBindings.all { it.keyId != null || it.key != null },
                expectedConfiguration = storage.configuration)
            val outcome = wallet.issuanceSessions(httpClient).storeReceivedCredentials(
                prepared, request.credentialConfigurationId, request.credentialIdentifier,
                persistable = true,
                beforeCredentialsStored = beforeCredentialsStored, onCredentialStored = onCredentialStored,
                notificationEndpoint = storage.notificationEndpoint,
                notificationId = result.notificationId,
                accessToken = request.accessToken,
                tokenType = request.tokenType,
                dpop = dpop?.algorithms,
                keyMaterial = dpop?.keyMaterial,
            )
            result.copy(storageOutcome = outcome)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            deliverCredentialNotification(
                httpClient = httpClient,
                target = target,
                event = NotificationEvent.CREDENTIAL_FAILURE,
            )
            throw error
        }
    }

    /**
     * Posts [NotificationEvent.CREDENTIAL_DELETED] for a credential fetched with
     * [FetchCredentialRequest.storeInWallet] left false. Resolves [RejectIssuedCredentialRequest.notificationEndpoint]
     * from issuer metadata when only [RejectIssuedCredentialRequest.credentialIssuerBaseUrl] is supplied.
     * Bearer-only, like isolated fetch. Missing advertised `notification_endpoint` is a best-effort no-op.
     */
    suspend fun rejectIssuedCredential(
        request: RejectIssuedCredentialRequest,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
    ) {
        val notificationEndpoint = request.notificationEndpoint?.takeIf { it.isNotBlank() }
            ?: request.credentialIssuerBaseUrl?.takeIf { it.isNotBlank() }?.let { issuer ->
                IssuerMetadataResolver(httpClient).resolveCredentialIssuerMetadata(issuer)
                    .metadata.notificationEndpoint
            }
        reportCredentialDeleted(
            notificationEndpoint = notificationEndpoint ?: return,
            notificationId = request.notificationId,
            accessToken = request.accessToken,
            tokenType = "Bearer",
            eventDescription = request.eventDescription,
            httpClient = httpClient,
        )
    }

    /**
     * Reports [NotificationEvent.CREDENTIAL_DELETED] for credentials the caller rejected instead of storing.
     * [tokenType] is `Bearer` or `DPoP`. A DPoP token requires [dpopProofFactory].
     */
    suspend fun reportCredentialDeleted(
        notificationEndpoint: String,
        notificationId: String,
        accessToken: String,
        tokenType: String,
        eventDescription: String? = null,
        dpopProofFactory: DPoPProofFactory? = null,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
    ) {
        deliverCredentialNotification(
            httpClient = httpClient,
            notificationEndpoint = notificationEndpoint,
            notificationId = notificationId,
            accessToken = accessToken,
            tokenType = tokenType,
            event = NotificationEvent.CREDENTIAL_DELETED,
            eventDescription = eventDescription,
            dpopProofFactory = dpopProofFactory,
        )
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

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
        request: GenerateBatchAuthorizationUrlRequest,
        offer: CredentialOffer?,
        issuerMetadata: CredentialIssuerMetadata,
        asMetadata: AuthorizationServerMetadata,
        authorizationEndpoint: String,
        authBuilder: AuthorizationRequestBuilder,
        credentialConfigurationIds: List<String>,
        scope: String?,
        parEndpoint: String,
        keyMaterial: WalletKeyStoreEntry?,
        attestationAssembler: ClientAttestationAssembler?,
        httpClient: HttpClient,
    ): GenerateBatchAuthorizationUrlResult {
        // Binds the eventual access token to this wallet's key at authorization time (RFC 9449
        // Section 10). Only offered when the key can actually sign an advertised algorithm.
        val dpopJkt = keyMaterial
            ?.takeIf { usableDpopAlgorithms(asMetadata, it) != null }
            ?.jwkThumbprint()

        val pushed = authBuilder.buildPushedAuthorizationRequestStateForCredentialConfigurations(
            credentialConfigurationIds = credentialConfigurationIds,
            issuerState = offer?.grants?.authorizationCode?.issuerState,
            usePKCE = request.usePkce,
            metadata = asMetadata,
            redirectUri = request.redirectUri.toString(),
            dpopJkt = dpopJkt,
            credentialIssuerLocations = issuerMetadata.authorizationDetailLocations(),
            scope = scope,
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
            httpClient = httpClient,
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

        return GenerateBatchAuthorizationUrlResult(
            authorizationUrl = Url(
                URLBuilder(authorizationEndpoint).apply {
                    parameters.append("client_id", request.clientId)
                    parameters.append("request_uri", response.requestUri)
                }.buildString()
            ),
            state = pushed.state,
            codeVerifier = pushed.pkceData?.codeVerifier,
            credentialConfigurationIds = credentialConfigurationIds,
            credentialIssuerBaseUrl = issuerMetadata.credentialIssuer,
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
    private fun CredentialIssuerMetadata.authorizationDetailLocations(): List<String>? =
        authorizationServers?.takeIf { it.isNotEmpty() }?.let { listOf(credentialIssuer) }

    private fun clientConfig(clientId: String, redirectUri: Url) =
        ClientConfiguration(clientId = clientId, redirectUris = listOf(redirectUri.toString()))

    private data class CredentialStorageContext(
        val configuration: CredentialConfiguration?,
        val label: String?,
        val metadata: JsonObject?,
        val notificationEndpoint: String? = null,
    )

    /** Resolve configuration and display metadata before an isolated fetch or poll. */
    private suspend fun resolveCredentialStorageContext(
        credentialIssuerBaseUrl: String?,
        credentialConfigurationId: String?,
        requestMetadata: JsonObject?,
        labelOverride: String?,
        httpClient: HttpClient,
    ): CredentialStorageContext {
        val issuer = credentialIssuerBaseUrl?.let {
            IssuerMetadataResolver(httpClient).resolveCredentialIssuerMetadata(it).metadata
        }
        val configuration = credentialConfigurationId?.let { id ->
            issuer?.let { requireNotNull(it.credentialConfigurationsSupported[id]) { "Unknown credential configuration '$id'" } }
        }
        return CredentialStorageContext(
            configuration = configuration,
            label = labelOverride ?: configuration?.credentialMetadata?.display?.firstOrNull()?.name,
            metadata = issuer?.let { storedCredentialDisplayMetadata(it, credentialConfigurationId, requestMetadata) }
                ?: requestMetadata,
            notificationEndpoint = issuer?.notificationEndpoint,
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
     * Token-endpoint client attestation that can be rebuilt per HTTP attempt.
     *
     * The Wallet Attestation JWT is obtained once; each factory invocation mints a fresh PoP `jti`
     * (and honors `OAuth-Client-Attestation-Challenge` from the previous response). Static headers
     * reused the same PoP across a DPoP `use_dpop_nonce` retry, which HAIP conformance rejects.
     */
    private class TokenClientAttestation(
        val factory: ClientAttestationHeadersFactory,
        val onResponseHeaders: TokenResponseHeadersHandler,
    )

    private suspend fun tokenClientAttestation(
        asMetadata: AuthorizationServerMetadata,
        clientId: String,
        attestationAssembler: ClientAttestationAssembler?,
        resolveInstanceKey: suspend () -> Crypto2Key?,
        onAttestationObtained: suspend () -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
    ): TokenClientAttestation? {
        val assembler = attestationAssembler ?: return null
        if (!asMetadata.supportsAttestationBasedClientAuthentication()) return null

        log.debug { "Issuer supports attestation-based client auth, building attestation headers" }
        val key = resolveInstanceKey()
            ?: error("No key available for client attestation")
        val attestationJwt = assembler.obtainAttestationJwt(key, clientId)
        onAttestationObtained()
        var challenge: String? = fetchAttestationChallenge(asMetadata, httpClient)
        return TokenClientAttestation(
            factory = {
                ClientAttestationHeaders(
                    attestationJwt = attestationJwt,
                    popJwt = assembler.buildPopJwt(key, clientId, asMetadata.issuer, challenge),
                )
            },
            onResponseHeaders = { headers ->
                headers[CLIENT_ATTESTATION_CHALLENGE]?.takeIf { it.isNotBlank() }?.let { challenge = it }
            },
        )
    }

    /**
     * Client attestation headers for any request that authenticates this client to the authorization
     * server - the token request and the pushed authorization request alike. Both take the
     * authorization server's issuer as the PoP audience (OAuth 2.0 Attestation-Based Client
     * Authentication Section 5.2), so one builder serves both.
     *
     * Token requests should use [tokenClientAttestation] instead so DPoP/challenge retries mint a
     * new PoP. PAR still uses this one-shot builder because [PushedAuthorizationRequestExecutor]
     * does not retry with a factory.
     *
     * When the authorization server advertises `challenge_endpoint`, the challenge is fetched before
     * the PoP is minted (draft-ietf-oauth-attestation-based-client-auth). Skipping that fetch is
     * what HAIP `oid4vci-1_0-wallet-test-client-attestation-challenge` fails.
     */
    private suspend fun buildClientAttestationHeaders(
        asMetadata: AuthorizationServerMetadata,
        clientId: String,
        attestationAssembler: ClientAttestationAssembler?,
        resolveInstanceKey: suspend () -> Crypto2Key?,
        onAttestationObtained: suspend () -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
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
            challenge = fetchAttestationChallenge(asMetadata, httpClient),
        )
        onAttestationObtained()
        return headers
    }

    private suspend fun fetchAttestationChallenge(
        asMetadata: AuthorizationServerMetadata,
        httpClient: HttpClient,
    ): String? {
        val endpoint = asMetadata.challengeEndpoint?.takeIf { it.isNotBlank() } ?: return null
        return WalletAttestationChallengeRequestBuilder(httpClient).requestChallenge(endpoint).attestationChallenge
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
        nonceEndpoint: String?,
    ): String? = nonceEndpoint?.let {
        NonceRequestBuilder(httpClient).requestNonce(it).cNonce
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
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
    ): GenerateAuthorizationUrlResult = generateAuthorizationUrl(
        request = request,
        keyMaterial = wallet.resolveKeyMaterial(null, setOf(KeyUsage.SIGN)),
        attestationAssembler = attestationAssembler,
        httpClient = httpClient,
    )

    suspend fun generateAuthorizationUrl(request: GenerateAuthorizationUrlRequest): GenerateAuthorizationUrlResult =
        generateAuthorizationUrl(request = request, keyMaterial = null)

    /** Authorizes selected configurations, negotiating scopes or authorization details from metadata. */
    suspend fun generateBatchAuthorizationUrl(
        wallet: Wallet,
        request: GenerateBatchAuthorizationUrlRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
    ): GenerateBatchAuthorizationUrlResult = generateAuthorizationUrl(
        request = request,
        keyMaterial = wallet.resolveKeyMaterial(null, setOf(KeyUsage.SIGN)),
        attestationAssembler = attestationAssembler,
        httpClient = httpClient,
    )

    suspend fun generateBatchAuthorizationUrl(request: GenerateBatchAuthorizationUrlRequest): GenerateBatchAuthorizationUrlResult =
        generateAuthorizationUrl(request = request, keyMaterial = null)

    private suspend fun generateAuthorizationUrl(
        request: GenerateAuthorizationUrlRequest,
        keyMaterial: WalletKeyStoreEntry?,
        attestationAssembler: ClientAttestationAssembler? = null,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
    ): GenerateAuthorizationUrlResult {
        val result = generateAuthorizationUrl(
            request = GenerateBatchAuthorizationUrlRequest(offerUrl = request.offerUrl, offerJson = request.offerJson,
                clientId = request.clientId, redirectUri = request.redirectUri, usePkce = request.usePkce),
            keyMaterial = keyMaterial, attestationAssembler = attestationAssembler, httpClient = httpClient,
            selection = AuthorizationSelection.ReleasedSingle(request.useScope),
        )
        return GenerateAuthorizationUrlResult(result.authorizationUrl, result.state, result.codeVerifier,
            result.credentialConfigurationIds.single(), result.credentialIssuerBaseUrl, result.nonceEndpoint)
    }

    private fun CredentialIssuerMetadata.requireCredentialScope(credentialConfigurationId: String): String =
        requireNotNull(credentialConfigurationsSupported[credentialConfigurationId]?.scope) {
            "Credential configuration '$credentialConfigurationId' publishes no scope, so this " +
                    "credential cannot be requested by scope (OID4VCI 1.0 Section 5.1.2)"
        }


    private sealed interface AuthorizationSelection {
        data object Negotiated : AuthorizationSelection
        data class ReleasedSingle(val useScope: Boolean) : AuthorizationSelection
    }

    private suspend fun generateAuthorizationUrl(
        request: GenerateBatchAuthorizationUrlRequest,
        keyMaterial: WalletKeyStoreEntry?,
        attestationAssembler: ClientAttestationAssembler? = null,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        selection: AuthorizationSelection = AuthorizationSelection.Negotiated,
    ): GenerateBatchAuthorizationUrlResult {
        val offer = if (request.credentialIssuer == null) resolveOffer(request, httpClient) else null
        val issuerMetadata = IssuerMetadataResolver(httpClient).resolveCredentialIssuerMetadata(request.credentialIssuer ?: requireNotNull(offer).credentialIssuer).metadata
        val asMetadata =
            IssuerMetadataResolver(httpClient).resolveAuthorizationServerMetadataWithFallback(issuerMetadata)

        val authorizationEndpoint = asMetadata.authorizationEndpoint
            ?: error("Authorization server has no authorization_endpoint")

        val clientConfig = clientConfig(request.clientId, request.redirectUri)
        val authBuilder = AuthorizationRequestBuilder(clientConfig)
        val credentialConfigurationIds = when (selection) {
            is AuthorizationSelection.ReleasedSingle -> listOf(requireNotNull(offer).credentialConfigurationIds.first())
            AuthorizationSelection.Negotiated -> request.credentialConfigurationIds ?: requireNotNull(offer).credentialConfigurationIds
        }
        require(credentialConfigurationIds.isNotEmpty() && credentialConfigurationIds.distinct().size == credentialConfigurationIds.size)
        credentialConfigurationIds.forEach {
            require(it in issuerMetadata.credentialConfigurationsSupported && (offer == null || it in offer.credentialConfigurationIds)) {
                "Unknown or unoffered credential configuration '$it'"
            }
        }

        val scope = when (selection) {
            is AuthorizationSelection.ReleasedSingle -> if (selection.useScope) {
                issuerMetadata.requireCredentialScope(credentialConfigurationIds.single())
            } else null
            AuthorizationSelection.Negotiated -> CredentialRequestBuilder.authorizationScope(
                issuerMetadata, credentialConfigurationIds, asMetadata)
        }

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
                credentialConfigurationIds = credentialConfigurationIds,
                scope = scope,
                parEndpoint = parEndpoint,
                keyMaterial = keyMaterial,
                attestationAssembler = attestationAssembler,
                httpClient = httpClient,
            )
        }

        val authRequest = authBuilder.buildAuthorizationRequestForCredentialConfigurations(
            authorizationEndpoint = authorizationEndpoint,
            credentialConfigurationIds = credentialConfigurationIds,
            issuerState = offer?.grants?.authorizationCode?.issuerState,
            credentialIssuerLocations = issuerMetadata.authorizationDetailLocations(),
            usePKCE = request.usePkce,
            metadata = asMetadata,
            scope = scope,
        )
        return GenerateBatchAuthorizationUrlResult(
            authorizationUrl = Url(authRequest.url),
            state = authRequest.state,
            codeVerifier = authRequest.pkceData?.codeVerifier,
            credentialConfigurationIds = credentialConfigurationIds,
            credentialIssuerBaseUrl = issuerMetadata.credentialIssuer,
            nonceEndpoint = issuerMetadata.nonceEndpoint?.let { Url(it) },
        )
    }

    /**
     * Step 2 of auth-code grant: exchange the authorization code for a token.
     * Wraps [TokenRequestBuilder.exchangeAuthorizationCode].
     */
    suspend fun exchangeCode(request: ExchangeCodeRequest): RequestTokenResult =
        exchangeCodeDetailed(request).toReleasedResult()

    suspend fun exchangeCode(
        wallet: Wallet,
        request: ExchangeCodeRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        onAttestationObtained: suspend () -> Unit = {},
        keyMaterial: WalletKeyStoreEntry? = null,
        useDpop: Boolean = false,
    ): RequestTokenResult = exchangeCodeDetailed(
        wallet, request, attestationAssembler, httpClient, onAttestationObtained, keyMaterial, useDpop,
    ).toReleasedResult()

    suspend fun exchangeCodeDetailed(request: ExchangeCodeRequest): RequestTokenDetailedResult {
        val httpClient = httpClient
        val credentialIssuerBaseUrl = request.credentialIssuerBaseUrl.takeIf { it.isNotBlank() }
            ?: error("credentialIssuerBaseUrl must be provided")
        val asMetadata = resolveAuthorizationCodeAuthorizationServerMetadata(credentialIssuerBaseUrl, httpClient)
        return exchangeCodeDetailed(
            request = request,
            tokenEndpoint = asMetadata.tokenEndpoint
                ?: error("Authorization server metadata contains no token_endpoint"),
            tokenAttestation = null,
            httpClient = httpClient,
        )
    }

    suspend fun exchangeCodeDetailed(
        wallet: Wallet,
        request: ExchangeCodeRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        onAttestationObtained: suspend () -> Unit = {},
        /**
         * Key to client authenticate and sender constrain this exchange with, when the caller already
         * resolved one.
         *
         * Reuse this key for subsequent DPoP proofs: the access token is bound to its `jkt`
         * (RFC 9449 Section 6). Credential holder keys are selected independently.
         */
        keyMaterial: WalletKeyStoreEntry? = null,
        /** See [ReceiveAuthorizedCredentialRequest.useDpop]. Client authentication is unaffected. */
        useDpop: Boolean = false,
    ): RequestTokenDetailedResult {
        val credentialIssuerBaseUrl = request.credentialIssuerBaseUrl.takeIf { it.isNotBlank() }
            ?: error("credentialIssuerBaseUrl must be provided")
        val asMetadata = resolveAuthorizationCodeAuthorizationServerMetadata(credentialIssuerBaseUrl, httpClient)
        val tokenEndpoint = asMetadata.tokenEndpoint
            ?: error("Authorization server metadata contains no token_endpoint")
        val resolvedKeyMaterial = keyMaterial ?: wallet.resolveKeyMaterial(null, setOf(KeyUsage.SIGN))
        val tokenAttestation = tokenClientAttestation(
            asMetadata = asMetadata,
            clientId = request.clientId,
            attestationAssembler = attestationAssembler,
            resolveInstanceKey = {
                resolvedKeyMaterial?.crypto2AttestationKey()
                    ?: wallet.resolveKeyMaterial(null, setOf(KeyUsage.SIGN))?.crypto2AttestationKey()
            },
            onAttestationObtained = onAttestationObtained,
            httpClient = httpClient,
        )
        return exchangeCodeDetailed(
            request = request,
            tokenEndpoint = tokenEndpoint,
            tokenAttestation = tokenAttestation,
            httpClient = httpClient,
            asMetadata = asMetadata,
            keyMaterial = resolvedKeyMaterial,
            useDpop = useDpop,
        )
    }

    private suspend fun exchangeCodeDetailed(
        request: ExchangeCodeRequest,
        tokenEndpoint: String,
        tokenAttestation: TokenClientAttestation?,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        /**
         * Authorization server metadata and wallet key, needed to client authenticate and sender
         * constrain the token request. Both optional so the key-less overload still works, but a
         * caller with a wallet should always supply them - see [exchangeCode].
         */
        asMetadata: AuthorizationServerMetadata? = null,
        keyMaterial: WalletKeyStoreEntry? = null,
        useDpop: Boolean = false,
    ): RequestTokenDetailedResult {
        val clientConfig = ClientConfiguration(
            clientId = request.clientId,
            redirectUris = listOf(request.redirectUri.toString())
        )

        // Mirrors the pre-authorized-code exchange. Attestation wins over private_key_jwt when both
        // are advertised, because it additionally attests the wallet instance rather than only proving
        // key control.
        val clientAssertionFactory = if (tokenAttestation == null && asMetadata != null && keyMaterial != null) {
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
            dpopProofFactory = senderConstraining?.let { (key, algorithms) ->
                { endpoint: String, nonce: String? ->
                    buildDpopProof(key, algorithms, endpoint, nonce = nonce)
                }
            },
            clientAssertionFactory = clientAssertionFactory,
            onResponseHeaders = tokenAttestation?.onResponseHeaders ?: {},
            attestationHeadersFactory = tokenAttestation?.factory,
        )
        return RequestTokenDetailedResult(
            accessToken = tokenResponse.access_token,
            expiresIn = tokenResponse.expires_in,
            tokenType = tokenResponse.token_type,
            authorizationDetails = tokenResponse.authorization_details,
            scope = tokenResponse.scope,
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
     * or HTTP 202 while pending. Draft-15 `issuance_pending` responses remain supported.
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
    ): Flow<StoredCredential> = pollDeferredFlow(
        wallet = wallet,
        request = request,
        onEvent = onEvent,
        httpClient = httpClient,
        beforeCredentialsStored = beforeCredentialsStored,
        onCredentialStored = onCredentialStored,
        onPending = {},
    )

    fun pollDeferredFlow(
        wallet: Wallet,
        request: PollDeferredRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        /** Called with the exact response batch size before any credential of that batch is persisted. */
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
        onPending: suspend (DeferredCredentialPending) -> Unit,
    ): Flow<StoredCredential> = channelFlow {
        wallet.issuanceSessions(httpClient).ensureOpen()
        val bindings = wallet.resolveStoredCredentialBindings(
            request.holderBindings, request.keyId, proofRequired = request.proofRequired || request.holderBindings.size > 1,
        )
        val dpop = resolveIsolatedDpop(wallet, request.tokenType, request.dpopKeyId,
            request.credentialIssuerBaseUrl, httpClient)
        val storage = resolveCredentialStorageContext(
            credentialIssuerBaseUrl = request.credentialIssuerBaseUrl,
            credentialConfigurationId = request.credentialConfigurationId,
            requestMetadata = request.metadata,
            labelOverride = request.label,
            httpClient = httpClient,
        )
        val credentialResponse = requestCredentialResponse(
            request.deferredCredentialEndpoint.toString(), request.accessToken,
            buildJsonObject { put("transaction_id", request.transactionId) }, httpClient, dpop, request.transactionId)
        if (credentialResponse.transactionId != null) {
            onPending(DeferredCredentialPending(request.transactionId, requireNotNull(credentialResponse.interval)))
            onEvent.emitSafely(WalletSessionEvent.issuance_deferred)
            return@channelFlow
        }
        val rawCredentials = credentialResponse.credentials
            ?: error("Deferred credential response contained no credentials")

        val notificationTarget = IssuerNotificationTarget(
            notificationEndpoint = storage.notificationEndpoint,
            notificationId = credentialResponse.notificationId,
            accessToken = request.accessToken,
            tokenType = request.tokenType,
            dpopProofFactory = dpop?.toProofFactory(request.accessToken),
        )
        val prepared = try {
            wallet.prepareIssuedCredentials(rawCredentials.map {
                val value = it.credential
                if (value is JsonPrimitive) value.content else value.toString()
            }, bindings, storage.label, storage.metadata, proofRequired = request.proofRequired || request.holderBindings.size > 1,
                holderBindingKnown = request.keyId != null || request.holderBindings.all { it.keyId != null || it.key != null },
                expectedConfiguration = storage.configuration)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            deliverCredentialNotification(
                httpClient = httpClient,
                target = notificationTarget,
                event = NotificationEvent.CREDENTIAL_FAILURE,
            )
            throw error
        }
        val outcome = wallet.issuanceSessions(httpClient).storeReceivedCredentials(
            prepared, request.credentialConfigurationId, credentialIdentifier = request.credentialIdentifier,
            persistable = true,
            beforeCredentialsStored = beforeCredentialsStored,
            onCredentialStored = { entry ->
                onCredentialStored(entry)
                onEvent.emitSafely(WalletSessionEvent.issuance_credential_stored)
                send(entry)
            },
            notificationEndpoint = storage.notificationEndpoint,
            notificationId = credentialResponse.notificationId,
            accessToken = request.accessToken,
            tokenType = request.tokenType,
            dpop = dpop?.algorithms,
            keyMaterial = dpop?.keyMaterial,
        )
        if (outcome is WalletIssuanceOutcome.Failed) throw CredentialStorageException(outcome)
        onEvent.emitSafely(WalletSessionEvent.issuance_completed)
    }

    suspend fun pollDeferred(
        wallet: Wallet,
        request: PollDeferredRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
    ): PollDeferredResult {
        val ids = mutableListOf<String>()
        var pending: DeferredCredentialPending? = null
        return try {
            pollDeferredFlow(wallet, request, onEvent, httpClient, beforeCredentialsStored,
                onCredentialStored = { ids += it.id; onCredentialStored(it) },
                onPending = { pending = it }).collect { }
            PollDeferredResult(ids, pending)
        } catch (error: CredentialStorageException) {
            PollDeferredResult(error.outcome.storedCredentialIds, storageOutcome = error.outcome)
        }
    }

    private suspend fun resolveIsolatedDpop(
        wallet: Wallet,
        tokenType: String,
        keyId: String?,
        credentialIssuer: String?,
        httpClient: HttpClient,
    ): DpopRequestContext? {
        if (tokenType.equals("Bearer", ignoreCase = true)) return null
        require(tokenType.equals("DPoP", ignoreCase = true)) { "Unsupported access token type" }
        val material = requireNotNull(wallet.resolveKeyMaterial(requireNotNull(keyId) {
            "dpopKeyId is required for a DPoP access token"
        }, setOf(KeyUsage.SIGN))) { "Sender-constraining key is unavailable" }
        val metadata = resolveAuthorizationCodeAuthorizationServerMetadata(requireNotNull(credentialIssuer) {
            "credentialIssuerBaseUrl is required for a DPoP access token"
        }, httpClient)
        return DpopRequestContext(requireNotNull(dpopAlgorithmsForToken(tokenType,
            usableDpopAlgorithms(metadata, material))), material)
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
    ): Flow<StoredCredential> = receiveCredentialsAuthCodeFlow(
        wallet = wallet,
        request = ReceiveAuthorizedCredentialsRequest(
            credentials = listOf(WalletCredentialSelection(credentialConfigurationId)), code = code,
            codeVerifier = codeVerifier, credentialIssuer = credentialIssuerBaseUrl, credentialEndpoint = credentialEndpoint,
            nonceEndpoint = nonceEndpoint?.let(::Url), clientId = clientId, redirectUri = redirectUri, useDpop = useDpop,
            key = key, keyId = keyReference, did = did, metadata = metadata, label = label,
        ),
        attestationAssembler = attestationAssembler, onEvent = onEvent, httpClient = httpClient,
        beforeCredentialsStored = beforeCredentialsStored, onCredentialStored = onCredentialStored,
    )

    /** Streams stored credentials for explicit accepted targets through the shared issuance executor. */
    fun receiveCredentialsAuthCodeFlow(
        wallet: Wallet,
        request: ReceiveAuthorizedCredentialsRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
        onDeferredCredential: suspend (DeferredCredentialTransaction) -> Unit = {},
    ): Flow<StoredCredential> = channelFlow {
        wallet.issuanceSessions(httpClient).ensureOpen()
        val keyMaterial = request.key?.key?.let { WalletKeyStoreEntry(it.getKeyId(), it, null) }
            ?: wallet.resolveKeyMaterial(request.keyId, setOf(KeyUsage.SIGN))
            ?: error("No key available for proof-of-possession")
        val holderDid = request.did ?: wallet.defaultDid()

        val issuerMetadata = IssuerMetadataResolver(httpClient)
            .resolveCredentialIssuerMetadata(request.credentialIssuer).metadata
        require(request.credentialEndpoint.toString() == issuerMetadata.credentialEndpoint) { "Credential endpoint does not match issuer metadata" }
        request.nonceEndpoint?.toString()?.let { require(it == issuerMetadata.nonceEndpoint) { "Nonce endpoint does not match issuer metadata" } }
        val selections = wallet.resolveCredentialSelections(
            request.credentials,
            issuerMetadata.credentialConfigurationsSupported.keys.toList(), issuerMetadata, keyMaterial, holderDid)

        // Exchange code for token
        val exchangeRequest = ExchangeCodeRequest(
            code = request.code,
            codeVerifier = request.codeVerifier,
            clientId = request.clientId,
            redirectUri = request.redirectUri,
            credentialIssuerBaseUrl = request.credentialIssuer,
        )
        val tokenResult = exchangeCodeDetailed(
            wallet = wallet,
            request = exchangeRequest,
            attestationAssembler = attestationAssembler,
            httpClient = httpClient,
            onAttestationObtained = { onEvent.emitSafely(WalletSessionEvent.issuance_attestation_obtained) },
            keyMaterial = keyMaterial,
            useDpop = request.useDpop,
        )
        onEvent.emitSafely(WalletSessionEvent.issuance_token_obtained)

        val dpop = if (!request.useDpop) null else dpopAlgorithmsForToken(
            tokenResult.tokenType ?: "Bearer",
            usableDpopAlgorithms(resolveAuthorizationCodeAuthorizationServerMetadata(request.credentialIssuer, httpClient), keyMaterial),
        )?.let { DpopRequestContext(it, keyMaterial) }
        executeCredentialTargets(
            wallet, httpClient, issuerMetadata,
            grantedCredentialSelections(issuerMetadata, selections, tokenResult.authorizationDetails, tokenResult.scope),
            CredentialIssuanceAccess(tokenResult.accessToken, keyMaterial, dpop?.algorithms, request.clientId),
            sessions = wallet.issuanceSessions(httpClient), requestMetadata = request.metadata,
            labelFor = { request.label ?: issuerMetadata.credentialConfigurationsSupported.getValue(it).credentialMetadata?.display?.firstOrNull()?.name },
            onEvent = onEvent, beforeCredentialsStored = beforeCredentialsStored,
            onCredentialStored = { entry ->
                onCredentialStored(entry)
                onEvent.emitSafely(WalletSessionEvent.issuance_credential_stored)
                send(entry)
            },
            onDeferredCredential = onDeferredCredential,
        )
        onEvent.emitSafely(WalletSessionEvent.issuance_completed)
    }

    /**
     * Retains the released authorization-code result. Use [receiveCredentialsAuthCode] for detailed progress.
     * Partial failures or repeated deferred configurations throw [CredentialReceiveException] with that progress.
     */
    suspend fun receiveCredentialAuthCode(
        wallet: Wallet,
        request: ReceiveAuthorizedCredentialRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
    ): ReceiveCredentialResult = receiveCredentialsAuthCode(
        wallet = wallet,
        request = ReceiveAuthorizedCredentialsRequest(
            credentials = listOf(WalletCredentialSelection(request.credentialConfigurationId)), code = request.code,
            codeVerifier = request.codeVerifier, credentialIssuer = request.credentialIssuer,
            credentialEndpoint = request.credentialEndpoint, nonceEndpoint = request.nonceEndpoint,
            clientId = request.clientId, redirectUri = request.redirectUri, useDpop = request.useDpop,
            key = request.key, keyId = request.keyId, did = request.did, metadata = request.metadata, label = request.label,
        ),
        attestationAssembler = attestationAssembler, onEvent = onEvent, httpClient = httpClient,
    ).let(::ReceiveCredentialResult)

    suspend fun receiveCredentialsAuthCode(
        wallet: Wallet,
        request: ReceiveAuthorizedCredentialsRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
    ): ReceiveCredentialsResult {
        val progress = IssuanceResultCollector(onCredentialStored)
        return progress.collect(receiveCredentialsAuthCodeFlow(
            wallet = wallet, request = request, attestationAssembler = attestationAssembler,
            onEvent = onEvent, httpClient = httpClient, beforeCredentialsStored = beforeCredentialsStored,
            onCredentialStored = progress::stored, onDeferredCredential = progress::deferred,
        ))
    }

    /**
     * Builds a JWT proof through the proof-builder contracts - no proof assembly happens here.
     *
     * [nonce] is null whenever the Credential Issuer advertises no Nonce Endpoint; the builder then
     * omits the `nonce` claim. The legacy branch is only reached for keys that cannot be represented
     * in crypto2 (remote v1 KMS keys, secp256k1) and goes away with the legacy key API.
     */
    internal suspend fun buildJwtProof(
        proofBuilder: JwtProofBuilder,
        keyMaterial: WalletKeyStoreEntry,
        audience: String,
        nonce: String?,
        did: String?,
        acceptedAlgorithms: Set<String>? = null,
        clientId: String? = null,
        keyAttestationsRequired: KeyAttestationsRequired? = null,
        keyAttestationProvider: KeyAttestationProvider? = null,
    ): Proofs {
        val binding = did
            ?.let { ProofKeyBinding.KeyId(if ('#' in it) it else DidService.resolveAuthenticationMethodId(it, keyMaterial.keyId)) }
            ?: ProofKeyBinding.Jwk
        val effectiveCrypto2Key = keyMaterial.crypto2Key
            ?: keyMaterial.legacyKey?.let { migrateLocalJwk(it) }?.let { crypto2Runtime.restore(it) }
        return effectiveCrypto2Key?.let {
            val keyAttestation = keyAttestationForProof(
                keyAttestationProvider, keyAttestationsRequired, it, audience, nonce, acceptedAlgorithms,
            )
            proofBuilder.buildProof(
                key = it,
                algorithm = it.selectJwsAlgorithm(acceptedAlgorithms),
                audience = audience,
                nonce = nonce,
                binding = binding,
                clientId = clientId,
                keyAttestation = keyAttestation,
            )
        } ?: run {
            require(keyAttestationsRequired == null) {
                "A key-attested JWT proof requires a crypto2 signing key"
            }
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
                clientId = clientId,
            )
        }
    }
}

internal fun supportedJwtProofAlgorithms(
    proofTypes: Map<String, ProofTypeMetadata>?,
    keyAttestationProviderAvailable: Boolean = false,
): Set<String>? {
    if (proofTypes.isNullOrEmpty()) return null
    val jwt = requireNotNull(proofTypes["jwt"]) {
        "Issuer requires an unsupported proof type: ${proofTypes.keys}"
    }
    require(jwt.keyAttestationsRequired == null || keyAttestationProviderAvailable) {
        "Issuer requires a key-attestation JWT; the configured proof path cannot supply one"
    }
    return jwt.proofSigningAlgValuesSupported
}

/** Target-level failure; protocol secrets and server error bodies never enter a public result. */
@Serializable
data class CredentialIssuanceFailure(
    val target: CredentialIssuanceTarget,
    val stage: CredentialIssuanceStage,
    val notAttempted: List<CredentialIssuanceTarget> = emptyList(),
)

@Serializable
enum class CredentialIssuanceStage { PROOF, REQUEST, RESPONSE, STORAGE, OBSERVER }

class CredentialIssuanceException(
    val failure: CredentialIssuanceFailure,
    cause: Exception,
    val storageOutcome: WalletIssuanceOutcome.Failed? = null,
) : Exception("Credential issuance stopped during ${failure.stage}", cause)

/** Complete issuance progress when it cannot be represented by the released collected API. */
class CredentialReceiveException(val result: ReceiveCredentialsResult) : Exception(
    "Issuance progress requires ReceiveCredentialsResult; use the plural receive API. Do not redeem the grant again."
)

private fun ReceiveCredentialsResult.releasedDeferredTransactions(): Map<String, String> {
    if (failure != null || deferredCredentials.map { it.credentialConfigurationId }.distinct().size != deferredCredentials.size) {
        throw CredentialReceiveException(this)
    }
    return deferredCredentials.associate { it.credentialConfigurationId to it.transactionId }
}

private class IssuanceResultCollector(private val onStored: suspend (StoredCredential) -> Unit) {
    private val ids = mutableListOf<String>()
    private val pending = mutableListOf<DeferredCredentialTransaction>()

    suspend fun stored(credential: StoredCredential) {
        ids += credential.id
        onStored(credential)
    }

    fun deferred(target: DeferredCredentialTransaction) { pending += target }

    suspend fun collect(flow: Flow<StoredCredential>): ReceiveCredentialsResult {
        val failure = try {
            flow.collect { }
            null
        } catch (error: CredentialIssuanceException) {
            error
        }
        return ReceiveCredentialsResult(ids.toList(), pending.toList(), failure?.failure, failure?.storageOutcome)
    }
}

/** Lifecycle events are notifications; cancellation still belongs to the caller. */
internal suspend fun (suspend (WalletSessionEvent) -> Unit).emitSafely(event: WalletSessionEvent) {
    try {
        this(event)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        // The retained engine applies the same observer isolation policy.
    }
}
