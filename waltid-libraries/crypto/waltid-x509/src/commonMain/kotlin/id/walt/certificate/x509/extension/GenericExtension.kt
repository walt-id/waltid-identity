package id.walt.certificate.x509.extension

import kotlinx.io.bytestring.ByteString

interface GenericExtension : Extension {
    val encoded: ByteString
}