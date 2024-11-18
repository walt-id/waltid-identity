@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.usecase.credential

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi


/*class CredentialStatusUseCase(
    private val credentialStatusServiceFactory: CredentialStatusServiceFactory,
) {
    suspend fun get(wallet: Uuid, credentialId: String): List<CredentialStatusResult> =
        credentialService.get(wallet, credentialId)?.parsedDocument?.let {
            getStatusEntry(it).fold(emptyList()) { acc, i ->
                acc.plus(credentialStatusServiceFactory.new(i.type).get(i))
            }
        } ?: error("Credential not found or invalid document for id: $credentialId")

    private fun getStatusEntry(json: JsonObject) = json.jsonObject["credentialStatus"]?.let {
        when (it) {
            is JsonArray -> Json.decodeFromJsonElement<List<StatusListEntry>>(it)
            is JsonObject -> listOf(Json.decodeFromJsonElement<StatusListEntry>(it))
            else -> null
        }
    } ?: emptyList()
}*/

@Serializable
data class CredentialStatusResult(
    val type: String,
    val result: Boolean,
    val message: String,
)
