package id.walt.crypto2.examples

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Pkcs11SoftHsmConfigurationTest {
    @Test
    fun `library is auto-detected and slot and reference use defaults`() {
        val candidate = "/test/libsofthsm2.so"
        val configuration = resolvePkcs11SoftHsmConfiguration(
            environment = mapOf(Pkcs11SoftHsmExample.PIN_ENV to "auto-detect-test-pin"),
            libraryCandidates = listOf("/missing/libsofthsm2.so", candidate),
            isRegularFile = { it == candidate },
        )

        assertEquals(candidate, configuration.libraryPath)
        assertTrue(configuration.libraryAutoDetected)
        assertEquals(0, configuration.slotListIndex)
        assertEquals("env:${Pkcs11SoftHsmExample.PIN_ENV}", configuration.pinReference)
    }

    @Test
    fun `advanced environment values override defaults`() {
        val configuration = resolvePkcs11SoftHsmConfiguration(
            environment = mapOf(
                Pkcs11SoftHsmExample.LIBRARY_ENV to "/custom/libsofthsm2.so",
                Pkcs11SoftHsmExample.SLOT_ENV to "3",
                Pkcs11SoftHsmExample.PIN_ENV to "override-test-pin",
                Pkcs11SoftHsmExample.PIN_REFERENCE_ENV to "secret-manager:softhsm",
            ),
            libraryCandidates = emptyList(),
            isRegularFile = { false },
        )

        assertEquals("/custom/libsofthsm2.so", configuration.libraryPath)
        assertFalse(configuration.libraryAutoDetected)
        assertEquals(3, configuration.slotListIndex)
        assertEquals("secret-manager:softhsm", configuration.pinReference)
    }

    @Test
    fun `manual configuration requires a PIN`() {
        val error = assertFailsWith<IllegalStateException> {
            resolvePkcs11SoftHsmConfiguration(
                environment = mapOf(Pkcs11SoftHsmExample.LIBRARY_ENV to "/custom/libsofthsm2.so"),
                libraryCandidates = emptyList(),
                isRegularFile = { false },
            )
        }

        assertTrue(Pkcs11SoftHsmExample.PIN_ENV in error.message.orEmpty())
    }
}
