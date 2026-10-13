package id.walt.certificate.x509.profile

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.builder.X509CertificateDataBuilder
import id.walt.certificate.x509.dn.DistinguishedName
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension.Companion.extensionAuthorityKeyIdentifier
import id.walt.certificate.x509.extension.CrlDistributionPointsExtension.Companion.extensionCrlDistributionPoints
import id.walt.certificate.x509.extension.ExtendedKeyUsageExtension
import id.walt.certificate.x509.extension.ExtendedKeyUsageExtension.Companion.extensionExtendedKeyUsage
import id.walt.certificate.x509.extension.KeyUsageExtension
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.certificate.x509.model.GeneralName
import id.walt.certificate.x509.profile.IsoProfileX509CertificateValidationUtil.validateSignatureAlgorithm
import id.walt.certificate.x509.profile.IsoProfileX509CertificateValidationUtil.validateValidityTime
import id.walt.certificate.x509.profile.X509CertificateProfileValidationUtil.validateExtensionsAreNotCritical
import id.walt.certificate.x509.profile.X509CertificateProfileValidationUtil.validateKeyUsageIsDigitalSignature
import id.walt.certificate.x509.profile.X509CertificateProfileValidationUtil.validateSerialNumber
import id.walt.certificate.x509.profile.X509CertificateProfileValidationUtil.validateVersionV3
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.certificate.x509.validation.validator.X509CertificateValidator
import id.walt.crypto2.keys.Key
import id.walt.x509.MdocReaderAuthenticationEkuOid
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import id.walt.crypto.keys.Key as Crypto1Key

/**
 * Profile for certificates used to authenticate an mdoc reader in the device retrieval mdoc response.
 * described in
 * ISO/IEC 18013-5 Second Edition
 *
 * Annex B
 * Section 1.7 mdoc reader authentication
 *
 * This profile validates the unconditional reader-certificate fields. Conditional IACA
 * issuer-contact validation is outside its scope and requires application policy.
 * Certificate-path validation remains a separate check, done by
 * [id.walt.x509.validatedMdocReaderAuthenticationCertificatePath].
 */
object IsoMdocReaderAuthenticationX509CertificateProfile : X509CertificateProfile, X509CertificateValidator {

    const val ID = "iso-mdoc-reader-authentication"

    private val allowedSignatureAlgorithmsOid = setOf(
        "1.2.840.10045.4.3.2", // ECDSA-with SHA256
        "1.2.840.10045.4.3.3", // ECDSA-with SHA384
        "1.2.840.10045.4.3.4"  // ECDSA with SHA512
    )

    private const val ecPublicKeyOid = "1.2.840.10045.2.1"

    private val allowedSubjectPublicKeyAlgorithmOid = setOf(
        ecPublicKeyOid,
        "1.3.101.112", // Ed25519
        "1.3.101.113"  // Ed448
    )

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
        ExtendedKeyUsageExtension.OID,
    )

    /** Maximum of 1187 days after "notBefore" date. */
    val maxValidityTime = 1_187.days

    override val id: String = ID

    fun X509CertificateDataBuilder.profileMdocReaderAuthenticationCertificate(
        crlDistributionPointUri: String,
        subjectKey: Key,
        subjectDnCommonName: String,
    ) {
        require(subjectDnCommonName.isNotBlank()) { "common name must not be blank" }
        profileMdocReaderAuthenticationCertificate()
        this.subjectDn = "CN=${subjectDnCommonName}"
        subjectPublicKey(subjectKey)
        extensionCrlDistributionPoints {
            addDistributionPointFullName(
                listOf(GeneralName(GeneralName.NameType.uniformResourceIdentifier, crlDistributionPointUri))
            )
        }
    }

    fun X509CertificateDataBuilder.profileMdocReaderAuthenticationCertificate(
        crlDistributionPointUri: String,
        subjectKey: Crypto1Key,
        subjectDnCommonName: String,
    ) {
        require(subjectDnCommonName.isNotBlank()) { "common name must not be blank" }
        profileMdocReaderAuthenticationCertificate()
        this.subjectDn = "CN=${subjectDnCommonName}"
        subjectPublicKey(subjectKey)
        extensionCrlDistributionPoints {
            addDistributionPointFullName(
                listOf(GeneralName(GeneralName.NameType.uniformResourceIdentifier, crlDistributionPointUri))
            )
        }
    }

    fun X509CertificateDataBuilder.profileMdocReaderAuthenticationCertificate() {
        val now = Clock.System.now()
        validity = X509Certificate.Validity(
            notBefore = now,
            notAfter = now + maxValidityTime
        )
        extensionSubjectKeyIdentifier()
        extensionKeyUsage {
            critical = true
            addKeyUsage(KeyUsageExtension.KeyUsage.digitalSignature)
        }
        extensionExtendedKeyUsage {
            critical = true
            addKeyUsage(MdocReaderAuthenticationEkuOid)
        }
    }

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
        validateKeyUsageIsDigitalSignature(context, x509Certificate)
        validateExtensionExtendedKeyUsage(context, x509Certificate)
        validateExtensionCrlDistributionPoints(context, x509Certificate)
        validateExtensionsAreNotCritical(context, x509Certificate, criticalExtensions)
    }

    /** commonName is mandatory and shall appear exactly once. */
    private fun validateSubjectDn(context: ValidationContext, x509Certificate: X509Certificate) {
        val dn = DistinguishedName.ofString(x509Certificate.data.subjectDn)
        val grouped = dn.rdnList.flatMap { it }.groupBy { it.type.name.lowercase() }
        if (grouped["cn"].orEmpty().count { it.value.isNotBlank() } != 1) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "subjectDn",
                "Reader certificate requires one common name"
            )
        }
    }

    /**
     * Algorithm: 1.2.840.10045.2.1 (id-ecPublicKey) on one of the FIPS 186-4 or RFC 5639 curves
     * below, encoded as an uncompressed EC point; or 1.3.101.112 / 1.3.101.113 (Ed25519 / Ed448)
     * with no EC parameters.
     */
    private fun validateSubjectPublicKeyInfo(context: ValidationContext, x509Certificate: X509Certificate) {
        val subjectPublicKeyInfo = x509Certificate.data.subjectPublicKeyInfo
        if (!allowedSubjectPublicKeyAlgorithmOid.contains(subjectPublicKeyInfo.algorithmOid)) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "subjectPublicKeyInfo",
                "Reader certificate uses an unsupported subject-public-key algorithm OID " +
                    "'${subjectPublicKeyInfo.algorithmOid}' ('${subjectPublicKeyInfo.algorithmName}')"
            )
            return
        }
        if (subjectPublicKeyInfo.algorithmOid == ecPublicKeyOid) {
            if (subjectPublicKeyInfo.ellipticCurveOid == null ||
                !allowedSubjectPublicKeyEllipticCurveOid.contains(subjectPublicKeyInfo.ellipticCurveOid)
            ) {
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "subjectPublicKeyInfo",
                    "Reader certificate uses an unsupported elliptic curve OID '${subjectPublicKeyInfo.ellipticCurveOid}'"
                )
            }
            if (subjectPublicKeyInfo.keyValueRaw.size == 0 ||
                subjectPublicKeyInfo.keyValueRaw[0] != uncompressedEcPointPrefix
            ) {
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "subjectPublicKeyInfo",
                    "Reader certificate EC public key must use uncompressed form"
                )
            }
        } else if (subjectPublicKeyInfo.ellipticCurveOid != null) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "subjectPublicKeyInfo",
                "Edwards-curve reader certificates must omit EC parameters"
            )
        }
    }

    /** Same value as the subject key identifier of the reader's issuing CA certificate; not critical. */
    private fun validateExtensionAuthorityKeyIdentifier(context: ValidationContext, x509Certificate: X509Certificate) {
        val extension = x509Certificate.data.extensionAuthorityKeyIdentifier
        if (extension == null || (extension.keyIdentifier?.size ?: 0) <= 0) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "authorityKeyIdentifier",
                "Reader certificate requires an authority key identifier"
            )
        } else if (extension.critical) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "authorityKeyIdentifier",
                "Reader certificate authority key identifier must not be critical"
            )
        }
    }

    /** SHA-1 hash of the subject public key; not critical. */
    private fun validateExtensionSubjectKeyIdentifier(context: ValidationContext, x509Certificate: X509Certificate) {
        val extension = x509Certificate.data.extensionSubjectKeyIdentifier
        if (extension == null) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "subjectKeyIdentifier",
                "Reader certificate requires a subject key identifier"
            )
            return
        }
        if (extension.critical) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "subjectKeyIdentifier",
                "Reader certificate subject key identifier must not be critical"
            )
        }
        if (extension.keyIdentifier != x509Certificate.data.subjectPublicKeyInfo.keyId) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "subjectKeyIdentifier",
                "Reader certificate requires the SHA-1 subject key identifier"
            )
        }
    }

    /**
     * Mandatory, critical; must contain the ISO/IEC 18013-5 reader-authentication EKU OID
     * ([MdocReaderAuthenticationEkuOid]). Other extended key usages (e.g. the ISO/IEC 23220-4
     * recommended OID) may additionally be present.
     */
    private fun validateExtensionExtendedKeyUsage(context: ValidationContext, x509Certificate: X509Certificate) {
        val extension = x509Certificate.data.extensionExtendedKeyUsage
        if (extension == null || extension.keyPurposeIdList.isEmpty()) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "extendedKeyUsage",
                "Reader certificate requires extended key usage"
            )
            return
        }
        if (!extension.critical) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "extendedKeyUsage",
                "Reader certificate extended key usage must be critical"
            )
        }
        if (MdocReaderAuthenticationEkuOid !in extension.keyPurposeIdList) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "extendedKeyUsage",
                "Reader certificate extended key usage must contain $MdocReaderAuthenticationEkuOid"
            )
        }
    }

    /**
     * Mandatory, not critical. The 'reasons' and 'cRL Issuer' fields shall not be used; distribution
     * points must be full-name URIs.
     */
    private fun validateExtensionCrlDistributionPoints(context: ValidationContext, x509Certificate: X509Certificate) {
        val extension = x509Certificate.data.extensionCrlDistributionPoints
        if (extension == null || extension.distributionPoints.isEmpty()) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "crlDistributionPoints",
                "Reader certificate requires CRL distribution points"
            )
            return
        }
        if (extension.critical) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "crlDistributionPoints",
                "Reader certificate CRL distribution points must not be critical"
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
                "Reader certificate CRL distribution points must contain only full-name URIs"
            )
        }
    }
}
