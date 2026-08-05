package id.walt.crypto2.examples

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmExamplesTest {
    @Test
    fun `JVM registry adds DID COSE ES256K and PKCS11`() = runTest {
        assertEquals(
            portableExampleCommands.map(ExampleCommand::name) + listOf("did", "cose", "es256k", "pkcs11-softhsm"),
            jvmExampleCommands.map(ExampleCommand::name),
        )
        assertTrue(!jvmExampleCommands.single { it.name == "pkcs11-softhsm" }.includeInAll)

        listOf("did", "cose", "es256k").forEach { name ->
            val lines = mutableListOf<String>()
            runExampleCommand(command = name, commands = jvmExampleCommands, output = lines::add)
            assertTrue(lines.first().startsWith("=== "))
            assertTrue(lines.last() == "Completed: $name")
        }
    }
}
