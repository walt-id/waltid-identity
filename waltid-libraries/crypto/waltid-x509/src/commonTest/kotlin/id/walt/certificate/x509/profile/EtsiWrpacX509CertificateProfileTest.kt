package id.walt.certificate.x509.profile

import id.walt.certificate.x509.TestKeyUtil
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.profile.EtsiWrpacX509CertificateProfile.profileWrpAccessCertificate
import id.walt.certificate.x509.validation.X509SingleCertificateValidator
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EtsiWrpacX509CertificateProfileTest {

    private val sigAlg = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)

    private suspend fun rootCa(keyName: String) = TestKeyUtil.genEcKey(keyName).let { rootKey ->
        rootKey to X509CertificateUtil.createSelfSignedCertificate(rootKey, sigAlg) {
            subjectDn = "CN=Example WRPAC Root CA,O=Walt.id,OrganizationIdentifier=VATAT-U99999999,C=AT"
            extensionBasicConstraints { critical = true; cA = true }
        }
    }

    @Test
    fun shouldCreateValidNaturalPersonNonQualifiedCertificate() = runTest {
        val (rootKey, rootCert) = rootCa("wrpac-ncp-n-root")
        val subjectKey = TestKeyUtil.genEcKey("wrpac-ncp-n-subject")
        val cert = X509CertificateUtil.createCertificate(rootKey, rootCert, sigAlg) {
            profileWrpAccessCertificate(
                subjectKey = subjectKey,
                subjectDn = "CN=Jane Doe,GivenName=Jane,Surname=Doe,SerialNumber=RP-12345,C=AT",
                policyOid = Etsi119411Part8.NCP_N_EUDIWRP,
                contactEmail = "relying-party@example.com",
                caIssuerUri = "https://ca.example.com/root.crt",
                crlDistributionPointUri = "https://ca.example.com/crl",
            )
        }

        val result = validator.validate(cert)
        assertTrue(result.valid, "Validation log: ${result.log}")
    }

    @Test
    fun shouldCreateValidLegalPersonQualifiedCertificate() = runTest {
        val (rootKey, rootCert) = rootCa("wrpac-qcp-l-root")
        val subjectKey = TestKeyUtil.genEcKey("wrpac-qcp-l-subject")
        val cert = X509CertificateUtil.createCertificate(rootKey, rootCert, sigAlg) {
            profileWrpAccessCertificate(
                subjectKey = subjectKey,
                subjectDn = "CN=Example Relying Party,O=Walt.id,OrganizationIdentifier=VATAT-U12345678,C=AT",
                policyOid = Etsi119411Part8.QCP_L_EUDIWRP,
                contactUri = "https://relying-party.example.com/contact",
                caIssuerUri = "https://ca.example.com/root.crt",
                ocspResponderUri = "https://ca.example.com/ocsp",
            )
        }

        val result = validator.validate(cert)
        assertTrue(result.valid, "Validation log: ${result.log}")
    }

    @Test
    fun shouldRejectMissingContactInfo() = runTest {
        val subjectKey = TestKeyUtil.genEcKey("wrpac-no-contact")
        assertFailsWith<IllegalArgumentException> {
            X509CertificateUtil.createSelfSignedCertificate(subjectKey, sigAlg) {
                profileWrpAccessCertificate(
                    subjectKey = subjectKey,
                    subjectDn = "CN=Example Relying Party,O=Walt.id,OrganizationIdentifier=VATAT-U12345678,C=AT",
                    policyOid = Etsi119411Part8.NCP_L_EUDIWRP,
                    crlDistributionPointUri = "https://ca.example.com/crl",
                )
            }
        }
    }

    @Test
    fun shouldRejectMissingRevocationMechanism() = runTest {
        val subjectKey = TestKeyUtil.genEcKey("wrpac-no-revocation")
        assertFailsWith<IllegalArgumentException> {
            X509CertificateUtil.createSelfSignedCertificate(subjectKey, sigAlg) {
                profileWrpAccessCertificate(
                    subjectKey = subjectKey,
                    subjectDn = "CN=Example Relying Party,O=Walt.id,OrganizationIdentifier=VATAT-U12345678,C=AT",
                    policyOid = Etsi119411Part8.NCP_L_EUDIWRP,
                    contactEmail = "relying-party@example.com",
                )
            }
        }
    }

    @Test
    fun shouldRejectInvalidPolicyOid() = runTest {
        val subjectKey = TestKeyUtil.genEcKey("wrpac-bad-policy")
        assertFailsWith<IllegalArgumentException> {
            X509CertificateUtil.createSelfSignedCertificate(subjectKey, sigAlg) {
                profileWrpAccessCertificate(
                    subjectKey = subjectKey,
                    subjectDn = "CN=Example Relying Party,O=Walt.id,OrganizationIdentifier=VATAT-U12345678,C=AT",
                    policyOid = "1.2.3.4.5",
                    contactEmail = "relying-party@example.com",
                    crlDistributionPointUri = "https://ca.example.com/crl",
                )
            }
        }
    }

    @Test
    fun shouldRejectSelfSignedLookingCertificate() = runTest {
        val (rootKey, rootCert) = rootCa("wrpac-self-signed-root")
        val cert = X509CertificateUtil.createCertificate(rootKey, rootCert, sigAlg) {
            profileWrpAccessCertificate(
                subjectKey = rootKey,
                subjectDn = rootCert.data.subjectDn,
                policyOid = Etsi119411Part8.NCP_L_EUDIWRP,
                contactEmail = "relying-party@example.com",
                crlDistributionPointUri = "https://ca.example.com/crl",
            )
        }

        val result = validator.validate(cert)
        assertFalse(result.valid)
        assertTrue(result.log.any { it.validatorId == "${EtsiWrpacX509CertificateProfile.ID}.issuerDn" })
    }

    companion object {
        private val validator = X509SingleCertificateValidator(listOf(EtsiWrpacX509CertificateProfile))
    }
}
