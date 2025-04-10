package id.walt.entrawallet.core.service

import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.requests.CredentialOfferRequest
import id.walt.sdjwt.SDJWTVCTypeMetadata
import id.walt.entrawallet.core.service.exchange.PresentationRequestParameter
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
abstract class WalletService(val tenant: String, val accountId: Uuid, val walletId: Uuid) {

    // SIOP
    abstract suspend fun usePresentationRequest(parameter: PresentationRequestParameter): Result<String?>

    abstract suspend fun resolvePresentationRequest(request: String): String
    abstract suspend fun resolveCredentialOffer(offerRequest: CredentialOfferRequest): CredentialOffer

    abstract suspend fun resolveVct(vct: String): SDJWTVCTypeMetadata

}
