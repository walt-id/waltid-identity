package id.walt.webwallet.usecase.claim

import id.walt.crypto.keys.Key
import id.walt.entrawallet.core.service.SSIKit2WalletService
import id.walt.entrawallet.core.service.exchange.IssuanceServiceExternalSignatures
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ExternalSignatureClaimStrategy(
    private val issuanceServiceExternalSignatures: IssuanceServiceExternalSignatures,
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
        credentialWallet = SSIKit2WalletService.getCredentialWallet(did),
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
        offeredCredentialProofsOfPossession: List<IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossession>,
    ) = issuanceServiceExternalSignatures.submitExternallySignedOfferRequest(
        offerURL = offerURL,
        credentialIssuerURL = credentialIssuerURL,
        credentialWallet = SSIKit2WalletService.getCredentialWallet(did),
        offeredCredentialProofsOfPossession = offeredCredentialProofsOfPossession,
        accessToken = accessToken,
    ).map { credentialDataResult ->
        ClaimCommons.convertCredentialDataResultToWalletCredential(
            credentialDataResult,
            walletId,
            pending,
        )
    }
}
