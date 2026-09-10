package id.walt.x509

import org.bouncycastle.asn1.x509.KeyUsage
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ClientAuthenticationKeyUsageTest {

    @Test
    fun `leaf without keyUsage is accepted for client authentication`() {
        val chain = TestCA.generateChain(leafKeyUsage = null)
        val leaf = CertificateDer(chain.leafCert.encoded)

        leaf.validateClientAuthenticationCertificateUsage()

        validateClientAuthenticationCertificateChain(
            leaf = leaf,
            chain = listOf(CertificateDer(chain.interCert.encoded)),
            trustAnchors = listOf(CertificateDer(chain.rootCert.encoded)),
        )
    }

    @Test
    fun `leaf with keyUsage lacking digitalSignature is rejected`() {
        val chain = TestCA.generateChain(leafKeyUsage = KeyUsage.keyEncipherment)
        val leaf = CertificateDer(chain.leafCert.encoded)

        assertFailsWith<IllegalArgumentException> {
            leaf.validateClientAuthenticationCertificateUsage()
        }
    }

    @Test
    fun `CA without keyUsage cannot issue client authentication certificates`() {
        val chain = TestCA.generateChain(caKeyUsage = null)

        assertFailsWith<X509ValidationException> {
            validateClientAuthenticationCertificateChain(
                leaf = CertificateDer(chain.leafCert.encoded),
                chain = listOf(CertificateDer(chain.interCert.encoded)),
                trustAnchors = listOf(CertificateDer(chain.rootCert.encoded)),
            )
        }
    }
}
