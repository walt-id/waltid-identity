import id.walt.mdoc.objects.mso.ValidityInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

class ValidityInfoTest {
    private val validFrom = Instant.parse("2025-10-15T02:42:42Z")
    private val validUntil = Instant.parse("2026-10-15T03:42:42Z")
    private val validity = ValidityInfo(validFrom, validFrom, validUntil)

    @Test
    fun acceptsBothValidityBoundariesAndAnInstantWithinTheWindow() {
        for (time in listOf(validFrom, Instant.parse("2025-10-15T12:00:00Z"), validUntil)) {
            validity.validate(time)
        }
    }

    @Test
    fun rejectsAnInstantImmediatelyBeforeTheWindow() {
        val failure = assertFailsWith<IllegalArgumentException> {
            validity.validate(validFrom - 1.nanoseconds)
        }
        assertEquals("MSO is not yet valid (becomes valid in 1ns)", failure.message)
    }

    @Test
    fun rejectsAnInstantImmediatelyAfterTheWindow() {
        val failure = assertFailsWith<IllegalArgumentException> {
            validity.validate(validUntil + 1.nanoseconds)
        }
        assertEquals("MSO is no longer valid (expired 1ns ago)", failure.message)
    }
}
