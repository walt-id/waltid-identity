package id.walt.certificate.x509

import id.walt.x509.id.walt.certificate.x509.JavaX509CertificateSerialNumberGenerator
import id.walt.x509.id.walt.certificate.x509.bouncycastle.BouncyPkcs10CertificateSigningRequestParser
import id.walt.x509.id.walt.certificate.x509.bouncycastle.BouncyPkcs10CertificateSigningRequestSigner
import id.walt.x509.id.walt.certificate.x509.bouncycastle.BouncyX509CertificateParser
import id.walt.x509.id.walt.certificate.x509.bouncycastle.BouncyX509CertificateSigner

actual object X509CertificateUtilDefaults : X509CertificateServices {
    actual override val csrParser: Pkcs10CertificateSigningRequestParser = BouncyPkcs10CertificateSigningRequestParser()
    actual override val csrSigner: Pkcs10CertificateSigningRequestSigner = BouncyPkcs10CertificateSigningRequestSigner()
    actual override val certificateParser: X509CertificateParser = BouncyX509CertificateParser()
    actual override val serialNumberGenerator: X509CertificateSerialNumberGenerator =
        JavaX509CertificateSerialNumberGenerator()
    actual override val certificateSigner: X509CertificateSigner = BouncyX509CertificateSigner()
}