@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.usecase.claim

import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.db.models.getAuthReqSessions
import id.walt.webwallet.service.SSIKit2WalletService
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.service.exchange.IssuanceService
import id.walt.webwallet.usecase.event.EventLogUseCase
import kotlinx.serialization.json.jsonPrimitive
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

    // claimAuthorize creates auth req and redirects the client
    @OptIn(ExperimentalUuidApi::class)
    suspend fun claimAuthorize(
        account: Uuid,
        wallet: Uuid,
        did: String,
        offer: String,
        successRedirectUri: String
    ): String {
        return issuanceService.useOfferRequestAuthorize(
            offer = offer,
            accountId = account,
            credentialWallet = SSIKit2WalletService.getCredentialWallet(did),
            walletId = wallet,
            successRedirectUri = successRedirectUri
        )
    }

    // handleCallback validates the code, exchange code for token, exchange token for final credential
    @OptIn(ExperimentalUuidApi::class)
    suspend fun handleCallback(
        code: String,
        state: String,
        tenant: String,
        account: Uuid,
        wallet: Uuid,
        authReqSessionId: String,
        pending: Boolean = false
    ): Pair<List<WalletCredential>, String> {
        val session = getAuthReqSessions(wallet = wallet, id = authReqSessionId)
        val credentialWallet =
            SSIKit2WalletService.getCredentialWallet(session!!.authorizationRequest["client_id"]!!.jsonPrimitive.content)

        val (credentials, successRedirectUri) = issuanceService.handleCallback(
            code = code,
            state = state,
            credentialWallet = credentialWallet,
            clientId = SSIKit2WalletService.testCIClientConfig.clientID,
            walletId = wallet,
            authReqSessionId = authReqSessionId
        )

        return credentials.map { credentialDataResult ->
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
        } to
                successRedirectUri
    }
}
