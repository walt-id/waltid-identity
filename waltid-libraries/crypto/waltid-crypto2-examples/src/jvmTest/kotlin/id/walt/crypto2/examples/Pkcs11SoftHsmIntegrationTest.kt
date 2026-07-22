package id.walt.crypto2.examples

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Pkcs11SoftHsmIntegrationTest {
    @Test
    fun `isolated SoftHSM key survives restart and is deleted`() {
        val executable = System.getProperty("waltid.test.softhsm.executable")
        val library = System.getProperty("waltid.test.softhsm.library")
        val configPath = System.getProperty("waltid.test.softhsm.config")
        val pin = System.getProperty("waltid.test.softhsm.pin")
        assumeTrue(
            executable != null && library != null && configPath != null && pin != null,
            "SoftHSM is not installed",
        )

        val lines = mutableListOf<String>()
        val result = runBlocking {
            Pkcs11SoftHsmExample.run(
                output = lines::add,
                environment = mapOf(
                    Pkcs11SoftHsmExample.LIBRARY_ENV to requireNotNull(library),
                    Pkcs11SoftHsmExample.SLOT_ENV to "0",
                    Pkcs11SoftHsmExample.PIN_ENV to requireNotNull(pin),
                    Pkcs11SoftHsmExample.PIN_REFERENCE_ENV to "integration-test-pin-reference",
                ),
            )
        }
        val narration = lines.joinToString("\n")

        assertTrue("pinReferencePresent=true, pinValuePresent=false" in narration)
        assertTrue("New runtime restore + sign/verify" in narration)
        assertTrue("Delete token key: deleted=true" in narration)
        assertTrue("Restore after deletion: rejected=true" in narration)
        assertFalse(pin in narration)
        assertTrue(result.restartVerified)
        assertTrue(result.deleted)
        assertTrue(result.restoreRejectedAfterDelete)
    }
}
