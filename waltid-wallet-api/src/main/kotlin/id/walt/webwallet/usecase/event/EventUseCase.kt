package id.walt.webwallet.usecase.event

import id.walt.crypto.keys.Key
import id.walt.did.dids.document.DidDocument
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.events.*
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.uuid.UUID

class EventUseCase(
    private val eventService: EventService
) {

    fun log(
        action: EventType.Action,
        originator: String,
        tenant: String,
        accountId: UUID,
        walletId: UUID,
        data: EventData,
        credentialId: String? = null,
        note: String? = null
    ) = log(
        Event(
            action = action,
            tenant = tenant,
            originator = originator,
            account = accountId,
            wallet = walletId,
            data = data,
            credentialId = credentialId,
            note = note,
        )
    )

    fun log(vararg event: Event) = eventService.add(event.toList())

    fun count(walletId: UUID, dataFilter: Map<String, String>) = eventService.count(walletId, dataFilter)

    fun get(parameter: EventFilterParameter) = eventService.get(
        accountId = parameter.accountId,
        walletId = parameter.walletId,
        limit = parameter.logFilter.limit,
        offset = parameter.offset,
        sortOrder = parameter.logFilter.sortOrder ?: "asc",
        sortBy = parameter.logFilter.sortBy ?: "",
        dataFilter = parameter.logFilter.data
    )

    fun delete(id: Int) = eventService.delete(id)

    fun credentialEventData(credential: WalletCredential, type: String?) = CredentialEventData(
        ecosystem = EventDataNotAvailable,
        issuerId = WalletCredential.parseIssuerDid(credential.parsedDocument, credential.parsedManifest)
            ?: EventDataNotAvailable,
        subjectId = credential.parsedDocument?.jsonObject?.get("credentialSubject")?.jsonObject?.get(
            "id"
        )?.jsonPrimitive?.content ?: EventDataNotAvailable,
        issuerKeyId = EventDataNotAvailable,
        issuerKeyType = EventDataNotAvailable,
        subjectKeyType = EventDataNotAvailable,
        credentialType = credential.parsedDocument?.jsonObject?.get("type")?.jsonArray?.last()?.jsonPrimitive?.content
            ?: EventDataNotAvailable,
        credentialFormat = type ?: EventDataNotAvailable,
        credentialProofType = EventDataNotAvailable,
        policies = emptyList(),
        protocol = "oid4vp",
        credentialId = credential.id,
    )

    fun didEventData(did: String, document: DidDocument) = didEventData(did, document.toString())

    fun didEventData(did: String, document: String) = DidEventData(
        did = did, document = document
    )

    suspend fun keyEventData(key: Key, kmsType: String) = keyEventData(
        id = key.getKeyId(), algorithm = key.keyType.name, kmsType = kmsType
    )

    fun keyEventData(id: String, algorithm: String, kmsType: String) = KeyEventData(
        id = id, algorithm = algorithm, keyManagementService = kmsType
    )

    data class EventFilterParameter(
        val accountId: UUID,
        val walletId: UUID,
        val offset: Long,
        val logFilter: EventLogFilter,
    )
}