package id.walt.x509

import kotlinx.serialization.Serializable

/**
 * Platform-agnostic key usage flags from the X.509 KeyUsage extension.
 */
@Serializable
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
