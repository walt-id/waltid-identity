package id.walt.credentials.issuance

import id.walt.mdoc.issuance.MsoData
import id.walt.mdoc.issuance.MsoValidUntilSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class MsoDataMappingTest {
    @Test
    fun `defaults validUntil provenance and omits expectedUpdate`() = runTest {
        val signed = Clock.System.now()
        val resolved = MsoDataMapping.resolve(null, signed)

        assertNull(resolved.validFrom)
        assertNull(resolved.expectedUpdate)
        assertNull(resolved.validUntil)
        assertEquals(MsoValidUntilSource.DEFAULT, resolved.validUntilSource)
    }

    @Test
    fun `resolves static ISO-8601 values`() = runTest {
        val signed = Clock.System.now()
        val validFrom = signed.toString()
        val validUntil = signed.plus(365.days * 5).toString()
        val expectedUpdate = signed.plus(180.days).toString()

        val resolved = MsoDataMapping.resolve(
            MsoData(validFrom = validFrom, validUntil = validUntil, expectedUpdate = expectedUpdate),
            signed,
            roundTimestampFunctions = true,
        )

        assertEquals(signed.epochSeconds, resolved.validFrom?.epochSeconds)
        assertEquals(signed.plus(365.days * 5).epochSeconds, resolved.validUntil?.epochSeconds)
        assertEquals(signed.plus(180.days).epochSeconds, resolved.expectedUpdate?.epochSeconds)
        assertEquals(MsoValidUntilSource.EXPLICIT, resolved.validUntilSource)
    }

    @Test
    fun `resolves timestamp data functions for all three fields`() = runTest {
        val signed = Instant.parse("2026-09-03T19:18:10Z")
        val resolved = MsoDataMapping.resolve(
            MsoData(
                validFrom = "<timestamp>",
                validUntil = "<timestamp-in:365d>",
                expectedUpdate = "<timestamp-in:180d>",
            ),
            signed,
            clock = InstantClock(signed),
        )

        val validFrom = assertNotNull(resolved.validFrom)
        assertEquals(signed.epochSeconds, validFrom.epochSeconds)
        assertEquals(signed.plus(365.days).epochSeconds, resolved.validUntil?.epochSeconds)
        assertEquals(signed.plus(180.days).epochSeconds, resolved.expectedUpdate?.epochSeconds)
    }

    @Test
    fun `rounds timestamp functions without moving validFrom before signed`() = runTest {
        val windowStart = Instant.parse("2026-09-03T12:00:00Z")
        val signed = Instant.parse("2026-09-03T14:10:05Z")
        val claimTime = Instant.parse("2026-09-03T19:18:10Z")
        val resolved = MsoDataMapping.resolve(
            MsoData(
                validFrom = "<timestamp-seconds>",
                validUntil = "<timestamp-in:365d>",
                expectedUpdate = "<timestamp-in-seconds:180d>",
            ),
            signed,
            clock = InstantClock(claimTime),
            roundTimestampFunctions = true,
        )

        assertEquals(signed, resolved.validFrom)
        assertEquals(windowStart + 365.days, resolved.validUntil)
        assertEquals(windowStart + 180.days, resolved.expectedUpdate)
    }

    @Test
    fun `rejects unknown data function`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            MsoDataMapping.resolve(MsoData(validUntil = "<not-a-function>"))
        }
    }

    @Test
    fun `rejects unparseable timestamp`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            MsoDataMapping.resolve(MsoData(validUntil = "not-a-date"))
        }
    }

    @Test
    fun `rejects validUntil before validFrom`() = runTest {
        val signed = Clock.System.now()
        assertFailsWith<IllegalArgumentException> {
            MsoDataMapping.resolve(
                MsoData(
                    validFrom = signed.plus(10.days).toString(),
                    validUntil = signed.plus(1.days).toString(),
                ),
                signed,
            )
        }
    }

    @Test
    fun `rejects validFrom before signed`() = runTest {
        val signed = Clock.System.now()
        assertFailsWith<IllegalArgumentException> {
            MsoDataMapping.resolve(
                MsoData(validFrom = signed.minus(2.days).toString()),
                signed,
            )
        }
    }

    @Test
    fun `allows expectedUpdate before validFrom unless the issuer policy is on`() = runTest {
        val signed = Instant.parse("2026-09-03T12:00:00Z")
        val resolved = MsoDataMapping.resolve(
            MsoData(
                validFrom = signed.plus(10.days).toString(),
                validUntil = signed.plus(30.days).toString(),
                expectedUpdate = signed.plus(1.days).toString(),
            ),
            signed,
        )
        assertNotNull(resolved.expectedUpdate)

        assertFailsWith<IllegalArgumentException> {
            MsoDataMapping.resolve(
                MsoData(
                    validFrom = signed.plus(10.days).toString(),
                    validUntil = signed.plus(30.days).toString(),
                    expectedUpdate = signed.plus(1.days).toString(),
                ),
                signed,
                requireExpectedUpdateWithinWindow = true,
            )
        }
    }

    @Test
    fun `rejects expectedUpdate after validUntil only when the issuer policy is on`() = runTest {
        val signed = Clock.System.now()
        val resolved = MsoDataMapping.resolve(
            MsoData(
                validUntil = signed.plus(10.days).toString(),
                expectedUpdate = signed.plus(20.days).toString(),
            ),
            signed,
        )
        assertNotNull(resolved.expectedUpdate)

        assertFailsWith<IllegalArgumentException> {
            MsoDataMapping.resolve(
                MsoData(
                    validUntil = signed.plus(10.days).toString(),
                    expectedUpdate = signed.plus(20.days).toString(),
                ),
                signed,
                requireExpectedUpdateWithinWindow = true,
            )
        }
    }

    @Test
    fun `rejects sub-second window that collapses to equal tdates`() = runTest {
        val signed = Instant.parse("2026-09-03T12:00:00Z")
        assertFailsWith<IllegalArgumentException> {
            MsoDataMapping.resolve(
                MsoData(
                    validFrom = "2026-09-03T12:00:00.100Z",
                    validUntil = "2026-09-03T12:00:00.900Z",
                ),
                signed,
            )
        }
    }

    @Test
    fun `uses fallback validUntil when msoData omits it`() = runTest {
        val signed = Instant.parse("2026-09-03T12:00:00Z")
        val fallback = Instant.parse("2026-12-01T00:00:00Z")
        val resolved = MsoDataMapping.resolve(
            msoData = null,
            signed = signed,
            fallbackValidUntil = fallback,
        )

        assertEquals(fallback.epochSeconds, resolved.validUntil?.epochSeconds)
        assertEquals(MsoValidUntilSource.FALLBACK, resolved.validUntilSource)
    }

    @Test
    fun `explicit msoData validUntil wins over fallback`() = runTest {
        val signed = Instant.parse("2026-09-03T12:00:00Z")
        val fallback = Instant.parse("2026-12-01T00:00:00Z")
        val explicit = Instant.parse("2027-09-03T12:00:00Z")
        val resolved = MsoDataMapping.resolve(
            msoData = MsoData(validUntil = explicit.toString()),
            signed = signed,
            fallbackValidUntil = fallback,
        )

        assertEquals(explicit.epochSeconds, resolved.validUntil?.epochSeconds)
        assertEquals(MsoValidUntilSource.EXPLICIT, resolved.validUntilSource)
    }
}
