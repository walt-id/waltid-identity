package id.walt.webwallet.manifests

import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.EntraIssuanceRequest
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class EntraManifest(offerRequestUrl: String) : Manifest {

    @OptIn(DelicateCoroutinesApi::class)
    private val manifest = GlobalScope.async { parse(offerRequestUrl).manifest }

    override suspend fun display(): JsonObject? = manifest.await().jsonObject["display"]?.jsonObject

    override suspend fun issuer(): JsonObject? = manifest.await().jsonObject["input"]?.jsonObject

    private suspend fun parse(offerRequestUrl: String) =
        parseQueryString(Url(offerRequestUrl).encodedQuery).toMap().let {
            AuthorizationRequest.fromHttpParametersAuto(it)
        }.let {
            EntraIssuanceRequest.fromAuthorizationRequest(it)
        }
}