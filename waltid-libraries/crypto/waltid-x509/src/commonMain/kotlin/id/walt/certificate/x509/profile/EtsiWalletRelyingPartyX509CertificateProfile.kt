package id.walt.certificate.x509.profile

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.builder.X509CertificateDataBuilder
import id.walt.certificate.x509.dn.DistinguishedName
import id.walt.certificate.x509.extension.AuthorityInfoAccessExtension.Companion.extensionAuthorityInfoAccess
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.extension.CertificatePoliciesExtension.Companion.extensionCertificatePolicies
import id.walt.certificate.x509.extension.KeyUsageExtension
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.ValidationResult


/**
 * Wallet Relying Party Certificates (e.g., WRPAC)
 *
 * Who uses them: Public administrations, private online services, or platforms (Relying Parties) that want to request
 * data from a user's EUDI Wallet.
 *
 * Primary Purpose: To authenticate the Relying Party to the wallet unit during a transaction. When a service asks a
 * user for their identity attributes, this certificate proves to the wallet (and the user) who is making the request.
 *
 * Ecosystem Role: They ensure that only officially registered services can interact with wallets, working alongside
 * registration components (like a WRPRC) to communicate the service's legal purpose and data access policy.
 *
 * Issuance: Issued by an authorised national provider after the organization successfully registers with its national
 * register of wallet-relying parties.
 */
sealed class EtsiWalletRelyingPartyX509CertificateProfile {

    companion object {

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

        /**
         * Certificate policy OIDs per ETSI TS 119 411-8 clause 5.3 (Wallet Relying Party Access
         * Certificate policy identifiers).
         *
         * Used by [EtsiWrpAcX509CertificateProfile]. NCP = normalized certificate policy, QCP = qualified
         * certificate policy; "-n" / "-l" select the natural-person / legal-person variant.
         */

        /*
         * NCP (Normalized Certificate Policy): This is a baseline, standard-level policy for public key certificates.
         * Certificates issued under NCP meet standard commercial security and operational requirements,
         * but they are not legally "qualified" under European eIDAS regulations.
        */

        /**
         * ncp-n-eudiwrp OBJECT IDENTIFIER ::= { itu-t(0) identified-organization(4) etsi(0)
         *   eudiwrp(194118) policy-identifiers(1) ncp-natural(1) }
         */
        const val NORMALIZED_CERT_POLICY_NATURAL_PERSON: String = "0.4.0.194118.1.1"

        /**
         * ncp-l-eudiwrp OBJECT IDENTIFIER ::= { itu-t(0) identified-organization(4) etsi(0)
         *   eudiwrp(194118) policy-identifiers(1) ncp-legal(2) }
         */
        const val NORMALIZED_CERT_POLICY_ID_LEGAL_PERSON: String = "0.4.0.194118.1.2"

        /*
         * QCP (Qualified Certificate Policy): This is a high-assurance policy designed for EU Qualified Certificates.
         * It means the certificate is issued by a strictly audited Qualified Trust Service Provider (QTSP) and carries
         * full, legally binding status across the European Union (often used for legally binding electronic signatures).
         */

        /**
         * qcp-n-eudiwrp OBJECT IDENTIFIER ::= { itu-t(0) identified-organization(4) etsi(0)
         *   eudiwrp(194118) policy-identifiers(1) qcp-natural(3) }
         */
        const val QUALIFIED_CERT_POLICY_NATURAL_PERSON: String = "0.4.0.194118.1.3"

        /**
         * qcp-l-eudiwrp OBJECT IDENTIFIER ::= { itu-t(0) identified-organization(4) etsi(0)
         *   eudiwrp(194118) policy-identifiers(1) qcp-legal(4) }
         */
        const val QUALIFIED_CERT_POLICY_LEGAL_PERSON: String = "0.4.0.194118.1.4"
    }

    protected fun X509CertificateDataBuilder.profileEtsiWalletRelyingParty() {
        extensionBasicConstraints {
            critical = true
            cA = false
        }
        extensionKeyUsage {
            critical = true
            addKeyUsage(KeyUsageExtension.KeyUsage.digitalSignature)
        }
        extensionSubjectKeyIdentifier()
    }


    /**
     * EN 319 412-2 4.3.3: certificatePolicies extension shall be present.
     */
    protected fun validateCertificatePoliciesPresent(context: ValidationContext, x509Certificate: X509Certificate) {
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
    protected fun validateAuthorityInfoAccessIfCaIssued(context: ValidationContext, x509Certificate: X509Certificate) {
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
     * ETSI TS 119 312: RSA >= 2048 bits, EC >= 256 bits (FIPS 186-4 or RFC 5639 brainpool curves).
     */
    protected fun validatePublicKeyAlgorithm(context: ValidationContext, x509Certificate: X509Certificate) {
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
     * Same field-shape checks as [validatePersonDn], but the natural-vs-legal-person distinction
     * is given explicitly rather than detected from the DN - used by [EtsiWrpAcX509CertificateProfile]
     * / [EtsiWrpRcX509CertificateProfile], whose person role is determined by the certificate's
     * policy OID (or, for WRPRC's issuer, is always a legal person) rather than by DN inspection.
     */
    protected fun validatePersonDnByRole(
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