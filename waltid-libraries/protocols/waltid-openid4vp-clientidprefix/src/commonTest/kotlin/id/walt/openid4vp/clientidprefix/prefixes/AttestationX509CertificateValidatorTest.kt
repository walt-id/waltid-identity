package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.certificate.x509.MockX509Certificate
import id.walt.certificate.x509.Pkcs10CertificateSigningRequest
import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.extension.Extension
import id.walt.certificate.x509.extension.KeyUsageExtension
import id.walt.certificate.x509.extension.KeyUsageExtension.KeyUsage
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class AttestationX509CertificateValidatorTest {

    @Test
    fun leafWithoutKeyUsageIsAccepted() = runTest {
        val context = leafContext()
        AttestationX509CertificateValidator().validate(context, MockX509Certificate("CN=leaf"))
        assertTrue(context.valid)
        assertFalse(context.log.any { it.severity == ValidationResult.Severity.ERROR })
    }

    @Test
    fun leafWithKeyUsageLackingDigitalSignatureIsRejected() = runTest {
        val context = leafContext()
        AttestationX509CertificateValidator().validate(
            context,
            certificateWithKeyUsage(KeyUsage.keyEncipherment),
        )
        assertFalse(context.valid)
        assertTrue(
            context.log.any {
                it.severity == ValidationResult.Severity.ERROR &&
                    it.message.contains("digitalSignature")
            }
        )
    }

    private fun leafContext(): ValidationContext {
        val context = ValidationContext(
            cryptoRuntime = CryptoRuntime(defaultSoftwareKeyProviders()),
            chainLength = 1,
            trustStore = InMemoryTrustStore(),
        )
        context.setCurrent(AttestationX509CertificateValidator.id, certificateIndex = 0, certificateSubjectDn = "CN=leaf")
        return context
    }

    private fun certificateWithKeyUsage(vararg usages: KeyUsage): X509Certificate =
        object : X509Certificate {
            inner class Data : X509Certificate.CertificateData {
                override val version = 3
                override val serialNumberRaw = ByteString()
                override val issuerDn = "CN=issuer"
                override val issuerDnRaw = ByteString()
                override val validity = X509Certificate.Validity(
                    Clock.System.now(),
                    Clock.System.now() + 1.days,
                )
                override val subjectDn = "CN=leaf"
                override val subjectDnRaw = ByteString()
                override val subjectPublicKeyInfo: Pkcs10CertificateSigningRequest.SubjectPublicKeyInfo
                    get() = error("unused")
                override val extensions: Map<String, Extension> = mapOf(
                    KeyUsageExtension.OID to KeyUsageExtension.Builder().apply {
                        addKeyUsage(*usages)
                    },
                )
            }

            override val data = Data()
            override val signatureAlgorithmOid = ""
            override val signatureValueRaw = ByteString()
            override val encodedDer = ByteString()
        }
}
