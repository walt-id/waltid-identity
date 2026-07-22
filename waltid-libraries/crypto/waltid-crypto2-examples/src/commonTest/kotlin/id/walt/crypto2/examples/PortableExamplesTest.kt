package id.walt.crypto2.examples

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortableExamplesTest {
    @Test
    fun `portable examples narrate meaningful safe results`() = runTest {
        val expectedResult = mapOf(
            "software-sign" to "tamperedRejected=true",
            "jws" to "verified=true",
            "stored-key" to "verified=true",
            "pem" to "wrongLabelRejected=true",
            "rsa-oaep" to "roundTrip=true",
            "x25519" to "secretsMatch=true",
        )

        portableExampleCommands.forEach { command ->
            val lines = mutableListOf<String>()
            command.execute(lines::add)

            assertTrue(lines.first().startsWith("=== "), command.name)
            assertTrue(lines.any { "provider=" in it }, command.name)
            assertTrue(lines.any { expectedResult.getValue(command.name) in it }, command.name)
            assertFalse(lines.any { "-----BEGIN PRIVATE KEY-----" in it }, command.name)
            assertFalse(lines.any { "\"d\":" in it }, command.name)
            if (command.name == "stored-key") {
                val warning = lines.indexOfFirst { "WARNING:" in it && "application logs" in it }
                assertTrue(warning >= 0, "stored-key warning")
                assertTrue(
                    lines.getOrNull(warning + 1)
                        ?.contains("Private StoredKey JSON: {\"kind\":\"software\"") == true,
                    "private StoredKey JSON must immediately follow the warning",
                )
            }
        }
    }

    @Test
    fun `portable registry lists commands and rejects unknown input`() = runTest {
        val lines = mutableListOf<String>()
        runExampleCommand(command = "list", commands = portableExampleCommands, output = lines::add)

        assertTrue(lines.first().contains("Supported examples"))
        portableExampleCommands.forEach { command -> assertTrue(lines.any { command.name in it }) }
        assertFailsWith<IllegalArgumentException> {
            runExampleCommand(command = "unknown", commands = portableExampleCommands, output = lines::add)
        }
    }
}
