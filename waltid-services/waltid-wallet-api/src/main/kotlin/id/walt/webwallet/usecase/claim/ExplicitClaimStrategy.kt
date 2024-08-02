package id.walt.webwallet.usecase.claim

import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.SSIKit2WalletService
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.service.events.EventType
import id.walt.webwallet.service.exchange.IssuanceService
import id.walt.webwallet.service.exchange.IssuanceService.ResolveAuthorizationRequestResponse
import id.walt.webwallet.service.exchange.authReqSessions
import id.walt.webwallet.usecase.event.EventLogUseCase
import io.ktor.client.statement.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.uuid.UUID

class ExplicitClaimStrategy(
    private val issuanceService: IssuanceService,
    private val credentialService: CredentialsService,
    private val eventUseCase: EventLogUseCase,
) {
    suspend fun claim(
        tenant: String, account: UUID, wallet: UUID, did: String, offer: String, pending: Boolean = true,
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
            wallet = wallet, credentials = it.toTypedArray()
        )
    }

    // claimAuthorize creates auth req and redirects the client
    suspend fun claimAuthorize(
        tenant: String, account: UUID, wallet: UUID, did: String, offer: String, consentPageUri: String
    ): String {
        return issuanceService.useOfferRequestAuthorize(
            offer = offer,
            accountId = account,
            credentialWallet = SSIKit2WalletService.getCredentialWallet(did),
            clientId = SSIKit2WalletService.testCIClientConfig.clientID,
            walletId = wallet,
            consentPageUri = consentPageUri
        )
    }

    // resolveReceivedAuthorizationRequest resolves authorization requests coming to the wallet(e.g. ID Token or VP Token requests) via callbacks
    suspend fun resolveReceivedAuthorizationRequest(
        tenant: String, account: UUID, wallet: UUID , receivedAuthReq: AuthorizationRequest, authReqSessionId: String
    ): ResolveAuthorizationRequestResponse = issuanceService.resolveReceivedAuthorizationRequest(
        receivedAuthReq = receivedAuthReq,
        authReqSessionId = authReqSessionId,
        credentialWallet = SSIKit2WalletService.getCredentialWallet(authReqSessions[authReqSessionId]!!.authorizationRequest.clientId),
        clientId = SSIKit2WalletService.testCIClientConfig.clientID,
        walletId = wallet
    )

    // handleIdTokenRequest assembles the id token and provided to the requester via an HTTP call, if the response is a redirect, it follows it
    suspend fun useIdTokenRequest(
        tenant: String, account: UUID, wallet: UUID, did: String, authReq: AuthorizationRequest
    ): String = issuanceService.useIdTokenRequest(
        authReq = authReq,
        credentialWallet = SSIKit2WalletService.getCredentialWallet(did),
        clientId = SSIKit2WalletService.testCIClientConfig.clientID,
        walletId = wallet
    )

    // handleCallback validates the code, exchange code for token, exchange token for final credential
    suspend fun handleCallback(
        code: String, state: String, tenant: String, account: UUID, wallet: UUID, authReqSessionId: String, pending: Boolean = false
    ): List<WalletCredential> = issuanceService.handleCallback(
        code = code,
        state = state,
        credentialWallet = SSIKit2WalletService.getCredentialWallet(authReqSessions[authReqSessionId]!!.authorizationRequest.clientId),
        clientId = SSIKit2WalletService.testCIClientConfig.clientID,
        walletId = wallet,
        authReqSessionId = authReqSessionId
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
            wallet = wallet, credentials = it.toTypedArray()
        )
    }
}
