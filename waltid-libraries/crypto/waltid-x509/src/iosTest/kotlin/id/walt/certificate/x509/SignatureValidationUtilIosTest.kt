package id.walt.certificate.x509

import id.walt.crypto.keys.KeyType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SignatureValidationUtilIosTest {

    @Test
    fun validatesSelfSignedRootCertificate() = runTest {
        withCertificateTestKey(KeyType.secp256r1) { key ->
            val root = X509CertificateUtil.createSelfSignedCertificate(key) {
                subjectDn = "CN=Root"
            }

            SignatureValidationUtil.verifyPemChain(root.encodedPem, root.encodedPem)
        }
    }

    @Test
    fun validatesChildCertificateAgainstRoot() = runTest {
        withCertificateTestKey(KeyType.secp256r1) { key ->
            val root = X509CertificateUtil.createSelfSignedCertificate(key) {
                subjectDn = "CN=Root"
            }
            val child = X509CertificateUtil.createCertificate(key, root) {
                subjectDn = "CN=Child"
                subjectPublicKey(key)
            }

            SignatureValidationUtil.verifyPemChain(child.encodedPem, root.encodedPem)
        }
    }
}
