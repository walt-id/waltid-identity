package id.walt.webwallet.web.controllers

import id.walt.webwallet.db.models.AccountWalletMappings
import id.walt.webwallet.db.models.Notification
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.SSIKit2WalletService
import id.walt.webwallet.service.WalletServiceManager
import id.walt.webwallet.service.dids.DidsService
import id.walt.webwallet.service.events.Event
import id.walt.webwallet.service.events.EventType
import id.walt.webwallet.service.exchange.IssuanceService
import id.walt.webwallet.web.WebBaseRoutes.webWalletRoute
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.util.*
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.silentExchange() = webWalletRoute {
    route("api", {
        tags = listOf("WalletCredential Exchange")
    }) {
        post("useOfferRequest/{did}", {
            summary = "Silently claim credentials"
            request {
                pathParameter<String>("did") { description = "The DID to issue the credential(s) to" }
                body<String> {
                    description = "The offer request to use"
                }
            }
        }) {
            val did = call.parameters.getOrFail("did")
            val offer = call.receiveText()

            // claim offer
            val result = IssuanceService.useOfferRequest(
                offer = offer,
                credentialWallet = SSIKit2WalletService.getCredentialWallet(did),
                clientId = SSIKit2WalletService.testCIClientConfig.clientID
            ).mapNotNull {
                val credential = WalletCredential.parseDocument(it.document, it.id) ?: JsonObject(emptyMap())
                val manifest = WalletCredential.tryParseManifest(it.manifest) ?: JsonObject(emptyMap())
                val issuerDid = WalletCredential.parseIssuerDid(credential, manifest) ?: "n/a"
                val type = WalletServiceManager.credentialTypeSeeker.get(credential)
                //TODO: improve for same issuer - type values
                if (validateIssuer(issuerDid, type)) {
                    Pair(it, issuerDid)
                } else null
            }.map {
                prepareCredentialData(did = did, data = it.first, issuerDid = it.second)
            }.flatten().let {
                storeCredentials(it)
            }
            context.respond(HttpStatusCode.Accepted, result.size)
        }
    }
}

internal suspend fun validateIssuer(issuer: String, type: String) =
    WalletServiceManager.issuerTrustValidationService.validate(
        did = issuer, type = type, egfUri = "test"
    ) || true

internal suspend fun storeCredentials(credentials: List<Pair<WalletCredential, String?>>) = credentials.groupBy {
    it.first.wallet
}.flatMap { entry ->
    WalletServiceManager.credentialService.add(
        wallet = entry.key, credentials = entry.value.map { it.first }.toTypedArray()
    ).also {
        otherStuff(entry.key, entry.value, EventType.Credential.Receive)
    }
}

internal suspend fun otherStuff(wallet: UUID, credentials: List<Pair<WalletCredential, String?>>, type: EventType.Action) {
    //TODO: no dsl here
    transaction {
        AccountWalletMappings.select(AccountWalletMappings.accountId).where { AccountWalletMappings.wallet eq wallet }
            .firstOrNull()?.let { it[AccountWalletMappings.accountId] }
    }?.let {
        prepareEvents(it, credentials, type).runCatching {
            WalletServiceManager.eventUseCase.log(*this.toTypedArray())
        }
        prepareNotifications(it, credentials.map { it.first }, type.toString()).runCatching {
            WalletServiceManager.notificationUseCase.add(*this.toTypedArray())
            WalletServiceManager.notificationUseCase.send(*this.toTypedArray())
        }
    }
}

internal fun prepareCredentialData(
    did: String, data: IssuanceService.CredentialDataResult, issuerDid: String
) = DidsService.getWalletsForDid(did).map {
    Pair(
        WalletCredential(
            wallet = it,
            id = data.id,
            document = data.document,
            disclosures = data.disclosures,
            addedOn = Clock.System.now(),
            manifest = data.manifest,
            deletedOn = null,
            pending = WalletServiceManager.issuerUseCase.get(wallet = it, name = issuerDid).getOrNull()?.authorized
                ?: true,
        ), data.type
    )
}

internal fun prepareEvents(account: UUID, credentials: List<Pair<WalletCredential, String?>>, type: EventType.Action) =
    credentials.map {
        Event(
            action = type,
            tenant = "global",
            originator = "",
            account = account,
            wallet = it.first.wallet,
            data = WalletServiceManager.eventUseCase.credentialEventData(
                credential = it.first, type = it.second
            ),
            credentialId = it.first.id,
        )
    }

internal fun prepareNotifications(account: UUID, credentials: List<WalletCredential>, type: String) = credentials.map {
    Notification(
        account = account.toString(),
        wallet = it.wallet.toString(),
        type = type,
        status = false,
        addedOn = Clock.System.now(),
        data = Json.encodeToString(
            Notification.CredentialData(
                credentialId = it.id,
                logo = it.parsedManifest?.jsonObject?.get("display")?.jsonObject?.get("card")?.jsonObject?.get(
                    "logo"
                )?.jsonObject?.get("uri")?.jsonPrimitive?.content ?: "",//TODO: placeholder logo?
                detail = "todo"//TODO: compute the message
            )
        )
    )
}