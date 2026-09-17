package id.walt.certificate.x509.profile

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.builder.X509CertificateDataBuilder
import id.walt.certificate.x509.dn.DistinguishedName
import id.walt.certificate.x509.extension.AuthorityInfoAccessExtension.Companion.extensionAuthorityInfoAccess
import id.walt.certificate.x509.extension.BasicConstraintsExtension
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.extension.CertificatePoliciesExtension.Companion.extensionCertificatePolicies
import id.walt.certificate.x509.extension.KeyUsageExtension
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.certificate.x509.extension.QcStatementsExtension
import id.walt.certificate.x509.extension.QcStatementsExtension.Companion.extensionQcStatements
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.certificate.x509.profile.X509CertificateProfileValidationUtil.validateBasicConstraintIsEndEntity
import id.walt.certificate.x509.profile.X509CertificateProfileValidationUtil.validateExtensionsAreNotCritical
import id.walt.certificate.x509.profile.X509CertificateProfileValidationUtil.validateKeyUsageIsDigitalSignature
import id.walt.certificate.x509.profile.X509CertificateProfileValidationUtil.validatePositiveValidity
import id.walt.certificate.x509.profile.X509CertificateProfileValidationUtil.validateSerialNumber
import id.walt.certificate.x509.profile.X509CertificateProfileValidationUtil.validateSubjectKeyIdentifierIsPresent
import id.walt.certificate.x509.profile.X509CertificateProfileValidationUtil.validateVersionV3
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.crypto2.keys.Key


/**
 * ETSI Provider Certificates (e.g., PID or Attestation Provider Certificates)
 *
 * Who uses them: Issuing authorities, such as Person Identification Data (PID) Providers,
 * Qualified Electronic Attestation of Attributes (QEAA) providers, or Wallet Providers.
 *
 * Primary Purpose: To cryptographically sign identity attributes, credentials, or wallet outputs.
 * For example, a PID Provider uses its certificate's private key to sign the digital ID stored inside the
 * user's wallet.
 *
 * Ecosystem Role: They act as proof of origin and integrity. When a Relying Party receives data from a wallet, it
 * checks the Provider Certificate to verify that the identity credentials were genuinely issued by a trusted,
 * state-recognized authority.
 *
 * Issuance: Issued by a Qualified Trust Service Provider (QTSP) CA to the organization responsible for generating
 * and managing the digital attributes.
 *
 * Shared builder/validator logic for the ETSI TS 119 412-6 "provider" certificate profiles -
 * PID Provider ([EtsiPidProviderX509CertificateProfile]) and Wallet Provider
 * ([EtsiWalletProviderX509CertificateProfile]). Both profiles are identical except for the
 * QcType statement OID they carry (id-etsi-qct-pid vs id-etsi-qct-wal, ETSI TS 119 412-6 Annex A) - per
 * the reference implementation both use the same issuer/subject DN validation regardless of
 * whether the certificate belongs to a natural or legal person.
 *
 * Only requirements that are normatively mandatory ("shall") are enforced here. Signature
 * algorithm selection (EN 319 412-1 GEN-4.2.2-1) is advisory ("should") and intentionally not
 * validated, matching the reference implementation.
 *
 * A handful of these checks (end-entity, keyUsage, subjectKeyIdentifier, certificatePolicies
 * presence, conditional authorityInfoAccess, public key algorithm/size, person-DN field shape) are
 * identical across every ETSI EUDI end-entity profile in this package, not just the two Provider
 * profiles - [EtsiWrpAcX509CertificateProfile] and [EtsiWrpRcX509CertificateProfile] reuse them
 * directly rather than duplicating the logic.
 */
sealed class EtsiProviderX509CertificateProfile {

    companion object {
        private val criticalExtensions = setOf(
            KeyUsageExtension.OID,
            BasicConstraintsExtension.OID,
        )

        /** RSA >= 2048 bits, EC >= 256 bits (ETSI TS 119 312). */
        private const val RSA_ALGORITHM_OID = "1.2.840.113549.1.1.1"
        private const val EC_ALGORITHM_OID = "1.2.840.10045.2.1"
        private const val MIN_RSA_KEY_LENGTH_BITS = 2048
        private val allowedEcCurveOids = setOf(
            "1.2.840.10045.3.1.7", // P-256
            "1.3.132.0.34",        // P-384
            "1.3.132.0.35",        // P-521
            "1.3.36.3.3.2.8.1.1.7",  // brainpoolP256r1
            "1.3.36.3.3.2.8.1.1.9",  // brainpoolP320r1
            "1.3.36.3.3.2.8.1.1.11", // brainpoolP384r1
            "1.3.36.3.3.2.8.1.1.13", // brainpoolP512r1
        )
    }

    /**
     * @param subjectKey the subject's public key, or `null` when this builder is used inside
     *   [id.walt.certificate.x509.X509CertificateUtil.createSelfSignedCertificate] - in that case
     *   the issuer key passed to that call is used as the subject key too.
     */
    protected fun X509CertificateDataBuilder.applyProviderCertificate(
        subjectKey: Key?,
        subjectDn: String,
        qcTypeOid: String,
        certificatePolicyOids: List<String>,
        caIssuerUri: String? = null,
        ocspResponderUri: String? = null,
    ) {
        require(certificatePolicyOids.isNotEmpty()) { "At least one certificate policy OID is required" }
        applyProviderCertificate(qcTypeOid)
        this.subjectDn = subjectDn
        if (subjectKey != null) {
            subjectPublicKey(subjectKey)
        } else {
            subjectPublicKeySelfSigned()
        }
        extensionCertificatePolicies {
            certificatePolicyOids.forEach { addPolicy(it) }
        }
        extensionQcStatements {
            addQcCompliance()
            addQcType(qcTypeOid)
        }
        if (caIssuerUri != null || ocspResponderUri != null) {
            extensionAuthorityInfoAccess {
                caIssuerUri?.let { addCaIssuerUri(it) }
                ocspResponderUri?.let { addOcspResponderUri(it) }
            }
        }
    }

    protected fun X509CertificateDataBuilder.applyProviderCertificate(
        qcTypeOid: String,
    ) {
        extensionBasicConstraints {
            critical = true
            cA = false
        }
        extensionKeyUsage {
            critical = true
            addKeyUsage(KeyUsageExtension.KeyUsage.digitalSignature)
        }
        extensionSubjectKeyIdentifier()
        extensionQcStatements {
            addQcCompliance()
            addQcType(qcTypeOid)
        }
    }


    protected fun validateProviderCertificate(
        context: ValidationContext,
        x509Certificate: X509Certificate,
        qcTypeOid: String,
    ) {
        // GEN-4.2.1-1: certificate version shall be v3.
        validateVersionV3(context, x509Certificate)
        // GEN-4.2.3-1: serial number non-sequential, positive, >=63 bits CSPRNG, <=20 octets.
        validateSerialNumber(context, x509Certificate)
        validatePositiveValidity(context, x509Certificate)

        // PID-4.1-01 / WAL-5.1-01: end-entity certificate (basicConstraints cA=FALSE, critical).
        validateBasicConstraintIsEndEntity(context, x509Certificate)

        // PID-4.4.1-01 / WAL-5.1-01: keyUsage digitalSignature, critical, and no other bit set.
        validateKeyUsageIsDigitalSignature(context, x509Certificate)
        validateSubjectKeyIdentifierIsPresent(context, x509Certificate)
        validateCertificatePoliciesPresent(context, x509Certificate)
        validateAuthorityInfoAccessIfCaIssued(context, x509Certificate)
        validateQcStatements(context, x509Certificate, qcTypeOid)
        validatePublicKeyAlgorithm(context, x509Certificate)
        validateIssuerAndSubjectDn(context, x509Certificate)
        validateExtensionsAreNotCritical(context, x509Certificate, criticalExtensions)
    }

    /**
     * EN 319 412-2 4.3.3: certificatePolicies extension shall be present.
     */
    fun validateCertificatePoliciesPresent(context: ValidationContext, x509Certificate: X509Certificate) {
        val extension = x509Certificate.data.extensionCertificatePolicies
        if (extension == null || extension.policyOids.isEmpty()) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "certificatePolicies",
                "Certificate extension 'certificatePolicies' is not present"
            )
        }
    }

    /**
     * PID-4.4.3-01 / EN 319 412-2 4.4.1: authorityInfoAccess required for CA-issued certificates.
     * Self-signed certificates (issuer DN == subject DN) are exempt.
     */
    fun validateAuthorityInfoAccessIfCaIssued(context: ValidationContext, x509Certificate: X509Certificate) {
        val isSelfSigned = x509Certificate.data.issuerDn == x509Certificate.data.subjectDn
        if (isSelfSigned) return
        val extension = x509Certificate.data.extensionAuthorityInfoAccess
        if (extension == null || extension.accessDescriptions.isEmpty()) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "authorityInfoAccess",
                "Certificate extension 'authorityInfoAccess' is required for CA-issued certificates"
            )
        }
    }

    /**
     * PID-4.5-01 / WAL-5.1-01: mandatory QcCompliance statement, plus a QcType statement naming
     * [qcTypeOid].
     */
    private fun validateQcStatements(context: ValidationContext, x509Certificate: X509Certificate, qcTypeOid: String) {
        val extension = x509Certificate.data.extensionQcStatements
        if (extension == null) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "qcStatements",
                "Certificate extension 'qcStatements' is not present"
            )
            return
        }
        if (!extension.statements.contains(QcStatementsExtension.QcStatement.QcCompliance)) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "qcStatements",
                "Certificate extension 'qcStatements' is missing the mandatory QcCompliance statement"
            )
        }
        val qcType = extension.statements.filterIsInstance<QcStatementsExtension.QcStatement.QcType>().firstOrNull()
        if (qcType == null || !qcType.typeOids.contains(qcTypeOid)) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "qcStatements",
                "Certificate extension 'qcStatements' is missing a QcType statement naming '$qcTypeOid'"
            )
        }
    }

    /**
     * ETSI TS 119 312: RSA >= 2048 bits, EC >= 256 bits (FIPS 186-4 or RFC 5639 brainpool curves).
     */
    fun validatePublicKeyAlgorithm(context: ValidationContext, x509Certificate: X509Certificate) {
        val subjectPublicKeyInfo = x509Certificate.data.subjectPublicKeyInfo
        when (subjectPublicKeyInfo.algorithmOid) {
            RSA_ALGORITHM_OID -> {
                val bits = subjectPublicKeyInfo.rsaKeyLengthBits
                if (bits == null || bits < MIN_RSA_KEY_LENGTH_BITS) {
                    context.addLogEntry(
                        ValidationResult.Severity.ERROR,
                        "subjectPublicKeyInfo",
                        "RSA key must be at least $MIN_RSA_KEY_LENGTH_BITS bits but was '${bits}'"
                    )
                }
            }

            EC_ALGORITHM_OID -> {
                if (subjectPublicKeyInfo.ellipticCurveOid == null || !allowedEcCurveOids.contains(subjectPublicKeyInfo.ellipticCurveOid)) {
                    context.addLogEntry(
                        ValidationResult.Severity.ERROR,
                        "subjectPublicKeyInfo",
                        "Subject public key parameter elliptic curve OID expected to be one of '${allowedEcCurveOids}' but is '${subjectPublicKeyInfo.ellipticCurveOid}'"
                    )
                }
            }

            else -> context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "subjectPublicKeyInfo",
                "Subject public key algorithm OID expected to be RSA ('$RSA_ALGORITHM_OID') or EC ('$EC_ALGORITHM_OID') but is '${subjectPublicKeyInfo.algorithmOid}' ('${subjectPublicKeyInfo.algorithmName}')"
            )
        }
    }

    /**
     * PID-4.2-01, PID-4.3-01, PID-4.3-02 (also referenced by WAL-5.1-01): issuer and subject DN
     * are validated as either a natural or legal person, detected by the presence of
     * organizationIdentifier - matching the reference implementation, which applies the same
     * check regardless of the certificate's role.
     */
    private fun validateIssuerAndSubjectDn(context: ValidationContext, x509Certificate: X509Certificate) {
        val isSelfSigned = x509Certificate.data.issuerDn == x509Certificate.data.subjectDn
        validatePersonDn(context, "subject", x509Certificate.data.subjectDn)
        if (!isSelfSigned) {
            validatePersonDn(context, "issuer", x509Certificate.data.issuerDn)
        }
    }

    private fun validatePersonDn(context: ValidationContext, attribute: String, dnString: String) {
        val dn = DistinguishedName.ofString(dnString)
        val isLegalPerson = dn.rdnList.flatMap { it }.any { it.type.name.lowercase() == "organizationidentifier" }
        validatePersonDnByRole(context, attribute, dnString, isLegalPerson)
    }

    /**
     * Same field-shape checks as [validatePersonDn], but the natural-vs-legal-person distinction
     * is given explicitly rather than detected from the DN - used by [EtsiWrpAcX509CertificateProfile]
     * / [EtsiWrpRcX509CertificateProfile], whose person role is determined by the certificate's
     * policy OID (or, for WRPRC's issuer, is always a legal person) rather than by DN inspection.
     */
    fun validatePersonDnByRole(
        context: ValidationContext,
        attribute: String,
        dnString: String,
        isLegalPerson: Boolean
    ) {
        val dn = DistinguishedName.ofString(dnString)
        val grouped = dn.rdnList.flatMap { it }.groupBy { it.type.name.lowercase() }

        val countryName = grouped["c"]
        if (countryName.isNullOrEmpty()) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "${attribute}Dn",
                "Missing countryName in $attribute DN"
            )
        }
        val commonName = grouped["cn"]
        if (commonName.isNullOrEmpty() || commonName.any { it.value.isBlank() }) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "${attribute}Dn",
                "Missing or blank commonName in $attribute DN"
            )
        }

        if (isLegalPerson) {
            if (grouped["o"].isNullOrEmpty()) {
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "${attribute}Dn",
                    "Legal person $attribute DN is missing organizationName"
                )
            }
        } else {
            val hasNameChoice = !grouped["givenname"].isNullOrEmpty() ||
                    !grouped["surname"].isNullOrEmpty() ||
                    !grouped["pseudonym"].isNullOrEmpty()
            if (!hasNameChoice) {
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "${attribute}Dn",
                    "Natural person $attribute DN requires one of givenName/surname/pseudonym"
                )
            }
            if (grouped["serialnumber"].isNullOrEmpty()) {
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "${attribute}Dn",
                    "Natural person $attribute DN is missing serialNumber"
                )
            }
        }
    }
}