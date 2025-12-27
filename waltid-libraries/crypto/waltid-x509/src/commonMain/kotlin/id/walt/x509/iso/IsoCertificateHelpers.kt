package id.walt.x509.iso

import okio.ByteString

internal expect fun generateIsoCompliantX509CertificateSerialNo(): ByteString

internal expect fun isValidIsoCountryCode(
    countryCode: String,
) : Boolean