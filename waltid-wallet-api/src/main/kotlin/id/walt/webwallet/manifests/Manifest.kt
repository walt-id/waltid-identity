package id.walt.webwallet.manifests

import id.walt.oid4vc.requests.EntraIssuanceRequest
import kotlinx.serialization.json.JsonObject

interface Manifest {
    suspend fun display(): JsonObject?
    suspend fun issuer(): JsonObject?

    companion object {
        fun new(offerRequestUrl: String): Manifest {
            // TODO: this check is performed twice (see [SSIKit2WalletService -> useOfferRequest])
            if (EntraIssuanceRequest.isEntraIssuanceRequestUri(offerRequestUrl)) {
                return EntraManifest(offerRequestUrl)
            }
            return DefaultManifest()
        }
    }
}