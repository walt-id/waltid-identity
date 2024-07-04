package id.walt.cli.commands

import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.testing.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class WaltIdVPCreateCmdTest {

    private val command = VPCreateCmd()

    @Test
    fun `should print help message when called with --help argument`() {
        assertFailsWith<PrintHelpMessage> {
            command.parse(listOf("--help"))
        }
    }

    @Test
    fun `should print help message when called with no argument`() {
        val result = command.test(emptyList())
        assertContains(result.stdout, "Usage: create")
    }

    @Test
    fun `should have -hd and --holder-did options displayed`() = runTest {
        val result = command.test(listOf("--help"))

        assertContains(result.stdout, "--holder-did")
        assertContains(result.stdout, "-hd")
    }

    @Test
    fun `should have -hk and --holder-key options displayed`() = runTest {
        val result = command.test(listOf("--help"))

        assertContains(result.stdout, "--holder-key")
        assertContains(result.stdout, "-hk")
    }

    @Test
    fun `should have -vd and --verifier-did options displayed`() = runTest {
        val result = command.test(listOf("--help"))

        assertContains(result.stdout, "--verifier-did")
        assertContains(result.stdout, "-vd")
    }

    @Test
    fun `should have -vc and --vc-file options displayed`() = runTest {
        val result = command.test(listOf("--help"))

        assertContains(result.stdout, "--vc-file")
        assertContains(result.stdout, "-vc")
    }

    @Test
    fun `should have -o and --vp-output options displayed`() = runTest {
        val result = command.test(listOf("--help"))

        assertContains(result.stdout, "--vp-output")
        assertContains(result.stdout, "-o")
    }

    @Test
    fun `should have -p and --presentation-definition options displayed`() = runTest {
        val result = command.test(listOf("--help"))

        assertContains(result.stdout, "--presentation-definition")
        assertContains(result.stdout, "-p")
    }

    @Test
    fun `should have -po and --presentation-submission-output options displayed`() = runTest {
        val result = command.test(listOf("--help"))

        assertContains(result.stdout, "--presentation-submission-output")
        assertContains(result.stdout, "-po")
    }
}