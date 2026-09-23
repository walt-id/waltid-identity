package id.walt.certificate.x509.profile

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.builder.X509CertificateDataBuilder
import id.walt.certificate.x509.extension.AuthorityInfoAccessExtension.Companion.extensionAuthorityInfoAccess
import id.walt.certificate.x509.extension.BasicConstraintsExtension
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.extension.CertificatePoliciesExtension.Companion.extensionCertificatePolicies
import id.walt.certificate.x509.extension.KeyUsageExtension
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
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
 * Profile for Wallet Relying Party Registration Certificates (WRPRC) - ETSI TS 119 475.
 *
 * *** DRAFT - INCOMPLETE, see below. ***
 *
 * WRPRC's defining feature is that it encodes a Relying Party's *registered intended use* - the
 * attribute/purpose scope a national registrar has authorized it to request from a wallet, which
 * is what would let a wallet explain to the user *why* a request is allowed. Unlike
 * [EtsiWrpAcX509CertificateProfile] (ETSI TS 119 411-8), there is no reference implementation to
 * cross-check against for this profile (the eudi-lib-kmp-etsi-1196x2 checkout used for every other
 * profile in this file has no WRPRC assessment or code), and the exact encoding of "registered
 * intended use" is not confirmed - the implementation plan flags it as likely needing a brand-new
 * custom X.509 extension carrying structured scope data, which requires reading the ETSI TS 119 475
 * text directly before it can be modelled correctly.
 *
 * What's implemented below is only the baseline end-entity certificate shape shared by every other
 * ETSI EUDI profile in this package (end-entity, keyUsage, subjectKeyIdentifier, certificatePolicies
 * presence, conditional authorityInfoAccess, restricted extension criticality, key size, legal-person
 * DN, not-self-signed) - reused directly from [EtsiWalletRelyingPartyX509CertificateProfile] /
 * [X509CertificateProfileValidationUtil] / [EtsiWrpAcX509CertificateProfile] rather than guessed at. It intentionally does NOT validate the
 * registered intended use itself: [validate] always emits a WARNING log entry flagging that gap,
 * rather than silently passing a certificate a real WRPRC issuer might reject, or silently pretending
 * to check something ETSI actually requires.
 *
 * The subject is assumed to always be a legal person (a registered Relying Party is an
 * organization) since no policy-OID-driven natural/legal distinction is confirmed for this
 * profile - unlike [EtsiWrpAcX509CertificateProfile], where the reference implementation confirms
 * that distinction exists.
 *
 * TODO(EUDI Phase 3): read ETSI TS 119 475 primary source, model the "registered intended use"
 * extension, and turn the WARNING below into real validation.
 */
object EtsiWrpRcX509CertificateProfile : EtsiWalletRelyingPartyX509CertificateProfile(), X509CertificateProfile,
    X509CertificateValidator {

    const val ID = "etsi-wrprc"

    override val id: String = ID

    private val criticalExtensions = setOf(
        KeyUsageExtension.OID,
        BasicConstraintsExtension.OID,
    )

    /**
     * @param subjectKey the Relying Party's public key. WRPRC certificates are always CA-issued
     *   (issued by a national registrar), so there is no self-signed variant.
     * @param certificatePolicyOids TSP-defined certificate policy OID(s); at least one required
     *   (EN 319 412-2 4.3.3). The WRPRC-specific policy OID(s), if ETSI TS 119 475 defines any,
     *   aren't confirmed yet - pass whatever the issuing registrar's CPS defines.
     * @param caIssuerUri / [ocspResponderUri] populate authorityInfoAccess, required since WRPRC
     *   certificates are always CA-issued (EN 319 412-2 4.4.1).
     */
    fun X509CertificateDataBuilder.profileEtsiWrpRegistrationCertificate(
        subjectKey: Key,
        subjectDn: String,
        certificatePolicyOids: List<String>,
        caIssuerUri: String? = null,
        ocspResponderUri: String? = null,
    ) {
        require(certificatePolicyOids.isNotEmpty()) { "At least one certificate policy OID is required" }
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
            certificatePolicyOids.forEach { addPolicy(it) }
        }
        if (caIssuerUri != null || ocspResponderUri != null) {
            extensionAuthorityInfoAccess {
                caIssuerUri?.let { addCaIssuerUri(it) }
                ocspResponderUri?.let { addOcspResponderUri(it) }
            }
        }
    }

    fun X509CertificateDataBuilder.profileEtsiWrpRegistrationCertificate() =
        profileEtsiWalletRelyingParty()

    override suspend fun validate(context: ValidationContext, x509Certificate: X509Certificate) {
        validateVersionV3(context, x509Certificate)
        validateSerialNumber(context, x509Certificate)
        validateBasicConstraintIsEndEntity(context, x509Certificate)
        // WRPAC (TS 119 411-8) / WRPRC (TS 119 475): both are always CA-issued, unlike PID/Wallet
        // Provider certificates which may be self-signed.
        validateNotSelfSigned(context, x509Certificate)
        validateKeyUsageIsDigitalSignature(context, x509Certificate)
        validateSubjectKeyIdentifierIsPresent(context, x509Certificate)
        validateCertificatePoliciesPresent(context, x509Certificate)
        validateAuthorityInfoAccessIfCaIssued(context, x509Certificate)
        validatePublicKeyAlgorithm(context, x509Certificate)
        validatePersonDnByRole(context, "subject", x509Certificate.data.subjectDn, isLegalPerson = true)
        validatePersonDnByRole(context, "issuer", x509Certificate.data.issuerDn, isLegalPerson = true)
        validateExtensionsAreNotCritical(context, x509Certificate, criticalExtensions)
        context.addLogEntry(
            ValidationResult.Severity.WARNING,
            "registeredIntendedUse",
            "Not yet validated - ETSI TS 119 475 primary-source reading is required before this " +
                    "profile can enforce the Relying Party's registered attribute scope (known gap, see class doc)."
        )
    }
}
