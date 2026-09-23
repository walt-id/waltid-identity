package id.walt.certificate.x509

import id.walt.certificate.x509.extension.SubjectAlternativeNameExtension.Companion.extensionSan
import id.walt.certificate.x509.model.GeneralName
import id.walt.crypto.keys.KeyType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class Pkcs10CertificateUtilCsrSigningTest {

    @Test
    fun shouldSignCsr() = runTest {
        withCertificateTestKey(KeyType.secp256r1) { key ->
            val expectedPublicPem = key.getPublicKey().exportPEM()
            val csr = X509CertificateUtil.createCsr(key) {
                requestedCertificate.apply {
                    subjectDn = "OU=unit test, O=Walt.id"

                    extensionSan {
                        addDnsName("www.walt.id")
                        addIpAddress("127.0.0.1")
                    }
                }
            }
            assertNotNull(csr).also { csr ->
                assertNotNull(csr.requestedCertificate).also { data ->
                    assertEquals("OU=unit test,O=Walt.id", data.subjectDn)
                    assertNotNull(data.subjectPublicKeyInfo) { pk ->
                        assertEquals("1.2.840.10045.2.1", pk.algorithmOid)
                        assertEquals("ecPublicKey", pk.algorithmName)
                        assertEquals(normalizePem(expectedPublicPem), normalizePem(pk.encodedPem))
                    }
                    assertNotNull(data.extensionSan) { san ->
                        assertEquals(2, san.alternativeNames.size)
                        san.alternativeNames.get(0).also {
                            assertEquals(GeneralName.NameType.dNSName, it.type)
                            assertEquals("www.walt.id", it.value)
                        }
                        san.alternativeNames.get(1).also {
                            assertEquals(GeneralName.NameType.IPAddress, it.type)
                            assertEquals("127.0.0.1", it.value)
                        }
                    }
                }
            }
            assertTrue(X509CertificateUtil.validateCsrSignature(csr))
            SignatureValidationUtil.verifyCsrPem(csr.encodedPem)
        }
    }
}
