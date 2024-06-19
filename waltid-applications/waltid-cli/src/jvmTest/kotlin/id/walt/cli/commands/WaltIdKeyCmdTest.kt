package id.walt.cli.commands

import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.testing.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WaltIdKeyCmdTest {

    @Test
    fun testKeyCmd() = runTest {
        val command = KeyCmd()
        val result = assertFailsWith<PrintHelpMessage> {
            command.parse(emptyList())
        }

        // TODO worth it?
        result.message?.let { assertTrue(it.contains("Key management")) }

        val result2 = command.test()
        assertTrue(result2.stdout.contains("Key management features", ignoreCase = true))
    }

    @Test
    fun testKeyHelp() {
        val command = KeyCmd()

        assertFailsWith<PrintHelpMessage> {
            command.parse(listOf("--help"))
        }
    }
}
