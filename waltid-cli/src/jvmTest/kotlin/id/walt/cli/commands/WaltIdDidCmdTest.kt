package id.walt.cli.commands

import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.testing.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WaltIdDidCmdTest {

    val command = DidCmd()

    @Test
    fun `should print help message when called with no arguments`() = runTest {
        // val command = DidCmd()
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
        assertFailsWith<PrintHelpMessage> {
            command.parse(listOf("--help"))
        }
    }

    @Test
    fun `should have subcommand 'create'`() {
        val result = command.test()
        assertContains(result.stdout, "create")
    }

    @Test
    fun `should have subcommand 'resolve'`() {
        val result = command.test()
        assertContains(result.stdout, "resolve")
    }
}