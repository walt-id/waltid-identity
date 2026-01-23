package id.walt.x509.iso.iaca

import id.walt.crypto.keys.Key
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData

data class IACABuilderInputTestVector(
    val profileData: IACACertificateProfileData,
    val signingKey: Key,
)
