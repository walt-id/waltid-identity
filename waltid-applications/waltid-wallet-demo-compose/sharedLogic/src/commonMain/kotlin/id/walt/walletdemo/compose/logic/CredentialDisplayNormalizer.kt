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
                technicalGroups = listOf(
                    ClaimGroup(
                        title = CredentialDisplayVocabulary.RawCredentialDataTitle,
                        items = listOf(
                            ClaimItem(
                                path = ClaimItemPath.root(),
                                label = CredentialDisplayVocabulary.RawCredentialDataLabel,
                                value = DisplayValue.Raw(rawJson),
                                rawValue = rawJson,
                            )
                        ),
                    )
                ),
            )

        val groupedItems = parsed.entries
            .map { (key, value) ->
                val path = ClaimPath.topLevel(key)
                CredentialDisplayVocabulary.groupKind(path) to
                        value.toClaimItem(path = path, label = CredentialDisplayVocabulary.humanizedClaimLabel(key))
            }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .entries
            .sortedBy { it.key.order }
            .map { (group, items) -> ClaimGroup(title = group.title, items = items) }

        return CredentialDetails(
            summary = summary,
            groups = groupedItems,
            technicalGroups = listOf(
                ClaimGroup(
                    title = CredentialDisplayVocabulary.RawCredentialDataTitle,
                    items = listOf(
                        ClaimItem(
                            path = ClaimItemPath.root(),
                            label = CredentialDisplayVocabulary.CredentialDataJsonLabel,
                            value = DisplayValue.Raw(rawJson),
                            rawValue = rawJson,
                        )
                    ),
                )
            ),
        )
    }

    private fun JsonElement.toClaimItem(path: ClaimPath, label: String): ClaimItem =
        ClaimItem(
            path = path.itemPath,
            label = label,
            value = toDisplayValue(path),
            rawValue = toString(),
            roles = CredentialDisplayVocabulary.roles(path),
        )

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
}
