package id.walt.certificate.x509.javasec

import id.walt.certificate.x509.PublicKeyInfo
import id.walt.certificate.x509.SignatureValidator
import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509SigningAlgorithmInfo
import id.walt.certificate.x509.X509SigningAlgorithmInfo.Companion.KEY_ALG_NAME_EC
import id.walt.certificate.x509.X509SigningAlgorithmInfo.Companion.KEY_ALG_NAME_RSA
import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.cert.CertificateFactory
import java.security.spec.X509EncodedKeySpec
import java.security.cert.X509Certificate as JvmX509Certificate

class JavaSignatureValidator : SignatureValidator {

    override val name: String = "JavaSecurity"

    override suspend fun validateCertificateSignature(
        issuerPublicKey: X509Certificate.SubjectPublicKeyInfo,
        certificate: X509Certificate
    ): Boolean {
        val jvmKeyAlgName = X509SigningAlgorithmInfo.algorithmNameByOid(issuerPublicKey.algorithmOid).let {
            when (it) {
                KEY_ALG_NAME_EC -> "EC"
                KEY_ALG_NAME_RSA -> "RSA"
                else -> error("Unsupported key algorithm: $it")
            }
        }
        val keySpec = X509EncodedKeySpec(issuerPublicKey.encodedDer.toByteArray())
        val jvmPublicKey = KeyFactory.getInstance(jvmKeyAlgName).generatePublic(keySpec)
        val factory = CertificateFactory.getInstance("X.509")
        val jvmCert = factory.generateCertificate(
            ByteArrayInputStream(certificate.encodedDer.toByteArray())
        ) as JvmX509Certificate

        return runCatching {
            jvmCert.verify(jvmPublicKey)
            true
        }.getOrElse { false }
    }

    override suspend fun validateCsrSignature(
        subjectPublicKey: PublicKeyInfo,
        certificate: X509Certificate
    ): Boolean {
        TODO("Not yet implemented")
    }
}