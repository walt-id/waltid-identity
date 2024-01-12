package id.walt.webwallet.manifests

import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.EntraIssuanceRequest
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.serialization.json.JsonObject

class EntraManifestExtractor : ManifestExtractor {

    override suspend fun extract(offerRequestUrl: String): JsonObject = parse(offerRequestUrl).manifest

    private suspend fun parse(offerRequestUrl: String) =
        parseQueryString(Url(offerRequestUrl).encodedQuery).toMap().let {
            AuthorizationRequest.fromHttpParametersAuto(it)
        }.let {
            EntraIssuanceRequest.fromAuthorizationRequest(it)
        }
}