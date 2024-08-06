package id.walt.crypto.utils

import id.walt.crypto.utils.Base64Utils.base64Decode
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.*
import java.time.Instant
import java.util.*
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

actual object X5CUtils {
    private val trustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    private val certificateFactory = CertificateFactory.getInstance("X509")
    private val certificatePathValidator = CertPathValidator.getInstance("PKIX")

    actual fun verifyX5Chain(certificateChain: List<String>, trustedRootCA: List<String>): Boolean = let {
        require(certificateChain.isNotEmpty()) { "No signing certificate" }
        val signingCertificate = certificateChain.first().base64Decode()
        val chain = certificateFactory.generateCertificates(ByteArrayInputStream(signingCertificate))
            .map { it as X509Certificate }
//        val additionalTrustedRootCAs = certificateFactory.generateCertificates(trustedRootCA.map { it.base64Decode() })
        checkDate(chain[0]) { "Certificate date is not valid" }
        validateCertificateChain(chain, emptyList())
    }

    private fun checkDate(certificate: X509Certificate, message: (() -> String)? = null) = let {
        val notBefore = certificate.notBefore
        val notAfter = certificate.notAfter
        val now = Date.from(Instant.now())
        now in notBefore..notAfter
    }.takeIf { it }?.let { /*nop*/ } ?: throw IllegalArgumentException(
        message?.invoke() ?: "Now is not within certificate notBefore and notAfter"
    )

    private fun findRootCA(cert: X509Certificate, additionalTrustedRootCAs: List<X509Certificate>): X509Certificate? {
        trustManager.init(null as? KeyStore)
        return trustManager.trustManagers
            .filterIsInstance<X509TrustManager>()
            .flatMap { it.acceptedIssuers.toList() }
            .plus(additionalTrustedRootCAs)
            .firstOrNull {
                cert.issuerX500Principal.name.equals(it.subjectX500Principal.name)
            }
    }

    private fun validateCertificateChain(
        certChain: List<X509Certificate>, additionalTrustedRootCAs: List<X509Certificate>
    ): Boolean {
        val certPath = certificateFactory.generateCertPath(certChain)
        val trustAnchorCert = findRootCA(certChain.last(), additionalTrustedRootCAs) ?: return false
        certificatePathValidator.validate(certPath, PKIXParameters(setOf(TrustAnchor(trustAnchorCert, null))).apply {
            isRevocationEnabled = false
        })
        return true
    }
}