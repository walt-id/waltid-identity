import id.walt.cose.coseCompliantCbor
import id.walt.vical.CertificateInfo
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromByteArray
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertTrue

class VicalDataModelTest {

    fun getResource(name: String) =
        this::class.java.classLoader.getResource(name) ?: error("Resource for test not found: $name")

    private fun toDer(pem: String): ByteArray {
        val base64 = pem
            .replace("-----BEGIN CERTIFICATE-----", "")
            .replace("-----END CERTIFICATE-----", "")
            .replace("\\s".toRegex(), "")
        val derBytes = Base64.getDecoder().decode(base64)
        return derBytes
    }

    private fun toX509(pem: String): X509Certificate {
        val derBytes = toDer(pem)
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(derBytes)) as X509Certificate
    }

    private fun parseValidateCertificate(certFilePem: String) {
        val pem = getResource(certFilePem).readText()

        val x509 = toX509(pem) // check if valid X.509 cert

        assertTrue(x509.subjectX500Principal.name.isNotEmpty(), "Could not parse x.509 cert")

        val der = toDer(pem)

        val certificateInfo =
            coseCompliantCbor.decodeFromByteArray<CertificateInfo>(der) // check if Certificate Info can be parsed

        assertTrue(certificateInfo.issuingAuthority?.isNotEmpty() ?: false, "Could not parse Certificate Info")
    }


    @Test
    fun `Should parse and validate Certificate austroads_root-certificate`() = runTest {
        parseValidateCertificate("root-certificates/austroads_root-certificate.pem")
    }

    @Test
    fun `Should parse and validate IACA Certificate iaca-certificate`() = runTest {
        parseValidateCertificate("root-certificates/iaca-certificate.pem")
    }

}