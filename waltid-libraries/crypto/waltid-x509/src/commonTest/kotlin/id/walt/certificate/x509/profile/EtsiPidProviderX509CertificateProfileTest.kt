package id.walt.certificate.x509.profile

import id.walt.certificate.x509.TestKeyUtil
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.profile.EtsiPidProviderX509CertificateProfile.profileEtsiPidProviderCertificate
import id.walt.certificate.x509.profile.EtsiWalletProviderX509CertificateProfile.profileEtsiWalletProviderCertificate
import id.walt.certificate.x509.validation.X509SingleCertificateValidator
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EtsiPidProviderX509CertificateProfileTest {

    private val sigAlg = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)

    @Test
    fun shouldCreateValidCaIssuedPidProviderCertificate() = runTest {
        val rootKey = TestKeyUtil.genEcKey("pid-root")
        val rootCert = X509CertificateUtil.createSelfSignedCertificate(rootKey, sigAlg) {
            subjectDn = "CN=Example Root CA,O=Walt.id,OrganizationIdentifier=VATAT-U99999999,C=AT"
            extensionBasicConstraints { critical = true; cA = true }
        }

        val subjectKey = TestKeyUtil.genEcKey("pid-provider")
        val cert = X509CertificateUtil.createCertificate(rootKey, rootCert, sigAlg) {
            profileEtsiPidProviderCertificate(
                subjectKey = subjectKey,
                subjectDn = "CN=Example PID Provider,O=Walt.id,OrganizationIdentifier=VATAT-U12345678,C=AT",
                certificatePolicyOids = listOf("0.4.0.194112.1.1"),
                caIssuerUri = "https://ca.example.com/root.crt",
            )
        }

        val result = validator.validate(cert)
        assertTrue(result.valid, "Validation log: ${result.log}")
    }

    @Test
    fun shouldCreateValidSelfSignedPidProviderCertificate() = runTest {
        val issuerKey = TestKeyUtil.genEcKey("pid-self-signed")
        val cert = X509CertificateUtil.createSelfSignedCertificate(issuerKey, sigAlg) {
            profileEtsiPidProviderCertificate(
                subjectDn = "CN=Example PID Provider,O=Walt.id,OrganizationIdentifier=VATAT-U12345678,C=AT",
                certificatePolicyOids = listOf("0.4.0.194112.1.1"),
            )
        }

        val result = validator.validate(cert)
        assertTrue(result.valid, "Validation log: ${result.log}")
    }

    @Test
    fun shouldCreateValidNaturalPersonPidProviderCertificate() = runTest {
        val rootKey = TestKeyUtil.genEcKey("pid-np-root")
        val rootCert = X509CertificateUtil.createSelfSignedCertificate(rootKey, sigAlg) {
            subjectDn = "CN=Example Root CA,O=Walt.id,OrganizationIdentifier=VATAT-U99999999,C=AT"
            extensionBasicConstraints { critical = true; cA = true }
        }

        val subjectKey = TestKeyUtil.genEcKey("pid-np-provider")
        val cert = X509CertificateUtil.createCertificate(rootKey, rootCert, sigAlg) {
            profileEtsiPidProviderCertificate(
                subjectKey = subjectKey,
                subjectDn = "CN=Jane Doe,GivenName=Jane,Surname=Doe,SerialNumber=AT-12345,C=AT",
                certificatePolicyOids = listOf("0.4.0.194112.1.1"),
                caIssuerUri = "https://ca.example.com/root.crt",
            )
        }

        val result = validator.validate(cert)
        assertTrue(result.valid, "Validation log: ${result.log}")
    }

    @Test
    fun shouldRejectMissingCertificatePolicy() = runTest {
        val subjectKey = TestKeyUtil.genEcKey("pid-no-policy")
        assertFailsWith<IllegalArgumentException> {
            X509CertificateUtil.createSelfSignedCertificate(subjectKey, sigAlg) {
                profileEtsiPidProviderCertificate(
                    subjectKey = subjectKey,
                    subjectDn = "CN=Example PID Provider,O=Walt.id,OrganizationIdentifier=VATAT-U12345678,C=AT",
                    certificatePolicyOids = emptyList(),
                )
            }
        }
    }

    @Test
    fun shouldRejectCaIssuedCertificateMissingAuthorityInfoAccess() = runTest {
        val rootKey = TestKeyUtil.genEcKey("pid-no-aia-root")
        val rootCert = X509CertificateUtil.createSelfSignedCertificate(rootKey, sigAlg) {
            subjectDn = "CN=Example Root CA,O=Walt.id,OrganizationIdentifier=VATAT-U99999999,C=AT"
            extensionBasicConstraints { critical = true; cA = true }
        }

        val subjectKey = TestKeyUtil.genEcKey("pid-no-aia")
        val cert = X509CertificateUtil.createCertificate(rootKey, rootCert, sigAlg) {
            profileEtsiPidProviderCertificate(
                subjectKey = subjectKey,
                subjectDn = "CN=Example PID Provider,O=Walt.id,OrganizationIdentifier=VATAT-U12345678,C=AT",
                certificatePolicyOids = listOf("0.4.0.194112.1.1"),
                // no caIssuerUri/ocspResponderUri, despite not being self-signed
            )
        }

        val result = validator.validate(cert)
        assertFalse(result.valid)
        assertTrue(result.log.any { it.validatorId == "${EtsiPidProviderX509CertificateProfile.ID}.authorityInfoAccess" })
    }

    @Test
    fun shouldRejectCertificateMissingRequiredPidQcType() = runTest {
        // A Wallet Provider certificate carries qct-wal, not qct-pid - the PID validator must reject it.
        val rootKey = TestKeyUtil.genEcKey("cross-role-root")
        val rootCert = X509CertificateUtil.createSelfSignedCertificate(rootKey, sigAlg) {
            subjectDn = "CN=Example Root CA,O=Walt.id,OrganizationIdentifier=VATAT-U99999999,C=AT"
            extensionBasicConstraints { critical = true; cA = true }
        }

        val subjectKey = TestKeyUtil.genEcKey("cross-role-subject")
        val walletCert = X509CertificateUtil.createCertificate(rootKey, rootCert, sigAlg) {
            profileEtsiWalletProviderCertificate(
                subjectKey = subjectKey,
                subjectDn = "CN=Example Wallet Provider,O=Walt.id,OrganizationIdentifier=VATAT-U12345678,C=AT",
                certificatePolicyOids = listOf("0.4.0.194112.1.1"),
                caIssuerUri = "https://ca.example.com/root.crt",
            )
        }

        val result = validator.validate(walletCert)
        assertFalse(result.valid)
        assertTrue(result.log.any { it.validatorId == "${EtsiPidProviderX509CertificateProfile.ID}.qcStatements" })
    }

    companion object {
        private val validator = X509SingleCertificateValidator(listOf(EtsiPidProviderX509CertificateProfile))
    }
}
