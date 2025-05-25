@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.usecase.claim

import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.SSIKit2WalletService
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.service.exchange.IssuanceService
import id.walt.webwallet.usecase.event.EventLogUseCase
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ExplicitClaimStrategy(
    private val issuanceService: IssuanceService,
    private val credentialService: CredentialsService,
    private val eventUseCase: EventLogUseCase,
) {
    @OptIn(ExperimentalUuidApi::class)
    suspend fun claim(
        tenant: String,
        account: Uuid,
        wallet: Uuid,
        did: String,
        offer: String,
        pending: Boolean = true,
        pinOrTxCode: String? = null,
    ): List<WalletCredential> = issuanceService.useOfferRequest(
        offer = offer,
        credentialWallet = SSIKit2WalletService.getCredentialWallet(did),
        pinOrTxCode = pinOrTxCode,
    ).map { credentialDataResult ->
        ClaimCommons.convertCredentialDataResultToWalletCredential(
            credentialDataResult,
            wallet,
            pending,
        ).also { credential ->
            ClaimCommons.addReceiveCredentialToUseCaseLog(
                tenant,
                account,
                wallet,
                credential,
                credentialDataResult.type,
                eventUseCase,
            )
        }
    }.also {
        ClaimCommons.storeWalletCredentials(
            wallet,
            it,
            credentialService,
        )
    }
}
