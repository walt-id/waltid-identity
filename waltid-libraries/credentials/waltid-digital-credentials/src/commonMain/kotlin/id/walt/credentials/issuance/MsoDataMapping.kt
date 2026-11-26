package id.walt.credentials.issuance

import id.walt.credentials.issuance.CredentialDataMergeUtils.isTemplate
import id.walt.mdoc.issuance.MsoData
import id.walt.mdoc.issuance.MsoValidityResolver
import id.walt.mdoc.issuance.ResolvedMsoValidity
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock
import kotlin.time.Instant

/** Resolves literal timestamps and issuance templates before applying the mdoc validity rules. */
object MsoDataMapping {
    suspend fun resolve(
        msoData: MsoData?,
        signed: Instant = Clock.System.now(),
        fallbackValidUntil: Instant? = null,
        clock: Clock = Clock.System,
        requireExpectedUpdateWithinWindow: Boolean = false,
    ): ResolvedMsoValidity {
        msoData?.requireNonBlankFields()
        val functions = dataFunctionsFor(clock)
        return MsoValidityResolver.resolve(
            signed = signed,
            validFrom = resolveInstant(msoData?.validFrom, "validFrom", functions),
            explicitValidUntil = resolveInstant(msoData?.validUntil, "validUntil", functions),
            expectedUpdate = resolveInstant(msoData?.expectedUpdate, "expectedUpdate", functions),
            fallbackValidUntil = fallbackValidUntil,
            requireExpectedUpdateWithinWindow = requireExpectedUpdateWithinWindow,
        )
    }

    private suspend fun resolveInstant(
        raw: String?,
        fieldName: String,
        functions: Map<String, suspend (CredentialDataMergeUtils.FunctionCall) -> JsonElement>,
    ): Instant? {
        if (raw.isNullOrBlank()) return null
        val primitive = JsonPrimitive(raw)
        val resolved = try {
            if (primitive.isTemplate()) {
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
        return parseInstant(resolved, fieldName)
    }

    private fun parseInstant(element: JsonElement, fieldName: String): Instant {
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
