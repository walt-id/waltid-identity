package id.walt.webwallet.usecase.issuer

import id.walt.webwallet.service.issuers.IssuerDataTransferObject
import id.walt.webwallet.service.issuers.IssuerNameResolutionService
import kotlinx.uuid.UUID

class IssuerNameResolutionUseCase(
    private val issuerUseCase: IssuerUseCase,
    private val nameResolutionService: IssuerNameResolutionService
) {
    suspend fun resolve(wallet: UUID, did: String): String = let {
        issuerUseCase.get(wallet, did).getOrNull() ?: IssuerDataTransferObject(
            wallet = wallet,
            did = did,
        )
    }.let { it.takeIf { !it.name.isNullOrEmpty() } ?: resolveNameAndUpdate(it) }.name ?: did

    private suspend fun resolveNameAndUpdate(issuer: IssuerDataTransferObject) =
        issuer.copy(name = nameResolutionService.resolve(issuer.did).getOrNull()).also { issuerUseCase.add(it) }
}