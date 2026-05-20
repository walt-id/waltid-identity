package id.walt.x509

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
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
        assertEquals(listOf("leaf.example.com"), parsed.subjectAlternativeNames?.dnsNames)
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
}
