package id.walt.cli.commands

import com.github.ajalt.clikt.core.MissingArgument
import com.github.ajalt.clikt.core.MissingOption
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.testing.test
import kotlinx.io.files.FileNotFoundException
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.*

class WaltIdVCSignCmdTest {

    val command = VCSignCmd()

    val resourcesPath = "src/jvmTest/resources"

    val keyFileName = "${resourcesPath}/key/ed25519_by_waltid_pvt_key.jwk"
    val issuerDid = "did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV"
    val subjectDid = "did:key:z6Mkjm2gaGsodGchfG4k8P6KwCHZsVEPZho5VuEbY94qiBB9"
    val vcFilePath = "${resourcesPath}/vc/openbadgecredential_sample.json"

    @Test
    fun `should print help message when called with --help argument`() {
        assertFailsWith<PrintHelpMessage> {
            command.parse(listOf("--help"))
        }
    }

    @Test
    fun `should print help message when called with no argument`() {
        val result = command.test(emptyList<String>())
        assertContains(result.stdout, "Usage: sign")
    }

    @Test
    fun `should have --key option`() {
        val result = command.test(listOf("--help"))

        assertContains(result.stdout, "--key")
    }

    @Test
    fun `should have --issuer option`() {
        val result = command.test(listOf("--help"))

        assertContains(result.stdout, "--issuer")
    }

    @Test
    fun `should have --subject option`() {
        val result = command.test(listOf("--help"))

        assertContains(result.stdout, "--subject")
    }

    @Test
    fun `should accept one positional argument after --options`() {
        val result = command.test(listOf("--help"))

        assertContains(result.stdout, "the verifiable credential file")
    }

    @Test
    fun `should fail when option --key is not provided`() {

        val failure = assertFailsWith<MissingOption> {
            command.parse(arrayOf("-i", issuerDid, "-s", subjectDid, vcFilePath))
        }

        failure.message?.let { assertContains(it, "missing option --key") }
    }

    @Test
    fun `should NOT fail when option --issuer is not provided`() {

        val result = assertDoesNotThrow {
            command.test(arrayOf("-k", keyFileName, "-s", subjectDid, vcFilePath, "--overwrite"))
        }

        assertContains(result.output, "Signed VC saved at")
        assertFalse(result.output.contains("Error: missing"))
    }

    @Test
    fun `should fail when option --subject is not provided`() {

        val failure = assertFailsWith<MissingOption> {
            command.parse(arrayOf("-k", keyFileName, "-i", issuerDid, vcFilePath))
        }

        failure.message?.let { assertContains(it, "missing option --subject") }
    }

    @Test
    fun `should fail when positional argument 'vc' is not provided`() {

        val failure = assertFailsWith<MissingArgument> {
            command.parse(arrayOf("-k", keyFileName, "-i", issuerDid, "-s", subjectDid))
        }

        failure.message?.let { assertContains(it, "missing argument") }
    }

    @Test
    fun `should fail if a non existent key file is provided`() {

        val invalidKeyFilePath = "foo.bar"
        val failure = assertFailsWith<FileNotFoundException> {
            command.parse(arrayOf("-k", invalidKeyFilePath, "-i", issuerDid, "-s", subjectDid, vcFilePath))
        }

        failure.message?.let { assertContains(it, "${invalidKeyFilePath} (No such file or directory)") }
    }

    @Test
    fun `should fail if a badly formatted DID is provided`() {

        val weirdDid = "did:foo:bar"
        val result = command.test(arrayOf("-k", keyFileName, "-i", weirdDid, "-s", subjectDid, vcFilePath))

        assertContains(result.output, "DID can not be resolved")
    }


    @Test
    fun `should sign an existing VC when the issuer key and a subject DID is provided`() {

        val result = command.test("""-k "${keyFileName}" -i ${issuerDid} -s ${subjectDid} "${vcFilePath}" """)

        assertContains(result.stdout, "Signed VC saved at")
    }

    @Test
    @Ignore
    fun `should generate a valid signature`() = Unit

    @Test
    @Ignore
    fun `should fail if a non-existent key file is provided`() = Unit

    @Test
    @Ignore
    fun `should fail if a non-JWK key file is provided`() = Unit

}