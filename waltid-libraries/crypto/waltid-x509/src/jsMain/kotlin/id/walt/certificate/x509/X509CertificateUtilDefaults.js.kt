package id.walt.certificate.x509

import id.walt.certificate.x509.node.NodeX509CertificateParser
import id.walt.certificate.x509.validation.X509CertificateChainValidator

actual object X509CertificateUtilDefaults : X509CertificateServices {
    actual override val csrParser: Pkcs10CertificateSigningRequestParser
        get() = TODO("Not yet implemented")
    actual override val csrSigner: Pkcs10CertificateSigningRequestSigner
        get() = TODO("Not yet implemented")
    actual override val certificateParser: X509CertificateParser = NodeX509CertificateParser()
    actual override val serialNumberGenerator: X509CertificateSerialNumberGenerator
        get() = TODO("Not yet implemented")
    actual override val certificateSigner: X509CertificateSigner
        get() = TODO("Not yet implemented")
    actual override val certificateChainValidator: X509CertificateChainValidator
        get() = TODO("Not yet implemented")
}