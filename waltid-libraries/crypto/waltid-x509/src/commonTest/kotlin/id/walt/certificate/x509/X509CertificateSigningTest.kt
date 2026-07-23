package id.walt.certificate.x509

import id.walt.certificate.x509.SignatureValidationUtil.verifyPemChain
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.extension.KeyUsageExtension
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.crypto.keys.KeyType
import id.walt.x509.createX509TestKey
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class X509CertificateSigningTest {

    @Test
    fun buildGenericSelfSignedCertificate() = runTest {
        val key = createX509TestKey(KeyType.secp256r1)
        val cert = X509CertificateUtil.createSelfSignedCertificate(key) {
            subjectDn = "CN=Example CA, C=US"
            issuerDn = "CN=Example CA, C=US"
            extensionBasicConstraints {
                cA = true
            }
            extensionKeyUsage {
                addKeyUsage(KeyUsageExtension.KeyUsage.keyCertSign, KeyUsageExtension.KeyUsage.cRLSign)
            }
        }

        val pem = cert.encodedPem

        assertTrue(pem.contains("BEGIN CERTIFICATE"))
        verifyPemChain(pem, pem)

        assertEquals("CN=Example CA,C=US", cert.data.subjectDn)
        assertEquals("CN=Example CA,C=US", cert.data.issuerDn)
        assertNotNull(cert.data.extensionBasicConstraints) { bc ->
            assertEquals(true, bc.cA)
            assertNull(bc.pathLenConstraint)
        }
        assertNotNull(cert.data.extensionKeyUsage) { ku ->
            assertTrue(ku.keyPurposeIdList.contains(KeyUsageExtension.KeyUsage.keyCertSign))
            assertTrue(ku.keyPurposeIdList.contains(KeyUsageExtension.KeyUsage.cRLSign))
            assertEquals(2, ku.keyPurposeIdList.size)
        }
    }
}