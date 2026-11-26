package id.walt.wallet2.handlers

import id.walt.wallet2.data.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** Reference returned by either a deferred outcome or the retained continuation listing. */
sealed interface WalletDeferredIssuanceReference {
    val id: String
}

/** Released public reference for a credential with a known requested configuration. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class WalletDeferredCredential(
    override val id: String,
    val credentialConfigurationId: String,
    val intervalSeconds: Long?,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val credentialIdentifier: String? = null,
) : WalletDeferredIssuanceReference {
    constructor(id: String, credentialConfigurationId: String, intervalSeconds: Long?) :
        this(id, credentialConfigurationId, intervalSeconds, credentialIdentifier = null)

    /** Retains the released copy contract without discarding the dataset identifier. */
    fun copy(
        id: String = this.id,
        credentialConfigurationId: String = this.credentialConfigurationId,
        intervalSeconds: Long? = this.intervalSeconds,
    ): WalletDeferredCredential = WalletDeferredCredential(id, credentialConfigurationId, intervalSeconds, credentialIdentifier)

    constructor(continuation: WalletIssuanceContinuation) : this(
        continuation.id,
        continuation.credentialConfigurationId ?: throw WalletIssuanceContinuationException(listOf(continuation)),
        continuation.intervalSeconds,
        continuation.credentialIdentifier,
    )
}

/** Retained issuance work, including local-save recovery whose original configuration may be unknown. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class WalletIssuanceContinuation(
    override val id: String,
    val credentialConfigurationId: String? = null,
    val intervalSeconds: Long? = null,
    val credentialIdentifier: String? = null,
    /** Current retained state when returned by [WalletIssuanceSessionService.listIssuanceContinuations]. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val status: WalletIssuanceContinuationStatus = WalletIssuanceContinuationStatus.UNRESOLVED,
    /** Issuer display metadata only; never credential values or protocol continuation secrets. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val displayMetadataJson: String? = null,
) : WalletDeferredIssuanceReference {
    constructor(deferred: WalletDeferredCredential) : this(
        deferred.id, deferred.credentialConfigurationId, deferred.intervalSeconds, deferred.credentialIdentifier,
    )
}

/** A presentation hint, not permission to bypass the checks performed by resumeDeferred. */
@Serializable
enum class WalletIssuanceContinuationStatus {
    UNRESOLVED,
    AWAITING_ISSUER,
    AWAITING_LOCAL_SAVE,
    REMOTE_OUTCOME_UNCERTAIN,
    STORAGE_OUTCOME_UNCERTAIN,
}

/** The released configured-only listing cannot describe every retained local-save handle. */
class WalletIssuanceContinuationException(val continuations: List<WalletIssuanceContinuation>) : Exception(
    "Retained local-save work has no configuration metadata; use listIssuanceContinuations."
)

/** Stable failure categories returned without protocol secrets or response bodies. */
@Serializable
enum class WalletIssuanceErrorCode {
    INVALID_SESSION,
    INVALID_CALLBACK,
    INVALID_INPUT,
    AUTHORIZATION_FAILED,
    ISSUER_METADATA,
    ISSUER_RESPONSE,
    NETWORK,
    REMOTE_OUTCOME_UNCERTAIN,
    STORAGE_OUTCOME_UNCERTAIN,
    CRYPTO,
    STORAGE,
    PROTOCOL,
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class WalletIssuanceError(
    val code: WalletIssuanceErrorCode,
    val message: String,
    /** Whether the caller may submit the same reviewed offer again after this transition. */
    val canRetryOffer: Boolean = false,
    /** True when [WalletIssuanceErrorCode.ISSUER_RESPONSE] identifies a credential rejected by the host's receipt profile. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val issuerTrustFailed: Boolean = false,
) {
    init {
        require(!issuerTrustFailed || code == WalletIssuanceErrorCode.ISSUER_RESPONSE)
    }

    constructor(code: WalletIssuanceErrorCode, message: String, canRetryOffer: Boolean = false) :
        this(code, message, canRetryOffer, false)

    constructor(code: WalletIssuanceErrorCode, message: String) : this(code, message, false, false)

    /** Preserves the released copy signatures and the detail for an issuer-response failure. */
    fun copy(
        code: WalletIssuanceErrorCode = this.code,
        message: String = this.message,
        canRetryOffer: Boolean = this.canRetryOffer,
    ): WalletIssuanceError = WalletIssuanceError(
        code, message, canRetryOffer, issuerTrustFailed && code == WalletIssuanceErrorCode.ISSUER_RESPONSE,
    )

    fun copy(code: WalletIssuanceErrorCode = this.code, message: String = this.message): WalletIssuanceError =
        copy(code, message, canRetryOffer)
}

/** Terminal or pending result of an issuance-session transition. */
@Serializable
sealed interface WalletIssuanceOutcome {
    val sessionId: String

    @Serializable
    @kotlinx.serialization.SerialName("stored")
    data class Stored(
        override val sessionId: String,
        val credentialIds: List<String>,
    ) : WalletIssuanceOutcome

    @Serializable
    @kotlinx.serialization.SerialName("deferred")
    data class Deferred(
        override val sessionId: String,
        val storedCredentialIds: List<String>,
        val credentials: List<WalletDeferredCredential>,
    ) : WalletIssuanceOutcome

    @Serializable
    @kotlinx.serialization.SerialName("cancelled")
    data class Cancelled(
        override val sessionId: String,
    ) : WalletIssuanceOutcome

    @Serializable
    @kotlinx.serialization.SerialName("failed")
    data class Failed(
        override val sessionId: String,
        val error: WalletIssuanceError,
        val storedCredentialIds: List<String> = emptyList(),
        val deferredCredentials: List<WalletIssuanceContinuation> = emptyList(),
        val failure: CredentialIssuanceFailure? = null,
    ) : WalletIssuanceOutcome {
        constructor(
            sessionId: String,
            error: WalletIssuanceError,
            storedCredentialIds: List<String> = emptyList(),
        ) : this(sessionId, error, storedCredentialIds, deferredCredentials = emptyList(), failure = null)

        /** Retains the released copy contract and all newly retained progress. */
        fun copy(
            sessionId: String = this.sessionId,
            error: WalletIssuanceError = this.error,
            storedCredentialIds: List<String> = this.storedCredentialIds,
        ): Failed = Failed(sessionId, error, storedCredentialIds, deferredCredentials, failure)
    }
}
