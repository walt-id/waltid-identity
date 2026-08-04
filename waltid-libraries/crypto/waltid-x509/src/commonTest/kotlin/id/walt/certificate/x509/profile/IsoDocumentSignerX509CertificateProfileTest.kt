package id.walt.certificate.x509.profile

import id.walt.certificate.TestKeys
import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.profile.IsoDocumentSignerX509CertificateProfile.profileDocumentSignerCertificate
import id.walt.certificate.x509.profile.IsoIaCaRootX509CertificateProfile.profileIaCaRootCertificate
import id.walt.certificate.x509.validation.X509SingleCertificateValidator
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

class IsoDocumentSignerX509CertificateProfileTest {

    @Ignore
    @Test
    fun abc() = runTest {
        val rootCert = rootCaCertDeferred.await()
        val subjectKey = JWKKey.importPEM(TestKeys.ed25519KeyPem).getOrThrow()
        val cert = X509CertificateUtil.createCertificate(
            rootCaKeyDeferred.await(),
            rootCaCertDeferred.await()
        ) {
            profileDocumentSignerCertificate(
                issuerCertificate = rootCert,
                subjectKey = subjectKey,
                subjectDnCountryCode = "AT",
                subjectDnStateOrProvinceName = "Styria",
                subjectDnLocalityName = "Graz",
                subjectDnOrganizationName = "Walt ID",
                subjectDnCommonName = "My Document Signer Certificate",
                subjectDnSerialNumber = "1234567"
            )
        }
        val result = validator.validate(cert)
        if (!result.valid) {
            result.log.forEach { println(it) }
        }
        assertTrue(result.valid)
    }

    companion object {
        private val validator = X509SingleCertificateValidator(listOf(IsoDocumentSignerX509CertificateProfile))

        private val companionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val rootCaKeyDeferred = CompletableDeferred<Key>()
        private val rootCaCertDeferred = CompletableDeferred<X509Certificate>()

        init {
            companionScope.launch {
                val key = JWKKey.importPEM(TestKeys.ecP256KeyPem).getOrThrow()
                rootCaKeyDeferred.complete(key)
                val cert = X509CertificateUtil.createSelfSignedCertificate(key) {
                    profileIaCaRootCertificate(
                        issuerEmailAddress = "iaca@example.com",
                        issuerUri = "https://iaca.example.com",
                        issuerDnCountryCode = "AT",
                        issuerDnCommonName = "Example IACA for testing Document Signer Profile",
                    )
                }
                rootCaCertDeferred.complete(cert)
            }
        }
    }
}