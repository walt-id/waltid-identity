package id.walt.x509.iso.documentsigner.builder

import id.walt.crypto.keys.Key
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData

/**
 * IACA information required to sign a Document Signer X.509 certificate.
 */
data class IACASignerSpecification(
    val profileData: IACACertificateProfileData,
    val signingKey: Key,
)
