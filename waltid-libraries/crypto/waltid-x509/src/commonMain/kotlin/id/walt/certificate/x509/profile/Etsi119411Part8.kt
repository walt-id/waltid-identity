package id.walt.certificate.x509.profile

/**
 * Certificate policy OIDs per ETSI TS 119 411-8 clause 5.3 (Wallet Relying Party Access
 * Certificate policy identifiers).
 *
 * Used by [EtsiWrpacX509CertificateProfile]. NCP = normalized certificate policy, QCP = qualified
 * certificate policy; "-n" / "-l" select the natural-person / legal-person variant.
 */
object Etsi119411Part8 {

    /**
     * ncp-n-eudiwrp OBJECT IDENTIFIER ::= { itu-t(0) identified-organization(4) etsi(0)
     *   eudiwrp(194118) policy-identifiers(1) ncp-natural(1) }
     */
    const val NCP_N_EUDIWRP: String = "0.4.0.194118.1.1"

    /**
     * ncp-l-eudiwrp OBJECT IDENTIFIER ::= { itu-t(0) identified-organization(4) etsi(0)
     *   eudiwrp(194118) policy-identifiers(1) ncp-legal(2) }
     */
    const val NCP_L_EUDIWRP: String = "0.4.0.194118.1.2"

    /**
     * qcp-n-eudiwrp OBJECT IDENTIFIER ::= { itu-t(0) identified-organization(4) etsi(0)
     *   eudiwrp(194118) policy-identifiers(1) qcp-natural(3) }
     */
    const val QCP_N_EUDIWRP: String = "0.4.0.194118.1.3"

    /**
     * qcp-l-eudiwrp OBJECT IDENTIFIER ::= { itu-t(0) identified-organization(4) etsi(0)
     *   eudiwrp(194118) policy-identifiers(1) qcp-legal(4) }
     */
    const val QCP_L_EUDIWRP: String = "0.4.0.194118.1.4"
}
