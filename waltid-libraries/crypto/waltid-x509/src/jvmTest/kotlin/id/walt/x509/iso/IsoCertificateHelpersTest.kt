package id.walt.x509.iso

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsoCertificateHelpersTest {

    @Test
    fun `valid ISO country codes return true`() {
        listOf("US", "DE", "GR", "JP", "BR").forEach { validCountryCode ->
            assertTrue(
                message = "Expected $validCountryCode to be a valid ISO country code"
            ) {
                isValidIsoCountryCode(validCountryCode)
            }
        }
    }

    @Test
    fun `invalid ISO country codes return false`() {
        listOf("", "U", "USA", "ZZ", "123", "us").forEach { invalidCountryCode ->
            assertFalse(
                message = "Expected $invalidCountryCode to be invalid"
            ) {
                isValidIsoCountryCode(invalidCountryCode)
            }
        }
    }
}
