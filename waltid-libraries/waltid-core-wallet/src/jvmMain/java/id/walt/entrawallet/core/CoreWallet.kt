package id.walt.entrawallet.core

import id.walt.crypto.keys.Key
import id.walt.entrawallet.core.service.SSIKit2WalletService
import id.walt.entrawallet.core.service.WalletServiceManager
import id.walt.entrawallet.core.service.WalletServiceManager.getWalletService
import id.walt.entrawallet.core.service.WalletServiceManager.httpClient
import id.walt.entrawallet.core.service.exchange.CredentialDataResult
import id.walt.entrawallet.core.service.exchange.IssuanceService
import id.walt.entrawallet.core.service.exchange.PresentationRequestParameter
import id.walt.entrawallet.core.service.exchange.UsePresentationResponse
import id.walt.entrawallet.core.utils.WalletCredential
import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.webwallet.usecase.exchange.FilterData
import io.ktor.http.*
import io.ktor.util.*

object CoreWallet {

    val wallet = SSIKit2WalletService(
        http = httpClient
    )

    /**
     * Claim credential(s) from an issuer
     * @param offer The offer request to use
     * @param did The DID to issue the credential(s) to
     *
     * @return List of credentials
     */
    suspend fun useOfferRequest(offer: String, did: String, key: Key): List<CredentialDataResult> {
        return IssuanceService.useOfferRequest(
            offer = offer,
            credentialWallet = SSIKit2WalletService.getCredentialWallet(did = did),
            clientId = SSIKit2WalletService.testCIClientConfig.clientID,
            key = key,
            did = did
        )
    }

    /**
     * Present credential(s) to a Relying Party
     *
     */
    suspend fun usePresentationRequest(
        did: String,
        presentationRequest: String,
        selectedCredentials: List<CredentialDataResult>,
        disclosures: Map<CredentialDataResult, List<String>>? = null,
        key: Key,
    ): UsePresentationResponse {
        val wallet = getWalletService()

        val result = wallet.usePresentationRequest(
            PresentationRequestParameter(
                key = key,
                did = did,
                request = presentationRequest,
                selectedCredentials = selectedCredentials,
                disclosures = disclosures,
            )
        ) // TODO add disclosures here

        if (result.isSuccess) {
            return UsePresentationResponse(ok = true, redirectUri = result.getOrThrow())
        } else {
            val err = result.exceptionOrNull()
            println("Presentation failed: $err")

            return when (err) {
                is SSIKit2WalletService.PresentationError ->
                    UsePresentationResponse(ok = false, redirectUri = err.redirectUri, errorMessage = err.message)

                else -> UsePresentationResponse(ok = false, redirectUri = null, errorMessage = err?.message)
            }
        }
    }

    /**
     * Returns the credentials stored in the wallet that match the passed presentation definition
     *
     * @param presentationDefinition Presentation definition to match credentials against
     *
     * @return Credentials that match the presentation definition
     */
    fun matchCredentialsForPresentationDefinition(
        credentials: List<WalletCredential>,
        presentationDefinition: PresentationDefinition
    ): List<WalletCredential> {
        val matchedCredentials =
            WalletServiceManager.matchPresentationDefinitionCredentialsUseCase.match(credentials, presentationDefinition)
        return matchedCredentials
    }

    /**
     * Returns the credentials that are required by the presentation definition but not found in the wallet
     *
     * @param presentationDefinition Presentation definition to match credentials against
     *
     * @return Filters that failed to fulfill the presentation definition
     */
    fun unmatchedCredentialsForPresentationDefinition(
        credentials: List<WalletCredential>,
        presentationDefinition: PresentationDefinition
    ): List<FilterData> {
        val unmatchedCredentialTypes = WalletServiceManager.unmatchedPresentationDefinitionCredentialsUseCase.find(
            credentials, presentationDefinition
        )
        return unmatchedCredentialTypes
    }

    /**
     * Return resolved / parsed presentation request
     * @param request PresentationRequest to resolve/parse
     */
    suspend fun resolvePresentationRequest(request: String): String {
        val wallet = getWalletService()
        val parsedRequest = wallet.resolvePresentationRequest(request)
        return parsedRequest
    }

    /**
     * Return resolved / parsed credential offer
     * @param offer Credential offer request to resolve/parse
     * @return Resolved credential offer
     */
    suspend fun resolveCredentialOffer(offer: String): CredentialOffer {
        val wallet = getWalletService()
        val reqParams = Url(offer).parameters.toMap()
        val parsedOffer = wallet.resolveCredentialOffer(id.walt.oid4vc.requests.CredentialOfferRequest.fromHttpParameters(reqParams))
        return parsedOffer
    }

}
