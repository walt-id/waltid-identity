package id.walt.certificate.x509

import kotlin.test.Test
import kotlin.test.assertEquals

class Pkcs10CertificateSigningRequestParserTest {

    @Test
    fun shouldParseCsrPem() {
        X509CertificateUtil.parseCsrPem(CSR_PEM).also { csr ->
            assertEquals("C=AT,ST=Vienna,L=Vienna,O=Walt.id,CN=://walt.id", csr.requestedCertificate.subjectDn)
            assertEquals("1.2.840.10045.2.1", csr.requestedCertificate.subjectPublicKeyInfo.algorithmOid)
            assertEquals("id-ecPublicKey", csr.requestedCertificate.subjectPublicKeyInfo.algorithmName)
            assertEquals(
                "040f62d46bb95bb0aef9cac3e291191042839ed4670c1c0121e58eff26983511bdef383cf9e352cbd4f520abebd262072b514cad988979853fd69dc25b00e97793",
                csr.requestedCertificate.subjectPublicKeyInfo.publicKeyHex
            )
            assertEquals(1, csr.requestedCertificate.extensions.size)
            assertEquals("1.2.840.10045.4.3.2", csr.signatureAlgorithmOid)
            assertEquals("ecdsa-with-SHA256", csr.signatureAlgorithmName)
            assertEquals(
                "3046022100ed434954325834e6b6108f9bd28f5a038409866dee4b470e92f709d21c0c221a022100e7ac8154cd9d2928f98deb08c3b7821c2be2a1edbd92186cacb177f2476a42a8",
                csr.signatureValueHex
            )
        }
    }

    companion object {
        val CSR_PEM = """
-----BEGIN CERTIFICATE REQUEST-----
MIIBVDCB+gIBADBWMQswCQYDVQQGEwJBVDEPMA0GA1UECAwGVmllbm5hMQ8wDQYD
VQQHDAZWaWVubmExEDAOBgNVBAoMB1dhbHQuaWQxEzARBgNVBAMMCjovL3dhbHQu
aWQwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQPYtRruVuwrvnKw+KRGRBCg57U
ZwwcASHljv8mmDURve84PPnjUsvU9SCr69JiBytRTK2YiXmFP9adwlsA6XeToEIw
QAYJKoZIhvcNAQkOMTMwMTAvBgNVHREEKDAmgg06Ly93YWx0aWQuY29tgg86Ly93
YWx0aWQuY2xvdWSHBMCoAWQwCgYIKoZIzj0EAwIDSQAwRgIhAO1DSVQyWDTmthCP
m9KPWgOECYZt7ktHDpL3CdIcDCIaAiEA56yBVM2dKSj5jesIw7eCHCvioe29khhs
rLF38kdqQqg=
-----END CERTIFICATE REQUEST-----""".trimIndent()
    }

}