package id.walt.certificate.x509.signum

import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.isSupported
import at.asitplus.signum.indispensable.pki.Pkcs10CertificationRequest
import at.asitplus.signum.indispensable.pki.X509Certificate as SignumCertificate
import id.walt.certificate.x509.Pkcs10CertificateSigningRequest
import id.walt.certificate.x509.SignatureValidator
import id.walt.certificate.x509.X509Certificate

class SignumSignatureValidator : SignatureValidator {

    override val name: String = "Signum"

    override suspend fun validateCertificateSignature(
        issuerPublicKey: X509Certificate.SubjectPublicKeyInfo,
        certificate: X509Certificate,
    ): Boolean {
        val signumCertificate = SignumCertificate.decodeFromDer(certificate.encodedDer.toByteArray())
        val description = signumCertificate.signatureAlgorithm
        require(description.isSupported()) { "Unsupported certificate signature algorithm" }

        return verifySignumSignature(
            publicKey = CryptoPublicKey.decodeFromDer(issuerPublicKey.encodedDer.toByteArray()),
            algorithm = description.algorithm,
            signedData = signumCertificate.tbsCertificate.encodeToDer(),
            signature = signumCertificate.decodedSignature.getOrThrow(),
        )
    }

    override suspend fun validateCsrSignature(
        csr: Pkcs10CertificateSigningRequest,
    ): Boolean {
        val signumCsr = Pkcs10CertificationRequest.decodeFromPem(csr.encodedPem).getOrThrow()
        val description = signumCsr.signatureAlgorithm
        require(description.isSupported()) { "Unsupported CSR signature algorithm" }

        return verifySignumSignature(
            publicKey = signumCsr.tbsCsr.publicKey,
            algorithm = description.algorithm,
            signedData = signumCsr.tbsCsr.encodeToDer(),
            signature = signumCsr.decodedSignature.getOrThrow(),
        )
    }
}
