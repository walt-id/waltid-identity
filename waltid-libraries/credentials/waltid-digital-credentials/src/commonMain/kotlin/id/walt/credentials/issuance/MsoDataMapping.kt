package id.walt.credentials.issuance

import id.walt.credentials.issuance.CredentialDataMergeUtils.isTemplate
import id.walt.mdoc.issuance.MsoData
import id.walt.mdoc.issuance.MsoValidityResolver
import id.walt.mdoc.issuance.ResolvedMsoValidity
import id.walt.mdoc.issuance.roundMdocTimestampDownToTwelveHours
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock
import kotlin.time.Instant

/** Resolves literal timestamps and issuance templates before applying the mdoc validity rules. */
object MsoDataMapping {
    private val timestampFunctions = setOf(
        "timestamp", "timestamp-seconds", "timestamp-in", "timestamp-in-seconds",
        "timestamp-before", "timestamp-before-seconds", "timestamp-ebsi", "timestamp-ebsi-in",
    )

    suspend fun resolve(
        msoData: MsoData?,
        signed: Instant = Clock.System.now(),
        fallbackValidUntil: Instant? = null,
        clock: Clock = Clock.System,
        requireExpectedUpdateWithinWindow: Boolean = false,
        roundTimestampFunctions: Boolean = false,
    ): ResolvedMsoValidity {
        msoData?.requireNonBlankFields()
        val functions = dataFunctionsFor(clock)
        return MsoValidityResolver.resolve(
            signed = signed,
            validFrom = resolveInstant(msoData?.validFrom, "validFrom", functions, roundTimestampFunctions, signed),
            explicitValidUntil = resolveInstant(msoData?.validUntil, "validUntil", functions, roundTimestampFunctions),
            expectedUpdate = resolveInstant(msoData?.expectedUpdate, "expectedUpdate", functions, roundTimestampFunctions),
            fallbackValidUntil = fallbackValidUntil,
            requireExpectedUpdateWithinWindow = requireExpectedUpdateWithinWindow,
        )
    }

    private suspend fun resolveInstant(
        raw: String?,
        fieldName: String,
        functions: Map<String, suspend (CredentialDataMergeUtils.FunctionCall) -> JsonElement>,
        roundTimestampFunctions: Boolean,
        signed: Instant? = null,
    ): Instant? {
        if (raw.isNullOrBlank()) return null
        val primitive = JsonPrimitive(raw)
        val isTemplate = primitive.isTemplate()
        val resolved = try {
            if (isTemplate) {
                CredentialDataMergeUtils.getTemplateData(
                    functionCall = raw,
                    dataFunctions = functions,
                    context = emptyMap(),
                    functionHistory = mutableMapOf(),
                )
            } else {
                primitive
            }
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid msoData.$fieldName data function: ${e.message}", e)
        }
        val timestamp = parseInstant(resolved, fieldName)
        if (!roundTimestampFunctions || !isTemplate) return timestamp
        val function = raw.substring(1, raw.lastIndex).substringBefore(':')
        if (function !in timestampFunctions) return timestamp

        val rounded = roundMdocTimestampDownToTwelveHours(timestamp)
        // A new signer certificate can put signed after the start of the UTC window.
        // Preserve a valid start in that window without making an earlier override valid.
        return if (signed != null && timestamp >= signed) maxOf(signed, rounded) else rounded
    }

    internal fun parseInstant(element: JsonElement, fieldName: String): Instant {
        val primitive = element as? JsonPrimitive
            ?: throw IllegalArgumentException("msoData.$fieldName must resolve to a timestamp")
        primitive.longOrNull?.let { return Instant.fromEpochSeconds(it) }
        val content = primitive.contentOrNull
            ?: throw IllegalArgumentException("msoData.$fieldName must resolve to a timestamp")
        return try {
            Instant.parse(content)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("msoData.$fieldName is not a valid ISO-8601 timestamp: $content", e)
        }
    }
}
