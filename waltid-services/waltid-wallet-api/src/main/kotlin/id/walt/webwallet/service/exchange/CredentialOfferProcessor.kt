package id.walt.webwallet.service.exchange

import id.walt.oid4vc.OpenID4VCI
import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.oid4vc.requests.BatchCredentialRequest
import id.walt.oid4vc.requests.CredentialRequest
import id.walt.webwallet.utils.WalletHttpClients
import io.klogging.logger

object CredentialOfferProcessor {
    private val http = WalletHttpClients.getHttpClient()
    private val logger = logger<CredentialOfferProcessor>()
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

        val batchCredentialResponse = OpenID4VCI.sendBatchCredentialRequest(
            providerMetadata = providerMetadata,
            accessToken = accessToken,
            batchCredentialRequest = batchCredentialRequest,
        )

        return (batchCredentialResponse.credentialResponses
            ?: throw IllegalArgumentException("No credential responses returned")).indices.map {
            ProcessedCredentialOffer(
                batchCredentialResponse.credentialResponses!![it],
                batchCredentialRequest.credentialRequests[it]
            )
        }
    }

    private suspend fun processedSingleCredentialOffer(
        credReqs: List<CredentialRequest>,
        providerMetadata: OpenIDProviderMetadata,
        accessToken: String,
    ): List<ProcessedCredentialOffer> {

        val credentialResponse = OpenID4VCI.sendCredentialRequest(
            providerMetadata = providerMetadata,
            accessToken = accessToken,
            credentialRequest = credReqs.first()
        ).let {
            ProcessedCredentialOffer(
                credentialResponse = it,
                credentialRequest = credReqs.first()
            )
        }

        return listOf(credentialResponse)
    }
}
