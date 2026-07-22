package id.walt.mdoc.issuance

import id.walt.cose.Cose
import id.walt.cose.CoseCertificate
import id.walt.cose.toCoseKey
import id.walt.cose.verify
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.mdoc.credsdata.DrivingPrivilege
import id.walt.mdoc.credsdata.Mdl
import id.walt.mdoc.crypto.MdocCrypto.getSharedSecret
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.mdoc.verification.verifyIssuerAuthentication
import id.walt.x509.GenericX509CertificateBuilder
import id.walt.x509.GenericX509CertificateProfileData
import id.walt.x509.X509DistinguishedName
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Crypto2MdocIssuerTest {
    private val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))

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
        val certificate = GenericX509CertificateBuilder().buildDer(
            profileData = GenericX509CertificateProfileData(
                subjectName = X509DistinguishedName(commonName = "Crypto2 mdoc issuer"),
            ),
            subjectPublicKey = issuerKey,
            signingKey = issuerKey,
            signatureAlgorithm = SignatureAlgorithm.Ecdsa(
                DigestAlgorithm.SHA_256,
                EcdsaSignatureEncoding.DER,
            ),
        )
        val issued = MdocIssuer.issueUniversal(
            issuerKey = issuerKey,
            signatureAlgorithm = Cose.Algorithm.ES256,
            issuerCertificate = listOf(CoseCertificate(certificate.bytes.toByteArray())),
            holderKey = holderCoseKey,
            docType = "org.example.mdoc",
            data = MdocIssuer.MdocUniversalIssuanceData(
                namespaces = mapOf(
                    "org.example" to JsonObject(mapOf("given_name" to JsonPrimitive("Jane")))
                )
            ),
        )

        assertTrue(issued.issuerAuth.verify(issuerKey, Cose.Algorithm.ES256))
        assertEquals("org.example.mdoc", issued.issuerAuth.decodeIsoPayload<MobileSecurityObject>().docType)
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
            issuerCertificate = listOf(CoseCertificate(certificate.bytes.toByteArray())),
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
