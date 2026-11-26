package id.walt.wallet2.handlers

import id.walt.wallet2.data.*
import id.walt.wallet2.handlers.WalletIssuanceSessionService.DeferredResponseValidation
import id.walt.wallet2.handlers.WalletIssuanceSessionService.SessionState
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
@SerialName("id.walt.wallet2.handlers.WalletIssuanceSessionService.PersistedHolderBinding")
internal data class PersistedHolderBinding(val keyId: String?, val did: String?, val publicJwk: String)

@Serializable
@SerialName("id.walt.wallet2.handlers.WalletIssuanceSessionService.PersistedCredentialSelection")
internal data class PersistedCredentialSelection(
    val selection: WalletCredentialSelection,
    val bindings: List<PersistedHolderBinding>,
)

@Serializable
@SerialName("id.walt.wallet2.handlers.WalletIssuanceSessionService.PersistedRequest")
internal data class PersistedRequest(
    val keyId: String?,
    val did: String?,
    val clientId: String? = null,
    val redirectUri: String? = null,
    val tokenRequestHeaders: Map<String, String>,
)

@Serializable
@SerialName("id.walt.wallet2.handlers.WalletIssuanceSessionService.PersistedActiveSession")
internal data class PersistedActiveSession(
    val selections: List<PersistedCredentialSelection>? = null,
    val public: WalletIssuanceSession,
    val request: PersistedRequest,
    val offer: String? = null,
    val issuerMetadata: String,
    val authorizationServerMetadata: String,
    val selectedPublicJwk: String,
    val authorization: WalletIssuanceAuthorization? = null,
    val codeVerifier: String? = null,
    val state: SessionState,
    val expiresAtEpochMilliseconds: Long,
    val attestationChallenge: String? = null,
    val authorizationServerDpopNonce: String? = null,
)

/** Exact flat shape published in 1.1.0; confined to the persistence decoder. */
@Serializable
@SerialName("id.walt.wallet2.handlers.WalletIssuanceSessionService.ReleasedDeferredRecord")
internal data class ReleasedDeferredRecord(
    val public: WalletDeferredCredential,
    val sessionId: String,
    val endpoint: String,
    val transactionId: String,
    val accessToken: String,
    val tokenType: String,
    val dpop: Set<String>?,
    val dpopNonce: String?,
    val keyId: String?,
    val selectedPublicJwk: String,
    val label: String?,
    val metadata: JsonObject? = null,
)

internal fun Json.decodePersistedDeferred(record: WalletIssuanceSessionRecord): PersistedDeferredRecord {
    val payload = parseToJsonElement(record.payload).jsonObject
    if ("content" in payload) return decodeFromJsonElement<PersistedDeferredRecord>(payload)
    require("claimed" !in payload) { "A claimed continuation must contain its current-format content" }
    val released = decodeFromJsonElement<ReleasedDeferredRecord>(payload)
    val interval = released.public.intervalSeconds ?: 0
    require(interval >= 0) { "Stored deferred credential interval is invalid" }
    return PersistedDeferredRecord(
        public = WalletIssuanceContinuation(released.public),
        sessionId = released.sessionId,
        content = PersistedDeferredContent.Remote(PersistedDeferredRequest(
            validation = DeferredResponseValidation.ReleasedSingleKey,
            bindings = listOf(PersistedHolderBinding(released.keyId, null, released.selectedPublicJwk)),
            nextPollAtEpochMilliseconds = nextDeferredPoll(interval, record.updatedAtEpochMilliseconds),
            endpoint = released.endpoint, transactionId = released.transactionId,
            accessToken = released.accessToken, tokenType = released.tokenType,
            dpop = released.dpop, dpopNonce = released.dpopNonce,
            keyId = released.keyId, selectedPublicJwk = released.selectedPublicJwk,
            label = released.label, metadata = released.metadata,
        )),
    )
}

@Serializable
@SerialName("id.walt.wallet2.handlers.WalletIssuanceSessionService.PersistedDeferredRecord")
internal data class PersistedDeferredRecord(
    val content: PersistedDeferredContent,
    val claimed: Boolean = false,
    val public: WalletIssuanceContinuation,
    val sessionId: String,
    val notificationEndpoint: String? = null,
    val notificationId: String? = null,
    val accessToken: String? = null,
    val tokenType: String? = null,
    val dpop: Set<String>? = null,
    val keyId: String? = null,
    val selectedPublicJwk: String? = null,
) {
    init {
        require(content !is PersistedDeferredContent.Remote || public.credentialConfigurationId != null) {
            "A remote deferred credential must identify its requested configuration"
        }
    }
}

@Serializable
@SerialName("id.walt.wallet2.handlers.WalletIssuanceSessionService.PersistedDeferredContent")
internal sealed interface PersistedDeferredContent {
    @Serializable
    @kotlinx.serialization.SerialName("remote")
    data class Remote(val request: PersistedDeferredRequest) : PersistedDeferredContent

    @Serializable
    @kotlinx.serialization.SerialName("received")
    data class Received(val credentials: List<StoredCredential>) : PersistedDeferredContent {
        init { require(credentials.isNotEmpty()) }
    }
}

@Serializable
@SerialName("id.walt.wallet2.handlers.WalletIssuanceSessionService.PersistedDeferredRequest")
internal data class PersistedDeferredRequest(
    val validation: DeferredResponseValidation,
    val nextPollAtEpochMilliseconds: Long,
    val bindings: List<PersistedHolderBinding>,
    val endpoint: String,
    val transactionId: String,
    val accessToken: String,
    val tokenType: String,
    val dpop: Set<String>?,
    val dpopNonce: String?,
    val keyId: String?,
    val selectedPublicJwk: String,
    val label: String?,
    val metadata: JsonObject? = null,
    val credentialIssuer: String? = null,
    val issuerMetadataJson: String? = null,
    val notificationEndpoint: String? = null,
)

internal fun nextDeferredPoll(intervalSeconds: Long, current: Long): Long {
    val delay = intervalSeconds.seconds.inWholeMilliseconds
    return if (current > Long.MAX_VALUE - delay) Long.MAX_VALUE else current + delay
}
