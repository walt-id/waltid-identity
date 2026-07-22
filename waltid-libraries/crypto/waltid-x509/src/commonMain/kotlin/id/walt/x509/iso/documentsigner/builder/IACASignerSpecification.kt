package id.walt.x509.iso.documentsigner.builder

import id.walt.crypto.keys.Key
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData

/**
 * IACA information required to sign a Document Signer X.509 certificate.
 */
@Deprecated("Use Crypto2IACASignerSpecification with a crypto2 key and explicit signature algorithm.")
data class IACASignerSpecification(
    val profileData: IACACertificateProfileData,
    val signingKey: Key,
)

/** Crypto2 IACA information used to sign a Document Signer certificate. */
data class Crypto2IACASignerSpecification(
    val profileData: IACACertificateProfileData,
    val signingKey: Crypto2Key,
    val signatureAlgorithm: SignatureAlgorithm,
)
