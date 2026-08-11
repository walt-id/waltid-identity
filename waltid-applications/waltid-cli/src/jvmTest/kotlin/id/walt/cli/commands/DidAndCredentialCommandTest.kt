package id.walt.cli.commands

import com.github.ajalt.clikt.testing.test
import id.walt.cli.util.CredentialHolderBinding
import id.walt.cli.util.KeyUtil
import id.walt.cose.CoseCertificate
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseKey
import id.walt.credentials.CredentialParser
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.preferredJwsAlgorithm
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.sdjwt.SDMap
import id.walt.w3c.vc.vcs.W3CVC
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.Files
import kotlin.io.encoding.Base64
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DidAndCredentialCommandTest {
    private val fixtureKey = TestCryptoFixtures.holderKey
    private val fixtureDid = TestCryptoFixtures.holderDid

    @Test
    fun `native crypto2 DID creation preserves known did-key parity and resolution is JSON only`() {
        val directory = Files.createTempDirectory("cli-did")
        val document = directory.resolve("did.json")
        val create = DidCreateCmd().test(listOf("-m", "key", "-k", fixtureKey.path, "-o", document.toString()))
        assertEquals(0, create.statusCode, create.stderr)
        assertContains(create.stdout, fixtureDid)
        assertTrue(!create.stdout.contains("Uau3y-0bThfD5e2_sGPhFkosg97wQqW-bqyNhCadU5o"))

        val resolve = DidResolveCmd().test(listOf("-d", fixtureDid))
        assertEquals(0, resolve.statusCode, resolve.stderr)
        val output = resolve.stdout.trim()
        assertTrue(output.startsWith("{"), output)
        assertEquals(fixtureDid, (Json.parseToJsonElement(output) as JsonObject)["id"]?.toString()?.trim('"'))
        assertTrue("Did resolved" !in output)
    }

    @Test
    fun `DID help and parser expose only native key and jwk methods`() {
        val help = DidCreateCmd().test("--help")
        assertContains(help.stdout, "KEY")
        assertContains(help.stdout, "JWK")
        assertTrue(!help.stdout.contains("WEB"))
        assertTrue(!help.stdout.contains("web-domain"))

        val result = DidCreateCmd().test(listOf("-m", "web", "-k", fixtureKey.path))
        assertTrue(result.statusCode != 0)
        assertContains(result.stderr.lowercase(), "invalid choice")
    }

    @Test
    fun `W3C issuance and policies2 signature schema and time checks succeed`() {
        val directory = Files.createTempDirectory("cli-vc")
        val credential = directory.resolve("credential.json")
        credential.writeText(credentialJson("2030-01-01T00:00:00Z"))
        val sign = VCSignCmd().test(
            listOf("-k", fixtureKey.path, "-i", fixtureDid, "-s", "did:example:holder", credential.toString())
        )
        assertEquals(0, sign.statusCode, sign.stderr)
        val signed = directory.resolve("credential.signed.json")

        val verify = VCVerifyCmd().test(listOf(signed.toString()))
        assertEquals(0, verify.statusCode, verify.stderr)
        assertContains(verify.stdout, "signature: Success!")
        assertContains(verify.stdout, "expiration: Success!")
        assertContains(verify.stdout, "not-before: Success!")

        val schema = directory.resolve("schema.json")
        schema.writeText(
            """{"type":"object","required":["credentialSubject"],"properties":{"credentialSubject":{"type":"object","required":["name"]}}}"""
        )
        val schemaResult = VCVerifyCmd().test(
            listOf("-p", "schema", "-a", "schema=$schema", signed.toString())
        )
        assertEquals(0, schemaResult.statusCode, schemaResult.stderr)
        assertContains(schemaResult.stdout, "signature: Success!")
        assertContains(schemaResult.stdout, "schema: Success!")

        schema.writeText("""{"type":"object","required":["missing"]}""")
        val invalidSchema = VCVerifyCmd().test(
            listOf("-p", "schema", "-a", "schema=$schema", signed.toString())
        )
        assertEquals(1, invalidSchema.statusCode)
        assertContains(invalidSchema.stdout, "signature: Success!")
        assertContains(invalidSchema.stdout, "schema: Fail!")
    }

    @Test
    fun `wrong signatures and expired credentials fail command verification`() {
        val directory = Files.createTempDirectory("cli-vc-invalid")
        val credential = directory.resolve("credential.json")
        credential.writeText(credentialJson("2026-01-02T00:00:00Z"))
        assertEquals(
            0,
            VCSignCmd().test(
                listOf("-k", fixtureKey.path, "-i", fixtureDid, "-s", "did:example:holder", credential.toString())
            ).statusCode,
        )
        val signed = directory.resolve("credential.signed.json")

        val token = signed.readText()
        val badSignature = directory.resolve("credential.bad.jwt")
        badSignature.writeText(token.dropLast(1) + if (token.last() == 'A') "B" else "A")
        val schema = directory.resolve("schema.json")
        schema.writeText("""{"type":"object"}""")
        val signatureResult = VCVerifyCmd().test(
            listOf("-p", "schema", "-a", "schema=$schema", badSignature.toString())
        )
        assertEquals(1, signatureResult.statusCode)
        assertContains(signatureResult.stdout, "signature: Fail!")
        assertContains(signatureResult.stdout, "schema: Success!")

        val expiredResult = VCVerifyCmd().test(listOf("-p", "expired", signed.toString()))
        assertEquals(1, expiredResult.statusCode)
        assertContains(expiredResult.stdout, "expiration: Fail!")

        val future = directory.resolve("future.json")
        future.writeText(credentialJson("2031-01-02T00:00:00Z", "2030-01-02T00:00:00Z"))
        assertEquals(
            0,
            VCSignCmd().test(
                listOf("-k", fixtureKey.path, "-i", fixtureDid, "-s", "did:example:holder", future.toString())
            ).statusCode,
        )
        val notBeforeResult = VCVerifyCmd().test(
            listOf("-p", "not-before", directory.resolve("future.signed.json").toString())
        )
        assertEquals(1, notBeforeResult.statusCode)
        assertContains(notBeforeResult.stdout, "not-before: Fail!")
    }

    @Test
    @OptIn(ExperimentalSerializationApi::class)
    fun `CredentialParser command path verifies SD-JWT and mdoc signatures as supported`() {
        val directory = Files.createTempDirectory("cli-formats")
        val sdJwt = directory.resolve("credential.sd-jwt")
        val key = runBlocking { KeyUtil.importJwk(fixtureKey.readText()) }
        val holderPublicJwk = runBlocking { Jwk.parse(KeyUtil.publicJwk(key)) }
        sdJwt.writeText(
            runBlocking {
                W3CVC.fromJson(credentialJson("2030-01-01T00:00:00Z")).signSdJwt(
                    issuerKey = key,
                    algorithm = key.preferredJwsAlgorithm(),
                    issuerId = fixtureDid,
                    issuerKid = "$fixtureDid#${fixtureDid.removePrefix("did:key:")}",
                    subjectDid = "did:example:holder",
                    disclosureMap = SDMap(emptyMap()),
                    additionalJwtOptions = mapOf(
                        "cnf" to buildJsonObject { put("jwk", holderPublicJwk) }
                    ),
                )
            }
        )
        val sdJwtResult = VCVerifyCmd().test(listOf("-p", "signature", sdJwt.toString()))
        assertEquals(0, sdJwtResult.statusCode, sdJwtResult.stdout + sdJwtResult.stderr)
        assertContains(sdJwtResult.stdout, "signature: Success!")
        val sdJwtCredential = runBlocking { CredentialParser.parseOnly(sdJwt.readText()) }
        runBlocking { CredentialHolderBinding.requireBoundToPresenter(sdJwtCredential, fixtureDid, key) }
        val unrelatedKey = runBlocking {
            KeyUtil.importJwk(File("src/jvmTest/resources/key/ed25519_key_sample2.json").readText())
        }
        assertFailsWith<IllegalArgumentException> {
            runBlocking { CredentialHolderBinding.requireBoundToPresenter(sdJwtCredential, fixtureDid, unrelatedKey) }
        }

        val mdoc = directory.resolve("credential.mdoc")
        val issuerKey = runBlocking { KeyUtil.importJwk(TestCryptoFixtures.mdocIssuerJwk) }
        val holderCoseKey = runBlocking { KeyUtil.publicJwk(issuerKey) }.toCoseKey()
        val issuerSigned = runBlocking {
            MdocIssuer.issueUniversal(
                issuerKey = issuerKey,
                signatureAlgorithm = -7,
                issuerCertificate = listOf(CoseCertificate(Base64.Default.decode(TestCryptoFixtures.mdocIssuerCertificate))),
                holderKey = holderCoseKey,
                docType = "org.iso.18013.5.1.mDL",
                data = MdocIssuer.MdocUniversalIssuanceData(
                    mapOf("org.iso.18013.5.1" to JsonObject(mapOf("given_name" to JsonPrimitive("Alice"))))
                ),
            )
        }
        mdoc.writeText(coseCompliantCbor.encodeToHexString(issuerSigned))
        val mdocResult = VCVerifyCmd().test(listOf("-p", "signature", mdoc.toString()))
        assertEquals(0, mdocResult.statusCode, mdocResult.stdout + mdocResult.stderr)
        assertContains(mdocResult.stdout, "signature: Success!")
        val mdocCredential = runBlocking { CredentialParser.parseOnly(mdoc.readText()) }
        runBlocking { CredentialHolderBinding.requireBoundToPresenter(mdocCredential, fixtureDid, issuerKey) }
        assertFailsWith<IllegalArgumentException> {
            runBlocking { CredentialHolderBinding.requireBoundToPresenter(mdocCredential, fixtureDid, unrelatedKey) }
        }
    }

    private fun credentialJson(expiration: String, issuance: String = "2026-01-01T00:00:00Z") = """
        {
          "@context": ["https://www.w3.org/2018/credentials/v1"],
          "id": "urn:uuid:cli-test",
          "type": ["VerifiableCredential", "ExampleCredential"],
          "issuer": "did:example:issuer",
          "issuanceDate": "$issuance",
          "expirationDate": "$expiration",
          "credentialSubject": {"id": "did:example:holder", "name": "Alice"}
        }
    """.trimIndent()

}
