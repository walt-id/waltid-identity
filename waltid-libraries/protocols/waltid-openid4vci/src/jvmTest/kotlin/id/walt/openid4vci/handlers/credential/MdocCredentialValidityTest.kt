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
            val configuration = CredentialConfiguration(CredentialFormat.MSO_MDOC, doctype = "org.example.mdoc")
            val result = MdocCredentialHandler(roundValidityToTwelveHours = true, now = { Instant.parse(time) }).sign(
                request = DefaultCredentialRequest(
                    client = DefaultClient("test-client", emptyList(), emptySet(), emptySet()),
                    credentialIdentifier = null, credentialConfigurationId = "mdoc", proofs = null,
                    credentialResponseEncryption = null,
                ),
                configuration = configuration,
                issuerKey = Crypto2CredentialSigningKey.select(issuer, configuration),
                issuerId = "https://issuer.example",
                credentialData = buildJsonObject { putJsonObject("org.example") { put("given_name", "Jane") } },
                dataMapping = null, selectiveDisclosure = null, x5Chain = listOf(certificate),
                display = null, w3cVersion = null, mDocNameSpacesDataMappingConfig = null,
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
            return issued.decodeMobileSecurityObject().validityInfo
        }
    }
}
