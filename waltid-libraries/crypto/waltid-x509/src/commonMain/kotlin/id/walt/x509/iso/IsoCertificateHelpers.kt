package id.walt.x509.iso

import okio.ByteString

/**
 * Generate an ISO-compliant X.509 certificate serial number.
 *
 * Implementations must produce a positive, non-zero serial number that is
 * larger than 63 bits and up to 20 octets.
 */
internal expect fun generateIsoCompliantX509CertificateSerialNo(): ByteString

/**
 * Validate that a country code is a valid ISO 3166-1 alpha-2 code.
 */
internal expect fun isValidIsoCountryCode(
    countryCode: String,
) : Boolean
