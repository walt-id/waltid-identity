package id.walt.cli.commands

import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.testing.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WaltIdVCCmdTest {

    val command = VCCmd()

    @Test
    fun `should print help message when called with no arguments`() = runTest {
        val result1 = assertFailsWith<PrintHelpMessage> {
            command.parse(emptyList())
        }

        val helpMsg = "Issuing, presenting and verifying Verifiable Credentials"
        result1.message?.let { assertTrue(it.contains(helpMsg)) }

        val result2 = command.test()
        assertTrue(result2.stdout.contains(helpMsg, ignoreCase = true))
    }

    @Test
    fun `should print help message when called with --help argument`() {
        assertFailsWith<PrintHelpMessage> {
            command.parse(listOf("--help"))
        }
    }

    @Test
    fun `should have subcommand 'sign'`() {
        val result = command.test()
        assertContains(result.stdout, "sign")
    }

    @Test
    fun `should have subcommand 'verify'`() {
        val result = command.test()
        assertContains(result.stdout, "verify")
    }
}