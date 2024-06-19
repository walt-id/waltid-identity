package id.walt.cli.util

import org.junit.jupiter.api.assertDoesNotThrow
import java.nio.file.FileSystems
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertTrue

class ResourceUtilTest {

    @Test
    fun `should return the right path if there is whitespaces in the folder name hirearchy`() {

        val filename = "key/ed25519_key_sample1.json"
        val filepath = getResourcePath(this, filename)

        assertDoesNotThrow {
            val path = FileSystems.getDefault().getPath(filepath)
            assertTrue(path.exists())
        }
    }
}