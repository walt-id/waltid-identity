package id.walt.certificate.x509.profile

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.builder.X509CertificateDataBuilder
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.validator.X509CertificateValidator
import id.walt.crypto2.keys.Key

/**
 * Profile for Wallet Provider end-entity certificates - certificates whose corresponding private
 * key signs Wallet Instance/Unit Attestations (WIA/WUA).
 *
 * ETSI TS 119 412-6 V1.1.1, clause 5.1 ("Certificate profile for Wallet providers", WAL-5.1-01),
 * which otherwise references the same requirements as the PID Provider profile
 * ([EtsiPidProviderX509CertificateProfile], clause 4, PID-4.2/-4.3/-4.4).
 */
object EtsiWalletProviderX509CertificateProfile : EtsiProviderX509CertificateProfile(), X509CertificateProfile,
    X509CertificateValidator {

    const val ID = "etsi-wallet-provider"

    /**
     * OID for the QcType statement that identifies this certificate as an ETSI Wallet Provider
     * certificate, asserted in the `qcStatements` extension alongside QcCompliance
     * (WAL-5.1-01, [Etsi119412Part6]).
     *
     * ```
     * id-etsi-qct-wal OBJECT IDENTIFIER ::= { itu-t(0) identified-organization(4) etsi(0)
     *   eudiw(194126) qct(1) wal(2) }
     * ```
     */
    const val QUALIFIED_CERTIFICATE_STATEMENT_ETSI_WALLET_PROVIDER: String = "0.4.0.194126.1.2"

    override val id: String = ID

    /**
     * @param subjectKey the subject's public key, or `null` when issuing a self-signed Wallet
     *   Provider certificate via [id.walt.certificate.x509.X509CertificateUtil.createSelfSignedCertificate].
     * @param certificatePolicyOids TSP-defined certificate policy OID(s); at least one required
     *   (EN 319 412-2 clause 4.3.3).
     * @param caIssuerUri / [ocspResponderUri] populate the authorityInfoAccess extension, required
     *   unless the certificate is self-signed (PID-4.4.3-01, referenced by WAL-5.1-01).
     */
    fun X509CertificateDataBuilder.profileEtsiWalletProviderCertificate(
        subjectKey: Key? = null,
        subjectDn: String,
        certificatePolicyOids: List<String>,
        caIssuerUri: String? = null,
        ocspResponderUri: String? = null,
    ) {
        applyProviderCertificate(
            subjectKey = subjectKey,
            subjectDn = subjectDn,
            qcTypeOid = QUALIFIED_CERTIFICATE_STATEMENT_ETSI_WALLET_PROVIDER,
            certificatePolicyOids = certificatePolicyOids,
            caIssuerUri = caIssuerUri,
            ocspResponderUri = ocspResponderUri,
        )
    }

    override suspend fun validate(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        validateProviderCertificate(
            context,
            x509Certificate,
            QUALIFIED_CERTIFICATE_STATEMENT_ETSI_WALLET_PROVIDER
        )
    }
}
