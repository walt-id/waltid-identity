package id.walt.webwallet.usecase.claim

import id.walt.webwallet.db.models.Notification
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.seeker.Seeker
import id.walt.webwallet.service.SSIKit2WalletService
import id.walt.webwallet.service.account.AccountsService
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.service.dids.DidsService
import id.walt.webwallet.service.events.Event
import id.walt.webwallet.service.events.EventType
import id.walt.webwallet.service.exchange.IssuanceService
import id.walt.webwallet.service.trust.TrustValidationService
import id.walt.webwallet.usecase.event.EventLogUseCase
import id.walt.webwallet.usecase.issuer.IssuerUseCase
import id.walt.webwallet.usecase.notification.NotificationDispatchUseCase
import id.walt.webwallet.usecase.notification.NotificationUseCase
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID

class SilentClaimStrategy(
    private val issuanceService: IssuanceService,
    private val credentialService: CredentialsService,
    private val issuerTrustValidationService: TrustValidationService,
    private val accountService: AccountsService,
    private val didService: DidsService,
    private val issuerUseCase: IssuerUseCase,
    private val eventUseCase: EventLogUseCase,
    private val notificationUseCase: NotificationUseCase,
    private val notificationDispatchUseCase: NotificationDispatchUseCase,
    private val credentialTypeSeeker: Seeker<String>,
) {
    suspend fun claim(did: String, offer: String) = issuanceService.useOfferRequest(
        offer = offer,
        credentialWallet = SSIKit2WalletService.getCredentialWallet(did),
        clientId = SSIKit2WalletService.testCIClientConfig.clientID
    ).mapNotNull {
        val credential = WalletCredential.parseDocument(it.document, it.id) ?: JsonObject(emptyMap())
        val manifest = WalletCredential.tryParseManifest(it.manifest) ?: JsonObject(emptyMap())
        val issuerDid = WalletCredential.parseIssuerDid(credential, manifest) ?: "n/a"
        val type = credentialTypeSeeker.get(credential)
        val egfUri = "test"
        //TODO: improve for same issuer - type values
        if (validateIssuer(issuerDid, type, egfUri)) {
            Pair(it, issuerDid)
        } else null
    }.map {
        prepareCredentialData(did = did, data = it.first, issuerDid = it.second)
    }.flatten().groupBy {
        it.first.wallet
    }.mapNotNull { entry ->
        val credentials = entry.value.map { it.first }.toTypedArray()
        storeCredentials(entry.key, credentials).getOrNull()?.let {
            accountService.getAccountForWallet(entry.key)?.run {
                createEvents("", this, entry.value, EventType.Credential.Receive)
                createNotifications(this, credentials, EventType.Credential.Receive)
            }
            entry.value.map { it.first.id }
        }
    }.flatten().fold(emptyList<String>()) { acc, i ->
        acc + i
    }

    private suspend fun validateIssuer(issuer: String, type: String, egfUri: String) =
        issuerTrustValidationService.validate(
            did = issuer, type = type, egfUri = egfUri
        )

    private fun storeCredentials(wallet: UUID, credentials: Array<WalletCredential>) = runCatching {
        credentialService.add(
            wallet = wallet, credentials = credentials
        )
    }

    private suspend fun createNotifications(
        account: UUID, credentials: Array<WalletCredential>, type: EventType.Action
    ) = prepareNotifications(account, credentials, type.toString()).runCatching {
        notificationUseCase.add(*this.toTypedArray())
        notificationDispatchUseCase.send(*this.toTypedArray())
    }

    private fun createEvents(
        tenant: String, account: UUID, credentials: List<Pair<WalletCredential, String?>>, type: EventType.Action
    ) = prepareEvents(account, tenant, credentials, type).runCatching {
        eventUseCase.log(*this.toTypedArray())
    }

    private fun prepareCredentialData(
        did: String, data: IssuanceService.CredentialDataResult, issuerDid: String
    ) = didService.getWalletsForDid(did).map {
        Pair(
            WalletCredential(
                wallet = it,
                id = data.id,
                document = data.document,
                disclosures = data.disclosures,
                addedOn = Clock.System.now(),
                manifest = data.manifest,
                deletedOn = null,
                pending = issuerUseCase.get(wallet = it, did = issuerDid).getOrNull()?.authorized ?: true,
            ), data.type
        )
    }

    private fun prepareEvents(
        account: UUID, tenant: String, credentials: List<Pair<WalletCredential, String?>>, type: EventType.Action
    ) = credentials.map {
        Event(
            action = type,
            tenant = tenant,
            originator = "",
            account = account,
            wallet = it.first.wallet,
            data = eventUseCase.credentialEventData(
                credential = it.first,
                subject = eventUseCase.subjectData(it.first),
                organization = eventUseCase.issuerData(it.first), type = it.second
            ),
            credentialId = it.first.id,
        )
    }

    private fun prepareNotifications(account: UUID, credentials: Array<WalletCredential>, type: String) =
        credentials.map {
            Notification(
                id = UUID.generateUUID().toString(),//TODO: converted back and forth (see notification-service)
                account = account.toString(),
                wallet = it.wallet.toString(),
                type = type,
                status = false,
                addedOn = Clock.System.now(),
                data = Notification.CredentialIssuanceData(
                    credentialId = it.id,
                    issuer = WalletCredential.parseIssuerDid(it.parsedDocument, it.parsedManifest) ?: "",
                    credentialType = credentialTypeSeeker.get(it.parsedDocument ?: JsonObject(emptyMap())),
                    logo = WalletCredential.getManifestLogo(it.parsedManifest) ?: "",
                )
            )
        }
}