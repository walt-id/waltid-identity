package id.walt.mdoc.issuance

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class MsoDataTest {
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
    fun `fallback is unused when validUntil is omitted from a merge`() {
        val profile = MsoData(validUntil = "<timestamp-in:30d>")
        val merged = profile.merge(MsoData(expectedUpdate = "<timestamp-in:10d>"))
        assertEquals("<timestamp-in:30d>", merged.validUntil)
    }
}
