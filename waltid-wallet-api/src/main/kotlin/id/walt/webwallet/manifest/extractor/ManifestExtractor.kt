package id.walt.webwallet.manifest.extractor

import id.walt.oid4vc.definitions.CROSS_DEVICE_CREDENTIAL_OFFER_URL
import id.walt.oid4vc.requests.EntraIssuanceRequest
import kotlinx.serialization.json.JsonObject

interface ManifestExtractor {
    suspend fun extract(offerRequestUrl: String): JsonObject

    companion object {
        fun new(offerRequestUrl: String): ManifestExtractor {
            // TODO: this check is performed twice (see [SSIKit2WalletService -> useOfferRequest])
            if (EntraIssuanceRequest.isEntraIssuanceRequestUri(offerRequestUrl)) {
                return EntraManifestExtractor()
            }
            if (offerRequestUrl.startsWith(CROSS_DEVICE_CREDENTIAL_OFFER_URL)) {
                return DefaultManifestExtractor()
            }
            return DefaultManifestExtractor()
        }
    }
}