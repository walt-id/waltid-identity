package id.walt.crypto2.examples

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SoftHsmGradleProvisioningTest {
    @Test
    fun `Gradle task provisions an isolated reusable token without persisting demo PINs`() {
        val statePath = System.getProperty("waltid.test.softhsm.state")
        assumeTrue(statePath != null, "SoftHSM is not installed")

        val state = Path.of(requireNotNull(statePath))
        val config = state.resolve("softhsm2.conf")
        val tokens = state.resolve("tokens")
        val configText = Files.readString(config)

        assertTrue(config.isRegularFile())
        assertTrue(state.resolve("initialized").isRegularFile())
        assertTrue(Files.list(tokens).use { entries -> entries.findAny().isPresent })
        assertTrue(tokens.toAbsolutePath().toString() in configText)
        assertFalse("123456" in configText)
        assertFalse("12345678" in configText)
    }
}
