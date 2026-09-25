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
import id.walt.openid4vci.handlers.endpoints.credential.CredentialIssuanceBatch
import id.walt.openid4vci.handlers.endpoints.credential.CredentialIssuanceInput
import id.walt.openid4vci.handlers.endpoints.credential.Crypto2CredentialSigningKey
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.openid4vci.metadata.issuer.CredentialDisplayLogo
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
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
            for (credentialCount in 1..2) {
                val validity = fixture.issue(time, credentialCount = credentialCount)
                assertEquals(Instant.parse(base), validity.signed, time)
                assertEquals(validity.signed, validity.validFrom, time)
                assertEquals(Instant.parse(base.replace("2026", "2027")), validity.validUntil, time)
            }
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
    fun `default validUntil uses the injected clock when rounding is disabled`() = runTest {
        val frozen = Instant.parse("2026-09-08T19:18:10Z")
        val validity = fixture().issueCredential(
            now = frozen.toString(),
            roundValidityToTwelveHours = false,
        ).decodeMobileSecurityObject().validityInfo
        assertEquals(frozen.plus(365.days), validity.validUntil)
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
                    put("issue_date", "2026-09-08")
                    put("expiry_date", "2027-09-15")
                    put("issuing_authority", "<issuerId>")
                    putJsonArray("administrative_number") {
                        add("<issuerId>")
                        add("mapped-value")
                    }
                }
            },
        )

        assertEquals(setOf(namespace), issued.namespaces!!.keys)
        val items = issued.namespaces!!.getValue(namespace).entries
            .associate { it.value.elementIdentifier to it.value.elementValue }
        assertEquals("2026-09-08", assertIs<CborString>(items.getValue("issue_date")).value)
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

        assertEquals(10, issueDate.value.length, "issue_date must be YYYY-MM-DD, got: ${issueDate.value}")
        assertEquals(10, expiryDate.value.length, "expiry_date must be YYYY-MM-DD, got: ${expiryDate.value}")
        assertTrue(expiryDate.value > issueDate.value, "expiry_date must be after issue_date")
        assertEquals(listOf(1004uL), issueDate.tags, "issue_date must have tag 1004")
        assertEquals(listOf(1004uL), expiryDate.tags, "expiry_date must have tag 1004")
        assertEquals("Jane", assertIs<CborString>(items.getValue("given_name")).value)
    }

    @Test
    fun `timestamp templates must not be used for full-date namespace fields`() = runTest {
        val namespace = "org.iso.18013.5.1"
        val result = fixture().issueResult(
            credentialData = buildJsonObject {
                putJsonObject(namespace) {
                    put("issue_date", "placeholder")
                }
            },
            dataMapping = buildJsonObject {
                putJsonObject(namespace) {
                    put("issue_date", "<timestamp>")
                }
            },
            mDocNameSpacesDataMappingConfig = mapOf(
                namespace to Json.decodeFromString<JsonObjectToCborMappingConfig>(
                    """{"entriesConfigMap":{"issue_date":{"type":"string","conversionType":"stringToFullDate"}}}"""
                )
            ),
        )
        assertIs<CredentialResponseResult.Failure>(result)
    }

    @Test
    fun `non-object namespace mapping values are rejected`() = runTest {
        val namespace = "org.iso.18013.5.1"
        val result = fixture().issueResult(
            credentialData = buildJsonObject {
                putJsonObject(namespace) { put("given_name", "Jane") }
            },
            dataMapping = buildJsonObject {
                put(namespace, "not-an-object")
            },
        )
        val failure = assertIs<CredentialResponseResult.Failure>(result)
        assertTrue(failure.error.description!!.contains("must be a JSON object"))
    }

    @Test
    fun `top-level mapping validFrom is rejected in favor of msoData`() = runTest {
        val result = fixture().issueResult(
            dataMapping = buildJsonObject {
                put("validFrom", "<timestamp>")
            },
        )
        val failure = assertIs<CredentialResponseResult.Failure>(result)
        assertTrue(failure.error.description!!.contains("msoData"))
    }

    @Test
    fun `mapped mDL issue_date after validFrom is rejected`() = runTest {
        val namespace = "org.iso.18013.5.1"
        val result = fixture().issueResult(
            credentialData = buildJsonObject {
                putJsonObject(namespace) {
                    put("issue_date", "2019-10-20")
                }
            },
            dataMapping = buildJsonObject {
                putJsonObject(namespace) {
                    put("issue_date", "2026-09-15")
                }
            },
        )
        val failure = assertIs<CredentialResponseResult.Failure>(result)
        assertTrue(failure.error.description!!.contains("issue_date"))
    }

    @Test
    fun `omitted msoData validUntil is rounded through the resolver provenance`() = runTest {
        val now = Instant.parse("2026-09-08T19:18:10Z")
        val resolved = id.walt.openid4vci.mdoc.MsoValidityResolver.resolve(null, signed = now)
        assertEquals(id.walt.openid4vci.mdoc.MsoValidUntilSource.DEFAULT, resolved.validUntilSource)
        assertNull(resolved.validUntil)
        val validity = fixture().issue(
            time = now.toString(),
            validFrom = resolved.validFrom,
            validUntil = resolved.validUntil,
        )
        assertEquals(Instant.parse("2026-09-08T12:00:00Z"), validity.signed)
        assertEquals(Instant.parse("2027-09-08T12:00:00Z"), validity.validUntil)
    }

    @Test
    fun `display mapping evaluates CredentialDisplay uri fields`() = runTest {
        val namespace = "org.iso.18013.5.1"
        val issued = fixture().issueCredential(
            credentialData = buildJsonObject {
                putJsonObject(namespace) { put("given_name", "Jane") }
            },
            dataMapping = buildJsonObject {
                putJsonObject(namespace) {
                    put("given_name", "<display>")
                }
            },
            display = listOf(
                CredentialDisplay(
                    name = "ISO mDL",
                    logo = CredentialDisplayLogo(uri = "https://issuer.example/logo.png", altText = "logo"),
                )
            ),
        )
        val namespaceJson = issued.namespacesToJson().getValue(namespace).jsonObject
        val display = assertIs<JsonArray>(namespaceJson.getValue("given_name"))
        assertEquals("ISO mDL", display.single().jsonObject.getValue("name").jsonPrimitive.content)
        assertEquals(
            "https://issuer.example/logo.png",
            display.single().jsonObject.getValue("logo").jsonObject.getValue("url").jsonPrimitive.content,
        )
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
        return Fixture(issuer, listOf(key("holder-1"), key("holder-2")), certificate)
    }

    private class Fixture(val issuer: Key, val holders: List<Key>, val certificate: X509Certificate) {
        suspend fun issue(
            time: String,
            validFrom: Instant? = null,
            validUntil: Instant? = null,
            credentialCount: Int = 1,
        ): ValidityInfo {
            val result = issueResult(
                now = time,
                credentialCount = credentialCount,
                validFrom = validFrom,
                validUntil = validUntil,
            )
            val response = assertIs<CredentialResponseResult.Success>(result).response
            val credentials = assertNotNull(response.credentials)
            assertEquals(credentialCount, credentials.size)
            val validities = credentials.map { credential ->
                val encoded = credential.credential.jsonPrimitive.content
                val issued = coseCompliantCbor.decodeFromByteArray<IssuerSigned>(encoded.base64UrlDecode())
                assertTrue(issued.issuerAuth.verify(issuer, -7))
                issued.decodeMobileSecurityObject().validityInfo
            }
            assertEquals(1, validities.distinct().size)
            return validities.first()
        }

        suspend fun issueCredential(
            now: String = "2026-09-08T19:18:10Z",
            credentialData: JsonObject = buildJsonObject { putJsonObject("org.example") { put("given_name", "Jane") } },
            dataMapping: JsonObject? = null,
            mDocNameSpacesDataMappingConfig: Map<String, JsonObjectToCborMappingConfig>? = null,
            display: List<CredentialDisplay>? = null,
            validFrom: Instant? = null,
            validUntil: Instant? = null,
            roundValidityToTwelveHours: Boolean = true,
        ): IssuerSigned {
            val result = issueResult(
                now = now,
                credentialData = credentialData,
                dataMapping = dataMapping,
                mDocNameSpacesDataMappingConfig = mDocNameSpacesDataMappingConfig,
                display = display,
                validFrom = validFrom,
                validUntil = validUntil,
                roundValidityToTwelveHours = roundValidityToTwelveHours,
            )
            val response = assertIs<CredentialResponseResult.Success>(result).response
            val encoded = assertNotNull(response.credentials).single().credential.jsonPrimitive.content
            val issued = coseCompliantCbor.decodeFromByteArray<IssuerSigned>(encoded.base64UrlDecode())
            assertTrue(issued.issuerAuth.verify(issuer, -7))
            return issued
        }

        suspend fun issueResult(
            now: String = "2026-09-08T19:18:10Z",
            credentialData: JsonObject = buildJsonObject { putJsonObject("org.example") { put("given_name", "Jane") } },
            dataMapping: JsonObject? = null,
            mDocNameSpacesDataMappingConfig: Map<String, JsonObjectToCborMappingConfig>? = null,
            display: List<CredentialDisplay>? = null,
            validFrom: Instant? = null,
            validUntil: Instant? = null,
            credentialCount: Int = 1,
            roundValidityToTwelveHours: Boolean = true,
        ): CredentialResponseResult {
            val configuration = CredentialConfiguration(CredentialFormat.MSO_MDOC, doctype = "org.example.mdoc")
            return MdocCredentialHandler(roundValidityToTwelveHours = roundValidityToTwelveHours, now = { Instant.parse(now) }).sign(
                request = DefaultCredentialRequest(
                    client = DefaultClient("test-client", emptyList(), emptySet(), emptySet()),
                    credentialIdentifier = null, credentialConfigurationId = "mdoc", proofs = null,
                    credentialResponseEncryption = null,
                ),
                configuration = configuration,
                issuerKey = Crypto2CredentialSigningKey.select(issuer, configuration),
                issuerId = "https://issuer.example",
                issuanceBatch = CredentialIssuanceBatch(
                    inputs = List(credentialCount) {
                        CredentialIssuanceInput(credentialData = credentialData)
                    },
                    verifiedProofs = holders.take(credentialCount).map { holder ->
                        VerifiedCredentialProof("jwt", "", "ES256", buildJsonObject {}, buildJsonObject {}, holder, null, null, null)
                    },
                ),
                dataMapping = dataMapping, selectiveDisclosure = null, x5Chain = listOf(certificate),
                display = display, w3cVersion = null, mDocNameSpacesDataMappingConfig = mDocNameSpacesDataMappingConfig,
                authorizedTransactionDataTypes = null,
                validFrom = validFrom, validUntil = validUntil, expectedUpdate = null,
            )
        }
    }
}
