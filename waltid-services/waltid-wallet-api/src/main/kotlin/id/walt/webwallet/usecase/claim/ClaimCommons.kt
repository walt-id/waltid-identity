package id.walt.webwallet.usecase.claim

import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.service.events.EventType
import id.walt.webwallet.service.exchange.IssuanceService
import id.walt.webwallet.usecase.event.EventLogUseCase
import kotlinx.datetime.Clock
import kotlinx.uuid.UUID

object ClaimCommons {

    fun mapCredentialDataResultsToWalletCredentials(
        credentialResults: List<IssuanceService.CredentialDataResult>,
        walletId: UUID,
        tenantId: String,
        accountId: UUID,
        pending: Boolean,
        eventUseCase: EventLogUseCase,
        credentialService: CredentialsService,
    ) = credentialResults.map {
        convertCredentialDataResultToWalletCredential(
            it,
            walletId,
            pending,
        ).also { credential ->
            eventUseCase.log(
                action = EventType.Credential.Receive,
                originator = "", //parsedOfferReq.credentialOffer!!.credentialIssuer,
                tenant = tenantId,
                accountId = accountId,
                walletId = walletId,
                data = eventUseCase.credentialEventData(
                    credential = credential,
                    subject = eventUseCase.subjectData(credential),
                    organization = eventUseCase.issuerData(credential),
                    type = it.type
                ),
                credentialId = credential.id,
            )
        }
    }.also {
        credentialService.add(
            wallet = walletId,
            credentials = it.toTypedArray(),
        )
    }

    private fun convertCredentialDataResultToWalletCredential(
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
}