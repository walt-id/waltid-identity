package id.walt.wallet2.handlers

import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.openid4vci.offers.CredentialOffer
import id.walt.openid4vci.offers.TxCode
import id.walt.wallet2.handlers.WalletIssuanceHandler.resolveOffer
import id.waltid.openid4vci.wallet.metadata.OfferedCredentialResolver
import id.waltid.openid4vci.wallet.metadata.ResolvedCredentialIssuerMetadata
import io.ktor.http.Headers
import io.ktor.http.Url
import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

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

/** Opaque identifier binding an issuance action to one reviewed credential-offer resolution. */
@Serializable
@JvmInline
value class IssuancePreviewHandle(val value: String) {
    init {
        require(value.isNotBlank()) { "Issuance preview handle must not be blank" }
    }

    override fun toString(): String = "IssuancePreviewHandle(<redacted>)"
}

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
    val grant: WalletIssuanceGrant,
    val summary: ResolveOfferResult,
    val offer: CredentialOffer,
    val resolvedIssuerMetadata: ResolvedCredentialIssuerMetadata,
    val authorizationServerMetadata: AuthorizationServerMetadata,
    val authorizationServerHeaders: Headers,
    val offeredCredentials: List<OfferedCredentialResolver.ResolvedCredentialOffer>,
) {
    val preAuthorizedGrant get() = offer.grants?.preAuthorizedCode?.takeIf { grant == WalletIssuanceGrant.PRE_AUTHORIZED_CODE }
}
