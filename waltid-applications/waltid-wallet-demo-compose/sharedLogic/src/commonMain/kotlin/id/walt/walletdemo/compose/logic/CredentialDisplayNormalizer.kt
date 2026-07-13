package id.walt.walletdemo.compose.logic

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object CredentialDisplayNormalizer {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val valueDecoder = CredentialDisplayValueDecoder(json) { element, path -> element.toDisplayValue(path) }

    fun toDetails(summary: CredentialSummary): CredentialDetails {
        val rawJson = summary.credentialDataJson?.trim().orEmpty()
        if (rawJson.isBlank()) {
            return CredentialDetails(summary = summary, groups = emptyList())
        }

        val parsed = runCatching { json.parseToJsonElement(rawJson).jsonObject }.getOrNull()
            ?: return CredentialDetails(
                summary = summary,
                groups = emptyList(),
            )

        val groupedItems = parsed.entries
            .flatMap { (key, value) ->
                val path = ClaimPath.topLevel(key)
                value.toClaimRows(path = path, label = CredentialDisplayVocabulary.humanizedClaimLabel(key))
                    .map { row -> CredentialDisplayVocabulary.groupKind(row.path) to row.item }
            }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .entries
            .sortedBy { it.key.order }
            .map { (group, items) -> ClaimGroup(title = group.title, items = items) }

        return CredentialDetails(
            summary = summary,
            groups = groupedItems,
        )
    }

    private fun JsonElement.toClaimItem(path: ClaimPath, label: String): ClaimItem =
        ClaimItem(
            path = path.itemPath,
            pathComponents = path.components,
            label = label,
            value = toProtocolDisplayValue(path) ?: toDisplayValue(path),
            rawValue = toString(),
            roles = CredentialDisplayVocabulary.roles(path),
        )

    private fun JsonElement.toProtocolDisplayValue(path: ClaimPath): DisplayValue? =
        when (path.leaf) {
            "_sd" -> (this as? JsonArray)?.let { digests ->
                DisplayValue.Text(digests.hiddenClaimCommitmentsText())
            }
            "cnf" -> (this as? JsonObject)?.let { confirmationKey ->
                DisplayValue.Text(confirmationKey.confirmationKeyText())
            }
            "status" -> (this as? JsonObject)?.let { status ->
                DisplayValue.Text(status.credentialStatusText())
            }
            "credentialStatus", "credential_status" -> (this as? JsonObject)?.let { status ->
                DisplayValue.Text(status.credentialStatusText())
            }
            "vct" -> (this as? JsonPrimitive)?.contentOrNull?.let { credentialType ->
                DisplayValue.Text(credentialType.readableCredentialTypeText())
            }
            else -> null
        }

    private fun JsonElement.toClaimRows(path: ClaimPath, label: String): List<ClaimRow> {
        val item = toClaimItem(path = path, label = label)
        return flattenObjectForClaimRows(path = path, item = item)
    }

    private fun JsonElement.flattenObjectForClaimRows(path: ClaimPath, item: ClaimItem): List<ClaimRow> =
        when {
            item.value !is DisplayValue.ObjectValue -> listOf(ClaimRow(path = path, item = item))
            this is JsonObject -> entries.flatMap { (key, value) ->
                val childPath = ClaimPath.child(path, key)
                val childItem = value.toClaimItem(
                    path = childPath,
                    label = CredentialDisplayVocabulary.humanizedClaimLabel(key),
                )
                val rows = value.flattenObjectForClaimRows(path = childPath, item = childItem)
                if (
                    rows.size == 1 &&
                    rows.single().item.value is DisplayValue.Image &&
                    rows.single().item.label == CredentialDisplayVocabulary.humanizedClaimLabel(imageWrapperClaimName)
                ) {
                    listOf(rows.single().copy(item = rows.single().item.copy(label = item.label)))
                } else {
                    rows
                }
            }
            else -> item.flattenDisplayObjectForClaimRows()
        }

    private fun ClaimItem.flattenDisplayObjectForClaimRows(): List<ClaimRow> =
        when (val displayValue = value) {
            is DisplayValue.ObjectValue -> displayValue.entries.flatMap { entry ->
                val rows = entry.flattenDisplayObjectForClaimRows()
                if (
                    rows.size == 1 &&
                    rows.single().item.value is DisplayValue.Image &&
                    rows.single().item.label == CredentialDisplayVocabulary.humanizedClaimLabel(imageWrapperClaimName)
                ) {
                    listOf(rows.single().copy(item = rows.single().item.copy(label = label)))
                } else {
                    rows
                }
            }
            else -> listOf(ClaimRow(path = ClaimPath(itemPath = path, components = pathComponents), item = this))
        }

    private fun JsonElement.toDisplayValue(path: ClaimPath): DisplayValue =
        when (this) {
            JsonNull -> DisplayValue.NullValue
            is JsonObject -> DisplayValue.ObjectValue(
                entries = entries.map { (key, value) ->
                    value.toClaimItem(path = ClaimPath.child(path, key), label = CredentialDisplayVocabulary.humanizedClaimLabel(key))
                }
            )
            is JsonArray -> valueDecoder.imageFromByteArray(this, CredentialDisplayVocabulary.roles(path))
                ?: DisplayValue.ListValue(mapIndexed { index, value -> value.toDisplayValue(ClaimPath.indexed(path, index)) })
            is JsonPrimitive -> toPrimitiveDisplayValue(path)
        }

    private fun JsonPrimitive.toPrimitiveDisplayValue(path: ClaimPath): DisplayValue {
        booleanOrNull?.let { return DisplayValue.BooleanValue(it) }
        if (!isString) {
            content.formatEpochDateIfTemporal(path)?.let { return DisplayValue.Text(it) }
            doubleOrNull?.let { return DisplayValue.NumberValue(content) }
        }

        val text = contentOrNull.orEmpty()
        text.formatEpochDateIfTemporal(path)?.let { return DisplayValue.Text(it) }

        val decoded = valueDecoder.decodedString(text, path)
        if (decoded != null) {
            return decoded
        }

        return DisplayValue.Text(text)
    }

    private fun String.formatEpochDateIfTemporal(path: ClaimPath): String? {
        if (!CredentialDisplayVocabulary.hasRole(path = path, role = ClaimRole.Temporal)) return null
        val rawEpoch = trim().toLongOrNull() ?: return null
        val epochSeconds = when {
            rawEpoch < minimumCredibleEpochSeconds -> return null
            rawEpoch >= epochMillisecondsThreshold -> rawEpoch / 1_000
            else -> rawEpoch
        }

        return runCatching {
            Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(TimeZone.UTC).date.toString()
        }.getOrNull()
    }
    private const val minimumCredibleEpochSeconds = 100_000_000L
    private const val epochMillisecondsThreshold = 10_000_000_000L
    private const val imageWrapperClaimName = "elementValue"
}

private data class ClaimRow(
    val path: ClaimPath,
    val item: ClaimItem,
)

private fun JsonArray.hiddenClaimCommitmentsText(): String =
    when (size) {
        0 -> "No undisclosed claim values"
        1 -> "1 undisclosed claim value"
        else -> "$size undisclosed claim values"
    }

private fun JsonObject.credentialStatusText(): String {
    val statusList = this["status_list"] as? JsonObject
    if (statusList != null) {
        val index = statusList["idx"]?.jsonPrimitive?.contentOrNull
        val uri = statusList["uri"]?.jsonPrimitive?.contentOrNull
        return listOfNotNull(
            index?.let { "Status list index $it" },
            uri,
        ).joinToString(" - ").ifBlank { "Status list credential" }
    }

    val id = this["id"]?.jsonPrimitive?.contentOrNull
    val type = this["type"]?.jsonPrimitive?.contentOrNull
    return listOfNotNull(type, id).joinToString(" - ").ifBlank { toString() }
}

private fun String.readableCredentialTypeText(): String =
    CredentialDisplayVocabulary.readableCredentialType(this)
        ?.let { readable -> "$readable ($this)" }
        ?: this

private fun JsonObject.confirmationKeyText(): String {
    val jwk = this["jwk"] as? JsonObject
    if (jwk != null) {
        val keyType = jwk["kty"]?.jsonPrimitive?.contentOrNull
        val curve = jwk["crv"]?.jsonPrimitive?.contentOrNull
        return listOfNotNull("Key-bound credential", keyType, curve)
            .joinToString(" - ")
    }

    val keyId = this["kid"]?.jsonPrimitive?.contentOrNull
    if (!keyId.isNullOrBlank()) return "Key-bound credential - $keyId"

    return "Key-bound credential"
}
