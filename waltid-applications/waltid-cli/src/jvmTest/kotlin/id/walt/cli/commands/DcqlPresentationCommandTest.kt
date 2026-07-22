package id.walt.cli.commands

import com.github.ajalt.clikt.testing.test
import id.walt.cli.util.KeyUtil
import id.walt.crypto2.jose.preferredJwsAlgorithm
import id.walt.w3c.vc.vcs.W3CVC
import id.walt.x509.CertificateDer
import id.walt.x509.authorityKeyIdentifier
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.encoding.Base64
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DcqlPresentationCommandTest {
    private val holderKey = File("src/jvmTest/resources/key/ed25519_by_waltid_pvt_key.jwk")
    private val holderDid = "did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV"
    private val credential = File("src/jvmTest/resources/vc/openbadgecredential_sample.signed.json")

    @Test
    fun `DCQL selects credential and crypto2 presentation passes policies2-vp verification`() {
        val directory = Files.createTempDirectory("cli-vp")
        val query = directory.resolve("query.json").also { it.writeText(dcqlQuery) }
        val vp = directory.resolve("vp.json")

        val create = VPCreateCmd().test(createArguments(query.toString(), vp.toString()))
        assertEquals(0, create.statusCode, create.stdout + create.stderr)
        val vpToken = Json.parseToJsonElement(vp.readText()).jsonObject
        assertEquals(setOf("badge"), vpToken.keys)
        assertEquals(1, vpToken.getValue("badge").jsonArray.size)

        val verify = VPVerifyCmd().test(verifyArguments(query.toString(), vp.toString()))
        assertEquals(0, verify.statusCode, verify.stdout + verify.stderr)
        assertContains(verify.stdout, "envelope_signature: Success!")
        assertContains(verify.stdout, "audience-check: Success!")
        assertContains(verify.stdout, "nonce-check: Success!")
        assertContains(verify.stdout, "dcql fulfillment: Success!")
        assertContains(verify.stdout, "Overall: Success!")
    }

    @Test
    fun `wrong VP signature nonce and algorithm validation fail`() {
        val directory = Files.createTempDirectory("cli-vp-invalid")
        val query = directory.resolve("query.json").also { it.writeText(dcqlQuery) }
        val vp = directory.resolve("vp.json")
        assertEquals(0, VPCreateCmd().test(createArguments(query.toString(), vp.toString())).statusCode)

        val original = Json.parseToJsonElement(vp.readText()).jsonObject
        val token = original.getValue("badge").jsonArray.single().toString().trim('"')
        val badToken = token.dropLast(1) + if (token.last() == 'A') "B" else "A"
        vp.writeText(Json.encodeToString(JsonObject(mapOf("badge" to JsonArray(listOf(JsonPrimitive(badToken)))))))
        val badSignature = VPVerifyCmd().test(verifyArguments(query.toString(), vp.toString()))
        assertEquals(1, badSignature.statusCode)
        assertContains(badSignature.stdout, "envelope_signature: Fail!")

        vp.writeText(Json.encodeToString(original))
        val badNonce = VPVerifyCmd().test(
            verifyArguments(query.toString(), vp.toString()).map { if (it == "nonce-test") "wrong-nonce" else it }
        )
        assertEquals(1, badNonce.statusCode)
        assertContains(badNonce.stdout, "nonce-check: Fail!")
    }

    @Test
    fun `help exposes Final DCQL and no default DIF path`() {
        val createHelp = VPCreateCmd().test("--help")
        val verifyHelp = VPVerifyCmd().test("--help")
        assertContains(createHelp.stdout, "--dcql-query")
        assertContains(verifyHelp.stdout, "--dcql-query")
        assertFalse(createHelp.stdout.contains("presentation-definition"))
        assertFalse(createHelp.stdout.contains("presentation-submission"))
        assertFalse(verifyHelp.stdout.contains("presentation-definition"))
        assertFalse(verifyHelp.stdout.contains("presentation-submission"))
    }

    @Test
    fun `W3C holder binding accepts matching subject DID and rejects unrelated presenter`() {
        val directory = Files.createTempDirectory("cli-vp-binding")
        val bindingQuery = directory.resolve("binding-query.json").also { it.writeText(dcql(requireBinding = true)) }
        val noBindingQuery = directory.resolve("no-binding-query.json").also { it.writeText(dcql(requireBinding = false)) }
        val boundCredential = issueCredential(directory, "bound", holderDid)
        val unboundCredential = issueCredential(directory, "unbound", "did:example:other-holder")

        val boundVp = directory.resolve("bound-vp.json")
        val bound = VPCreateCmd().test(
            createArguments(bindingQuery.toString(), boundVp.toString(), credential = boundCredential)
        )
        assertEquals(0, bound.statusCode, bound.stdout + bound.stderr)
        val verified = VPVerifyCmd().test(verifyArguments(bindingQuery.toString(), boundVp.toString()))
        assertEquals(0, verified.statusCode, verified.stdout + verified.stderr)
        assertContains(verified.stdout, "holder-binding: Success!")

        val rejected = VPCreateCmd().test(
            createArguments(bindingQuery.toString(), directory.resolve("rejected.json").toString(), credential = unboundCredential)
        )
        assertTrue(rejected.statusCode != 0)
        assertContains(rejected.stderr, "not cryptographically or semantically bound")

        val unrelatedKey = File("src/jvmTest/resources/key/ed25519_key_sample2.json")
        val unrelatedDidDocument = directory.resolve("unrelated-did.json")
        val unrelatedDidResult = DidCreateCmd().test(
            listOf("-k", unrelatedKey.path, "-o", unrelatedDidDocument.toString())
        )
        assertEquals(0, unrelatedDidResult.statusCode, unrelatedDidResult.stdout + unrelatedDidResult.stderr)
        val unrelatedDid = Regex("did:key:[A-Za-z0-9]+").findAll(unrelatedDidResult.stdout).last().value
        val unrelatedVp = directory.resolve("unrelated-vp.json")
        val unrelatedCreate = VPCreateCmd().test(
            createArguments(
                noBindingQuery.toString(),
                unrelatedVp.toString(),
                holderDid = unrelatedDid,
                holderKey = unrelatedKey,
                credential = boundCredential,
            )
        )
        assertEquals(0, unrelatedCreate.statusCode, unrelatedCreate.stdout + unrelatedCreate.stderr)
        val unrelatedVerify = VPVerifyCmd().test(
            verifyArguments(bindingQuery.toString(), unrelatedVp.toString(), expectedHolderDid = null)
        )
        assertEquals(1, unrelatedVerify.statusCode)
        assertContains(unrelatedVerify.stdout, "holder-binding: Fail!")
    }

    @Test
    fun `multiple false and credential sets are enforced exactly`() {
        val directory = Files.createTempDirectory("cli-vp-cardinality")
        val query = directory.resolve("query.json").also { it.writeText(dcql(requireBinding = false)) }
        val ambiguousVp = directory.resolve("ambiguous.json")
        val ambiguous = VPCreateCmd().test(
            createArguments(query.toString(), ambiguousVp.toString()) + listOf("-vc", credential.path)
        )
        assertTrue(ambiguous.statusCode != 0)
        assertContains(ambiguous.stderr, "multiple=false")

        val vp = directory.resolve("vp.json")
        assertEquals(0, VPCreateCmd().test(createArguments(query.toString(), vp.toString())).statusCode)
        val original = Json.parseToJsonElement(vp.readText()).jsonObject
        val token = original.getValue("badge").jsonArray.single()
        vp.writeText(Json.encodeToString(JsonObject(mapOf("badge" to JsonArray(listOf(token, token))))))
        val duplicateResponse = VPVerifyCmd().test(verifyArguments(query.toString(), vp.toString()))
        assertEquals(1, duplicateResponse.statusCode)
        assertContains(duplicateResponse.stdout, "cardinality: Fail!")

        val impossibleSet = directory.resolve("impossible-set.json").also {
            it.writeText(credentialSetQuery(options = "[[\"badge\",\"missing\"]]"))
        }
        val impossible = VPCreateCmd().test(
            createArguments(impossibleSet.toString(), directory.resolve("impossible.json").toString())
        )
        assertTrue(impossible.statusCode != 0)
        assertContains(impossible.stderr, "credential set is not satisfied")

        val alternativeSet = directory.resolve("alternative-set.json").also {
            it.writeText(credentialSetQuery(options = "[[\"badge\"],[\"missing\"]]"))
        }
        val alternative = VPCreateCmd().test(
            createArguments(alternativeSet.toString(), directory.resolve("alternative.json").toString())
        )
        assertEquals(0, alternative.statusCode, alternative.stdout + alternative.stderr)
    }

    @Test
    fun `trusted authorities support AKI and fail closed for mismatches and unsupported types`() {
        val directory = Files.createTempDirectory("cli-vp-authority")
        val x5cCredential = issueX5cCredential(directory)
        val aki = CertificateDer(Base64.Default.decode(TestCryptoFixtures.mdocIssuerCertificate)).authorityKeyIdentifier
            ?: error("Test certificate has no AKI")
        val akiValue = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(aki)

        val acceptedQuery = directory.resolve("aki.json").also {
            it.writeText(authorityQuery("aki", akiValue))
        }
        val accepted = VPCreateCmd().test(
            createArguments(acceptedQuery.toString(), directory.resolve("aki-vp.json").toString(), credential = x5cCredential)
        )
        assertEquals(0, accepted.statusCode, accepted.stdout + accepted.stderr)

        val wrongAkiQuery = directory.resolve("wrong-aki.json").also {
            it.writeText(authorityQuery("aki", "AAAAAAAAAAAAAAAAAAAAAA"))
        }
        val wrongAki = VPCreateCmd().test(
            createArguments(wrongAkiQuery.toString(), directory.resolve("wrong-aki-vp.json").toString(), credential = x5cCredential)
        )
        assertTrue(wrongAki.statusCode != 0)

        val unsupportedQuery = directory.resolve("unsupported-authority.json").also {
            it.writeText(authorityQuery("etsi_tl", "https://example.com/trusted-list"))
        }
        val unsupported = VPCreateCmd().test(
            createArguments(unsupportedQuery.toString(), directory.resolve("unsupported-vp.json").toString(), credential = x5cCredential)
        )
        assertTrue(unsupported.statusCode != 0)
        assertContains(unsupported.stderr, "Unsupported trusted_authorities type")
    }

    @Test
    fun `CLI source and direct dependencies contain no forbidden legacy stack`() {
        val forbiddenImports = listOf(
            "id.walt.crypto." + "keys",
            "id.walt." + "policies.",
            "id.walt." + "oid4vc",
        )
        val kotlinFiles = File("src").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        forbiddenImports.forEach { forbidden ->
            val offenders = kotlinFiles.filter { forbidden in it.readText() }
            assertTrue(offenders.isEmpty(), "$forbidden found in $offenders")
        }

        val build = File("build.gradle.kts").readText()
        assertFalse(build.contains("crypto:waltid-crypto\")"))
        assertFalse(build.contains("credentials:waltid-verification-policies\")"))
        assertFalse(build.contains("protocols:waltid-openid4vc\")"))
    }

    private fun createArguments(
        query: String,
        vp: String,
        holderDid: String = this.holderDid,
        holderKey: File = this.holderKey,
        credential: File = this.credential,
    ) = listOf(
        "-hd", holderDid,
        "-hk", holderKey.path,
        "-vd", "verifier-test",
        "-n", "nonce-test",
        "-vc", credential.path,
        "-dq", query,
        "-vp", vp,
    )

    private fun verifyArguments(query: String, vp: String, expectedHolderDid: String? = holderDid) = buildList {
        expectedHolderDid?.let { addAll(listOf("-hd", it)) }
        addAll(listOf(
        "-vd", "verifier-test",
        "-n", "nonce-test",
        "-dq", query,
        "-vp", vp,
        ))
    }

    private val dcqlQuery = """
        {
          "credentials": [
            {
              "id": "badge",
              "format": "jwt_vc_json",
              "meta": {},
              "require_cryptographic_holder_binding": false,
              "claims": [{"path": ["type"]}]
            }
          ]
        }
    """.trimIndent()

    private fun dcql(requireBinding: Boolean) = dcqlQuery.replace(
        "\"require_cryptographic_holder_binding\": false",
        "\"require_cryptographic_holder_binding\": $requireBinding",
    )

    private fun credentialSetQuery(options: String) = """
        {
          "credentials": [
            {
              "id": "badge",
              "format": "jwt_vc_json",
              "meta": {},
              "require_cryptographic_holder_binding": false,
              "claims": [{"path": ["type"]}]
            },
            {
              "id": "missing",
              "format": "jwt_vc_json",
              "meta": {},
              "require_cryptographic_holder_binding": false,
              "claims": [{"path": ["doesNotExist"]}]
            }
          ],
          "credential_sets": [{"required": true, "options": $options}]
        }
    """.trimIndent()

    private fun authorityQuery(type: String, value: String) = """
        {
          "credentials": [
            {
              "id": "badge",
              "format": "jwt_vc_json",
              "meta": {},
              "trusted_authorities": [{"type": "$type", "values": ["$value"]}],
              "claims": [{"path": ["type"]}]
            }
          ]
        }
    """.trimIndent()

    private fun issueCredential(directory: Path, name: String, subjectDid: String): File {
        val input = directory.resolve("$name.json")
        input.writeText(
            """
            {
              "@context": ["https://www.w3.org/2018/credentials/v1"],
              "id": "urn:uuid:$name",
              "type": ["VerifiableCredential", "ExampleCredential"],
              "issuer": "$holderDid",
              "issuanceDate": "2026-01-01T00:00:00Z",
              "expirationDate": "2030-01-01T00:00:00Z",
              "credentialSubject": {"id": "$subjectDid", "name": "Alice"}
            }
            """.trimIndent()
        )
        val result = VCSignCmd().test(
            listOf("-k", holderKey.path, "-i", holderDid, "-s", subjectDid, input.toString())
        )
        assertEquals(0, result.statusCode, result.stdout + result.stderr)
        return directory.resolve("$name.signed.json").toFile()
    }

    private fun issueX5cCredential(directory: Path): File {
        val issuerKey = runBlocking { KeyUtil.importJwk(TestCryptoFixtures.mdocIssuerJwk) }
        val credential = W3CVC.fromJson(
            """
            {
              "@context": ["https://www.w3.org/2018/credentials/v1"],
              "id": "urn:uuid:x5c-authority",
              "type": ["VerifiableCredential", "ExampleCredential"],
              "issuer": "https://issuer.example",
              "issuanceDate": "2026-01-01T00:00:00Z",
              "expirationDate": "2030-01-01T00:00:00Z",
              "credentialSubject": {"id": "$holderDid", "name": "Alice"}
            }
            """.trimIndent()
        )
        val signed = runBlocking {
            credential.signJws(
                issuerKey = issuerKey,
                algorithm = issuerKey.preferredJwsAlgorithm(),
                issuerId = "https://issuer.example",
                issuerKid = issuerKey.id.value,
                subjectDid = holderDid,
                additionalJwtHeader = mapOf(
                    "x5c" to JsonArray(listOf(JsonPrimitive(TestCryptoFixtures.mdocIssuerCertificate)))
                ),
            )
        }
        return directory.resolve("x5c.jwt").also { it.writeText(signed) }.toFile()
    }
}
