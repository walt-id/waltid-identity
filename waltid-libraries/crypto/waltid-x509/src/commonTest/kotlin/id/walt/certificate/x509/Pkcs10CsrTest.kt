package id.walt.certificate.x509

import id.walt.certificate.TestKeys
import id.walt.certificate.x509.extension.SubjectAlternativeNameExtension.Companion.extensionSan
import id.walt.certificate.x509.model.GeneralName
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Pkcs10CsrTest {

    @Test
    fun buildAndParseGenericCsrRoundTrip() = runTest {
        val key = JWKKey.importPEM(TestKeys.ecP256KeyPem).getOrThrow()
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
}