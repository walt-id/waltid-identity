package id.walt.wallet2.data

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** A list row. [typeMetadataJson] contains only format-specific type fields used to title a card. */
data class WalletCredentialSummary(
    val id: String,
    val format: String,
    val issuer: String?,
    val subject: String?,
    val label: String?,
    val addedAt: String?,
    val typeMetadataJson: String,
    val metadataJson: String?,
)

/** Optional capability for stores that can read a wallet inventory without loading signed credentials. */
interface WalletCredentialInventory {
    suspend fun credentialSummaries(): List<WalletCredentialSummary>
}

suspend fun WalletCredentialStore.readCredentialSummaries(): List<WalletCredentialSummary> =
    if (this is WalletCredentialInventory) credentialSummaries()
    else listCredentials().map { it.toCredentialSummary() }.toList()

fun StoredCredential.toCredentialSummary(): WalletCredentialSummary = WalletCredentialSummary(
    id, credential.format, credential.issuer, credential.subject, label, addedAt?.toString(),
    credential.credentialData.credentialTypeMetadata().toString(), metadata?.toString(),
)

/** Selects the type hints recognized by credential title renderers. */
fun JsonObject.credentialTypeMetadata(): JsonObject {
    val payload = this
    fun JsonElement?.typeValue(): JsonElement? = when (this) {
        is JsonPrimitive -> takeIf { it.isString }
        is JsonArray -> JsonArray(filterIsInstance<JsonPrimitive>().filter { it.isString })
        else -> null
    }
    return buildJsonObject {
        for (key in listOf("vct", "docType", "doctype", "doc_type")) {
            (payload[key] as? JsonPrimitive)?.takeIf { it.isString }?.let { put(key, it) }
        }
        payload["type"].typeValue()?.let { put("type", it) }
        (payload["vc"] as? JsonObject)?.get("type").typeValue()?.let {
            put("vc", JsonObject(mapOf("type" to it)))
        }
    }
}
