package id.walt.cli

import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.testing.test
import id.walt.cli.commands.VCSignCmd
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class WaltIdVCSignCmdTest {

    val command = VCSignCmd()

    @Test
    fun `should print help message when called with --help argument`() {
        assertFailsWith<PrintHelpMessage> {
            command.parse(listOf("--help"))
        }
    }

    @Test
    fun `should XXX when called with no argument`() {
        val result = command.test(emptyList<String>())

        assertContains(result.stdout, "???")
    }

    // -k, —key
    // -i, —issuerDid<str>
    // -s, —subjectDid=<str>
    // -vc, —verifiableCredential=<filepath>

    @Test
    fun `should have --key option`() {
        val result = command.test(listOf("--help"))

        assertContains(result.stdout, "--key")
    }

    @Test
    fun `should have --issuerDid option`() {
        val result = command.test(listOf("--help"))

        assertContains(result.stdout, "--issuerDid")
    }

    @Test
    fun `should have --subjectDid option`() {
        val result = command.test(listOf("--help"))

        assertContains(result.stdout, "--subjectDid")
    }

    @Test
    fun `should have --verifiableCredential option`() {
        val result = command.test(listOf("--help"))

        assertContains(result.stdout, "--verifiableCredential")
    }

}