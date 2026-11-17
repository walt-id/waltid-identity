import id.walt.mdoc.objects.mso.ValidityInfo
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class ValidityInfoClockTest {
    @Test
    fun publicValidationUsesTheCurrentTime() {
        val now = Clock.System.now()
        ValidityInfo(
            signed = now - 1.hours,
            validFrom = now - 1.hours,
            validUntil = now + 1.hours,
        ).validate()

        val expired = ValidityInfo(
            signed = now - 2.days,
            validFrom = now - 2.days,
            validUntil = now - 1.days,
        )
        val error = assertFailsWith<IllegalArgumentException> { expired.validate() }
        assertTrue(error.message.orEmpty().startsWith("MSO is no longer valid"))
    }

    @Test
    fun explicitValidationTimePreservesBothValidityBoundaries() {
        val start = Instant.parse("2025-10-15T02:42:42Z")
        val end = Instant.parse("2026-10-15T03:42:42Z")
        val validity = ValidityInfo(signed = start, validFrom = start, validUntil = end)

        validity.validateAt(start)
        validity.validateAt(end)
        assertFailsWith<IllegalArgumentException> { validity.validateAt(start - 1.hours) }
        assertFailsWith<IllegalArgumentException> { validity.validateAt(end + 1.hours) }
    }
}
