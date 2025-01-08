@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.usecase.event

import id.walt.crypto.keys.Key
import id.walt.did.dids.document.DidDocument
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.events.*
import id.walt.webwallet.utils.JsonUtils
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


class EventLogUseCase(
    private val eventService: EventService,
) {
    private val typePropertyNameByCredentialFormat = mapOf(
        CredentialFormat.jwt_vc_json to "type",
        CredentialFormat.sd_jwt_vc to "vct",
        CredentialFormat.mso_mdoc to "docType"
    )

    fun log(
        action: EventType.Action,
        originator: String,
        tenant: String,
        accountId: Uuid,
        walletId: Uuid,
        data: EventData,
        credentialId: String? = null,
        note: String? = null,
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

    fun count(walletId: Uuid, dataFilter: Map<String, List<String>>) = eventService.count(walletId, dataFilter)

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

    fun credentialEventData(
        credential: WalletCredential,
        subject: CredentialEventDataActor.Subject? = null,
        organization: CredentialEventDataActor.Organization? = null,
        type: String? = null,
    ) = CredentialEventData(
        ecosystem = EventDataNotAvailable,
        type = credential.parsedDocument?.let {
            getCredentialType(it, credential.format)
        } ?: EventDataNotAvailable,
        format = type ?: EventDataNotAvailable,
        proofType = EventDataNotAvailable,
        protocol = "oid4vp",
        credentialId = credential.id,
        //TODO: these calls are made multiple times (e.g. see notifications in [SilentClaimStrategy]
        logo = WalletCredential.getManifestLogo(credential.parsedManifest),
        subject = subject,
        organization = organization,
        //end TODO
    )

    fun subjectData(credential: WalletCredential) = CredentialEventDataActor.Subject(
        subjectId = credential.parsedDocument?.let {
            JsonUtils.tryGetData(it, "credentialSubject.id")?.jsonPrimitive?.content
        } ?: EventDataNotAvailable,
        subjectKeyType = EventDataNotAvailable,
    )

    fun issuerData(credential: WalletCredential) =
        WalletCredential.parseIssuerDid(credential.parsedDocument, credential.parsedManifest).let {
            CredentialEventDataActor.Organization.Issuer(
                did = it ?: EventDataNotAvailable,
                keyId = EventDataNotAvailable,
                keyType = EventDataNotAvailable,
            )
        }

    fun verifierData(request: AuthorizationRequest) = CredentialEventDataActor.Organization.Verifier(
        did = request.clientId.takeIf { it.isNotEmpty() } ?: EventDataNotAvailable,
        policies = emptyList(),//TODO: from input-descriptors?
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

    private fun getCredentialType(json: JsonObject, format: CredentialFormat) =
        typePropertyNameByCredentialFormat[format]?.let {
            JsonUtils.tryGetData(json, it)
        }?.let {
            when (it) {
                is JsonArray -> it.jsonArray.last()
                else -> it
            }
        }?.jsonPrimitive?.content

    data class EventFilterParameter(
        val accountId: Uuid,
        val walletId: Uuid,
        val offset: Long,
        val logFilter: EventLogFilter,
    )
}
