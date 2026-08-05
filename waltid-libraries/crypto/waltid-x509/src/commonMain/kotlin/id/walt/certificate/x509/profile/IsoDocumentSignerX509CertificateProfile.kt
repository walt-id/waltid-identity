package id.walt.certificate.x509.profile

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.builder.X509CertificateDataBuilder
import id.walt.certificate.x509.dn.DistinguishedName
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension.Companion.extensionAuthorityKeyIdentifier
import id.walt.certificate.x509.extension.CrlDistributionPointsExtension.Companion.extensionCrlDistributionPoints
import id.walt.certificate.x509.extension.ExtendedKeyUsageExtension
import id.walt.certificate.x509.extension.ExtendedKeyUsageExtension.Companion.extensionExtendedKeyUsage
import id.walt.certificate.x509.extension.IssuerAlternativeNameExtension
import id.walt.certificate.x509.extension.IssuerAlternativeNameExtension.Companion.extensionIssuerAltName
import id.walt.certificate.x509.extension.KeyUsageExtension
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.certificate.x509.model.GeneralName
import id.walt.certificate.x509.profile.IsoProfileX509CertificateValidationUtil.validateExtensionsAreNotCritical
import id.walt.certificate.x509.profile.IsoProfileX509CertificateValidationUtil.validateSerialNumber
import id.walt.certificate.x509.profile.IsoProfileX509CertificateValidationUtil.validateSignatureAlgorithm
import id.walt.certificate.x509.profile.IsoProfileX509CertificateValidationUtil.validateValidityTime
import id.walt.certificate.x509.profile.IsoProfileX509CertificateValidationUtil.validateVersion
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.certificate.x509.validation.validator.X509CertificateValidator
import id.walt.crypto.keys.Key
import kotlin.time.Duration.Companion.days


/**
 * Profile for certificates which are used to sign the mobile security object in the device retrieval mdoc response.
 * described in
 * ISO/IEC 18014-5 Second Edition
 *
 * Annex B
 * Section 1.4 Document signer certificate
 */
object IsoDocumentSignerX509CertificateProfile : X509CertificateProfile, X509CertificateValidator {

    const val ID = "iso-document-signer"

    private val criticalExtensions = setOf(
        "2.5.29.15", // Key usage
        "2.5.29.37"  // Extended key usage
    )

    private val allowedSignatureAlgorithmsOid = setOf(
        "1.2.840.10045.4.3.2", // ECDSA-with SHA256
        "1.2.840.10045.4.3.3", // ECDSA-with SHA384
        "1.2.840.10045.4.3.4"  // ECDSA with SHA512
    )

    private val allowedSubjectPublicKeyAlgorithmOid = setOf(
        "1.2.840.10045.2.1",
        "1.3.101.112",
        "1.3.101.113"
    )


    private val allowedSubjectPublicKeyEllipticCurveOid = listOf(
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

    /**
     * Maximum of 457 days after “notBefore” date
     */
    private val maxValidityTime = 457.days

    override val id: String = ID

    fun X509CertificateDataBuilder.profileDocumentSignerCertificate(
        issuerCertificate: X509Certificate,
        crlDistributionPointUri: String,
        issuerEmailAddress: String? = null,
        issuerUri: String? = null,
        subjectKey: Key,
        subjectDnCountryCode: String,
        subjectDnStateOrProvinceName: String? = null,
        subjectDnLocalityName: String? = null,
        subjectDnOrganizationName: String? = null,
        subjectDnCommonName: String,
        subjectDnSerialNumber: String? = null,
    ) {
        require(subjectDnCountryCode.length == 2) { "Require two letter country code but is '${subjectDnCountryCode}'" }
        require(subjectDnCommonName.isNotBlank()) { "common name must not be blank" }
        val subjectDn = listOfNotNull(
            subjectDnCommonName.let { cn -> "CN=${cn}${subjectDnSerialNumber?.let { "+SERIALNUMBER=${it.trim()}" } ?: ""}" },
            subjectDnOrganizationName?.ifBlank { null }?.let { "O=${it.trim()}" },
            subjectDnLocalityName?.ifBlank { null }?.let { "L=${it.trim()}" },
            subjectDnStateOrProvinceName?.ifBlank { null }?.let { "ST=${it.trim()}" },
            subjectDnCountryCode.let { "C=${it.uppercase().trim()}" },
        )
            .joinToString(",")
        profileDocumentSignerCertificate(
            issuerCertificate = issuerCertificate,
            crlDistributionPointUri = crlDistributionPointUri,
            issuerEmailAddress = issuerEmailAddress,
            issuerUri = issuerUri,
            subjectKey = subjectKey,
            subjectDn = subjectDn,
        )
    }

    fun X509CertificateDataBuilder.profileDocumentSignerCertificate(
        issuerCertificate: X509Certificate,
        crlDistributionPointUri: String,
        issuerEmailAddress: String? = null,
        issuerUri: String? = null,
        subjectKey: Key,
        subjectDn: String,
    ) {
        this.issuerDnRaw = issuerCertificate.data.subjectDnRaw
        this.subjectDn = subjectDn
        subjectPublicKey(subjectKey)
        extensionSubjectKeyIdentifier()
        extensionKeyUsage {
            critical = true
            addKeyUsage(KeyUsageExtension.KeyUsage.digitalSignature)
        }
        extensionExtendedKeyUsage {
            critical = true
            addKeyUsage(ExtendedKeyUsageExtension.KeyUsage.mdlDS)
        }
        extensionIssuerAltName {
            require(issuerEmailAddress != null || issuerUri != null) { "Either issuerEmailAddress or issuerUri must be set" }
            if (issuerEmailAddress != null) {
                addEmail(issuerEmailAddress)
            }
            if (issuerUri != null) {
                addUri(issuerUri)
            }
        }
        extensionCrlDistributionPoints {
            addDistributionPointFullName(
                listOf(
                    GeneralName(
                        GeneralName.NameType.uniformResourceIdentifier,
                        crlDistributionPointUri
                    )
                )
            )
        }
    }

    override suspend fun validate(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        validateVersion(context, x509Certificate)
        validateSerialNumber(context, x509Certificate)
        validateValidityTime(context, x509Certificate, maxValidityTime)
        validateSubjectDn(context, x509Certificate)
        validateSubjectPublicKeyInfo(context, x509Certificate)
        validateExtensionAuthorityKeyIdentifier(context, x509Certificate)
        validateExtensionSubjectKeyIdentifier(context, x509Certificate)
        validateExtensionKeyUsage(context, x509Certificate)
        validateExtensionExtendedKeyUsage(context, x509Certificate)
        validateExtensionIssuerAlternativeName(context, x509Certificate)
        validateExtensionCrlDistributionPoints(context, x509Certificate)
        validateSignatureAlgorithm(context, x509Certificate, allowedSignatureAlgorithmsOid)
        validateExtensionsAreNotCritical(context, x509Certificate, criticalExtensions)
    }

    private val countryNameRegex = Regex("^[A-Z]{2}$")

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
    fun validateSubjectDn(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val dn = DistinguishedName.ofString(x509Certificate.data.subjectDn)
        val grouped = dn.rdnList.flatMap { it }
            .groupBy { it.type.name.lowercase() }

        val countryName = grouped.get("c")
        if (countryName == null) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "subjectDn",
                "Missing countryName in DN"
            )
        } else {
            if (countryName.size != 1) {
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "subjectDn",
                    "Multiple countryName in DN"
                )
            }
            countryName.forEach {
                if (!countryNameRegex.matches(it.value)) {
                    context.addLogEntry(
                        ValidationResult.Severity.ERROR,
                        "subjectDn",
                        "Invalid countryName in DN: '${it}'"
                    )
                }
            }
        }

        val cn = grouped.get("cn")
        if (cn == null) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "subjectDn",
                "Missing commonName in DN"
            )
        } else {
            cn.forEach {
                if (it.value.isBlank()) {
                    context.addLogEntry(
                        ValidationResult.Severity.ERROR,
                        "subjectDn",
                        "commonName must not be blank"
                    )
                }
            }
        }
    }

    /**
     * Algorithm:
     * If any of the curves specified below for the parameters field is used, the following OID must be used, as
     * specified in RFC 5480 and RFC 5639:
     * 1.2.840.10045.2.1 (id-ecPublicKey)
     * For curves Ed25519 or Ed448, one of the following OIDs must be
     * used, as specified in RFC 8410:
     * 1.3.101.112(Curve Ed25519)
     * 1.3.101.113(Curve Ed448)
     *
     * Parameter:
     * This field must only be present when the algorithm field contains the OID 1.2.840.10045.2.1.
     * Implicitly specify curve parameters through an OID associated with
     * one of the following curves specified in FIPS 186-4:
     * 1.2.840.10045.3.1.7 (Curve P-256)
     * 1.3.132.0.34 (Curve P-384)
     * 1.3.132.0.35 (Curve P-521)
     * Or one of the following curves specified in RFC 5639:
     * 1.3.36.3.3.2.8.1.1.7 (brainpoolP256r1)
     * 1.3.36.3.3.2.8.1.1.9 (brainpoolP320r1)
     * 1.3.36.3.3.2.8.1.1.11 (brainpoolP384r1)
     * 1.3.36.3.3.2.8.1.1.13 (brainpoolP512r1
     */
    fun validateSubjectPublicKeyInfo(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val subjectPublicKeyInfo = x509Certificate.data.subjectPublicKeyInfo
        if (!allowedSubjectPublicKeyAlgorithmOid.contains(subjectPublicKeyInfo.algorithmOid)) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "subjectPublicKeyInfo",
                "Subject public key algorithm OID expected to be one of '${allowedSubjectPublicKeyAlgorithmOid}' but is '${subjectPublicKeyInfo.algorithmOid}' ('${subjectPublicKeyInfo.algorithmName}') "
            )
        } else {
            if ("1.2.840.10045.2.1".equals(subjectPublicKeyInfo.algorithmOid)) {
                if (subjectPublicKeyInfo.ellipticCurveOid == null) {
                    context.addLogEntry(
                        ValidationResult.Severity.ERROR,
                        "subjectPublicKeyInfo",
                        "Subject public key parameter elliptic curve OID expected to be one of '${allowedSubjectPublicKeyEllipticCurveOid}' but is null"
                    )
                } else if (!allowedSubjectPublicKeyEllipticCurveOid.contains(subjectPublicKeyInfo.ellipticCurveOid)) {
                    context.addLogEntry(
                        ValidationResult.Severity.ERROR,
                        "subjectPublicKeyInfo",
                        "Subject public key parameter elliptic curve OID expected to be one of '${allowedSubjectPublicKeyEllipticCurveOid}' but is '${subjectPublicKeyInfo.ellipticCurveOid}'"
                    )
                }
            } else {
                if (subjectPublicKeyInfo.ellipticCurveOid != null) {
                    context.addLogEntry(
                        ValidationResult.Severity.ERROR,
                        "subjectPublicKeyInfo",
                        "Subject public key parameter 'elliptic curve OID' to be null but is '${subjectPublicKeyInfo.ellipticCurveOid}'"
                    )
                }
            }
        }
    }

    /**
     * Same value as the subject key identifier of the IACA root certificate
     */
    fun validateExtensionAuthorityKeyIdentifier(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val authorityKeyId = x509Certificate.data.extensionAuthorityKeyIdentifier?.keyIdentifier
        if (authorityKeyId == null) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "authorityKeyIdentifier",
                "Certificate extension '${AuthorityKeyIdentifierExtension.OID}' ('${AuthorityKeyIdentifierExtension.NAME}') is not present OR keyId is not set"
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

    /**
     * Extension Key usage 4.2.1.3
     * Mandatory Critical
     * Extension Key Usage:
     *  - Digital signature:         true
     *  - Non-repudiation:           false
     *  - Key encipherment:          false
     *  - Data encipherment:         false
     *  - Key agreement:             false
     *  - Key certificate signature: false
     *  - CRL signature:             false
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
        } else {
            if (!extension.critical) {
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "keyUsage",
                    "Certificate extension '${KeyUsageExtension.OID}' ('${KeyUsageExtension.NAME}') must have critical flag set"
                )
            }
            extension.keyPurposeIdList.also { actualKeyUsage ->
                if (actualKeyUsage.size != 1) {
                    context.addLogEntry(
                        ValidationResult.Severity.ERROR,
                        "keyUsage",
                        "Certificate extension '${KeyUsageExtension.OID}' ('${KeyUsageExtension.NAME}') requires ony 'digitalSignature' flag set, but has set $actualKeyUsage"
                    )
                } else if (actualKeyUsage.first() != KeyUsageExtension.KeyUsage.digitalSignature) {
                    context.addLogEntry(
                        ValidationResult.Severity.ERROR,
                        "keyUsage",
                        "Certificate extension '${KeyUsageExtension.OID}' ('${KeyUsageExtension.NAME}') requires digitalSignature flag set, but is false"
                    )
                }
            }
        }
    }

    /**
     * Extension: Extended key usage 4.2.1.12
     * Mandatory Critical
     * Key usage must be: OID 1.0.18013.5.1.2 (mdlDS)
     */
    fun validateExtensionExtendedKeyUsage(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val extension = x509Certificate.data.extensionExtendedKeyUsage
        if (extension == null || extension.keyPurposeIdList.isEmpty()) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "extendedKeyUsage",
                "Certificate extension '${ExtendedKeyUsageExtension.OID}' ('${ExtendedKeyUsageExtension.NAME}') is not present"
            )
        } else {
            if (!extension.critical) {
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "extendedKeyUsage",
                    "Certificate extension '${ExtendedKeyUsageExtension.OID}' ('${ExtendedKeyUsageExtension.NAME}') must have critical flag set"
                )
            }
            if (extension.keyPurposeIdList.size > 1) {
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "extendedKeyUsage",
                    "Extended Key Usage expected to have one entry but it has ${extension.keyPurposeIdList.size} entries"
                )
            } else if (!extension.keyPurposeList.contains(ExtendedKeyUsageExtension.KeyUsage.mdlDS)) {
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "extendedKeyUsage",
                    "Extended Key Usage expected to have one entry of type 'mdlDS' (oid: '${ExtendedKeyUsageExtension.KeyUsage.mdlDS.id}') but it has ${extension.keyPurposeList} entries"
                )
            }
        }
    }

    /**
     * Extension Issuer alternative name
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
     * CRLDistributionPoints (mandatory, not critical):
     * The ‘reasons’ and ‘cRL Issuer’ fields shall not be used.
     * distributionPoint: mandatory URI for CRL distribution point
     */
    fun validateExtensionCrlDistributionPoints(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val extension = x509Certificate.data.extensionCrlDistributionPoints
        if (extension == null) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "crlDistributionPoints",
                "Extension CRL Distribution Points is missing"
            )
        } else {
            if (extension.distributionPoints.isEmpty()) {
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "crlDistributionPoints",
                    "Extension CRL Distribution Points should contain at least one DistributionPoint"
                )
            }
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