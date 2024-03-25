package id.walt.cli.commands

import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.testing.test
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class WaltIdVCVerifyCmdTest {

    val command = VCVerifyCmd()

    val resourcesPath = "src/jvmTest/resources"

    val keyFileName = "${resourcesPath}/key/ed25519_by_waltid_pvt_key.jwk"
    val issuerDid = "did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV"
    val subjectDid = "did:key:z6Mkjm2gaGsodGchfG4k8P6KwCHZsVEPZho5VuEbY94qiBB9"
    val vcFilePath = "${resourcesPath}/vc/openbadgecredential_sample.json"
    val signedVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.signed.json"


    @Test
    fun `should print help message when called with --help argument`() {
        assertFailsWith<PrintHelpMessage> {
            command.parse(listOf("--help"))
        }
    }

    @Test
    fun `should print help message when called with no argument`() {
        val result = command.test(emptyList<String>())
        assertContains(result.stdout, "Usage: verify")
    }

    //
    // @Test
    // fun `should have --key option`() {
    //     val result = command.test(listOf("--help"))
    //
    //     assertContains(result.stdout, "--key")
    // }

    @Test
    fun `should accept one positional argument after --options`() {
        val result = command.test(listOf("--help"))

        assertContains(result.stdout, "the verifiable credential file (in the JWS format) to be verified")
    }

    @Test
    fun `should validate the VC signature when a good JWS is provided`() {
        val result = command.test(listOf("""${signedVCFilePath}"""))
        assertContains(result.output, "The VC signature is valid")
    }

}