package id.walt.openid4vci.mdoc

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class MsoValidityResolverTest {

    @Test
    fun `merges override fields onto profile msoData`() {
        val profile = MsoData(validFrom = "<timestamp>", validUntil = "<timestamp-in:365d>")
        val merged = profile.merge(MsoData(expectedUpdate = "<timestamp-in:180d>"))

        assertEquals("<timestamp>", merged.validFrom)
        assertEquals("<timestamp-in:365d>", merged.validUntil)
        assertEquals("<timestamp-in:180d>", merged.expectedUpdate)
    }

    @Test
    fun `defaults validUntil to 365 days and omits expectedUpdate`() = runTest {
        val signed = Clock.System.now()
        val resolved = MsoValidityResolver.resolve(null, signed)

        assertNull(resolved.validFrom)
        assertNull(resolved.expectedUpdate)
        assertTrue(resolved.validUntil - signed in (364.days..366.days))
    }

    @Test
    fun `resolves static ISO-8601 values`() = runTest {
        val signed = Clock.System.now()
        val validFrom = signed.toString()
        val validUntil = signed.plus(365.days * 5).toString()
        val expectedUpdate = signed.plus(180.days).toString()

        val resolved = MsoValidityResolver.resolve(
            MsoData(validFrom = validFrom, validUntil = validUntil, expectedUpdate = expectedUpdate),
            signed,
        )

        assertEquals(signed.epochSeconds, resolved.validFrom?.epochSeconds)
        assertEquals(signed.plus(365.days * 5).epochSeconds, resolved.validUntil.epochSeconds)
        assertEquals(signed.plus(180.days).epochSeconds, resolved.expectedUpdate?.epochSeconds)
    }

    @Test
    fun `resolves timestamp data functions for all three fields`() = runTest {
        val signed = Clock.System.now()
        val resolved = MsoValidityResolver.resolve(
            MsoData(
                validFrom = "<timestamp>",
                validUntil = "<timestamp-in:365d>",
                expectedUpdate = "<timestamp-in:180d>",
            ),
            signed,
        )

        val validFrom = assertNotNull(resolved.validFrom)
        assertTrue(validFrom - signed in (-2.days..2.days))
        assertTrue(resolved.validUntil - validFrom in (364.days..366.days))
        assertTrue(assertNotNull(resolved.expectedUpdate) - validFrom in (179.days..181.days))
    }

    @Test
    fun `rejects unknown data function`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            MsoValidityResolver.resolve(MsoData(validUntil = "<not-a-function>"))
        }
    }

    @Test
    fun `rejects unparseable timestamp`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            MsoValidityResolver.resolve(MsoData(validUntil = "not-a-date"))
        }
    }

    @Test
    fun `rejects validUntil before validFrom`() = runTest {
        val signed = Clock.System.now()
        assertFailsWith<IllegalArgumentException> {
            MsoValidityResolver.resolve(
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
            MsoValidityResolver.resolve(
                MsoData(validFrom = signed.minus(2.days).toString()),
                signed,
            )
        }
    }

    @Test
    fun `rejects expectedUpdate after validUntil`() = runTest {
        val signed = Clock.System.now()
        assertFailsWith<IllegalArgumentException> {
            MsoValidityResolver.resolve(
                MsoData(
                    validUntil = signed.plus(10.days).toString(),
                    expectedUpdate = signed.plus(20.days).toString(),
                ),
                signed,
            )
        }
    }

    @Test
    fun `rejects sub-second window that collapses to equal tdates`() = runTest {
        val signed = Instant.parse("2026-09-03T12:00:00Z")
        assertFailsWith<IllegalArgumentException> {
            MsoValidityResolver.resolve(
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
        val resolved = MsoValidityResolver.resolve(
            msoData = null,
            signed = signed,
            fallbackValidUntil = fallback,
        )

        assertEquals(fallback.epochSeconds, resolved.validUntil.epochSeconds)
    }

    @Test
    fun `explicit msoData validUntil wins over fallback`() = runTest {
        val signed = Instant.parse("2026-09-03T12:00:00Z")
        val fallback = Instant.parse("2026-12-01T00:00:00Z")
        val explicit = Instant.parse("2027-09-03T12:00:00Z")
        val resolved = MsoValidityResolver.resolve(
            msoData = MsoData(validUntil = explicit.toString()),
            signed = signed,
            fallbackValidUntil = fallback,
        )

        assertEquals(explicit.epochSeconds, resolved.validUntil.epochSeconds)
    }
}
