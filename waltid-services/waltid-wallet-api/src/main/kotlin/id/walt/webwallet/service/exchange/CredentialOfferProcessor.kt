package id.walt.webwallet.service.exchange

import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.oid4vc.requests.BatchCredentialRequest
import id.walt.oid4vc.requests.CredentialRequest
import id.walt.oid4vc.responses.BatchCredentialResponse
import id.walt.oid4vc.responses.CredentialResponse
import id.walt.oid4vc.responses.TokenResponse
import id.walt.webwallet.utils.WalletHttpClients
import io.klogging.logger
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.JsonObject

object CredentialOfferProcessor {
  private val http = WalletHttpClients.getHttpClient()
  private val logger = logger<CredentialOfferProcessor>()
  suspend fun process(
    credentialRequests: List<CredentialRequest>,
    providerMetadata: OpenIDProviderMetadata,
    tokenResponse: TokenResponse
  ) = when (credentialRequests.size) {
    1 -> processedSingleCredentialOffer(credentialRequests, providerMetadata, tokenResponse)
    else -> processBatchCredentialOffer(credentialRequests, providerMetadata, tokenResponse)
  }

  private suspend fun processBatchCredentialOffer(
    credReqs: List<CredentialRequest>,
    providerMetadata: OpenIDProviderMetadata,
    tokenResp: TokenResponse
  ): List<ProcessedCredentialOffer> {
    val batchCredentialRequest = BatchCredentialRequest(credReqs)

    val batchResponse = http.post(providerMetadata.batchCredentialEndpoint!!) {
      contentType(ContentType.Application.Json)
      bearerAuth(tokenResp.accessToken!!)
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
    tokenResp: TokenResponse
  ): List<ProcessedCredentialOffer> {
    val credReq = credReqs.first()

    val credentialResponse = http.post(providerMetadata.credentialEndpoint!!) {
      contentType(ContentType.Application.Json)
      bearerAuth(tokenResp.accessToken!!)
      setBody(credReq.toJSON())
    }.body<JsonObject>().let { ProcessedCredentialOffer(CredentialResponse.fromJSON(it), credReq) }
    logger.debug { "credentialResponse: $credentialResponse" }

    return listOf(credentialResponse)
  }
}
