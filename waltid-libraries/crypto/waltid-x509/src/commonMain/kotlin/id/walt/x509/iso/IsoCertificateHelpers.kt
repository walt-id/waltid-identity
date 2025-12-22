package id.walt.x509.iso

import okio.ByteString

internal expect fun generateCertificateSerialNo(): ByteString

internal expect fun isValidIsoCountryCode(
    countryCode: String,
) : Boolean