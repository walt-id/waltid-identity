package id.walt.openid4vci.mdoc

import id.walt.w3c.issuance.dataFunctionsFor
import id.walt.w3c.utils.CredentialDataMergeUtils
import id.walt.w3c.utils.CredentialDataMergeUtils.isTemplate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

enum class MsoValidUntilSource {
    EXPLICIT,
    FALLBACK,
    DEFAULT,
}

data class ResolvedMsoValidity(
    val validFrom: Instant?,
    val validUntil: Instant?,
    val expectedUpdate: Instant?,
    val validUntilSource: MsoValidUntilSource,
)

object MsoValidityResolver {

    suspend fun resolve(
        msoData: MsoData?,
        signed: Instant = Clock.System.now(),
        fallbackValidUntil: Instant? = null,
        clock: Clock = Clock.System,
        requireExpectedUpdateWithinWindow: Boolean = false,
    ): ResolvedMsoValidity {
        msoData?.requireNonBlankFields()
        val functions = dataFunctionsFor(clock)
        val signedTDate = signed.asTDate()
        val validFrom = resolveInstant(msoData?.validFrom, "validFrom", functions)?.asTDate()
        val explicitValidUntil = resolveInstant(msoData?.validUntil, "validUntil", functions)?.asTDate()
        val (validUntil, validUntilSource) = when {
            explicitValidUntil != null -> explicitValidUntil to MsoValidUntilSource.EXPLICIT
            fallbackValidUntil != null -> fallbackValidUntil.asTDate() to MsoValidUntilSource.FALLBACK
            else -> null to MsoValidUntilSource.DEFAULT
        }
        val expectedUpdate = resolveInstant(msoData?.expectedUpdate, "expectedUpdate", functions)?.asTDate()
        val windowStart = validFrom ?: signedTDate

        if (validFrom != null && validFrom < signedTDate) {
            throw IllegalArgumentException("msoData.validFrom cannot be before the MSO signed time")
        }
        if (validUntil != null && validUntil <= windowStart) {
            throw IllegalArgumentException("msoData.validUntil must be after validFrom")
        }
        if (requireExpectedUpdateWithinWindow && expectedUpdate != null) {
            val windowEnd = validUntil ?: signedTDate.plus(365.days).asTDate()
            if (expectedUpdate < windowStart) {
                throw IllegalArgumentException("msoData.expectedUpdate cannot be before validFrom")
            }
            if (expectedUpdate > windowEnd) {
                throw IllegalArgumentException("msoData.expectedUpdate cannot be after validUntil")
            }
        }

        return ResolvedMsoValidity(
            validFrom = validFrom,
            validUntil = validUntil,
            expectedUpdate = expectedUpdate,
            validUntilSource = validUntilSource,
        )
    }

    private fun Instant.asTDate(): Instant = Instant.fromEpochSeconds(epochSeconds)

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
