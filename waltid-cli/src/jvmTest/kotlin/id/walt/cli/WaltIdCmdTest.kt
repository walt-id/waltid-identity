package id.walt.cli

import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.testing.test
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
        val command = WaltIdCmd()
        assertFailsWith<PrintHelpMessage>(message = "Walt.id CLI usage") {
            command.parse(listOf("--help"))
        }
    }
}