package id.walt.x509.iso

import okio.ByteString

/**
 * Validate an ISO-compliant X.509 certificate serial number.
 *
 * Requirements:
 * - Non-zero positive integer.
 * - At least 63 bits of entropy.
 * - Maximum length of 20 octets.
 */
internal fun validateSerialNo(
    serialNo: ByteString,
) {
    require(serialNo.size <= 20 && (serialNo.size*8) >= 63) {
        "Serial number must have a size of maximum 20 octets and at least 63 bits, but was " +
                "found to have a size of: ${serialNo.size} octets"
    }
}
