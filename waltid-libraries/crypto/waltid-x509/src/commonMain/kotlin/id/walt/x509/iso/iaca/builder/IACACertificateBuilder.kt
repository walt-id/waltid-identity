@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca.builder

import id.walt.crypto.keys.Key
import id.walt.x509.iso.iaca.certificate.IACACertificateBundle
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData
import id.walt.x509.iso.iaca.validate.IACAValidator
import kotlin.time.ExperimentalTime

/**
 * Builder for ISO IACA X.509 certificates.
 *
 * The builder validates profile data and signing key inputs before delegating
 * the certificate creation to platform-specific implementations.
 */
class IACACertificateBuilder {

    private val validator by lazy {
        IACAValidator()
    }

    /**
     * Build a new IACA certificate.
     *
     * @param profileData ISO profile inputs for the generating the X.509 certificate.
     * @param signingKey Private key used to sign the X.509 certificate.
     */
    suspend fun build(
        profileData: IACACertificateProfileData,
        signingKey: Key,
    ): IACACertificateBundle {
        validator.validateSigningKey(signingKey)
        validator.validateCertificateProfileData(profileData)
        return platformSignIACACertificate(
            profileData = profileData,
            signingKey = signingKey,
        )
    }

}

/**
 * Platform-specific signing implementation for IACA certificates.
 */
internal expect suspend fun platformSignIACACertificate(
    profileData: IACACertificateProfileData,
    signingKey: Key,
): IACACertificateBundle
