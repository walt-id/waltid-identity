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
        val displayData = if (summary.format == MdocFormat) parsed.withoutNullObjectMembers() else parsed

        val groupedItems = displayData.entries
            .flatMap { (key, value) ->
                val path = ClaimPath.topLevel(key)
                value.toClaimRows(path = path, label = CredentialDisplayVocabulary.humanizedClaimLabel(key))
                    .map { row -> row.withMdocDisplaySemantics(summary.format) }
                    .map { row -> CredentialDisplayVocabulary.groupKind(row.path, summary.format) to row }
            }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .entries
            .sortedBy { it.key.order }
            .map { (group, rows) ->
                ClaimGroup(
                    title = group.title,
                    items = rows
                        .sortedWith { left, right ->
                            CredentialDisplayVocabulary.compareClaimPaths(left.path, right.path, summary.format)
                        }
                        .map { it.item },
                    initiallyExpanded = group.initiallyExpanded,
                )
            }

        return CredentialDetails(
            summary = summary,
            groups = groupedItems,
        )
    }

    internal fun toDisclosureValue(valueJson: String, displayValue: String?, path: ClaimPath): DisplayValue {
        val parsed = runCatching { json.parseToJsonElement(valueJson) }.getOrNull()
        if (parsed != null) {
            val parsedValue = parsed.toDisplayValue(path)
            if (parsedValue is DisplayValue.Text && !displayValue.isNullOrBlank()) {
                return DisplayValue.Text(displayValue)
            }
            return parsedValue
        }

        return displayValue
            ?.takeIf { it.isNotBlank() }
            ?.let(DisplayValue::Text)
            ?: DisplayValue.Raw(valueJson)
    }

    fun transactionDataGroups(items: List<WalletDemoTransactionDataItem>): List<ClaimGroup> {
        val baseTitles = items.map { item ->
            item.transactionDataTitle()
        }
        val titleCounts = baseTitles.groupingBy { it }.eachCount()
        val seenTitles = mutableMapOf<String, Int>()

        return items.mapIndexed { index, item ->
            val baseTitle = baseTitles[index]
            val seenCount = seenTitles.getOrElse(baseTitle) { 0 } + 1
            seenTitles[baseTitle] = seenCount
            val title = if (titleCounts.getValue(baseTitle) > 1) "$baseTitle $seenCount" else baseTitle
            val typePath = ClaimPath.transactionData(index, TransactionDataField.Type)
            val claimItems = buildList {
                addAll(
                    claimItemsFromJson(
                        rawJson = item.detailsJson,
                        path = ClaimPath.transactionData(index, TransactionDataField.Details),
                        fallbackLabel = CredentialDisplayVocabulary.transactionDataLabel(TransactionDataField.Details),
                    )
                )
                add(
                    ClaimItem(
                        path = typePath.itemPath,
                        label = CredentialDisplayVocabulary.transactionDataLabel(TransactionDataField.Type),
                        value = DisplayValue.Text(item.type),
                        rawValue = item.type,
                        roles = CredentialDisplayVocabulary.roles(typePath),
                    )
                )
                if (item.credentialQueryIds.isNotEmpty()) {
                    val queryPath = ClaimPath.transactionData(index, TransactionDataField.CredentialQueryIds)
                    add(
                        ClaimItem(
                            path = queryPath.itemPath,
                            label = CredentialDisplayVocabulary.transactionDataLabel(TransactionDataField.CredentialQueryIds),
                            value = DisplayValue.Text(item.credentialQueryIds.joinToString()),
                            rawValue = item.credentialQueryIds.joinToString(),
                            roles = CredentialDisplayVocabulary.roles(queryPath),
                        )
                    )
                }
            }

            ClaimGroup(title = title, items = claimItems)
        }
    }

    private fun WalletDemoTransactionDataItem.transactionDataTitle(): String {
        val title = displayName.trim()
        return when {
            title.isBlank() -> CredentialDisplayVocabulary.TransactionDataTitle
            title == type -> CredentialDisplayVocabulary.humanizedClaimLabel(title)
            else -> title
        }
    }

    private fun claimItemsFromJson(rawJson: String, path: ClaimPath, fallbackLabel: String): List<ClaimItem> {
        if (rawJson.isBlank()) {
            return emptyList()
        }

        val parsed = runCatching { json.parseToJsonElement(rawJson) }.getOrNull()
            ?: return listOf(
                ClaimItem(
                    path = path.itemPath,
                    pathComponents = path.components,
                    label = fallbackLabel,
                    value = DisplayValue.Raw(rawJson),
                    rawValue = rawJson,
                    roles = CredentialDisplayVocabulary.roles(path),
                )
            )

        return when (parsed) {
            is JsonObject -> parsed.entries.flatMap { (key, value) ->
                value.toClaimRows(
                    path = ClaimPath.child(path, key),
                    label = CredentialDisplayVocabulary.humanizedClaimLabel(key),
                ).map { row -> row.item }
            }
            else -> parsed.toClaimRows(path = path, label = fallbackLabel).map { row -> row.item }
        }
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

    private fun ClaimRow.withMdocDisplaySemantics(format: String): ClaimRow {
        val semantics = MdocClaimDisplaySemantics.describe(format = format, path = path.components)
            ?: return this
        val displayValue = when (semantics.valueKind) {
            MdocClaimValueKind.Boolean -> if (item.value is DisplayValue.BooleanValue) {
                item.value
            } else {
                DisplayValue.Text("Unsupported value")
            }
            MdocClaimValueKind.Binary -> item.value.toBinaryAvailability()
            MdocClaimValueKind.Other -> item.value
        }
        return copy(
            item = item.copy(
                label = semantics.label,
                value = displayValue,
            )
        )
    }

    private fun DisplayValue.toBinaryAvailability(): DisplayValue.Text {
        val byteCount = when (this) {
            is DisplayValue.Image -> byteCount
            is DisplayValue.ListValue -> values
                .takeIf { it.isNotEmpty() && it.all { value -> value is DisplayValue.NumberValue } }
                ?.size
            else -> null
        }
        return DisplayValue.Text(
            byteCount?.let { "Available, ${it.toReadableByteCount()}" } ?: "Available"
        )
    }

    private fun Int.toReadableByteCount(): String =
        if (this == 1) "1 byte" else "$this bytes"

    private fun JsonObject.withoutNullObjectMembers(): JsonObject =
        JsonObject(
            entries.mapNotNull { (key, value) ->
                if (value is JsonNull) null else key to value.withoutNullObjectMembers()
            }.toMap()
        )

    private fun JsonElement.withoutNullObjectMembers(): JsonElement =
        when (this) {
            is JsonObject -> withoutNullObjectMembers()
            is JsonArray -> JsonArray(map { element -> element.withoutNullObjectMembers() })
            else -> this
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
    private const val MdocFormat = "mso_mdoc"
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
