package id.walt.x509

/**
 * Platform-agnostic key usage flags from the X.509 KeyUsage extension.
 */
enum class X509KeyUsage {
    DigitalSignature,
    NonRepudiation,
    KeyEncipherment,
    DataEncipherment,
    KeyAgreement,
    KeyCertSign,
    CRLSign,
    EncipherOnly,
    DecipherOnly,
}
