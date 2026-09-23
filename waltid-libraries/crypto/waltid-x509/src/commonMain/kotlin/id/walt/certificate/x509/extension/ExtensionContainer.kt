package id.walt.certificate.x509.extension

interface ExtensionContainer {
    val extensions: Map<String, Extension>
}