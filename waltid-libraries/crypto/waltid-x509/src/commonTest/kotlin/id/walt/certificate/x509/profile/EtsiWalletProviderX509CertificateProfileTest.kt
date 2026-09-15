package id.walt.certificate.x509.profile

import id.walt.certificate.x509.TestKeyUtil
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.profile.EtsiPidProviderX509CertificateProfile.profilePidProviderCertificate
import id.walt.certificate.x509.profile.EtsiWalletProviderX509CertificateProfile.profileWalletProviderCertificate
import id.walt.certificate.x509.validation.X509SingleCertificateValidator
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EtsiWalletProviderX509CertificateProfileTest {

    private val sigAlg = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)

    @Test
    fun shouldCreateValidCaIssuedWalletProviderCertificate() = runTest {
        val rootKey = TestKeyUtil.genEcKey("wal-root")
        val rootCert = X509CertificateUtil.createSelfSignedCertificate(rootKey, sigAlg) {
            subjectDn = "CN=Example Root CA,O=Walt.id,OrganizationIdentifier=VATAT-U99999999,C=AT"
            extensionBasicConstraints { critical = true; cA = true }
        }

        val subjectKey = TestKeyUtil.genEcKey("wal-provider")
        val cert = X509CertificateUtil.createCertificate(rootKey, rootCert, sigAlg) {
            profileWalletProviderCertificate(
                subjectKey = subjectKey,
                subjectDn = "CN=Example Wallet Provider,O=Walt.id,OrganizationIdentifier=VATAT-U12345678,C=AT",
                certificatePolicyOids = listOf("0.4.0.194112.1.2"),
                caIssuerUri = "https://ca.example.com/root.crt",
            )
        }

        val result = validator.validate(cert)
        assertTrue(result.valid, "Validation log: ${result.log}")
    }

    @Test
    fun shouldRejectCertificateMissingRequiredWalletQcType() = runTest {
        // A PID Provider certificate carries qct-pid, not qct-wal - the Wallet validator must reject it.
        val rootKey = TestKeyUtil.genEcKey("cross-role-root-2")
        val rootCert = X509CertificateUtil.createSelfSignedCertificate(rootKey, sigAlg) {
            subjectDn = "CN=Example Root CA,O=Walt.id,OrganizationIdentifier=VATAT-U99999999,C=AT"
            extensionBasicConstraints { critical = true; cA = true }
        }

        val subjectKey = TestKeyUtil.genEcKey("cross-role-subject-2")
        val pidCert = X509CertificateUtil.createCertificate(rootKey, rootCert, sigAlg) {
            profilePidProviderCertificate(
                subjectKey = subjectKey,
                subjectDn = "CN=Example PID Provider,O=Walt.id,OrganizationIdentifier=VATAT-U12345678,C=AT",
                certificatePolicyOids = listOf("0.4.0.194112.1.1"),
                caIssuerUri = "https://ca.example.com/root.crt",
            )
        }

        val result = validator.validate(pidCert)
        assertFalse(result.valid)
        assertTrue(result.log.any { it.validatorId == "${EtsiWalletProviderX509CertificateProfile.ID}.qcStatements" })
    }

    companion object {
        private val validator = X509SingleCertificateValidator(listOf(EtsiWalletProviderX509CertificateProfile))
    }
}
