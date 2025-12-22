package id.walt.x509.iso.documentsigner.builder

import id.walt.crypto.keys.Key
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData

data class IACASignerSpecification(
    val signingKey: Key,
    val data: IACACertificateProfileData,
)
