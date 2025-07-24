package id.walt.cli.commands

import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.testing.test
import id.walt.cli.util.getResourcePath
import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.oid4vc.data.dif.PresentationSubmission
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WaltIdVPCreateCmdTest {

    private val command = VPCreateCmd()

    private val holderKeyPath = getResourcePath(this, "key/ed25519_by_waltid_pvt_key.jwk")
    private val holderDID = "did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV"

    private val verifierDID: String = "did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV"

    private val openBadgeSignedVcPath = getResourcePath(this, "vc/openbadgecredential_sample.signed.json")
    private val verifiableEducationalIdSignedVcPath = getResourcePath(this, "vc/verifiable_educational_id_sample.signed.json")

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
    fun `should have -vp and --vp-output options displayed`() = runTest {
        val result = command.test(listOf("--help"))

        assertContains(result.stdout, "--vp-output")
        assertContains(result.stdout, "-vp")
    }

    @Test
    fun `should have -pd and --presentation-definition options displayed`() = runTest {
        val result = command.test(listOf("--help"))

        assertContains(result.stdout, "--presentation-definition")
        assertContains(result.stdout, "-pd")
    }

    @Test
    fun `should have -ps and --presentation-submission-output options displayed`() = runTest {
        val result = command.test(listOf("--help"))

        assertContains(result.stdout, "--presentation-submission-output")
        assertContains(result.stdout, "-ps")
    }

    @Test
    fun `should successfully create vp based on openbadge_presdef_vc_type and submission should match openbadge_pressub_vc_type`() =
        runTest {
            val presDefPath = getResourcePath(this, "presexch/openbadge_presdef_vc_type.json")
            val expectedPresSubPath = getResourcePath(this, "presexch/openbadge_pressub_vc_type.json")
            val outputPresSubPath = "${randomUUIDString()}_temp_pressub.json"
            val outputVpPath = "${randomUUIDString()}_temp_vp.json"
            File(outputPresSubPath).deleteOnExit()
            File(outputVpPath).deleteOnExit()
            val result = command.test(
                listOf(
                    "-hd",
                    holderDID,
                    "-hk",
                    holderKeyPath,
                    "-vd",
                    verifierDID,
                    "-vc",
                    openBadgeSignedVcPath,
                    "-pd",
                    presDefPath,
                    "-vp",
                    outputVpPath,
                    "-ps",
                    outputPresSubPath,
                )
            )
            assertContains(result.stdout, "Done".toRegex(RegexOption.MULTILINE))
            assertContains(result.stdout, "VP saved at file .*${outputVpPath}".toRegex(RegexOption.MULTILINE))
            assertContains(result.stdout, "Presentation Submission saved at file .*${outputPresSubPath}".toRegex(RegexOption.MULTILINE))
            val expectedPresSub = Json.decodeFromString<PresentationSubmission>(File(expectedPresSubPath).readText())
            val outputPresSub = Json.decodeFromString<PresentationSubmission>(File(outputPresSubPath).readText())
            assertEquals(expectedPresSub.descriptorMap, outputPresSub.descriptorMap, "Presentation submissions descriptor maps do not match")
        }

    @Test
    fun `should successfully create vp based on openbadge_presdef_issuer_did and submission should match openbadge_pressub_vc_type`() =
        runTest {
            val presDefPath = getResourcePath(this, "presexch/openbadge_presdef_issuer_did.json")
            val expectedPresSubPath = getResourcePath(this, "presexch/openbadge_pressub_vc_type.json")
            val outputPresSubPath = "${randomUUIDString()}_temp_pressub.json"
            val outputVpPath = "${randomUUIDString()}_temp_vp.json"
            File(outputPresSubPath).deleteOnExit()
            File(outputVpPath).deleteOnExit()
            val result = command.test(
                listOf(
                    "-hd",
                    holderDID,
                    "-hk",
                    holderKeyPath,
                    "-vd",
                    verifierDID,
                    "-vc",
                    openBadgeSignedVcPath,
                    "-pd",
                    presDefPath,
                    "-vp",
                    outputVpPath,
                    "-ps",
                    outputPresSubPath,
                )
            )
            assertContains(result.stdout, "Done".toRegex(RegexOption.MULTILINE))
            assertContains(result.stdout, "VP saved at file .*${outputVpPath}".toRegex(RegexOption.MULTILINE))
            assertContains(result.stdout, "Presentation Submission saved at file .*${outputPresSubPath}".toRegex(RegexOption.MULTILINE))
            val expectedPresSub = Json.decodeFromString<PresentationSubmission>(File(expectedPresSubPath).readText())
            val outputPresSub = Json.decodeFromString<PresentationSubmission>(File(outputPresSubPath).readText())
            assertEquals(expectedPresSub.descriptorMap, outputPresSub.descriptorMap, "Presentation submissions descriptor maps do not match")
        }

    @Test
    fun `should successfully create vp based on openbadge_presdef_vc_type_and_issuer_did and submission should match openbadge_pressub_vc_type`() =
        runTest {
            val presDefPath = getResourcePath(this, "presexch/openbadge_presdef_vc_type_and_issuer_did.json")
            val expectedPresSubPath = getResourcePath(this, "presexch/openbadge_pressub_vc_type.json")
            val outputPresSubPath = "${randomUUIDString()}_temp_pressub.json"
            val outputVpPath = "${randomUUIDString()}_temp_vp.json"
            File(outputPresSubPath).deleteOnExit()
            File(outputVpPath).deleteOnExit()
            val result = command.test(
                listOf(
                    "-hd",
                    holderDID,
                    "-hk",
                    holderKeyPath,
                    "-vd",
                    verifierDID,
                    "-vc",
                    openBadgeSignedVcPath,
                    "-pd",
                    presDefPath,
                    "-vp",
                    outputVpPath,
                    "-ps",
                    outputPresSubPath,
                )
            )
            assertContains(result.stdout, "Done".toRegex(RegexOption.MULTILINE))
            assertContains(result.stdout, "VP saved at file .*${outputVpPath}".toRegex(RegexOption.MULTILINE))
            assertContains(result.stdout, "Presentation Submission saved at file .*${outputPresSubPath}".toRegex(RegexOption.MULTILINE))
            val expectedPresSub = Json.decodeFromString<PresentationSubmission>(File(expectedPresSubPath).readText())
            val outputPresSub = Json.decodeFromString<PresentationSubmission>(File(outputPresSubPath).readText())
            assertEquals(expectedPresSub.descriptorMap, outputPresSub.descriptorMap, "Presentation submissions descriptor maps do not match")
        }
    @Test
    fun `should successfully create vp based on openbadge_verifiable_educational_id_presdef and submission should match openbadge_verifiable_educational_id_pressub`() =
        runTest {
            val presDefPath = getResourcePath(this, "presexch/openbadge_verifiable_educational_id_presdef.json")
            val expectedPresSubPath = getResourcePath(this, "presexch/openbadge_verifiable_educational_id_pressub.json")
            val outputPresSubPath = "${randomUUIDString()}_temp_pressub.json"
            val outputVpPath = "${randomUUIDString()}_temp_vp.json"
            File(outputPresSubPath).deleteOnExit()
            File(outputVpPath).deleteOnExit()
            val result = command.test(
                listOf(
                    "-hd",
                    holderDID,
                    "-hk",
                    holderKeyPath,
                    "-vd",
                    verifierDID,
                    "-vc",
                    openBadgeSignedVcPath,
                    "-vc",
                    verifiableEducationalIdSignedVcPath,
                    "-pd",
                    presDefPath,
                    "-vp",
                    outputVpPath,
                    "-ps",
                    outputPresSubPath,
                )
            )
            assertContains(result.stdout, "Done".toRegex(RegexOption.MULTILINE))
            assertContains(result.stdout, "VP saved at file .*${outputVpPath}".toRegex(RegexOption.MULTILINE))
            assertContains(result.stdout, "Presentation Submission saved at file .*${outputPresSubPath}".toRegex(RegexOption.MULTILINE))
            val expectedPresSub = Json.decodeFromString<PresentationSubmission>(File(expectedPresSubPath).readText())
            val outputPresSub = Json.decodeFromString<PresentationSubmission>(File(outputPresSubPath).readText())
            assertEquals(expectedPresSub.descriptorMap, outputPresSub.descriptorMap, "Presentation submissions descriptor maps do not match")
        }
}
