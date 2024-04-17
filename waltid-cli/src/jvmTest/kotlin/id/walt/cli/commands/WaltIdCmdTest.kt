package id.walt.cli.commands

import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.testing.test
import id.walt.cli.WaltIdCmd
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class WaltIdCmdTest {

    @Test
    fun testMainNoArgs() = runTest {
        val command = WaltIdCmd()
        assertFailsWith<PrintHelpMessage> {
            command.parse(emptyList())
        }
        val result = command.test()
        assertContains(result.stdout, "The walt.id CLI is a command line tool")
    }

    @Test
    fun testMainHelp() = runTest {
        assertFailsWith<PrintHelpMessage>(message = "Walt.id CLI usage") {
            WaltIdCmd().parse(listOf("--help"))
        }
    }

    @Test
    fun `should have 'key' subcommand`() {
        val result = WaltIdCmd().test(listOf("--help"))
        assertContains(result.stdout, "Commands:\n+.*key".toRegex(RegexOption.MULTILINE))
    }

    @Test
    fun `should have 'did' subcommand`() {
        val result = WaltIdCmd().test(listOf("--help"))
        assertContains(result.stdout, "Commands:(\n+.*)+did".toRegex(RegexOption.MULTILINE))
    }

    @Test
    fun `should have 'vc' subcommand`() {
        val result = WaltIdCmd().test(listOf("--help"))
        assertContains(result.stdout, "Commands:(\n+.*)+vc".toRegex(RegexOption.MULTILINE))
    }
}