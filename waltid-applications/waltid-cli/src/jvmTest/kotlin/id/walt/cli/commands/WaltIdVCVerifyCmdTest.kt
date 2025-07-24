package id.walt.cli.commands

import com.github.ajalt.clikt.core.MissingOption
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.testing.test
import id.walt.cli.util.KeyUtil
import id.walt.cli.util.VCUtil
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.test.*

class WaltIdVCVerifyCmdTest {

    val command = VCVerifyCmd()

    val resourcesPath = "src/jvmTest/resources"

    val keyFileName = "${resourcesPath}/key/ed25519_by_waltid_pvt_key.jwk"
    val key = runBlocking { KeyUtil().getKey(File(keyFileName)) }
    val issuerDid = "did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV"
    val subjectDid = "did:key:z6Mkjm2gaGsodGchfG4k8P6KwCHZsVEPZho5VuEbY94qiBB9"
    val vcFilePath = "${resourcesPath}/vc/openbadgecredential_sample.json"

    val signedVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.signed.json"
    val badSignedVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.signed.badsignature.json"

    val expiredVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.expired.json"
    val notExpiredVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.json"
    val signedExpiredVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.expired.signed.json"
    val signedNotExpiredVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.signed.json"

    val validFromVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.json"
    val invalidFromVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.invalidnotbefore.json"
    val signedValidFromVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.signed.json"
    val signedInvalidFromVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.invalidnotbefore.signed.json"

    val validSchemaVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.json"
    val invalidSchemaVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.invalidschema.json"
    val signedValidSchemaVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.signed.json"
    val signedInvalidSchemaVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.invalidschema.signed.json"

    val validHolderVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.json"
    val invalidHolderVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.invalidholder.json"
    val signedValidHolderVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.signed.json"
    val signedInvalidHolderVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.invalidholder.signed.json"

    val schemaFilePath = "${resourcesPath}/schema/OpenBadgeV3_schema.json"

    private val webhookTestServer: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>
    private val webhookTestServerURL: String
    private val webhookTestServerSuccessURL: String
    private val webhookTestServerFailURL: String

    init {
        var url: String
        webhookTestServer = embeddedServer(
            Netty,
            port = 0,
        ) {
            routing {
                post("/success") {
                    call.respondText("{\"status\": \"OK\"}", ContentType.Application.Json)
                }

                post("/fail") {
                    call.respondText("{\"status\": \"Error\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                }
            }
        }.start(false)
        runBlocking {
            url = "http://" + webhookTestServer.engine.resolvedConnectors()
                .first().host + ":${webhookTestServer.engine.resolvedConnectors().first().port}"
        }
        webhookTestServerURL = url
        webhookTestServerSuccessURL = "$webhookTestServerURL/success"
        webhookTestServerFailURL = "$webhookTestServerURL/fail"
    }

    @AfterTest
    fun cleanUp() {
        webhookTestServer.stop()
    }

    @Test
    fun `should print help message when called with --help argument`() {
        assertFailsWith<PrintHelpMessage> {
            command.parse(listOf("--help"))
        }
    }

    @Test
    fun `should print help message when called with no argument`() {
        val result = command.test(emptyList())
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

        assertContains(result.stdout, "the verifiable credential file (in JWS format) to be verified")
    }

    @Test
    fun `should validate the VC signature when a good JWS is provided`() {
        val result = command.test(listOf(signedVCFilePath))
        assertContains(result.output, "signature: Success")
    }

    @Test
    fun `should have --policy option`() {
        val result = command.test(listOf("--policy=aPolicy", signedVCFilePath))
        assertFalse(result.output.contains("Error: no such option --policy"))
    }

    @Test
    fun `should accept multiple --policy options`() {
        assertDoesNotThrow {
            command.parse(
                listOf(
                    "--policy=signature",
                    "--policy=schema",
                    "--arg=schema=${schemaFilePath}",
                    signedVCFilePath
                )
            )
        }
    }

    @Test
    fun `should not require a --policy`() {
        val result = command.test(listOf(signedVCFilePath))
        assertContains(result.output, "signature: Success")
    }

    @Test
    fun `should not accept a --policy value other than the ones accepted`() {
        val result = command.test(listOf("--policy=xxx", signedVCFilePath))
        assertContains(result.output, "--policy: invalid choice: xxx")
    }

    @Test
    fun `should apply only the specified policy`() {
        val result1 = command.test(listOf(signedVCFilePath))
        assertContains(result1.output, "signature: Success")

        val result2 = command.test(listOf("--policy=schema", "--arg=schema=${schemaFilePath}", signedVCFilePath))
        assertContains(result2.output, "schema: Success")
    }

    // Static Verification Policies

    @Test
    fun `should verify the VC's signature when no policy is specified`() {
        val result = command.test(listOf(signedVCFilePath))
        assertContains(result.output, """signature: Success!\s*$""".toRegex())
    }

    @Test
    fun `should verify the VC's signature when --policy=signature`() {
        val result1 = command.test(listOf("--policy=signature", signedVCFilePath))
        assertContains(result1.output, "signature: Success")

        val result2 = command.test(listOf("--policy=signature", badSignedVCFilePath))
        assertContains(result2.output, "signature: Fail!")
    }

    @Test
    fun `should verify if the credentials expiration date (exp for JWTs) has not been exceeded when --policy=expired`() {
        val result1 = command.test(listOf("--policy=expired", signedNotExpiredVCFilePath))
        assertContains(result1.output, "expired: Success")

        val result2 = command.test(listOf("--policy=expired", signedExpiredVCFilePath))
        assertContains(result2.output, "expired: Fail! VC expired since")
    }

    @Test
    fun `should verify if credential is valid when --policy=not-before`() {
        val result1 = command.test(listOf("--policy=not-before", signedValidFromVCFilePath))
        assertContains(result1.output, "not-before: Success")

        val result2 = command.test(listOf("--policy=not-before", signedInvalidFromVCFilePath))
        assertContains(result2.output, "not-before: Fail! VC not valid until")
    }

    @Test
    @Ignore("Policy not implemented yet")
    fun `should verify if the VP's issuer - ie the presenter - is also the subject of all VCs included when --policy=holder-binding`() {
        val result1 = command.test(listOf("--policy=holder-binding", signedValidHolderVCFilePath))
        assertContains(result1.output, "holder-binding: Success")

        val result2 = command.test(listOf("--policy=holder-binding", signedInvalidHolderVCFilePath))
        assertContains(result2.output, "holder-binding: Fail!$".toRegex())
    }

    // Parameterized Verification Policies

    @Test
    fun `should require --arg=schema=filepath when --policy=schema`() {
        val failure =
            assertThrows<MissingOption> { command.parse(listOf("--policy=schema", signedValidSchemaVCFilePath)) }
        failure.paramName?.let { assertContains(it, "--arg") }
    }

    @Test
    fun `should verify the VC's schema when --policy=schema`() {
        val result1 =
            command.test(listOf("--policy=schema", "--arg=schema=${schemaFilePath}", signedValidSchemaVCFilePath))
        assertContains(result1.output, "schema: Success")

        val result2 =
            command.test(listOf("--policy=schema", "--arg=schema=${schemaFilePath}", signedInvalidSchemaVCFilePath))
        assertContains(result2.output, """schema: Fail!.*missing required properties: \[name\].*""".toRegex())
    }

    @Test
    @Ignore("Policy not implemented yet")
    fun `should (call the URL) when --policy=webhook`() {
        val result = command.test(listOf("--policy=webhook", signedVCFilePath))
        assertContains(result.output, "webhook: Success")
    }

    @Test
    @Ignore("Policy not implemented yet")
    fun `should verify XXX when --policy=maximum-credentials`() {
        val result = command.test(listOf("--policy=maximum-credentials", signedVCFilePath))
        assertContains(result.output, "maximum-credentials: Success")
    }

    @Test
    @Ignore("Policy not implemented yet")
    fun `should verify XXX when --policy=minimum-credentials`() {
        val result = command.test(listOf("--policy=minimum-credentials", signedVCFilePath))
        assertContains(result.output, "minimum-credentials: Success")
    }

    @Test
    fun `should verify VC when --policy=allowed-issuer has multiple arguments and one of them is valid`() {
        val result = command.test(
            listOf(
                "--policy=allowed-issuer",
                "--arg=issuer=did:key:z6MkmtB51KWs2ayDmqU7fRcfxx8k5DYfUFqUSJ8wiPFkahNe",
                "--arg=issuer=did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV",
                signedVCFilePath,
            )
        )
        assertContains(result.output, "allowed-issuer: Success")
    }

    @Test
    fun `should verify the VC when --policy=allowed-issuer has one argument that is valid`() {
        val result = command.test(
            listOf(
                "--policy=allowed-issuer",
                "--arg=issuer=did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV",
                signedVCFilePath,
            )
        )
        assertContains(result.output, "allowed-issuer: Success")
    }

    @Test
    fun `should not verify the VC when --policy=allowed-issuer has one argument that is not valid`() {
        val result = command.test(
            listOf(
                "--policy=allowed-issuer",
                "--arg=issuer=did:key:z6MkmtB51KWs2ayDmqU7fRcfxx8k5DYfUFqUSJ8wiPFkahNe",
                signedVCFilePath,
            )
        )
        assertContains(result.output, "allowed-issuer: Fail!")
    }

    @Test
    fun `should not verify the VC when --policy=allowed-issuer has multiple arguments and all of them are invalid`() {
        val result = command.test(
            listOf(
                "--policy=allowed-issuer",
                "--arg=issuer=did:key:z6MkmtB51KWs2ayDmqU7fRcfxx8k5DYfUFqUSJ8wiPFkahNe",
                "--arg=issuer=did:key:z6MkosbA8G31BkzZDPZepew7c498656kaNS9E8q4czWCj47o",
                signedVCFilePath,
            )
        )
        assertContains(result.output, "allowed-issuer: Fail!")
    }

    @Test
    fun `should output Success when webhook URL returns HTTP 200 status code`() = runTest {
        val result = command.test(
            listOf(
                "--policy=webhook",
                "--arg=url=$webhookTestServerSuccessURL",
                signedVCFilePath,
            )
        )
        assertContains(result.output, "webhook: Success")
    }

    @Test
    fun `should output Fail! when webhook URL returns HTTP 400 status code`() = runTest {
        val result = command.test(
            listOf(
                "--policy=webhook",
                "--arg=url=$webhookTestServerFailURL",
                signedVCFilePath,
            )
        )
        assertContains(result.output, "webhook: Fail!")
    }

    @Test
    fun `should output Success when the credential does not contain a revocation status list entry`() = runTest {
        val result = command.test(
            listOf(
                "--policy=revoked-status-list",
                signedVCFilePath,
            )
        )
        assertContains(result.output, "revoked-status-list: Success!")
    }

    private fun sign(vcFilePath: String): String {
        val vc = File(vcFilePath).readText()
        return runBlocking { VCUtil.sign(key = key, issuerDid = issuerDid, subjectDid = subjectDid, payload = vc) }
    }
}
