package id.walt.openid4vci.mdoc

import id.walt.w3c.issuance.dataFunctions
import id.walt.w3c.utils.CredentialDataMergeUtils
import id.walt.w3c.utils.CredentialDataMergeUtils.isTemplate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

data class ResolvedMsoValidity(
    val validFrom: Instant?,
    val validUntil: Instant,
    val expectedUpdate: Instant?,
)

object MsoValidityResolver {

    suspend fun resolve(
        msoData: MsoData?,
        signed: Instant = Clock.System.now(),
        fallbackValidUntil: Instant? = null,
    ): ResolvedMsoValidity {
        val signedTDate = signed.asTDate()
        val validFrom = resolveInstant(msoData?.validFrom, "validFrom")?.asTDate()
        val validUntil = (
            resolveInstant(msoData?.validUntil, "validUntil")
                ?: fallbackValidUntil
                ?: signed.plus(365.days)
            ).asTDate()
        val expectedUpdate = resolveInstant(msoData?.expectedUpdate, "expectedUpdate")?.asTDate()

        if (validFrom != null && validFrom < signedTDate) {
            throw IllegalArgumentException("msoData.validFrom cannot be before the MSO signed time")
        }
        if (validUntil <= (validFrom ?: signedTDate)) {
            throw IllegalArgumentException("msoData.validUntil must be after validFrom")
        }
        if (expectedUpdate != null) {
            val windowStart = validFrom ?: signedTDate
            if (expectedUpdate < windowStart) {
                throw IllegalArgumentException("msoData.expectedUpdate cannot be before validFrom")
            }
            if (expectedUpdate > validUntil) {
                throw IllegalArgumentException("msoData.expectedUpdate cannot be after validUntil")
            }
        }

        return ResolvedMsoValidity(
            validFrom = validFrom,
            validUntil = validUntil,
            expectedUpdate = expectedUpdate,
        )
    }

    private fun Instant.asTDate(): Instant = Instant.fromEpochSeconds(epochSeconds)

    private suspend fun resolveInstant(raw: String?, fieldName: String): Instant? {
        if (raw.isNullOrBlank()) return null
        val primitive = JsonPrimitive(raw)
        val resolved = try {
            if (primitive.isTemplate()) {
                CredentialDataMergeUtils.getTemplateData(
                    functionCall = raw,
                    dataFunctions = dataFunctions,
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
