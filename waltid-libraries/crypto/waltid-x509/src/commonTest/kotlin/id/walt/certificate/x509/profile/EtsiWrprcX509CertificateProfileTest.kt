package id.walt.certificate.x509.profile

import id.walt.certificate.x509.TestKeyUtil
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.profile.EtsiWrprcX509CertificateProfile.profileWrpRegistrationCertificate
import id.walt.certificate.x509.validation.X509SingleCertificateValidator
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Covers only the baseline certificate shape this DRAFT profile implements - see
 * [EtsiWrprcX509CertificateProfile]'s class doc for what's deliberately not validated yet.
 */
class EtsiWrprcX509CertificateProfileTest {

    private val sigAlg = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)

    @Test
    fun shouldCreateValidRegistrationCertificateButFlagUnvalidatedIntendedUse() = runTest {
        val rootKey = TestKeyUtil.genEcKey("wrprc-root")
        val rootCert = X509CertificateUtil.createSelfSignedCertificate(rootKey, sigAlg) {
            subjectDn = "CN=Example WRPRC Registrar,O=Walt.id,OrganizationIdentifier=VATAT-U99999999,C=AT"
            extensionBasicConstraints { critical = true; cA = true }
        }

        val subjectKey = TestKeyUtil.genEcKey("wrprc-subject")
        val cert = X509CertificateUtil.createCertificate(rootKey, rootCert, sigAlg) {
            profileWrpRegistrationCertificate(
                subjectKey = subjectKey,
                subjectDn = "CN=Example Relying Party,O=Walt.id,OrganizationIdentifier=VATAT-U12345678,C=AT",
                certificatePolicyOids = listOf("0.4.0.194118.1.2"),
                caIssuerUri = "https://ca.example.com/root.crt",
            )
        }

        val result = validator.validate(cert)
        assertTrue(result.valid, "Validation log: ${result.log}")
        assertTrue(
            result.log.any { it.validatorId == "${EtsiWrprcX509CertificateProfile.ID}.registeredIntendedUse" },
            "Expected a WARNING flagging the unvalidated registered intended use, log: ${result.log}"
        )
    }

    @Test
    fun shouldRejectMissingCertificatePolicy() = runTest {
        val subjectKey = TestKeyUtil.genEcKey("wrprc-no-policy")
        assertFailsWith<IllegalArgumentException> {
            X509CertificateUtil.createSelfSignedCertificate(subjectKey, sigAlg) {
                profileWrpRegistrationCertificate(
                    subjectKey = subjectKey,
                    subjectDn = "CN=Example Relying Party,O=Walt.id,OrganizationIdentifier=VATAT-U12345678,C=AT",
                    certificatePolicyOids = emptyList(),
                )
            }
        }
    }

    companion object {
        private val validator = X509SingleCertificateValidator(listOf(EtsiWrprcX509CertificateProfile))
    }
}
