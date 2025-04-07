package id.walt.entrawallet.core.service.exchange

import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.oid4vc.requests.BatchCredentialRequest
import id.walt.oid4vc.requests.CredentialRequest
import id.walt.oid4vc.responses.BatchCredentialResponse
import id.walt.oid4vc.responses.CredentialResponse
import id.walt.webwallet.utils.WalletHttpClients
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.JsonObject

object CredentialOfferProcessor {
    private val http = WalletHttpClients.getHttpClient()
    private val logger = KotlinLogging.logger {}
    suspend fun process(
        credentialRequests: List<CredentialRequest>,
        providerMetadata: OpenIDProviderMetadata,
        accessToken: String,
    ) = when (credentialRequests.size) {
        1 -> processedSingleCredentialOffer(credentialRequests, providerMetadata, accessToken)
        else -> processBatchCredentialOffer(credentialRequests, providerMetadata, accessToken)
    }

    private suspend fun processBatchCredentialOffer(
        credReqs: List<CredentialRequest>,
        providerMetadata: OpenIDProviderMetadata,
        accessToken: String,
    ): List<ProcessedCredentialOffer> {
        val batchCredentialRequest = BatchCredentialRequest(credReqs)

        val batchResponse = http.post(providerMetadata.batchCredentialEndpoint!!) {
            contentType(ContentType.Application.Json)
            bearerAuth(accessToken)
            setBody(batchCredentialRequest.toJSON())
        }.body<JsonObject>().let { BatchCredentialResponse.fromJSON(it) }
        logger.debug { "credential batch response: $batchResponse" }

        return (batchResponse.credentialResponses
            ?: throw IllegalArgumentException("No credential responses returned")).indices.map {
            ProcessedCredentialOffer(
                batchResponse.credentialResponses!![it],
                batchCredentialRequest.credentialRequests[it]
            )
        }
    }

    private suspend fun processedSingleCredentialOffer(
        credReqs: List<CredentialRequest>,
        providerMetadata: OpenIDProviderMetadata,
        accessToken: String,
    ): List<ProcessedCredentialOffer> {
        val credReq = credReqs.first()

        val credentialResponse = http.post(providerMetadata.credentialEndpoint!!) {
            contentType(ContentType.Application.Json)
            bearerAuth(accessToken)
            setBody(credReq.toJSON())
        }.body<JsonObject>().let { ProcessedCredentialOffer(CredentialResponse.fromJSON(it), credReq) }
        logger.debug { "credentialResponse: $credentialResponse" }

        return listOf(credentialResponse)
    }
}
