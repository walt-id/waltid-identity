@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca.builder

import id.walt.crypto.keys.Key
import id.walt.x509.iso.iaca.certificate.IACACertificateBundle
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData
import id.walt.x509.iso.iaca.validate.IACAValidator
import kotlin.time.ExperimentalTime

class IACACertificateBuilder(
    val profileData: IACACertificateProfileData,
    val signingKey: Key,
) {

    suspend fun build(): IACACertificateBundle {
        val validator = IACAValidator()
        validator.validateSigningKey(signingKey)
        validator.validateCertificateProfileData(profileData)
        return platformSignIACACertificate(
            profileData = profileData,
            signingKey = signingKey,
        )
    }

}

internal expect suspend fun platformSignIACACertificate(
    profileData: IACACertificateProfileData,
    signingKey: Key,
): IACACertificateBundle
