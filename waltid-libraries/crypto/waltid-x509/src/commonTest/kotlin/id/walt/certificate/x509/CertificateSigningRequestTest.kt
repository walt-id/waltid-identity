package id.walt.certificate.x509

import id.walt.certificate.x509.extension.SubjectAlternativeNameExtension.Companion.extensionSan
import id.walt.certificate.x509.model.GeneralName
import id.walt.crypto.keys.KeyType
import id.walt.x509.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class CertificateSigningRequestTest {

    @Ignore
    @Test
    fun buildAndParseGenericCsrRoundTrip() = runTest {
        val key = createX509TestKey(KeyType.secp256r1)
        val csr = X509CertificateUtil.createCsr(key) {
            requestedCertificate.apply {
                subjectDn = "CN=Example Leaf,O=Example Org,C=US"
                extensionSan {
                    addDnsName("leaf.example.com")
                }
            }
        }

        val pem = csr.encodedPem
        assertTrue(pem.contains("BEGIN CERTIFICATE REQUEST"))

        val parsed = X509CertificateUtil.parseCsrPem(pem)

        assertEquals("CN=Example Leaf,O=Example Org,C=US", parsed.requestedCertificate.subjectDn)
        assertNotNull(parsed.requestedCertificate.extensionSan)
        assertEquals(
            listOf("leaf.example.com"), parsed.requestedCertificate.extensionSan
                ?.alternativeNames
                ?.filter { it.type == GeneralName.NameType.dNSName }
                ?.map { it.value })
    }

    // @Test
    fun buildGenericSelfSignedCertificate() = runTest {
        val key = createX509TestKey(KeyType.secp256r1)
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
        assertEquals(setOf(X509KeyUsage.KeyCertSign, X509KeyUsage.CRLSign), bundle.decodedCertificate.keyUsage)
    }
}