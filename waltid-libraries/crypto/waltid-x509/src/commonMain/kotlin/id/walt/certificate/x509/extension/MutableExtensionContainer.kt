package id.walt.certificate.x509.extension

interface MutableExtensionContainer {
    val extensions: MutableMap<String, Extension>
}