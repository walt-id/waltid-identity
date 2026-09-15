package id.walt.certificate.x509.profile

/**
 * QCStatement OIDs per ETSI TS 119 412-6 Annex A (normative ASN.1 declarations).
 *
 * These identify the QcType statement carried by PID Provider and Wallet Provider certificates
 * (see [EtsiPidProviderX509CertificateProfile], [EtsiWalletProviderX509CertificateProfile]).
 */
object Etsi119412Part6 {

    /**
     * id-etsi-qct-pid OBJECT IDENTIFIER ::= { itu-t(0) identified-organization(4) etsi(0)
     *   eudiw(194126) qct(1) pid(1) }
     */
    const val ID_ETSI_QCT_PID: String = "0.4.0.194126.1.1"

    /**
     * id-etsi-qct-wal OBJECT IDENTIFIER ::= { itu-t(0) identified-organization(4) etsi(0)
     *   eudiw(194126) qct(1) wal(2) }
     */
    const val ID_ETSI_QCT_WAL: String = "0.4.0.194126.1.2"
}
