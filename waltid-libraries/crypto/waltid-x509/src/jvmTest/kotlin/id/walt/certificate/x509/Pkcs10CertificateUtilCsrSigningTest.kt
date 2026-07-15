package id.walt.certificate.x509

import id.walt.certificate.TestData.intermediateIssuerPrivateKey
import id.walt.certificate.TestData.intermediateIssuerPublicKeyHex
import id.walt.certificate.x509.extension.SubjectAlternativeNameExtension.Companion.extensionSan
import id.walt.certificate.x509.model.GeneralName
import id.walt.crypto.keys.JvmJWKKeyCreator
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.pkcs.CertificationRequest
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.operator.ContentVerifierProvider
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class Pkcs10CertificateUtilCsrSigningTest {

    @Test
    fun shouldSignCsr() = runTest {

        val key = JvmJWKKeyCreator.importPEM(intermediateIssuerPrivateKey).getOrThrow()

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
                    assertEquals("id-ecPublicKey", pk.algorithmName)
                    assertEquals(intermediateIssuerPublicKeyHex, pk.publicKeyHex)
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

        verifyCsrWithJdk(csr.encodedPem)
    }


    companion object {

        fun verifyCsrWithJdk(csrPemContent: String) {
            StringReader(csrPemContent).use { csrReader ->
                PEMParser(csrReader).use { pemParser ->
                    // Read object from PEM file
                    val parsedObject = pemParser.readObject()

                    val csr: PKCS10CertificationRequest =
                        parsedObject as? PKCS10CertificationRequest
                            ?: if (parsedObject is CertificationRequest) {
                                PKCS10CertificationRequest(parsedObject as CertificationRequest?)
                            } else {
                                throw IllegalArgumentException("Provided file is not a valid CSR")
                            }

                    // Build the verifier provider using standard JCA providers
                    val verifierProvider: ContentVerifierProvider? = JcaContentVerifierProviderBuilder()
                        .setProvider("BC") // Uses Bouncy Castle
                        .build(csr.getSubjectPublicKeyInfo())

                    // Validate the signature against the embedded public key
                    assertTrue(csr.isSignatureValid(verifierProvider))
                }
            }
        }
    }
}