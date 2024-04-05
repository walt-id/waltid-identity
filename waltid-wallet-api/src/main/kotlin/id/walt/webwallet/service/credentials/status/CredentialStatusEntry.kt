package id.walt.webwallet.service.credentials.status

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val RevocationStatusPurpose = "revocation"

@Serializable(CredentialStatusEntryBaseSerializer::class)
sealed class CredentialStatusEntry {
    abstract val type: String
}

@Serializable
data class StatusListEntry(
    val id: String,
    override val type: String, //"RevocationList2021Status", "BitstringStatusListEntry", "StatusList2021Entry"
    @SerialName("statusPurpose")
    val statusPurposeOptional: String? = RevocationStatusPurpose,
    val statusListIndex: ULong,
    val statusListCredential: String,
) : CredentialStatusEntry() {
    @Transient
    val statusPurpose = statusPurposeOptional!!//workaround for optional json field (but required for app logic)
}

object CredentialStatusEntryBaseSerializer : JsonContentPolymorphicSerializer<CredentialStatusEntry>(
    CredentialStatusEntry::class,
) {
    override fun selectDeserializer(
        element: JsonElement,
    ): DeserializationStrategy<CredentialStatusEntry> {
        val json = element.jsonObject
        val type = json.getValue("type").jsonPrimitive.content
        return when (type) {
            in CredentialStatusTypes.StatusList.type -> StatusListEntry.serializer()
            else -> throw IllegalArgumentException("$type is not a supported Base type.")
        }
    }
}

enum class CredentialStatusTypes(vararg val type: String) {
    StatusList("RevocationList2021Status", "BitstringStatusListEntry", "StatusList2021Entry")
}