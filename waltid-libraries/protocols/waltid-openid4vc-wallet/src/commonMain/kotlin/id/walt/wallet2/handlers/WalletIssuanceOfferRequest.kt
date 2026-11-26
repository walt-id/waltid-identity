package id.walt.wallet2.handlers

import id.walt.crypto.keys.DirectSerializedKey
import io.ktor.http.Url
import kotlinx.serialization.json.JsonObject

/** Offer and holder inputs known before a wallet chooses or accepts an OAuth grant. */
data class WalletIssuanceOfferRequest(
    override val offerUrl: Url? = null,
    override val offerJson: JsonObject? = null,
    val key: DirectSerializedKey? = null,
    val keyId: String? = null,
    val did: String? = null,
    val tokenRequestHeaders: Map<String, String> = emptyMap(),
    val credentialIssuer: String? = null,
    val credentialConfigurationIds: List<String>? = null,
) : CredentialOfferSource {
    init {
        if (credentialIssuer == null) checkOfferSource() else {
            require(offerUrl == null && offerJson == null)
            require(credentialIssuer.isNotBlank() && !credentialConfigurationIds.isNullOrEmpty())
        }
    }
}

internal fun WalletIssuanceSessionRequest.offerRequest() = WalletIssuanceOfferRequest(
    offerUrl, offerJson, key, keyId, did, tokenRequestHeaders, credentialIssuer, credentialConfigurationIds,
)
