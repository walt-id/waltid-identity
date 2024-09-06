package id.walt.webwallet.usecase.claim

import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.service.events.EventType
import id.walt.webwallet.service.exchange.IssuanceService
import id.walt.webwallet.usecase.event.EventLogUseCase
import kotlinx.datetime.Clock
import kotlinx.uuid.UUID

object ClaimCommons {

    fun convertCredentialDataResultToWalletCredential(
        credentialDataResult: IssuanceService.CredentialDataResult,
        walletId: UUID,
        pending: Boolean,
    ) = WalletCredential(
        wallet = walletId,
        id = credentialDataResult.id,
        document = credentialDataResult.document,
        disclosures = credentialDataResult.disclosures,
        addedOn = Clock.System.now(),
        manifest = credentialDataResult.manifest,
        deletedOn = null,
        pending = pending,
        format = credentialDataResult.format,
    )

    fun addReceiveCredentialToUseCaseLog(
        tenant: String,
        account: UUID,
        wallet: UUID,
        credential: WalletCredential,
        credentialType: String,
        eventUseCase: EventLogUseCase,
    ) {
        eventUseCase.log(
            action = EventType.Credential.Receive,
            originator = "", //parsedOfferReq.credentialOffer!!.credentialIssuer,
            tenant = tenant,
            accountId = account,
            walletId = wallet,
            data = eventUseCase.credentialEventData(
                credential = credential,
                subject = eventUseCase.subjectData(credential),
                organization = eventUseCase.issuerData(credential),
                type = credentialType
            ),
            credentialId = credential.id,
        )
    }

    fun storeWalletCredentials(
        wallet: UUID,
        credentials: List<WalletCredential>,
        credentialService: CredentialsService,
    ) {
        credentialService.add(
            wallet = wallet,
            credentials = credentials.toTypedArray(),
        )
    }
}