package id.walt.x509

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CertificateSigningRequestTest {

    @Test
    fun buildAndParseGenericCsRoundTrip() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val profileData = CertificateSigningRequestProfileData(
            subjectName = X509DistinguishedName(
                commonName = "Example Leaf",
                country = "US",
                organizationName = "Example Org",
            ),
            subjectAlternativeNames = X509SubjectAlternativeNames(
                dnsNames = listOf("leaf.example.com"),
            ),
        )

        val bundle = CertificateSigningRequestBuilder().build(
            profileData = profileData,
            signingKey = key,
        )

        val pem = bundle.csrDer.toPEMEncodedString()
        assertTrue(pem.contains("BEGIN CERTIFICATE REQUEST"))

        val parsed = parseCertificateSigningRequest(
            CertificateSigningRequestDer.fromPEMEncodedString(pem),
        )

        assertEquals(profileData.subjectName.commonName, parsed.subjectName.commonName)
        assertEquals(profileData.subjectName.country, parsed.subjectName.country)
        assertNotNull(parsed.subjectAlternativeNames)
        assertEquals(listOf("leaf.example.com"), parsed.subjectAlternativeNames.dnsNames)
    }

    @Test
    fun buildGenericSelfSignedCertificate() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val bundle = GenericX509CertificateBuilder().build(
            profileData = GenericX509CertificateProfileData(
                subjectName = X509DistinguishedName(
                    commonName = "Example CA",
                    country = "US",
                ),
                isCertificateAuthority = true,
                keyUsage = setOf(X509KeyUsage.KeyCertSign, X509KeyUsage.CRLSign),
            ),
            subjectPublicKey = key.getPublicKey(),
            signingKey = key,
        )

        assertTrue(bundle.certificateDer.toPEMEncodedString().contains("BEGIN CERTIFICATE"))
        assertEquals("Example CA", bundle.decodedCertificate.subjectName.commonName)
        assertTrue(bundle.decodedCertificate.isCertificateAuthority)
    }

    @Test
    fun buildAndVerifyRsaCertificatesAndCsrs() = runTest {
        val rsaTypes = mapOf(
            KeyType.RSA to "1.2.840.113549.1.1.11",
            KeyType.RSA3072 to "1.2.840.113549.1.1.12",
            KeyType.RSA4096 to "1.2.840.113549.1.1.13",
        )

        rsaTypes.forEach { (keyType, expectedSignatureOid) ->
            val key = JWKKey.generate(keyType)
            val subjectName = X509DistinguishedName(commonName = "$keyType Test")
            val certificate = GenericX509CertificateBuilder().build(
                profileData = GenericX509CertificateProfileData(subjectName = subjectName),
                subjectPublicKey = key.getPublicKey(),
                signingKey = key,
            )
            val parsedCertificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(certificate.certificateDer.bytes.toByteArray())) as X509Certificate
            parsedCertificate.verify(parsedCertificate.publicKey)
            assertEquals(expectedSignatureOid, parsedCertificate.sigAlgOID)

            val csr = CertificateSigningRequestBuilder().build(
                profileData = CertificateSigningRequestProfileData(subjectName = subjectName),
                signingKey = key,
            )
            assertEquals(
                subjectName.commonName,
                parseCertificateSigningRequest(csr.csrDer).subjectName.commonName,
            )
        }
    }
}
