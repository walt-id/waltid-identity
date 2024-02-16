package id.walt.cli

import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.testing.test
import id.walt.cli.commands.DidCmd
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WaltIdDidCmdTest {

    @Test
    fun `should print help message when called with no arguments`() = runTest {
        val command = DidCmd()
        val result = assertFailsWith<PrintHelpMessage> {
            command.parse(emptyList())
        }

        // TODO worth it?
        result.message?.let { assertTrue(it.contains("DID management")) }

        val result2 = command.test()
        assertTrue(result2.stdout.contains("DID management features", ignoreCase = true))
    }

    @Test
    fun `should print help message when called with --help argument`() {
        val command = DidCmd()

        assertFailsWith<PrintHelpMessage> {
            command.parse(listOf("--help"))
        }
    }
}