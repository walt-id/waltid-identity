@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca.builder

import id.walt.crypto.keys.Key
import id.walt.x509.iso.iaca.certificate.IACACertificateBundle
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData
import id.walt.x509.iso.iaca.validate.IACAValidator
import kotlin.time.ExperimentalTime

class IACACertificateBuilder {

    suspend fun build(
        profileData: IACACertificateProfileData,
        signingKey: Key,
    ): IACACertificateBundle {
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
