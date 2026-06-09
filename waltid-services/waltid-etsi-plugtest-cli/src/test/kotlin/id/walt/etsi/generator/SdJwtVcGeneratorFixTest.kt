package id.walt.etsi.generator

import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.etsi.TestCase
import id.walt.etsi.TestCaseSection
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression tests for ETSI plugtest SD-JWT VC generator fixes:
 *  - FIX 1: QEAA/PuBEAA carry issuing_authority + issuing_country + iss_reg_id when the signing
 *           certificate is NOT qualified (BOSA: "'issuing_country' is required when no qualified
 *           certificate provides the issuer country"). ETSI QEAA-5.2.4.2 / PuB-EAA-5.2.4.3.
 *  - FIX 2: the SD-JWT `status.uri` points to a JWT Status List Token (identifier-list.jwt), not a
 *           CWT — SD-JWT VC draft-16 requires the status list token to be in JWT format.
 *
 * Uses a self-signed, NON-qualified test certificate (no qcStatements) from test resources, which
 * is exactly the condition under which FIX 1 must emit the issuer-identity claims.
 */
class SdJwtVcGeneratorFixTest {

    private fun loadResource(path: String): String =
        requireNotNull(this::class.java.classLoader.getResource(path)) { "Missing test resource: $path" }
            .readText()

    private val issuerKey: JWKKey by lazy {
        runBlocking { KeyManager.resolveSerializedKey(loadResource("testgen/key.jwk")) as JWKKey }
    }
    private val issuerCertPem: String by lazy { loadResource("testgen/cert.pem") }

    /** A QEAA/PuBEAA test case whose Payload section lists ONLY family/given name + status, i.e.
     *  it does NOT list issuing_country — reproducing the BOSA-failing fixtures. */
    private fun pubEaaTestCase(id: String) = TestCase(
        id = id,
        name = id,
        subtitle = "",
        description = "PuB-EAA test case",
        sections = listOf(
            TestCaseSection(
                title = "Payload",
                items = listOf("given_name", "family_name", "category", "status")
            )
        )
    )

    private fun decodePayload(sdJwt: String): JsonObject {
        val jwt = sdJwt.substringBefore("~")
        val payloadB64 = jwt.split(".")[1]
        return Json.parseToJsonElement(payloadB64.base64UrlDecode().decodeToString()).jsonObject
    }

    @Test
    fun pubEaaWithUnqualifiedCertEmitsIssuingCountry(): Unit = runBlocking {
        val result = SdJwtVcGenerator.generate(
            testCase = pubEaaTestCase("SJV-PuBEAA-1"),
            issuerKey = issuerKey,
            issuerCertificatePem = issuerCertPem,
        )
        val payload = decodePayload(result.sdJwtVc)

        // FIX 1: all three issuer-identity claims must be present (cert is unqualified).
        assertEquals(JsonPrimitive("DE"), payload["issuing_country"], "issuing_country must be present for unqualified-cert PuB-EAA")
        assertNotNull(payload["issuing_authority"], "issuing_authority must be present")
        assertNotNull(payload["iss_reg_id"], "iss_reg_id must be present")
    }

    @Test
    fun qeaaWithUnqualifiedCertEmitsIssuingCountry(): Unit = runBlocking {
        val result = SdJwtVcGenerator.generate(
            testCase = pubEaaTestCase("SJV-QEAA-1"),
            issuerKey = issuerKey,
            issuerCertificatePem = issuerCertPem,
        )
        val payload = decodePayload(result.sdJwtVc)
        assertEquals(JsonPrimitive("DE"), payload["issuing_country"], "issuing_country must be present for unqualified-cert QEAA")
    }

    @Test
    fun statusUriPointsToJwtStatusListToken(): Unit = runBlocking {
        val result = SdJwtVcGenerator.generate(
            testCase = pubEaaTestCase("SJV-PuBEAA-1"),
            issuerKey = issuerKey,
            issuerCertificatePem = issuerCertPem,
        )
        val payload = decodePayload(result.sdJwtVc)
        val status = assertNotNull(payload["status"]?.jsonObject, "status object must be present")
        val uri = status["uri"]?.jsonPrimitive?.content
        assertNotNull(uri, "status.uri must be present")

        // FIX 2: SD-JWT status list token MUST be a JWT (SD-JWT VC draft-16), not a CWT.
        assertTrue(uri.endsWith("identifier-list.jwt"), "SD-JWT status.uri must point to a JWT status list, got: $uri")
        assertTrue(!uri.endsWith(".cwt"), "SD-JWT status.uri must NOT point to a CWT, got: $uri")
    }

    // ── Per-tier vct + schema-required claims (PID / Medical License / Birth Certificate) ──────

    private fun generateFor(id: String) = runBlocking {
        decodePayload(
            SdJwtVcGenerator.generate(
                testCase = pubEaaTestCase(id),
                issuerKey = issuerKey,
                issuerCertificatePem = issuerCertPem,
            ).sdJwtVc
        )
    }

    @Test
    fun eaaUsesPidVctWithRequiredClaims() {
        val p = generateFor("SJV-EAA-1")
        assertEquals(JsonPrimitive("urn:eudi:pid:1"), p["vct"], "EAA must be issued as PID")
        assertNotNull(p["vct#integrity"], "vct#integrity (hash of Type Metadata) must be present")
        // PID schema required: vct, issuing_authority, issuing_country, expiry_date, given_name, family_name
        listOf("issuing_authority", "issuing_country", "expiry_date", "given_name", "family_name").forEach {
            assertNotNull(p[it], "PID requires '$it'")
        }
    }

    @Test
    fun qeaaUsesMedicalLicenseVctWithRequiredClaims() {
        val p = generateFor("SJV-QEAA-1")
        assertEquals(JsonPrimitive("urn:example:eaa:medical_license:1"), p["vct"], "QEAA must be issued as Medical License")
        assertNotNull(p["vct#integrity"])
        // Medical License adds: license_number, profession
        assertNotNull(p["license_number"], "Medical License requires 'license_number'")
        assertNotNull(p["profession"], "Medical License requires 'profession'")
    }

    @Test
    fun pubEaaUsesBirthCertificateVctWithRequiredClaims() {
        val p = generateFor("SJV-PuBEAA-1")
        assertEquals(JsonPrimitive("urn:example:eaa:birth_certificate:1"), p["vct"], "PuB-EAA must be issued as Birth Certificate")
        assertNotNull(p["vct#integrity"])
        // Birth Certificate adds: date_of_birth, place_of_birth{locality,country}, parents[…]
        assertNotNull(p["date_of_birth"], "Birth Certificate requires 'date_of_birth'")
        val pob = assertNotNull(p["place_of_birth"]?.jsonObject, "Birth Certificate requires 'place_of_birth'")
        assertNotNull(pob["locality"]); assertNotNull(pob["country"])
        val parents = assertNotNull(p["parents"]?.jsonArray, "Birth Certificate requires 'parents'")
        assertTrue(parents.isNotEmpty(), "parents must be a non-empty array")
        val firstParent = parents.first().jsonObject
        listOf("relation", "given_name", "family_name").forEach {
            assertNotNull(firstParent[it], "each parent requires '$it'")
        }
    }

    @Test
    fun explicitVctOverrideIsHonoured() {
        val p = runBlocking {
            decodePayload(
                SdJwtVcGenerator.generate(
                    testCase = pubEaaTestCase("SJV-EAA-1"),
                    issuerKey = issuerKey,
                    issuerCertificatePem = issuerCertPem,
                    vct = "urn:custom:override:1",
                ).sdJwtVc
            )
        }
        assertEquals(JsonPrimitive("urn:custom:override:1"), p["vct"], "explicit --vct override must win")
    }
}
