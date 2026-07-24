package id.walt.certificate.x509.extension

interface Extension {
    val oid: String
    val critical: Boolean
}