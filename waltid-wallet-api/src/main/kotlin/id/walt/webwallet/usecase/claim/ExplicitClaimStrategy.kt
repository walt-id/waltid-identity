package id.walt.webwallet.usecase.claim

import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.SSIKit2WalletService
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.service.events.EventType
import id.walt.webwallet.service.exchange.IssuanceService
import id.walt.webwallet.usecase.event.EventUseCase
import kotlinx.datetime.Clock
import kotlinx.uuid.UUID

class ExplicitClaimStrategy(
    private val issuanceService: IssuanceService,
    private val credentialService: CredentialsService,
    private val eventUseCase: EventUseCase,
) {
    suspend fun claim(
        tenant: String, account: UUID, wallet: UUID, did: String, offer: String, pending: Boolean = true
    ): List<WalletCredential> = issuanceService.useOfferRequest(
        offer = offer,
        credentialWallet = SSIKit2WalletService.getCredentialWallet(did),
        clientId = SSIKit2WalletService.testCIClientConfig.clientID
    ).map {
        WalletCredential(
            wallet = wallet,
            id = it.id,
            document = it.document,
            disclosures = it.disclosures,
            addedOn = Clock.System.now(),
            manifest = it.manifest,
            deletedOn = null,
            pending = pending,
        ).also { credential ->
            eventUseCase.log(
                action = EventType.Credential.Receive,
                originator = "", //parsedOfferReq.credentialOffer!!.credentialIssuer,
                tenant = tenant,
                accountId = account,
                walletId = wallet,
                data = eventUseCase.credentialEventData(credential = credential, type = it.type),
                credentialId = credential.id,
            )
        }
    }.also {
        credentialService.add(
            wallet = wallet, credentials = it.toTypedArray()
        )
    }
}