package id.walt.certificate.x509.builder

import id.walt.certificate.x509.Pkcs10CertificateSigningRequest
import id.walt.certificate.x509.extension.Extension
import id.walt.certificate.x509.extension.MutableExtensionContainer
import kotlinx.io.bytestring.ByteString

open class Pkcs10CertificateSigningRequestBuilder(subjectDn: String) {

    val requestedCertificate: RequestedCertificateDataBuilder = RequestedCertificateDataBuilder(subjectDn)

    class RequestedCertificateDataBuilder(
        override var subjectDn: String
    ) : Pkcs10CertificateSigningRequest.RequestedCertificateData, MutableExtensionContainer {

        override val subjectDnRaw: ByteString
            get() = error("Not available in builder")

        override val extensions: MutableMap<String, Extension> = mutableMapOf()
        override val subjectPublicKeyInfo: Pkcs10CertificateSigningRequest.SubjectPublicKeyInfo
            get() = error("Not available in builder - will be set when signed.")
    }

}