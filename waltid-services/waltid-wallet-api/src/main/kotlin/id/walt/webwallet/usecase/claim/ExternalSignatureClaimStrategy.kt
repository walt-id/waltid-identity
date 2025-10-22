package id.walt.webwallet.usecase.claim

import id.walt.crypto.keys.Key
import id.walt.webwallet.service.SSIKit2WalletService
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.service.exchange.IssuanceServiceExternalSignatures
import id.walt.webwallet.service.exchange.IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossession
import id.walt.webwallet.usecase.event.EventLogUseCase
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ExternalSignatureClaimStrategy(
    private val issuanceServiceExternalSignatures: IssuanceServiceExternalSignatures,
    private val credentialService: CredentialsService,
    private val eventUseCase: EventLogUseCase,
) {

    suspend fun prepareCredentialClaim(
        did: String,
        didAuthKeyId: String,
        publicKey: Key,
        offerURL: String,
    ) = issuanceServiceExternalSignatures.prepareExternallySignedOfferRequest(
        offerURL = offerURL,
        did = did,
        didAuthKeyId = didAuthKeyId,
        publicKey = publicKey,
    )

    suspend fun submitCredentialClaim(
        tenantId: String,
        accountId: Uuid,
        walletId: Uuid,
        pending: Boolean = true,
        did: String,
        offerURL: String,
        credentialIssuerURL: String,
        accessToken: String?,
        offeredCredentialProofsOfPossession: List<OfferedCredentialProofOfPossession>,
    ) = issuanceServiceExternalSignatures.submitExternallySignedOfferRequest(
        offerURL = offerURL,
        credentialIssuerURL = credentialIssuerURL,
        credentialWallet = SSIKit2WalletService.getCredentialWallet(accountId, walletId, did),
        offeredCredentialProofsOfPossession = offeredCredentialProofsOfPossession,
        accessToken = accessToken,
    ).map { credentialDataResult ->
        ClaimCommons.convertCredentialDataResultToWalletCredential(
            credentialDataResult,
            walletId,
            pending,
        ).also { credential ->
            ClaimCommons.addReceiveCredentialToUseCaseLog(
                tenantId,
                accountId,
                walletId,
                credential,
                credentialDataResult.type,
                eventUseCase,
            )
        }
    }.also {
        ClaimCommons.storeWalletCredentials(
            walletId,
            it,
            credentialService,
        )
    }
}
