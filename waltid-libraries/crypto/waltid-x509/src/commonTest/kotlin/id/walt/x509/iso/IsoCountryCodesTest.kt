package id.walt.x509.iso

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsoCountryCodesTest {

    @Test
    fun `all codes are valid and normalized`() {
        IsoCountryCodes.alpha2.forEach { code ->
            assertTrue(code.length == 2, "ISO alpha-2 code must be length 2: $code")
            assertTrue(code == code.uppercase(), "ISO alpha-2 code must be uppercase: $code")
            assertTrue(code.all { it in 'A'..'Z' }, "ISO alpha-2 code must be A-Z: $code")
            assertTrue(isValidIsoCountryCode(code), "Code must be accepted: $code")
        }
    }

    @Test
    fun `invalid codes are rejected`() {
        listOf(
            "",
            "U",
            "USA",
            "us",
            "ZZ",
            "1A",
            "A1",
            "AA ",
        ).forEach { code ->
            assertFalse(isValidIsoCountryCode(code), "Code must be rejected: $code")
        }
    }
}
