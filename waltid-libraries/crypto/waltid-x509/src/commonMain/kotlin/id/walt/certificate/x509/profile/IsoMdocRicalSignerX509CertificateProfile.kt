package id.walt.certificate.x509.profile

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.dn.DistinguishedName
import id.walt.certificate.x509.extension.AuthorityInfoAccessExtension.Companion.extensionAuthorityInfoAccess
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension.Companion.extensionAuthorityKeyIdentifier
import id.walt.certificate.x509.extension.CertificatePoliciesExtension.Companion.extensionCertificatePolicies
import id.walt.certificate.x509.extension.CrlDistributionPointsExtension.Companion.extensionCrlDistributionPoints
import id.walt.certificate.x509.extension.KeyUsageExtension
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.certificate.x509.model.GeneralName
import id.walt.certificate.x509.profile.IsoProfileX509CertificateValidationUtil.validateSignatureAlgorithm
import id.walt.certificate.x509.profile.IsoProfileX509CertificateValidationUtil.validateValidityTime
import id.walt.certificate.x509.profile.X509CertificateProfileValidationUtil.validateExtensionsAreNotCritical
import id.walt.certificate.x509.profile.X509CertificateProfileValidationUtil.validateSerialNumber
import id.walt.certificate.x509.profile.X509CertificateProfileValidationUtil.validateVersionV3
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.certificate.x509.validation.validator.X509CertificateValidator
import kotlin.time.Duration.Companion.days

/**
 * Profile for ISO/IEC 18013-5 RICAL signer certificates - certificates whose corresponding private
 * key signs a Reader Identity Certificate Authority List (RICAL).
 *
 * Certificate-path validation and the COSE signature remain separate checks. [acceptedCertificatePolicyOids]
 * is this application's set of accepted RICAL governance policy OIDs.
 */
class IsoMdocRicalSignerX509CertificateProfile(
    private val acceptedCertificatePolicyOids: Set<String>
) : X509CertificateProfile, X509CertificateValidator {

    init {
        require(acceptedCertificatePolicyOids.isNotEmpty()) { "Accepted RICAL certificate-policy OIDs are required" }
        require(acceptedCertificatePolicyOids.none(String::isBlank)) { "RICAL certificate-policy OIDs must not be blank" }
    }

    companion object {
        const val ID = "iso-rical-signer"

        private val allowedSignatureAlgorithmsOid = setOf(
            "1.2.840.10045.4.3.2", // ECDSA-with SHA256
            "1.2.840.10045.4.3.3", // ECDSA-with SHA384
            "1.2.840.10045.4.3.4"  // ECDSA with SHA512
        )

        private const val allowedSubjectPublicKeyAlgorithmOid = "1.2.840.10045.2.1"

        private val allowedSubjectPublicKeyEllipticCurveOid = setOf(
            // FIPS 186-4:
            "1.2.840.10045.3.1.7", // (Curve P-256)
            "1.3.132.0.34", // (Curve P-384)
            "1.3.132.0.35", // (Curve P-521)
            // Or one of the following curves specified in RFC 5639:
            "1.3.36.3.3.2.8.1.1.7",  // (brainpoolP256r1)
            "1.3.36.3.3.2.8.1.1.9",  // (brainpoolP320r1)
            "1.3.36.3.3.2.8.1.1.11", // (brainpoolP384r1)
            "1.3.36.3.3.2.8.1.1.13"  // (brainpoolP512r1)
        )

        private const val uncompressedEcPointPrefix: Byte = 0x04

        private val criticalExtensions = setOf(
            KeyUsageExtension.OID,
        )

        /** Maximum of 1187 days after "notBefore" date. */
        val maxValidityTime = 1_187.days
    }

    override val id: String = ID

    override suspend fun validate(
        context: ValidationContext,
        x509Certificate: X509Certificate,
    ) {
        validateVersionV3(context, x509Certificate)
        validateSerialNumber(context, x509Certificate)
        validateValidityTime(context, x509Certificate, maxValidityTime)
        validateSignatureAlgorithm(context, x509Certificate, allowedSignatureAlgorithmsOid)
        validateSubjectDn(context, x509Certificate)
        validateSubjectPublicKeyInfo(context, x509Certificate)
        validateExtensionAuthorityKeyIdentifier(context, x509Certificate)
        validateExtensionSubjectKeyIdentifier(context, x509Certificate)
        validateExtensionKeyUsage(context, x509Certificate)
        validateExtensionCrlDistributionPoints(context, x509Certificate)
        validateExtensionCertificatePolicies(context, x509Certificate)
        validateExtensionAuthorityInfoAccess(context, x509Certificate)
        validateExtensionsAreNotCritical(context, x509Certificate, criticalExtensions)
    }

    /**
     * countryName, organizationName and commonName are each mandatory and shall appear exactly once.
     */
    private fun validateSubjectDn(context: ValidationContext, x509Certificate: X509Certificate) {
        val dn = DistinguishedName.ofString(x509Certificate.data.subjectDn)
        val grouped = dn.rdnList.flatMap { it }.groupBy { it.type.name.lowercase() }

        if (grouped["c"].orEmpty().count { it.value.isNotBlank() } != 1) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "subjectDn",
                "RICAL signer certificate requires one country"
            )
        }
        if (grouped["o"].orEmpty().count { it.value.isNotBlank() } != 1) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "subjectDn",
                "RICAL signer certificate requires one organization"
            )
        }
        if (grouped["cn"].orEmpty().count { it.value.isNotBlank() } != 1) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "subjectDn",
                "RICAL signer certificate requires one common name"
            )
        }
    }

    /**
     * Algorithm: 1.2.840.10045.2.1 (id-ecPublicKey) on one of the FIPS 186-4 or RFC 5639 curves below,
     * encoded as an uncompressed EC point.
     */
    private fun validateSubjectPublicKeyInfo(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val subjectPublicKeyInfo = x509Certificate.data.subjectPublicKeyInfo
        if (subjectPublicKeyInfo.algorithmOid != allowedSubjectPublicKeyAlgorithmOid) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "subjectPublicKeyInfo",
                "RICAL signer certificate must use an elliptic-curve public key, but algorithm OID is " +
                        "'${subjectPublicKeyInfo.algorithmOid}' ('${subjectPublicKeyInfo.algorithmName}')"
            )
            return
        }
        if (subjectPublicKeyInfo.ellipticCurveOid == null ||
            !allowedSubjectPublicKeyEllipticCurveOid.contains(subjectPublicKeyInfo.ellipticCurveOid)
        ) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "subjectPublicKeyInfo",
                "RICAL signer certificate uses an unsupported elliptic curve OID '${subjectPublicKeyInfo.ellipticCurveOid}'"
            )
        }
        if (subjectPublicKeyInfo.keyValueRaw.size == 0 ||
            subjectPublicKeyInfo.keyValueRaw[0] != uncompressedEcPointPrefix
        ) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "subjectPublicKeyInfo",
                "RICAL signer certificate EC public key must use uncompressed form"
            )
        }
    }

    /** Same value as the subject key identifier of the RICAL provider root certificate; not critical. */
    private fun validateExtensionAuthorityKeyIdentifier(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val extension = x509Certificate.data.extensionAuthorityKeyIdentifier
        if (extension == null || (extension.keyIdentifier?.size ?: 0) <= 0) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "authorityKeyIdentifier",
                "RICAL signer certificate requires an authority key identifier"
            )
        } else if (extension.critical) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "authorityKeyIdentifier",
                "RICAL signer certificate authority key identifier must not be critical"
            )
        }
    }

    /** SHA-1 hash of the subject public key; not critical. */
    private fun validateExtensionSubjectKeyIdentifier(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val extension = x509Certificate.data.extensionSubjectKeyIdentifier
        if (extension == null) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "subjectKeyIdentifier",
                "RICAL signer certificate requires a subject key identifier"
            )
            return
        }
        if (extension.critical) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "subjectKeyIdentifier",
                "RICAL signer certificate subject key identifier must not be critical"
            )
        }
        if (extension.keyIdentifier != x509Certificate.data.subjectPublicKeyInfo.keyId) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "subjectKeyIdentifier",
                "RICAL signer certificate requires the SHA-1 subject key identifier"
            )
        }
    }

    /** Mandatory, critical, must contain only nonRepudiation. */
    private fun validateExtensionKeyUsage(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val extension = x509Certificate.data.extensionKeyUsage
        if (extension == null) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "keyUsage",
                "RICAL signer certificate requires key usage"
            )
            return
        }
        if (!extension.critical) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "keyUsage",
                "RICAL signer certificate key usage must be critical"
            )
        }
        if (extension.keyPurposeIdList != setOf(KeyUsageExtension.KeyUsage.nonRepudiation)) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "keyUsage",
                "RICAL signer certificate key usage must contain only nonRepudiation, but has ${extension.keyPurposeIdList}"
            )
        }
    }

    /**
     * Mandatory, not critical. The 'reasons' and 'cRL Issuer' fields shall not be used; distribution
     * points must be full-name URIs.
     */
    private fun validateExtensionCrlDistributionPoints(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val extension = x509Certificate.data.extensionCrlDistributionPoints
        if (extension == null || extension.distributionPoints.isEmpty()) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "crlDistributionPoints",
                "RICAL signer certificate requires CRL distribution points"
            )
            return
        }
        if (extension.critical) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "crlDistributionPoints",
                "RICAL signer certificate CRL distribution points must not be critical"
            )
        }
        val onlyFullNameUris = extension.distributionPoints.all { point ->
            val fullNames = point.distributionPointFullName
            point.reason == null &&
                    point.cRLIssuer == null &&
                    point.distributionPointNameRelativeToCrlIssuer == null &&
                    fullNames?.isNotEmpty() == true &&
                    fullNames.all { name ->
                        name.type == GeneralName.NameType.uniformResourceIdentifier && name.value.isNotBlank()
                    }
        }
        if (!onlyFullNameUris) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "crlDistributionPoints",
                "RICAL signer certificate CRL distribution points must contain only full-name URIs"
            )
        }
    }

    /** Mandatory, not critical, must contain an application-accepted RICAL governance policy OID. */
    private fun validateExtensionCertificatePolicies(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val extension = x509Certificate.data.extensionCertificatePolicies
        if (extension == null) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "certificatePolicies",
                "RICAL signer certificate requires certificatePolicies"
            )
            return
        }
        if (extension.critical) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "certificatePolicies",
                "RICAL signer certificate policies must be non-critical"
            )
        }
        if (extension.policyOids.none(acceptedCertificatePolicyOids::contains)) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "certificatePolicies",
                "RICAL signer certificate does not contain an accepted certificate-policy OID"
            )
        }
    }

    /** Optional; when present must not be critical. */
    private fun validateExtensionAuthorityInfoAccess(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val extension = x509Certificate.data.extensionAuthorityInfoAccess ?: return
        if (extension.critical) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "authorityInfoAccess",
                "RICAL signer authority information access must be non-critical"
            )
        }
    }
}
