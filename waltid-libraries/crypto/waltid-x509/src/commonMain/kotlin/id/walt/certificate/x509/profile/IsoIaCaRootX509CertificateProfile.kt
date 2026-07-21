package id.walt.certificate.x509.profile

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509SigningAlgorithmInfo
import id.walt.certificate.x509.builder.X509CertificateDataBuilder
import id.walt.certificate.x509.extension.BasicConstraintsExtension
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.extension.CrlDistributionPointsExtension.Companion.extensionCrlDistributionPoints
import id.walt.certificate.x509.extension.IssuerAlternativeNameExtension
import id.walt.certificate.x509.extension.IssuerAlternativeNameExtension.Companion.extensionIssuerAltName
import id.walt.certificate.x509.extension.KeyUsageExtension
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.certificate.x509.model.GeneralName
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.certificate.x509.validation.validator.X509CertificateValidator
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days


/**
 * Profile for certificates in accordance to requirements for Issuing Authority Root Ca Certificates
 * described in
 * ISO/IEC 18014-5 Second Edition
 *
 * Annex B
 * Section 1.2 IACA root certificate
 */
object IsoIaCaRootX509CertificateProfile : X509CertificateProfile, X509CertificateValidator {

    const val ID = "iso-iaca-root"

    val allowedSignatureAlgorithmsOid = listOf(
        "1.2.840.10045.4.3.2", // ECDSA-with SHA256
        "1.2.840.10045.4.3.3", // ECDSA-with SHA384
        "1.2.840.10045.4.3.4"  // ECDSA with SHA512
    )

    val allowedSubjectPulicKeyAlgorithmOid = "1.2.840.10045.2.1"

    val allowedSubjectPublicKeyEllipticCurveOid = listOf(
        // FIPS 186-4:
        "1.2.840.10045.3.1.7", // (Curve P-256)
        "1.3.132.0.34", // (Curve P-384)
        "1.3.132.0.35", // (Curve P-521)
        //Or one of the following curves specified in RFC 5639:
        "1.3.36.3.3.2.8.1.1.7",  // (brainpoolP256r1)
        "1.3.36.3.3.2.8.1.1.9",  // (brainpoolP320r1)
        "1.3.36.3.3.2.8.1.1.11", // (brainpoolP384r1)
        "1.3.36.3.3.2.8.1.1.13"  // (brainpoolP512r1
    )

    val maxValidityTime = 365.days * 20 // 20 years

    override val id: String = ID

    fun X509CertificateDataBuilder.profileIaCaRootCertificate(
        issuerDnCountryCode: String,
        issuerDnStateOrProvinceName: String? = null,
        issuerDnOrganizationName: String? = null,
        issuerDnCommonName: String? = null,
        issuerDnSerialNumber: String? = null,
        issuerEmailAddress: String? = null,
        issuerUri: String? = null
    ) {
        require(issuerDnCountryCode.length == 2) { "Require two letter country code but is '${issuerDnCountryCode}'" }
        val issuerDn = listOfNotNull(
            issuerDnSerialNumber?.ifBlank { null }?.let {
                if (issuerDnCommonName?.ifBlank { null } != null) {
                    // append it to commonName
                    null
                } else {
                    "SERIALNUMBER=${it}"
                }
            },
            issuerDnCommonName?.ifBlank { null }
                ?.let { cn -> "CN=${cn}${issuerDnSerialNumber?.let { "+SERIALNUMBER=${it.trim()}" } ?: ""}" },
            issuerDnOrganizationName?.ifBlank { null }?.let { "O=${it.trim()}" },
            issuerDnStateOrProvinceName?.ifBlank { null }?.let { "ST=${it.trim()}" },
            issuerDnCountryCode.let { "C=${it.uppercase().trim()}" },
        )
            .joinToString(",")
        profileIaCaRootCertificate(issuerDn, issuerEmailAddress, issuerUri)
    }

    fun X509CertificateDataBuilder.profileIaCaRootCertificate(
        issuerDn: String,
        issuerEmailAddress: String?,
        issuerUri: String?
    ) {
        this.subjectDn = issuerDn
        this.issuerDn = issuerDn
        subjectPublicKeyInfo = X509CertificateDataBuilder.SelfSignedSubjectPublicKeyInfo()
        extensionBasicConstraints {
            critical = true
            cA = true
            pathLenConstraint = 0
        }
        extensionKeyUsage {
            critical = true
            addKeyUsage(KeyUsageExtension.KeyUsage.keyCertSign)
            addKeyUsage(KeyUsageExtension.KeyUsage.cRLSign)
        }
        extensionSubjectKeyIdentifier()
        require(issuerEmailAddress?.isNotBlank() == true || issuerUri?.isNotBlank() == true) { "Issuer email address or issuer uri is required" }
        extensionIssuerAltName {
            issuerEmailAddress?.also { addEmail(it) }
            issuerUri?.also { addUri(it) }
        }
    }

    override suspend fun validate(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        validateVersion(context, x509Certificate)
        validateSignatureAlgorithm(context, x509Certificate)
        validateIssuerDn(context, x509Certificate)
        validateValidityTime(context, x509Certificate)
        validateSubjectDn(context, x509Certificate)
        validateSubjectPublicKeyInfo(context, x509Certificate)
        validateExtensionSubjectKeyIdentifier(context, x509Certificate)
        validateExtensionKeyUsage(context, x509Certificate)
        validateExtensionIssuerAlternativeName(context, x509Certificate)
        validateExtensionBasicConstraints(context, x509Certificate)
        validateExtensionCrlDistributionPoints(context, x509Certificate)
    }


    /**
     * Shall be v3
     */
    fun validateVersion(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        if (x509Certificate.data.version != 3) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "Expected version to be '3' but was '${x509Certificate.data.version}'"
            )
        }
    }

    /**
     * Value shall match the OID in the signature algorithm:
     * Options:
     * 1.2.840.10045.4.3.2 (ECDSA-with SHA256)
     * 1.2.840.10045.4.3.3 (ECDSA-with SHA384)
     * 1.2.840.10045.4.3.4 (ECDSA with SHA512)
     */
    fun validateSignatureAlgorithm(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        if (!allowedSignatureAlgorithmsOid.contains(x509Certificate.signatureAlgorithmOid)) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "signatureAlgorithm",
                "Expected signature algorithm  to be one of ${allowedSignatureAlgorithmsOid} but was " +
                        "'${x509Certificate.signatureAlgorithmOid}' (${x509Certificate.signatureAlgorithmName})"
            )
        }

    }

    /**
     * countryName is mandatory. The value shall be in upper case and
     * contain the ISO 3166-1 alpha-2 code of the issuing country, exactly
     * the same value as in the issuing country data element. The
     * countryName shall be PrintableString.
     *
     * stateOrProvinceName is optional. If this element is present,
     * the element shall also be present in the end-entity certificates and
     * hold the same value. The value shall exactly match the value of the
     * data element “issuing_jurisdiction”, if that element is present on the
     * mDL.
     *
     * organizationName is optional. Its value is at the discretion of the
     * IACA.
     *
     * commonName shall be present. Its value is at the discretion of the
     * IACA.
     *
     * serialNumber is optional. If present, it shall be a PrintableString.
     *
     * Attributes that have a DirectoryString and for which the
     * encoding is not listed above syntax shall be either
     * PrintableString or UTF8String.
     */

    private val countryCodeRegx = Regex("""\bc\s*=\s*"?([A-Z]{2})"?\b""", RegexOption.IGNORE_CASE)

    fun validateIssuerDn(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        // check presents of country code
        val issuerDn = x509Certificate.data.issuerDn
        val foundCountyCode = countryCodeRegx.find(issuerDn)
        if (foundCountyCode != null) {
            val countryCode = foundCountyCode.groups[1]?.value
            if (countryCode == null || countryCode.length != 2 || countryCode != countryCode.uppercase()) {
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "issuerDn",
                    "Expected country name to be ISO 3166-1 alpha-2 code  with capital letters but found '${countryCode}'"
                )
            }
        } else {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "issuerDn",
                "Issuer DN doesn't contain a valid country name, expected country name to be ISO 3166-1 alpha-2 code in issuer DN '${issuerDn}'"
            )
        }
    }

    /**
     * Maximum of 20 years after “notBefore” date.
     * NOTE The 20-year validity period results from the possibility of
     * using the IACA root certificate for issuing an IDL according to
     * ISO/IEC 18013-3, which allows the use of DS certificates with
     * validity periods up to 15 years. If the IACA root certificate is only
     * used to issue mDLs, a maximum validity period of 9 years is
     * sufficient.
     */
    fun validateValidityTime(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val validityPeriod = x509Certificate.data.validity.notAfter - x509Certificate.data.validity.notBefore
        if (validityPeriod.isNegative() || validityPeriod == Duration.ZERO) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "validityTime",
                "Validity time must be positive"
            )
        } else if (validityPeriod > maxValidityTime) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "validityTime",
                "Validity time must be less then 20 years"
            )
        }
    }

    /**
     * Same exact binary value as Issuer.
     */
    fun validateSubjectDn(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        if (x509Certificate.data.subjectDn != x509Certificate.data.issuerDn) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "subjectDn",
                "Subject DN '${x509Certificate.data.subjectDn}' must be same as issuer DN ''${x509Certificate.data.issuerDn}'"
            )
        }
    }

    /**
     * Algorithm: 1.2.840.10045.2.1 (Elliptic curve)
     * Parameter:
     *   one of the following curves specified in FIPS 186-4:
     *     1.2.840.10045.3.1.7 (Curve P-256)
     *     1.3.132.0.34 (Curve P-384)
     *     1.3.132.0.35 (Curve P-521)
     *   or one of the following curves specified in RFC 5639:
     *     1.3.36.3.3.2.8.1.1.7 (brainpoolP256r1)
     *     1.3.36.3.3.2.8.1.1.9 (brainpoolP320r1)
     *     1.3.36.3.3.2.8.1.1.11 (brainpoolP384r1)
     *     1.3.36.3.3.2.8.1.1.13 (brainpoolP512r1)
     */
    fun validateSubjectPublicKeyInfo(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val subjectPublicKeyInfo = x509Certificate.data.subjectPublicKeyInfo
        if (allowedSubjectPulicKeyAlgorithmOid != subjectPublicKeyInfo.algorithmOid) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "subjectPublicKeyInfo",
                "Subject public key algorithm OID expected to be '${allowedSubjectPulicKeyAlgorithmOid}' but is '${subjectPublicKeyInfo.algorithmOid}' ('${subjectPublicKeyInfo.algorithmName}') "
            )
        } else if (subjectPublicKeyInfo.ellipticCurveOid == null ||
            !allowedSubjectPublicKeyEllipticCurveOid.contains(subjectPublicKeyInfo.ellipticCurveOid)
        ) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "subjectPublicKeyInfo",
                "Subject public key parameter elliptic curve OID expected to be one of '${allowedSubjectPublicKeyEllipticCurveOid}' but is '${subjectPublicKeyInfo.ellipticCurveOid}'"
            )
        }
    }

    /**
     * SHA-1 hash of the subject public key BIT STRING value (excluding
     * tag, length, and number of unused bits).
     */
    fun validateExtensionSubjectKeyIdentifier(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val extension = x509Certificate.data.extensionSubjectKeyIdentifier
        if (extension == null) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "subjectKeyIdentifier",
                "Certificate extension '${SubjectKeyIdentifierExtension.OID}' ('${SubjectKeyIdentifierExtension.NAME}') is not present"
            )
        } else if (extension.critical) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "subjectKeyIdentifier",
                "Certificate extension '${SubjectKeyIdentifierExtension.OID}' ('${SubjectKeyIdentifierExtension.NAME}') must not have a critical flag set"
            )
        }
    }

    val requiredKeyUsage = setOf(
        KeyUsageExtension.KeyUsage.keyCertSign,
        KeyUsageExtension.KeyUsage.cRLSign,
    )

    /**
     * Extension Key Usage:
     * Mandator, Critical
     *  - Digital signature:         false
     *  - Non-repudiation:           false
     *  - Key encipherment:          false
     *  - Data encipherment:         false
     *  - Key agreement:             false
     *  - Key certificate signature: true
     *  - CRL signature:             true
     *  - Encipher only:             false
     *  - Decipher only:             false
     */
    fun validateExtensionKeyUsage(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val extension = x509Certificate.data.extensionKeyUsage
        if (extension == null) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "keyUsage",
                "Certificate extension '${KeyUsageExtension.OID}' ('${KeyUsageExtension.NAME}') is not present"
            )
        } else if (!extension.critical) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "keyUsage",
                "Certificate extension '${KeyUsageExtension.OID}' ('${KeyUsageExtension.NAME}') must have a critical flag set"
            )
        }
        extension?.keyPurposeIdList?.also { actualKeyUsage ->
            KeyUsageExtension.KeyUsage.entries.forEach {
                if (requiredKeyUsage.contains(it) && !actualKeyUsage.contains(it)) {
                    context.addLogEntry(
                        ValidationResult.Severity.ERROR,
                        "keyUsage",
                        "Certificate extension '${KeyUsageExtension.OID}' ('${KeyUsageExtension.NAME}') requires '${it} flag set, but is false"
                    )
                } else if (!requiredKeyUsage.contains(it) && actualKeyUsage.contains(it)) {
                    context.addLogEntry(
                        ValidationResult.Severity.ERROR,
                        "keyUsage",
                        "Certificate extension '${KeyUsageExtension.OID}' ('${KeyUsageExtension.NAME}') requires '${it} flag not to be set, but is true"
                    )
                }
            }
        }
    }

    /**
     * Extension Issuer alternative name:
     * Mandatory, not critical
     * The issuer alternative name extension shall provide contact
     * information for the issuer of the certificate. For that purpose, the
     * issuer alternative name shall include at least one of
     *  rfc822Name, or uniformResourceIdentifier.
     * NOTE This contact information is intended to help establish trust
     * in the certificate and the certified key by appropriate out of band
     * mechanisms. Note that this information is only meant for contact
     * information and does not in itself imply any level of trust in the
     * certificate.
     */
    fun validateExtensionIssuerAlternativeName(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val extension = x509Certificate.data.extensionIssuerAltName
        if (extension == null) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "issuerAlternativeName",
                "Certificate extension '${IssuerAlternativeNameExtension.OID}' ('${IssuerAlternativeNameExtension.NAME}') is not present"
            )
        } else {
            if (extension.critical) {
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "issuerAlternativeName",
                    "Certificate extension '${IssuerAlternativeNameExtension.OID}' ('${IssuerAlternativeNameExtension.NAME}') must not have a critical flag set"
                )
            }
            val hasRequiredAlternativeNames = extension.alternativeNames.any {
                (it.type == GeneralName.NameType.rfc822Name
                        || it.type == GeneralName.NameType.uniformResourceIdentifier)
                        && it.value.isNotBlank()
            }
            if (!hasRequiredAlternativeNames) {
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "issuerAlternativeName",
                    "Certificate extension '${IssuerAlternativeNameExtension.OID}' ('${IssuerAlternativeNameExtension.NAME}') doesn't have required rfc822Name or uniformResourceIdentifier set"
                )
            }
        }
    }

    /**
     * Extension Basic constraints:
     * Mandatory, Critical
     */
    fun validateExtensionBasicConstraints(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val extension = x509Certificate.data.extensionBasicConstraints
        if (extension == null) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "basicConstraints",
                "Certificate extension '${BasicConstraintsExtension.OID}' ('${BasicConstraintsExtension.NAME}') is not present"
            )
        } else {
            if (!extension.critical) {
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "basicConstraints",
                    "Certificate extension '${BasicConstraintsExtension.OID}' ('${BasicConstraintsExtension.NAME}') must have a critical flag set"
                )
            }
            if (!extension.cA) {
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "basicConstraints",
                    "Certificate extension '${BasicConstraintsExtension.OID}' ('${BasicConstraintsExtension.NAME}') must have cA flag set"
                )
            }
            if (extension.pathLenConstraint != 0) {
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "basicConstraints",
                    "Certificate extension '${BasicConstraintsExtension.OID}' ('${BasicConstraintsExtension.NAME}') must have pathLenConstraint set to '0' but is '${extension.pathLenConstraint}'"
                )
            }
        }
    }

    fun validateExtensionCrlDistributionPoints(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val extension = x509Certificate.data.extensionCrlDistributionPoints
        if (extension != null) {
            context.addLogEntry(
                ValidationResult.Severity.WARNING,
                "crlDistributionPoints",
                "Presence of CRL Distribution Points is not recommended; its presence is allowed for backwards compatibility reasons"
            )

            extension.distributionPoints.forEachIndexed { index, dp ->
                if (dp.reason != null) {
                    context.addLogEntry(
                        ValidationResult.Severity.WARNING,
                        "crlDistributionPoints",
                        "DistributionPoint[${index}] The ‘reasons’ field shall not be used"
                    )
                }
                if (dp.cRLIssuer != null) {
                    context.addLogEntry(
                        ValidationResult.Severity.WARNING,
                        "crlDistributionPoints",
                        "DistributionPoint[${index}] The ‘cRL Issuer’ field shall not be used"
                    )
                }
                if (dp.distributionPointFullName == null) {
                    context.addLogEntry(
                        ValidationResult.Severity.ERROR,
                        "crlDistributionPoints",
                        "DistributionPoint[${index}] Full name is required"
                    )
                } else {
                    if (dp.distributionPointFullName.isEmpty()
                        || !dp.distributionPointFullName.all { fullName ->
                            fullName.type == GeneralName.NameType.uniformResourceIdentifier
                                    && fullName.value.isNotBlank()
                        }
                    ) {
                        context.addLogEntry(
                            ValidationResult.Severity.ERROR,
                            "crlDistributionPoints",
                            "DistributionPoint[${index}] Full name must be of type URI"
                        )
                    }
                }
            }
        }
    }
}