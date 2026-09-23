package id.walt.certificate.x509.profile

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.withCertificateTestKey
import id.walt.certificate.x509.profile.IsoIaCaRootX509CertificateProfile.profileIaCaRootCertificate
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.certificate.x509.validation.X509SingleCertificateValidator
import id.walt.crypto.keys.KeyType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class IsoIaCaRootX509CertificateProfileJvmTest {

    private val validator = X509SingleCertificateValidator(
        listOf(IsoIaCaRootX509CertificateProfile)
    )

    @Test
    fun `Validation should fail for every JVM-only invalid IACA signing key type`() = runTest {
        listOf(
            KeyType.RSA3072,
            KeyType.RSA4096,
            KeyType.secp256k1,
        ).forEach { invalidKeyType ->
            withCertificateTestKey(invalidKeyType) { key ->
                val cert = X509CertificateUtil.createSelfSignedCertificate(key) {
                    profileIaCaRootCertificate(
                        issuerEmailAddress = "illegal.key@example.com",
                        issuerUri = "https://illegal-key.iaca.example.com",
                        issuerDnCountryCode = "US",
                        issuerDnCommonName = "Example IACA Illegal Key",
                    )
                }

                val result = validator.validate(cert)

                if (invalidKeyType != KeyType.secp256k1) {
                    assertTrue(
                        result.log.any {
                            it.validatorId == "iso-iaca-root.signatureAlgorithm" &&
                                    it.severity == ValidationResult.Severity.ERROR
                        },
                        "Key: $invalidKeyType"
                    )
                }
                assertTrue(
                    result.log.any {
                        it.validatorId == "iso-iaca-root.subjectPublicKeyInfo" &&
                                it.severity == ValidationResult.Severity.ERROR
                    },
                    "Key: $invalidKeyType"
                )
            }
        }
    }
}
