package id.walt.x509.iso

import okio.ByteString

internal fun validateSerialNo(
    serialNo: ByteString,
) {

    /*
    Non-sequential positive, non-zero integer, shall contain at least 63
    bits of output from a CSPRNG, should contain at least 71 bits of
    output from a CSPRNG, maximum 20 octets.
    * */
    require(serialNo.size <= 20 && (serialNo.size*8) >= 63) {
        "Serial number must have a size of maximum 20 octets and at least 63 bits, but was " +
                "found to have a size of: ${serialNo.size} octets"
    }
}