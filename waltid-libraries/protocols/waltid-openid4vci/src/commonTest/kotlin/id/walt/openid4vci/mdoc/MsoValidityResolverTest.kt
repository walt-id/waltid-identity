package id.walt.openid4vci.mdoc

import id.walt.w3c.issuance.InstantClock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class MsoValidityResolverTest {

    private val json = Json { explicitNulls = true }

    @Test
    fun `merges override fields onto profile msoData`() {
        val profile = MsoData(validFrom = "<timestamp>", validUntil = "<timestamp-in:365d>")
        val merged = profile.merge(MsoData(expectedUpdate = "<timestamp-in:180d>"))

        assertEquals("<timestamp>", merged.validFrom)
        assertEquals("<timestamp-in:365d>", merged.validUntil)
        assertEquals("<timestamp-in:180d>", merged.expectedUpdate)
    }

    @Test
    fun `omitted override fields inherit profile values`() {
        val profile = MsoData(validFrom = "<timestamp>", validUntil = "<timestamp-in:30d>", expectedUpdate = "<timestamp-in:10d>")
        val merged = profile.merge(MsoData(validFrom = "<timestamp-in:1d>"))

        assertEquals("<timestamp-in:1d>", merged.validFrom)
        assertEquals("<timestamp-in:30d>", merged.validUntil)
        assertEquals("<timestamp-in:10d>", merged.expectedUpdate)
    }

    @Test
    fun `rejects blank and whitespace override fields`() {
        val profile = MsoData(validUntil = "<timestamp-in:30d>")
        assertFailsWith<IllegalArgumentException> { profile.merge(MsoData(validUntil = "")) }
        assertFailsWith<IllegalArgumentException> { profile.merge(MsoData(validFrom = "  ")) }
        assertFailsWith<IllegalArgumentException> { profile.merge(MsoData(expectedUpdate = "\t")) }
    }

    @Test
    fun `json null expectedUpdate clears the profile value`() {
        val profile = MsoData(validUntil = "<timestamp-in:30d>", expectedUpdate = "<timestamp-in:10d>")
        val override = json.decodeFromString<MsoData>("""{"expectedUpdate":null}""")
        val merged = profile.merge(override)

        assertEquals("<timestamp-in:30d>", merged.validUntil)
        assertNull(merged.expectedUpdate)
        assertFalse(merged.expectedUpdateCleared)
    }

    @Test
    fun `json null validUntil inherits rather than clearing`() {
        val profile = MsoData(validUntil = "<timestamp-in:30d>")
        val override = json.decodeFromString<MsoData>("""{"validUntil":null}""")
        val merged = profile.merge(override)
        assertEquals("<timestamp-in:30d>", merged.validUntil)
    }

    @Test
    fun `json blank validUntil is rejected`() {
        assertFailsWith<Exception> {
            json.decodeFromString<MsoData>("""{"validUntil":""}""")
        }
        assertFailsWith<Exception> {
            json.decodeFromString<MsoData>("""{"validFrom":" "}""")
        }
    }

    @Test
    fun `defaults validUntil provenance and omits expectedUpdate`() = runTest {
        val signed = Clock.System.now()
        val resolved = MsoValidityResolver.resolve(null, signed)

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

        val resolved = MsoValidityResolver.resolve(
            MsoData(validFrom = validFrom, validUntil = validUntil, expectedUpdate = expectedUpdate),
            signed,
        )

        assertEquals(signed.epochSeconds, resolved.validFrom?.epochSeconds)
        assertEquals(signed.plus(365.days * 5).epochSeconds, resolved.validUntil?.epochSeconds)
        assertEquals(signed.plus(180.days).epochSeconds, resolved.expectedUpdate?.epochSeconds)
        assertEquals(MsoValidUntilSource.EXPLICIT, resolved.validUntilSource)
    }

    @Test
    fun `resolves timestamp data functions for all three fields`() = runTest {
        val signed = Instant.parse("2026-09-03T12:00:00Z")
        val resolved = MsoValidityResolver.resolve(
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
    fun `allows expectedUpdate before validFrom unless the issuer policy is on`() = runTest {
        val signed = Instant.parse("2026-09-03T12:00:00Z")
        val resolved = MsoValidityResolver.resolve(
            MsoData(
                validFrom = signed.plus(10.days).toString(),
                validUntil = signed.plus(30.days).toString(),
                expectedUpdate = signed.plus(1.days).toString(),
            ),
            signed,
        )
        assertNotNull(resolved.expectedUpdate)

        assertFailsWith<IllegalArgumentException> {
            MsoValidityResolver.resolve(
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
        val resolved = MsoValidityResolver.resolve(
            MsoData(
                validUntil = signed.plus(10.days).toString(),
                expectedUpdate = signed.plus(20.days).toString(),
            ),
            signed,
        )
        assertNotNull(resolved.expectedUpdate)

        assertFailsWith<IllegalArgumentException> {
            MsoValidityResolver.resolve(
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

        assertEquals(fallback.epochSeconds, resolved.validUntil?.epochSeconds)
        assertEquals(MsoValidUntilSource.FALLBACK, resolved.validUntilSource)
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

        assertEquals(explicit.epochSeconds, resolved.validUntil?.epochSeconds)
        assertEquals(MsoValidUntilSource.EXPLICIT, resolved.validUntilSource)
    }

    @Test
    fun `fallback is unused when validUntil is omitted from a merge`() {
        val profile = MsoData(validUntil = "<timestamp-in:30d>")
        val merged = profile.merge(MsoData(expectedUpdate = "<timestamp-in:10d>"))
        assertEquals("<timestamp-in:30d>", merged.validUntil)
    }
}
