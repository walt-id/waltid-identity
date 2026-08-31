package id.walt.mdoc.issuance

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.cose.Cose
import id.walt.cose.CoseCertificate
import id.walt.cose.toCoseKey
import id.walt.cose.verify
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.credsdata.DrivingPrivilege
import id.walt.mdoc.credsdata.Mdl
import id.walt.mdoc.crypto.MdocCrypto.getSharedSecret
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.mdoc.verification.verifyIssuerAuthentication
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Crypto2MdocIssuerTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    @Test
    fun `mdoc issuance and issuer verification use crypto2`() = runTest {
        val issuerKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("issuer"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val holderKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("holder"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.KEY_AGREEMENT),
            )
        )
        val holderJwk = holderKey.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk
        val holderCoseKey = holderJwk.toCoseKey()
        val sigAlg = SignatureAlgorithm.Ecdsa(
            DigestAlgorithm.SHA_256,
            EcdsaSignatureEncoding.DER,
        )
        val certificate = X509CertificateUtil.createSelfSignedCertificate(issuerKey, sigAlg) {
            subjectDn = "CN=Crypto2 mdoc issuer"
        }
        val expectedUpdate = kotlin.time.Clock.System.now().plus(kotlin.time.Duration.parse("180d"))
        val issued = MdocIssuer.issueUniversal(
            issuerKey = issuerKey,
            signatureAlgorithm = Cose.Algorithm.ES256,
            issuerCertificate = listOf(CoseCertificate(certificate.encodedDer.toByteArray())),
            holderKey = holderCoseKey,
            docType = "org.example.mdoc",
            data = MdocIssuer.MdocUniversalIssuanceData(
                namespaces = mapOf(
                    "org.example" to JsonObject(mapOf("given_name" to JsonPrimitive("Jane")))
                )
            ),
            expectedUpdate = expectedUpdate,
        )

        assertTrue(issued.issuerAuth.verify(issuerKey, Cose.Algorithm.ES256))
        val mso = issued.issuerAuth.decodeIsoPayload<MobileSecurityObject>()
        assertEquals("org.example.mdoc", mso.docType)
        assertEquals(expectedUpdate.epochSeconds, mso.validityInfo.expectedUpdate?.epochSeconds)
        val parsedIssuerAuth = issued.getParsedIssuerAuthCrypto2()
        assertTrue(issued.issuerAuth.verify(parsedIssuerAuth.signerKey, Cose.Algorithm.ES256))
        val verification = verifyIssuerAuthentication(
            document = Document(docType = "org.example.mdoc", issuerSigned = issued),
            validateCertificateConstraints = false,
        )
        assertEquals(Cose.Algorithm.ES256, verification.coseAlgorithm)

        val issueDate = LocalDate(2026, 1, 1)
        val typesafeIssued = MdocIssuer.issueTypesafe(
            issuerKey = issuerKey,
            signatureAlgorithm = Cose.Algorithm.ES256,
            issuerCertificate = listOf(CoseCertificate(certificate.encodedDer.toByteArray())),
            holderKey = holderCoseKey,
            typesafeData = Mdl(
                familyName = "Doe",
                givenName = "Jane",
                issueDate = issueDate,
                expiryDate = LocalDate(2036, 1, 1),
                documentNumber = "DOC-1",
                drivingPrivileges = listOf(DrivingPrivilege("B", issueDate)),
            ),
        )
        assertTrue(typesafeIssued.issuerAuth.verify(issuerKey, Cose.Algorithm.ES256))
        assertEquals(
            "org.iso.18013.5.1.mDL",
            typesafeIssued.issuerAuth.decodeIsoPayload<MobileSecurityObject>().docType,
        )
    }

    @Test
    fun `crypto2 ECDH derives the same mdoc shared secret`() = runTest {
        val first = agreementKey("first")
        val second = agreementKey("second")
        val firstPublic = first.capabilities.publicKeyExporter!!.exportPublicKey()
        val secondPublic = second.capabilities.publicKeyExporter!!.exportPublicKey()

        assertContentEquals(
            first.getSharedSecret(secondPublic),
            second.getSharedSecret(firstPublic),
        )
    }

    private suspend fun agreementKey(id: String) = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId(id),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.KEY_AGREEMENT),
        )
    )
}
