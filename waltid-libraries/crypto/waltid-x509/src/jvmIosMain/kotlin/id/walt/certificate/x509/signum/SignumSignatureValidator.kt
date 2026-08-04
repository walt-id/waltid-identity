package id.walt.certificate.x509.signum

import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.isSupported
import at.asitplus.signum.indispensable.pki.Pkcs10CertificationRequest
import at.asitplus.signum.supreme.sign.InvalidSignature
import at.asitplus.signum.supreme.sign.verifierFor
import at.asitplus.signum.supreme.sign.verify
import id.walt.certificate.x509.Pkcs10CertificateSigningRequest
import id.walt.certificate.x509.SignatureValidator
import id.walt.certificate.x509.X509Certificate
import at.asitplus.signum.indispensable.pki.X509Certificate as SignumCertificate

class SignumSignatureValidator : SignatureValidator {

    override val name: String = "Signum"

    override suspend fun validateCertificateSignature(
        issuerPublicKey: X509Certificate.SubjectPublicKeyInfo,
        certificate: X509Certificate
    ): Boolean {
        val signumCert = SignumCertificate.decodeFromDer(certificate.encodedDer.toByteArray())
        val tbsData = signumCert.tbsCertificate.encodeToDer()
        val signature = signumCert.decodedSignature.getOrThrow()
        val description = signumCert.signatureAlgorithm
        require(description.isSupported()) { "Unsupported certificate signature algorithm" }
        val publicKey = CryptoPublicKey.decodeFromDer(issuerPublicKey.encodedDer.toByteArray())
        val verifier = description.algorithm.verifierFor(publicKey).getOrThrow()
        return verifier.verify(tbsData, signature).fold(
            onSuccess = { true },
            onFailure = { cause ->
                when (cause) {
                    is InvalidSignature -> false
                    else -> throw cause
                }
            },
        )
    }

    override suspend fun validateCsrSignature(
        csr: Pkcs10CertificateSigningRequest
    ): Boolean {
        val signumCsr = Pkcs10CertificationRequest.decodeFromPem(csr.encodedPem).getOrThrow()
        val tbsData = signumCsr.tbsCsr.encodeToDer()
        val signature = signumCsr.decodedSignature.getOrThrow()
        val description = signumCsr.signatureAlgorithm
        require(description.isSupported()) { "Unsupported CSR signature algorithm" }
        val verifier = description.algorithm.verifierFor(signumCsr.tbsCsr.publicKey).getOrThrow()
        return verifier.verify(tbsData, signature).fold(
            onSuccess = { true },
            onFailure = { cause ->
                when (cause) {
                    is InvalidSignature -> false
                    else -> throw cause
                }
            },
        )
    }
}
