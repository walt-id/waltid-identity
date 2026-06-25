package id.walt.x509.iso.iaca.builder

import id.walt.crypto.keys.Key
import id.walt.x509.iso.iaca.certificate.IACACertificateBundle
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData

internal actual suspend fun platformSignIACACertificate(
    profileData: IACACertificateProfileData,
    signingKey: Key,
): IACACertificateBundle {
    // TODO(iOS): Implement ISO IACA certificate building, then remove the guarded ISO X.509 tests.
    TODO("Not yet implemented")
}
