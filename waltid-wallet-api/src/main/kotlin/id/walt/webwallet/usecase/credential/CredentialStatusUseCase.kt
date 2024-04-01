package id.walt.webwallet.usecase.credential

import id.walt.webwallet.service.credentials.CredentialsService
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID

class CredentialStatusUseCase(
    private val credentialService: CredentialsService,
) {
    fun get(wallet: UUID, credentialId: String): List<CredentialStatusResult> =
        credentialService.get(wallet, credentialId)?.parsedDocument?.let {
            val statusEntries = getStatusEntry(it)
            emptyList()
        } ?: error("Credential not found or invliad document for id: $credentialId")

    private fun getStatusEntry(json: JsonObject) = json.jsonObject["credentialStatus"]?.let {
        when (it) {
            is JsonArray -> Json.decodeFromJsonElement<List<StatusListEntry>>(it)
            is JsonObject -> listOf(Json.decodeFromJsonElement<StatusListEntry>(it))
            else -> null
        }
    } ?: emptyList()
}

@Serializable
data class CredentialStatusResult(
    val type: String,
    val result: Boolean,
)

@Serializable
data class StatusListEntry(
    val id: String,
    val type: String, //"RevocationList2021Status", "BitstringStatusListEntry", "StatusList2021Entry"
    val statusPurpose: String = "revocation",
    val statusListIndex: Long,
    val statusListCredential: String,
)