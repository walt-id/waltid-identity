package id.walt.webwallet.service.credentials.status

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable(CredentialStatusEntryBaseSerializer::class)
sealed class CredentialStatusEntry {
    abstract val type: String
}

@Serializable
data class StatusListEntry(
    val id: String,
    override val type: String, //"RevocationList2021Status", "BitstringStatusListEntry", "StatusList2021Entry"
    val statusPurpose: String = "revocation",
    val statusListIndex: ULong,
    val statusListCredential: String,
) : CredentialStatusEntry()


object CredentialStatusEntryBaseSerializer : JsonContentPolymorphicSerializer<CredentialStatusEntry>(
    CredentialStatusEntry::class,
) {
    override fun selectDeserializer(
        element: JsonElement,
    ): DeserializationStrategy<CredentialStatusEntry> {
        val json = element.jsonObject
        val type = json.getValue("type").jsonPrimitive.content
        return when (type) {
            "RevocationList2021Status", "BitstringStatusListEntry", "StatusList2021Entry" -> StatusListEntry.serializer()
            else -> throw IllegalArgumentException("$type is not a supported Base type.")
        }
    }
}