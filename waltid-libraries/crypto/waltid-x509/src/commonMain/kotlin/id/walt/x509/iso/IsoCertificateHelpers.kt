package id.walt.x509.iso

import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.io.bytestring.ByteString

/**
 * Generate an ISO-compliant X.509 certificate serial number.
 *
 * Implementations must produce a positive, non-zero serial number that is
 * larger than 63 bits and up to 20 octets.
 */
internal fun generateIsoCompliantX509CertificateSerialNo(): ByteString {
    val randomBytes = secureRandomBytes(ISO_CERT_SERIAL_NUMBER_REQUIRED_LENGTH)

    // Keep the ASN.1 INTEGER positive while preserving well over 63 bits of entropy.
    randomBytes[0] = ((randomBytes[0].toInt() and 0x7f) or 0x40).toByte()

    return ByteString(randomBytes)
}

internal fun secureRandomBytes(size: Int): ByteArray =
    CryptographyRandom.Default.nextBytes(size)

/**
 * Validate that a country code is a valid ISO 3166-1 alpha-2 code.
 */
internal fun isValidIsoCountryCode(
    countryCode: String,
): Boolean = IsoCountryCodes.alpha2.contains(countryCode)
