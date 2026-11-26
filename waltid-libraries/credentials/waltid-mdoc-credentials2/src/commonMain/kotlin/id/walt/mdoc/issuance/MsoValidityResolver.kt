package id.walt.mdoc.issuance

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

    fun resolve(
        signed: Instant,
        validFrom: Instant? = null,
        explicitValidUntil: Instant? = null,
        expectedUpdate: Instant? = null,
        fallbackValidUntil: Instant? = null,
        requireExpectedUpdateWithinWindow: Boolean = false,
    ): ResolvedMsoValidity {
        val signedTDate = truncateMdocTDate(signed)
        val from = validFrom?.let(::truncateMdocTDate)
        val (validUntil, validUntilSource) = when {
            explicitValidUntil != null -> truncateMdocTDate(explicitValidUntil) to MsoValidUntilSource.EXPLICIT
            fallbackValidUntil != null -> truncateMdocTDate(fallbackValidUntil) to MsoValidUntilSource.FALLBACK
            else -> null to MsoValidUntilSource.DEFAULT
        }
        val update = expectedUpdate?.let(::truncateMdocTDate)
        val windowStart = from ?: signedTDate

        if (from != null && from < signedTDate) {
            throw IllegalArgumentException("msoData.validFrom cannot be before the MSO signed time")
        }
        if (validUntil != null && validUntil <= windowStart) {
            throw IllegalArgumentException("msoData.validUntil must be after validFrom")
        }
        if (requireExpectedUpdateWithinWindow && update != null) {
            val windowEnd = validUntil ?: truncateMdocTDate(signedTDate + 365.days)
            if (update < windowStart) {
                throw IllegalArgumentException("msoData.expectedUpdate cannot be before validFrom")
            }
            if (update > windowEnd) {
                throw IllegalArgumentException("msoData.expectedUpdate cannot be after validUntil")
            }
        }

        return ResolvedMsoValidity(
            validFrom = from,
            validUntil = validUntil,
            expectedUpdate = update,
            validUntilSource = validUntilSource,
        )
    }
}
