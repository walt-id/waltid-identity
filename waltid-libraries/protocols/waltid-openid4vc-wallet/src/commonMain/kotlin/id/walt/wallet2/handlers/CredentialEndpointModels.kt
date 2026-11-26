@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.handlers

import id.walt.openid4vci.errors.CredentialError
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.proofs.Proofs
import id.walt.wallet2.data.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class FetchCredentialRequest(
    val credentialEndpoint: Url,
    val accessToken: String,
    val credentialConfigurationId: String,
    /** Existing single-proof REST input. Mutually exclusive with [proofs]. */
    val proofJwt: String? = null,
    val clientId: String = Wallet2DefaultClientId,
    /**
     * When true, [WalletIssuanceHandler.fetchCredential] stores the fetched
     * credential(s) when called with a wallet. Defaults to false (stateless).
     *
     * When storing, pass [credentialIssuerBaseUrl] (and optionally [metadata]/[label])
     * so issuer display metadata and credential labels are persisted the same way as
     * the full pre-authorized receive path. [holderBindings] must identify the stored wallet keys
     * selected for storage. Expected public keys come separately from [proofs], including all keys
     * in a nested key attestation. Each returned proof-bound credential must match its evidence and
     * a selected wallet key; fewer or reordered credentials are accepted. Inline holder keys are
     * rejected, and copies require distinct public keys. Attestation authentication belongs to the issuer.
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
        clientId: String = Wallet2DefaultClientId,
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
 * OpenID4VCI notification endpoint. The isolated rejection request accepts a Bearer token.
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
