package id.walt.openid4vci.handlers.credential

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.cose.coseCompliantCbor
import id.walt.cose.verify
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.mdoc.objects.mso.ValidityInfo
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.handlers.endpoints.credential.Crypto2CredentialSigningKey
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.proofs.VerifiedCredentialProof
import id.walt.openid4vci.requests.credential.DefaultCredentialRequest
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalSerializationApi::class)
class MdocCredentialValidityTest {
    @Test
    fun `issued MSOs share all validity timestamps within each half day`() = runTest {
        val fixture = fixture()
        val cases = mapOf(
            "2026-09-08T00:00:00Z" to "2026-09-08T00:00:00Z",
            "2026-09-08T11:59:59Z" to "2026-09-08T00:00:00Z",
            "2026-09-08T12:00:00Z" to "2026-09-08T12:00:00Z",
            "2026-09-08T19:18:10Z" to "2026-09-08T12:00:00Z",
            "2026-09-08T19:18:17Z" to "2026-09-08T12:00:00Z",
            "2026-09-08T23:59:59Z" to "2026-09-08T12:00:00Z",
            "2026-09-09T00:00:00Z" to "2026-09-09T00:00:00Z",
        )
        for ((time, base) in cases) {
            val validity = fixture.issue(time)
            assertEquals(Instant.parse(base), validity.signed, time)
            assertEquals(validity.signed, validity.validFrom, time)
            assertEquals(Instant.parse(base.replace("2026", "2027")), validity.validUntil, time)
        }
    }

    @Test
    fun `rounding does not move signing before certificate validity or override explicit dates`() = runTest {
        val fixture = fixture("2026-09-08T14:10:05Z")
        val first = fixture.issue("2026-09-08T19:18:10Z")
        val second = fixture.issue("2026-09-08T19:18:17Z")
        assertEquals(first, second)
        assertEquals(Instant.parse("2026-09-08T14:10:05Z"), first.signed)
        assertEquals(first.signed, first.validFrom)
        val from = Instant.parse("2026-09-09T09:00:00Z")
        val until = Instant.parse("2026-10-09T09:00:00Z")
        val explicit = fixture.issue("2026-09-08T19:18:17Z", from, until)
        assertEquals(from, explicit.validFrom)
        assertEquals(until, explicit.validUntil)
    }

    @Test
    fun `data mapping overrides mdoc namespace data and evaluates functions before signing`() = runTest {
        val namespace = "org.iso.18013.5.1"
        val issued = fixture().issueCredential(
            credentialData = buildJsonObject {
                putJsonObject(namespace) {
                    put("issue_date", "2019-10-20")
                    put("expiry_date", "2024-10-20")
                    put("issuing_authority", "request-authority")
                    putJsonArray("administrative_number") {
                        add("request-value")
                    }
                }
            },
            dataMapping = buildJsonObject {
                putJsonObject(namespace) {
                    put("issue_date", "2026-09-15")
                    put("expiry_date", "2027-09-15")
                    put("issuing_authority", "<issuerId>")
                    putJsonArray("administrative_number") {
                        add("<issuerId>")
                        add("mapped-value")
                    }
                }
                put("id", "<uuid>")
                put("issuanceDate", "<timestamp>")
                put("expirationDate", "<timestamp-in:365d>")
            },
        )

        assertEquals(setOf(namespace), issued.namespaces!!.keys)
        val items = issued.namespaces!!.getValue(namespace).entries
            .associate { it.value.elementIdentifier to it.value.elementValue }
        assertEquals("2026-09-15", assertIs<CborString>(items.getValue("issue_date")).value)
        assertEquals("2027-09-15", assertIs<CborString>(items.getValue("expiry_date")).value)
        assertEquals("https://issuer.example", assertIs<CborString>(items.getValue("issuing_authority")).value)
        val administrativeNumber = assertIs<CborArray>(items.getValue("administrative_number"))
        assertEquals(
            listOf("https://issuer.example", "mapped-value"),
            administrativeNumber.map { assertIs<CborString>(it).value },
        )
    }

    @Test
    fun `date template functions produce YYYY-MM-DD strings accepted by full-date CBOR conversion`() = runTest {
        val namespace = "org.iso.18013.5.1"
        val issued = fixture().issueCredential(
            credentialData = buildJsonObject {
                putJsonObject(namespace) {
                    put("issue_date", "2019-10-20")
                    put("expiry_date", "2024-10-20")
                }
            },
            dataMapping = buildJsonObject {
                putJsonObject(namespace) {
                    put("issue_date", "<date>")
                    put("expiry_date", "<date-in:365d>")
                }
            },
        )
        val items = issued.namespaces!!.getValue(namespace).entries
            .associate { it.value.elementIdentifier to it.value.elementValue }
        val issueDate = assertIs<CborString>(items.getValue("issue_date")).value
        val expiryDate = assertIs<CborString>(items.getValue("expiry_date")).value
        // Both must be YYYY-MM-DD (10 chars, no time component)
        assertEquals(10, issueDate.length, "issue_date must be YYYY-MM-DD, got: $issueDate")
        assertEquals(10, expiryDate.length, "expiry_date must be YYYY-MM-DD, got: $expiryDate")
        assertTrue(expiryDate > issueDate, "expiry_date must be after issue_date")
    }

    @Test
    fun `date template functions produce CBOR full-date tag 1004 via mDocNameSpacesDataMappingConfig`() = runTest {
        val namespace = "org.iso.18013.5.1"
        val issued = fixture().issueCredential(
            credentialData = buildJsonObject {
                putJsonObject(namespace) {
                    put("issue_date", "placeholder")
                    put("expiry_date", "placeholder")
                    put("given_name", "Jane")
                }
            },
            dataMapping = buildJsonObject {
                putJsonObject(namespace) {
                    put("issue_date", "<date>")
                    put("expiry_date", "<date-in:365d>")
                }
            },
            mDocNameSpacesDataMappingConfig = mapOf(
                namespace to Json.decodeFromString<JsonObjectToCborMappingConfig>(
                    """{"entriesConfigMap":{"issue_date":{"type":"string","conversionType":"stringToFullDate"},"expiry_date":{"type":"string","conversionType":"stringToFullDate"}}}"""
                )
            ),
        )
        val items = issued.namespaces!!.getValue(namespace).entries
            .associate { it.value.elementIdentifier to it.value.elementValue }

        val issueDate = assertIs<CborString>(items.getValue("issue_date"))
        val expiryDate = assertIs<CborString>(items.getValue("expiry_date"))

        // Values must be YYYY-MM-DD (10 chars)
        assertEquals(10, issueDate.value.length, "issue_date must be YYYY-MM-DD, got: ${issueDate.value}")
        assertEquals(10, expiryDate.value.length, "expiry_date must be YYYY-MM-DD, got: ${expiryDate.value}")
        assertTrue(expiryDate.value > issueDate.value, "expiry_date must be after issue_date")

        // Both must carry CBOR tag 1004 (RFC 8943 full-date)
        assertEquals(listOf(1004uL), issueDate.tags, "issue_date must have tag 1004")
        assertEquals(listOf(1004uL), expiryDate.tags, "expiry_date must have tag 1004")

        // Unmapped field must pass through unchanged
        assertEquals("Jane", assertIs<CborString>(items.getValue("given_name")).value)
    }

    @Test
    fun `non-object namespace mapping values are silently dropped to prevent signer crash`() = runTest {
        val namespace = "org.iso.18013.5.1"
        val issued = fixture().issueCredential(
            credentialData = buildJsonObject {
                putJsonObject(namespace) { put("given_name", "Jane") }
            },
            dataMapping = buildJsonObject {
                put(namespace, "not-an-object")
            },
        )
        val items = issued.namespaces!!.getValue(namespace).entries
            .associate { it.value.elementIdentifier to it.value.elementValue }
        assertEquals("Jane", assertIs<CborString>(items.getValue("given_name")).value)
    }

    private suspend fun fixture(notBefore: String = "2026-01-01T00:00:00Z"): Fixture {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        suspend fun key(id: String) = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(KeyId(id), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY))
        )
        val issuer = key("issuer")
        val certificate = X509CertificateUtil.createSelfSignedCertificate(
            issuer, SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER),
        ) {
            subjectDn = "CN=mdoc validity test"
            validity = X509Certificate.Validity(Instant.parse(notBefore), Instant.parse("2028-01-01T00:00:00Z"))
        }
        return Fixture(issuer, key("holder"), certificate)
    }

    private class Fixture(val issuer: Key, val holder: Key, val certificate: X509Certificate) {
        suspend fun issue(time: String, validFrom: Instant? = null, validUntil: Instant? = null): ValidityInfo {
            return issueCredential(
                now = time,
                validFrom = validFrom,
                validUntil = validUntil,
            ).decodeMobileSecurityObject().validityInfo
        }

        suspend fun issueCredential(
            now: String = "2026-09-08T19:18:10Z",
            credentialData: JsonObject = buildJsonObject { putJsonObject("org.example") { put("given_name", "Jane") } },
            dataMapping: JsonObject? = null,
            mDocNameSpacesDataMappingConfig: Map<String, JsonObjectToCborMappingConfig>? = null,
            validFrom: Instant? = null,
            validUntil: Instant? = null,
        ): IssuerSigned {
            val configuration = CredentialConfiguration(CredentialFormat.MSO_MDOC, doctype = "org.example.mdoc")
            val result = MdocCredentialHandler(roundValidityToTwelveHours = true, now = { Instant.parse(now) }).sign(
                request = DefaultCredentialRequest(
                    client = DefaultClient("test-client", emptyList(), emptySet(), emptySet()),
                    credentialIdentifier = null, credentialConfigurationId = "mdoc", proofs = null,
                    credentialResponseEncryption = null,
                ),
                configuration = configuration,
                issuerKey = Crypto2CredentialSigningKey.select(issuer, configuration),
                issuerId = "https://issuer.example",
                credentialData = credentialData,
                dataMapping = dataMapping, selectiveDisclosure = null, x5Chain = listOf(certificate),
                display = null, w3cVersion = null, mDocNameSpacesDataMappingConfig = mDocNameSpacesDataMappingConfig,
                authorizedTransactionDataTypes = null, credentialStatus = null,
                validFrom = validFrom, validUntil = validUntil,
                verifiedProofs = listOf(
                    VerifiedCredentialProof("jwt", "", "ES256", buildJsonObject {}, buildJsonObject {}, holder, null, null, null)
                ),
            )
            val response = assertIs<CredentialResponseResult.Success>(result).response
            val encoded = assertNotNull(response.credentials).single().credential.jsonPrimitive.content
            val issued = coseCompliantCbor.decodeFromByteArray<IssuerSigned>(encoded.base64UrlDecode())
            assertTrue(issued.issuerAuth.verify(issuer, -7))
            return issued
        }
    }
}
