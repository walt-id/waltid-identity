package id.walt.webwallet.usecase.claim

import id.walt.entrawallet.core.service.exchange.CredentialDataResult
import id.walt.entrawallet.core.utils.WalletCredential
import kotlin.time.Clock
import kotlin.uuid.Uuid

object ClaimCommons {

    fun convertCredentialDataResultToWalletCredential(
        credentialDataResult: CredentialDataResult,
        walletId: Uuid,
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
