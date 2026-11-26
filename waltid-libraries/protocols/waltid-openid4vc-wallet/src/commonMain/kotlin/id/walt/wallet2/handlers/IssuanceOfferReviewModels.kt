package id.walt.wallet2.handlers

import id.walt.crypto.keys.DirectSerializedKey
import id.waltid.openid4vci.wallet.metadata.MetadataSignerTrustType
import io.ktor.http.Url
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Grant selected for a resolved issuance session. */
@Serializable
enum class WalletIssuanceGrant {
    PRE_AUTHORIZED_CODE,
    AUTHORIZATION_CODE,
}

/** Typed transaction-code requirement from a pre-authorized offer. */
@Serializable
data class WalletIssuanceTransactionCode(
    val inputMode: String?,
    val length: Int?,
    val descriptionText: String?,
)

/** Issuer display information safe for an app review screen. */
@Serializable
data class WalletIssuanceIssuerPreview(
    val identifier: String,
    val name: String?,
    val locale: String?,
    val logoUri: String?,
    val logoAltText: String?,
    /** Whether the issuer metadata was unsigned or verified signed metadata. */
    val metadataProvenance: WalletIssuanceMetadataProvenance,
)

/** Verification provenance of issuer metadata retained by an issuance session. */
@Serializable
sealed interface WalletIssuanceMetadataProvenance {
    /** Metadata was received as an unsigned JSON document. */
    @Serializable
    data object Unsigned : WalletIssuanceMetadataProvenance

    /** Metadata was received in a JWS verified by the configured trust resolver. */
    @Serializable
    data class Signed(
        /** Exact compact JWS returned by the issuer. */
        val compactJwt: String,
        /** JWS algorithm verified by the configured trust resolver. */
        val algorithm: String,
        /** Identifier of the trusted verification key, as reported by the trust resolver. */
        val keyId: String?,
        /** Authority relationship established by the trust resolver. */
        val trustType: MetadataSignerTrustType,
    ) : WalletIssuanceMetadataProvenance
}

/** Offered credential information safe for an app review screen. */
@Serializable
data class WalletIssuanceCredentialPreview(
    val configurationId: String,
    val format: String,
    val name: String?,
    val descriptionText: String?,
    val logoUri: String?,
    val logoAltText: String? = null,
    val backgroundColor: String? = null,
    val backgroundImageUri: String? = null,
    val textColor: String? = null,
    val vct: String? = null,
    val doctype: String? = null,
)

/** Typed offer preview retained by the issuance session. */
@Serializable
data class WalletIssuanceOfferPreview(
    val grant: WalletIssuanceGrant,
    val issuer: WalletIssuanceIssuerPreview,
    val credentials: List<WalletIssuanceCredentialPreview>,
    val transactionCode: WalletIssuanceTransactionCode?,
)

/** Non-secret PKCE metadata bound to an authorization-code session. */
@Serializable
data class WalletIssuancePkceState(
    val codeChallenge: String,
    val codeChallengeMethod: String,
)

/** Browser request and callback binding for an authorization-code session. */
@Serializable
data class WalletIssuanceAuthorization(
    val url: String,
    val state: String,
    val redirectUri: String,
    val pkce: WalletIssuancePkceState,
    val pushedAuthorizationRequestUsed: Boolean,
    val requestUriExpiresAtEpochMilliseconds: Long? = null,
)

/** Public handle returned by [WalletIssuanceSessionService.start]. */
@Serializable
data class WalletIssuanceSession(
    val id: String,
    val offer: WalletIssuanceOfferPreview,
)

/** Batch-aware review of a session, sharing the released offer preview. */
@Serializable
data class WalletIssuanceBatchSession(
    val id: String,
    val offer: WalletIssuanceOfferPreview,
    val batchSize: Int? = null,
    /** Ordered/localized display metadata by configuration ID, using the stored credential sidecar shape. */
    val credentialDisplayMetadata: Map<String, String> = emptyMap(),
)

/** Input used to start either supported grant from one offer. */
@Serializable
data class WalletIssuanceSessionRequest(
    override val offerUrl: Url? = null,
    override val offerJson: JsonObject? = null,
    val key: DirectSerializedKey? = null,
    val keyId: String? = null,
    val did: String? = null,
    val clientId: String = "eudiw-abca",
    val redirectUri: Url = Url("openid://"),
    /**
     * Manual token authentication; see [ReceiveCredentialRequest.tokenRequestHeaders]. Validated
     * before session creation. PAR authenticates independently; issuer refusals retain the session
     * outcome contract. A static manual PoP cannot be regenerated for automatic recovery.
     */
    val tokenRequestHeaders: Map<String, String> = emptyMap(),
    val credentialIssuer: String? = null,
    val credentialConfigurationIds: List<String>? = null,
) : CredentialOfferSource {
    /** Retains the released constructor contract. */
    constructor(
        offerUrl: Url? = null,
        offerJson: JsonObject? = null,
        key: DirectSerializedKey? = null,
        keyId: String? = null,
        did: String? = null,
        clientId: String = "eudiw-abca",
        redirectUri: Url = Url("openid://"),
        tokenRequestHeaders: Map<String, String> = emptyMap(),
    ) : this(
        offerUrl, offerJson, key, keyId, did, clientId, redirectUri, tokenRequestHeaders, credentialIssuer = null,
        credentialConfigurationIds = null,
    )

    /** Retains the released copy contract and preserves the added fields. */
    fun copy(
        offerUrl: Url? = this.offerUrl,
        offerJson: JsonObject? = this.offerJson,
        key: DirectSerializedKey? = this.key,
        keyId: String? = this.keyId,
        did: String? = this.did,
        clientId: String = this.clientId,
        redirectUri: Url = this.redirectUri,
        tokenRequestHeaders: Map<String, String> = this.tokenRequestHeaders,
    ): WalletIssuanceSessionRequest = WalletIssuanceSessionRequest(
        offerUrl, offerJson, key, keyId, did, clientId, redirectUri, tokenRequestHeaders,
        credentialIssuer = this.credentialIssuer, credentialConfigurationIds = this.credentialConfigurationIds,
    )

    init {
        if (credentialIssuer == null) checkOfferSource() else {
            require(offerUrl == null && offerJson == null)
            require(credentialIssuer.isNotBlank() && !credentialConfigurationIds.isNullOrEmpty())
        }
        require(clientId.isNotBlank()) { "clientId cannot be blank" }
    }

}

/** Callback continuation supplied after the browser returns to the wallet. */
@Serializable
data class WalletIssuanceAuthorizationCallback(
    val sessionId: String,
    val callbackUri: String,
)

/** Wallet-local retention policy for uncompleted issuance sessions. */
@Serializable
data class WalletIssuanceSessionPolicy(
    val reviewTtl: Duration = 10.minutes,
    val authorizationCallbackTtl: Duration = 10.minutes,
)
