package id.walt.certificate.x509.signum

import id.walt.certificate.x509.PublicKeyInfo
import id.walt.certificate.x509.SignatureValidator
import id.walt.certificate.x509.X509Certificate
import id.walt.crypto.keys.jwk.JWKKey
import at.asitplus.signum.indispensable.pki.X509Certificate as SignumCertificate

class SignumSignatureValidator : SignatureValidator {

    override val name: String = "Signum"

    override suspend fun validateCertificateSignature(
        issuerPublicKey: X509Certificate.SubjectPublicKeyInfo,
        certificate: X509Certificate
    ): Boolean {
        val signumCert = SignumCertificate.decodeFromDer(certificate.encodedDer.toByteArray())
        val publicKey = JWKKey.importPEM(issuerPublicKey.encodedPem).getOrThrow()
        val tbsData: ByteArray = signumCert.tbsCertificate.encodeToDer()
        val signature = signumCert.decodedSignature.getOrThrow()
        // Bad API, hard to distinct between invalid signature and verification failure
        val result = publicKey.verifyRaw(signature.encodeToDer(), tbsData)
        return result.getOrElse {
            if (it.message?.contains("verification") == true) {
                null
            } else {
                throw it
            }
        } != null
    }

    override suspend fun validateCsrSignature(
        subjectPublicKey: PublicKeyInfo,
        certificate: X509Certificate
    ): Boolean {
        TODO("Not yet implemented")
    }
}