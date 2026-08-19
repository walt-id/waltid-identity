package id.walt.x509.iso.iaca.builder

import id.walt.x509.iso.iaca.certificate.IACACertificateBundle
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData

internal actual suspend fun platformSignIACACertificate(
    profileData: IACACertificateProfileData,
    signingKey: id.walt.crypto.keys.Key
): IACACertificateBundle {
    TODO("Not yet implemented")
}