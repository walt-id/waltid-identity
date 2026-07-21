package id.walt.certificate.x509.signum

import id.walt.certificate.x509.Pkcs10CertificateSigningRequest
import id.walt.certificate.x509.extension.Extension
import id.walt.certificate.x509.signum.dn.toDistinguishedName
import kotlinx.io.bytestring.ByteString
import at.asitplus.signum.indispensable.pki.Pkcs10CertificationRequest as SignumCertificateRequest

class SignumCsr(val csr: SignumCertificateRequest) : Pkcs10CertificateSigningRequest {

    override val requestedCertificate = SignumRequestedCertificate()

    override val signatureAlgorithmOid: String
        get() = TODO()
    override val signatureValueRaw: ByteString
        get() = TODO()
    override val encodedDer: ByteString
        get() = TODO()

    inner class SignumRequestedCertificate : Pkcs10CertificateSigningRequest.RequestedCertificateData {
        override val subjectDn: String =
            csr.tbsCsr.subjectName.toDistinguishedName().toString()

        override val subjectPublicKeyInfo: Pkcs10CertificateSigningRequest.SubjectPublicKeyInfo
            get() = TODO("Not yet implemented")

        override val extensions: Map<String, Extension>
            get() = TODO("Not yet implemented")

    }
}