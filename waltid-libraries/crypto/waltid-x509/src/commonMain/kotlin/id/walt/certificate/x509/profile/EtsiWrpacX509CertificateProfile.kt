package id.walt.certificate.x509.profile

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.builder.X509CertificateDataBuilder
import id.walt.certificate.x509.extension.AuthorityInfoAccessExtension
import id.walt.certificate.x509.extension.AuthorityInfoAccessExtension.Companion.extensionAuthorityInfoAccess
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension.Companion.extensionAuthorityKeyIdentifier
import id.walt.certificate.x509.extension.BasicConstraintsExtension
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.extension.CertificatePoliciesExtension.Companion.extensionCertificatePolicies
import id.walt.certificate.x509.extension.CrlDistributionPointsExtension.Companion.extensionCrlDistributionPoints
import id.walt.certificate.x509.extension.KeyUsageExtension
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.certificate.x509.extension.QcStatementsExtension
import id.walt.certificate.x509.extension.QcStatementsExtension.Companion.extensionQcStatements
import id.walt.certificate.x509.extension.SubjectAlternativeNameExtension.Companion.extensionSan
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.certificate.x509.model.GeneralName
import id.walt.certificate.x509.profile.X509CertificateProfileValidationUtil.validateBasicConstraintIsEndEntity
import id.walt.certificate.x509.profile.X509CertificateProfileValidationUtil.validateExtensionsAreNotCritical
import id.walt.certificate.x509.profile.X509CertificateProfileValidationUtil.validateKeyUsageIsDigitalSignature
import id.walt.certificate.x509.profile.X509CertificateProfileValidationUtil.validateNotSelfSigned
import id.walt.certificate.x509.profile.X509CertificateProfileValidationUtil.validateSerialNumber
import id.walt.certificate.x509.profile.X509CertificateProfileValidationUtil.validateSubjectKeyIdentifierIsPresent
import id.walt.certificate.x509.profile.X509CertificateProfileValidationUtil.validateVersionV3
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.certificate.x509.validation.validator.X509CertificateValidator
import id.walt.crypto2.keys.Key

/**
 * Profile for Wallet Relying Party Access Certificates (WRPAC) - end-entity certificates whose
 * private key a Relying Party uses to authenticate a presentation request, so the wallet can
 * confirm the requester is a registered Relying Party before releasing any credential.
 *
 * ETSI TS 119 411-8 V1.1.1, clause 6.6.1. Cross-checked against the reference implementation's
 * `wrpAccessCertificateProfile()` (eudi-lib-kmp-etsi-1196x2, EUWRPAccessCertificate.kt).
 *
 * Not implemented: the validity-assured short-term certificate variant (ext-etsi-valassured-ST-certs
 * / noRevocationAvail, RFC 9608), which lets a short-lived (<=7 day) WRPAC skip the revocation
 * mechanism below. This profile always requires one. Support is deferred to a follow-up, since it
 * needs its own extension type with little reuse elsewhere.
 */
object EtsiWrpacX509CertificateProfile : EtsiWalletRelyingPartyX509CertificateProfile(), X509CertificateProfile,
    X509CertificateValidator {

    const val ID = "etsi-wrpac"

    override val id: String = ID

    private val criticalExtensions = setOf(
        KeyUsageExtension.OID,
        BasicConstraintsExtension.OID,
    )

    private val naturalPersonPolicyOids = setOf(
        NORMALIZED_CERT_POLICY_NATURAL_PERSON,
        QUALIFIED_CERT_POLICY_NATURAL_PERSON
    )
    private val legalPersonPolicyOids = setOf(
        NORMALIZED_CERT_POLICY_ID_LEGAL_PERSON,
        QUALIFIED_CERT_POLICY_LEGAL_PERSON
    )
    private val qualifiedPolicyOids =
        setOf(QUALIFIED_CERT_POLICY_NATURAL_PERSON, QUALIFIED_CERT_POLICY_LEGAL_PERSON)
    private val allPolicyOids = naturalPersonPolicyOids + legalPersonPolicyOids

    /**
     * @param subjectKey the Relying Party's public key. WRPAC certificates are always CA-issued
     *   (TS 119 411-8 6.6.1) - unlike the Provider profiles, there is no self-signed variant.
     * @param policyOid exactly one of [Etsi119411Part8.NORMALIZED_CERT_POLICY_NATURAL_PERSON], [Etsi119411Part8.NORMALIZED_CERT_POLICY_ID_LEGAL_PERSON],
     *   [Etsi119411Part8.QUALIFIED_CERT_POLICY_NATURAL_PERSON], [Etsi119411Part8.QUALIFIED_CERT_POLICY_LEGAL_PERSON] - determines both the
     *   natural-vs-legal-person subject DN shape and whether qcStatements are required.
     * @param contactUri / [contactEmail] populate the mandatory subjectAltName contact information
     *   (TS 119 411-8 6.6.1); at least one is required. Telephone contact info is not modelled yet -
     *   [GeneralName] has no dedicated phone-number name type, and the exact ASN.1 encoding ETSI
     *   expects for one hasn't been confirmed against the spec text.
     * @param caIssuerUri / [ocspResponderUri] populate authorityInfoAccess, required for CA-issued
     *   certificates (EN 319 412-2 4.4.1).
     * @param crlDistributionPointUri required unless [ocspResponderUri] is set - this profile always
     *   requires a revocation mechanism (the short-term/no-revocation exemption isn't implemented,
     *   see the class-level doc comment).
     */
    fun X509CertificateDataBuilder.profileWrpAccessCertificate(
        subjectKey: Key,
        subjectDn: String,
        policyOid: String,
        contactUri: String? = null,
        contactEmail: String? = null,
        caIssuerUri: String? = null,
        ocspResponderUri: String? = null,
        crlDistributionPointUri: String? = null,
    ) {
        require(policyOid in allPolicyOids) {
            "policyOid must be one of the WRPAC policy OIDs (Etsi119411Part8), but was '$policyOid'"
        }
        require(contactUri != null || contactEmail != null) {
            "At least one of contactUri or contactEmail is required (subjectAltName contact info)"
        }
        require(ocspResponderUri != null || crlDistributionPointUri != null) {
            "Either ocspResponderUri or crlDistributionPointUri is required (WRPAC always needs a revocation mechanism)"
        }
        this.subjectDn = subjectDn
        subjectPublicKey(subjectKey)
        extensionBasicConstraints {
            critical = true
            cA = false
        }
        extensionKeyUsage {
            critical = true
            addKeyUsage(KeyUsageExtension.KeyUsage.digitalSignature)
        }
        extensionSubjectKeyIdentifier()
        extensionCertificatePolicies {
            addPolicy(policyOid)
        }
        extensionSan {
            contactUri?.let { addUri(it) }
            contactEmail?.let { addEmail(it) }
        }
        if (caIssuerUri != null || ocspResponderUri != null) {
            extensionAuthorityInfoAccess {
                caIssuerUri?.let { addCaIssuerUri(it) }
                ocspResponderUri?.let { addOcspResponderUri(it) }
            }
        }
        if (crlDistributionPointUri != null) {
            extensionCrlDistributionPoints {
                addUriDistributionPoint(crlDistributionPointUri)
            }
        }
        if (policyOid in qualifiedPolicyOids) {
            extensionQcStatements {
                addQcCompliance()
                addQcSscd()
                if (policyOid == QUALIFIED_CERT_POLICY_LEGAL_PERSON) {
                    // The reference implementation only requires this statement's presence for
                    // QCP-l, not a specific attestation type OID (unlike the PID/Wallet Provider
                    // profiles' id-etsi-qct-pid/wal) - so no typeOid is asserted here.
                    addQcType()
                }
            }
        }
    }

    override suspend fun validate(context: ValidationContext, x509Certificate: X509Certificate) {
        validateVersionV3(context, x509Certificate)
        validateSerialNumber(context, x509Certificate)
        validateBasicConstraintIsEndEntity(context, x509Certificate)

        // WRPAC (TS 119 411-8) / WRPRC (TS 119 475): both are always CA-issued, unlike PID/Wallet
        // Provider certificates which may be self-signed.
        validateNotSelfSigned(context, x509Certificate)
        validateKeyUsageIsDigitalSignature(context, x509Certificate)
        validateSubjectKeyIdentifierIsPresent(context, x509Certificate)
        val policyOid = validateCertificatePolicy(context, x509Certificate)
        validateAuthorityInfoAccessIfCaIssued(context, x509Certificate)
        validateAuthorityKeyIdentifier(context, x509Certificate)
        validateSubjectAlternativeName(context, x509Certificate)
        validateRevocationMechanism(context, x509Certificate)
        validatePublicKeyAlgorithm(context, x509Certificate)
        if (policyOid != null) {
            validateQcStatements(context, x509Certificate, policyOid)
            validatePersonDnByRole(
                context,
                "subject",
                x509Certificate.data.subjectDn,
                policyOid in legalPersonPolicyOids
            )
        }
        validatePersonDnByRole(context, "issuer", x509Certificate.data.issuerDn, isLegalPerson = true)
        validateExtensionsAreNotCritical(context, x509Certificate, criticalExtensions)
    }

    /** TS 119 411-8 5.3 / 6.6.1: exactly one WRPAC policy OID. */
    private fun validateCertificatePolicy(context: ValidationContext, x509Certificate: X509Certificate): String? {
        val policyOids = x509Certificate.data.extensionCertificatePolicies?.policyOids.orEmpty()
        val matched = policyOids.filter { it in allPolicyOids }
        if (matched.size != 1) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "certificatePolicies",
                "Certificate must declare exactly one WRPAC policy OID (Etsi119411Part8), found $policyOids"
            )
            return null
        }
        return matched.first()
    }

    /** EN 319 412-2 4.3.1: authorityKeyIdentifier required (populated automatically for CA-issued certs). */
    private fun validateAuthorityKeyIdentifier(context: ValidationContext, x509Certificate: X509Certificate) {
        if (x509Certificate.data.extensionAuthorityKeyIdentifier == null) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "authorityKeyIdentifier",
                "Certificate extension 'authorityKeyIdentifier' is not present"
            )
        }
    }

    /** TS 119 411-8 6.6.1: subjectAltName must carry contact information (URI or email). */
    private fun validateSubjectAlternativeName(context: ValidationContext, x509Certificate: X509Certificate) {
        val alternativeNames = x509Certificate.data.extensionSan?.alternativeNames.orEmpty()
        val hasContactInfo = alternativeNames.any {
            it.type == GeneralName.NameType.uniformResourceIdentifier || it.type == GeneralName.NameType.rfc822Name
        }
        if (!hasContactInfo) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "subjectAltName",
                "Certificate extension 'subjectAltName' must contain contact information (URI or email); telephone contact info is not modelled yet"
            )
        }
    }

    /**
     * EN 319 412-2 4.3.11: a revocation mechanism is required unless the certificate uses the
     * (not implemented, see class doc) short-term / noRevocationAvail exemption.
     */
    private fun validateRevocationMechanism(context: ValidationContext, x509Certificate: X509Certificate) {
        val hasCrlDp = x509Certificate.data.extensionCrlDistributionPoints?.distributionPoints?.isNotEmpty() == true
        val hasOcsp = x509Certificate.data.extensionAuthorityInfoAccess?.accessDescriptions
            ?.any { it.accessMethod == AuthorityInfoAccessExtension.AccessMethod.ocsp } == true
        if (!hasCrlDp && !hasOcsp) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "crlDistributionPoints",
                "Certificate requires a revocation mechanism - either crlDistributionPoints or an OCSP responder in authorityInfoAccess"
            )
        }
    }

    /**
     * EN 319 412-5: QCStatements required for the QCP-n/QCP-l policies only; NCP-n/NCP-l don't
     * require them.
     */
    private fun validateQcStatements(context: ValidationContext, x509Certificate: X509Certificate, policyOid: String) {
        if (policyOid !in qualifiedPolicyOids) return
        val statements = x509Certificate.data.extensionQcStatements?.statements.orEmpty()
        if (!statements.contains(QcStatementsExtension.QcStatement.QcCompliance)) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "qcStatements",
                "Certificate extension 'qcStatements' is missing the mandatory QcCompliance statement"
            )
        }
        if (!statements.contains(QcStatementsExtension.QcStatement.QcSSCD)) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "qcStatements",
                "Certificate extension 'qcStatements' is missing the mandatory QcSSCD statement"
            )
        }
        if (policyOid == QUALIFIED_CERT_POLICY_LEGAL_PERSON) {
            val hasQcType = statements.any { it.statementId == QcStatementsExtension.QcStatement.ID_QC_TYPE }
            if (!hasQcType) {
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "qcStatements",
                    "Certificate extension 'qcStatements' is missing the mandatory QcType statement required for QCP-l"
                )
            }
        }
    }
}
