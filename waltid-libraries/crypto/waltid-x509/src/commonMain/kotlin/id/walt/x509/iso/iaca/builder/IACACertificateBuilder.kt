@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca.builder

import id.walt.crypto.keys.Key
import id.walt.x509.iso.blockingBridge
import id.walt.x509.iso.iaca.certificate.IACACertificateBundle
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData
import id.walt.x509.iso.iaca.validate.IACAValidator
import kotlin.time.ExperimentalTime

/**
 * Builder for ISO IACA X.509 certificates.
 *
 * The builder validates profile data and signing key inputs before delegating
 * the certificate creation to platform-specific implementations.
 *
 * Validity period precision: input [kotlin.time.Instant] values are truncated
 * to whole seconds when encoded. Sub-second precision is discarded and the
 * returned decoded certificate reflects this truncation.
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
     *
     * Note: Validity period instants are stored with second-level precision;
     * any milliseconds or nanoseconds in the input are discarded. The decoded
     * certificate returned by this builder exposes the truncated values.
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

    /**
     * Blocking variant of [build].
     */
    fun buildBlocking(
        profileData: IACACertificateProfileData,
        signingKey: Key,
    ): IACACertificateBundle = blockingBridge {
        build(
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
