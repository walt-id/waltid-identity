package id.walt.webwallet.utils

import id.walt.crypto.utils.Base64Utils.base64Decode
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.*
import java.time.Instant
import java.util.*
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class X5CValidator(
    private val trustedCA: List<String> = emptyList(),
) {
    //??not really required, as we have the trustedCA list we check against
    private val trustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    private val certificateFactory = CertificateFactory.getInstance("X509")
    private val certificatePathValidator = CertPathValidator.getInstance("PKIX")

    init {
        trustManager.init(null as? KeyStore)
    }

    fun verifyX5Chain(certificates: List<String>): Result<Unit> = runCatching {
        require(certificates.isNotEmpty()) { "No signing certificate" }
        val chain = generateX509Chain(certificates)
        val trusted = generateX509Chain(trustedCA)
        checkDate(chain[0]) { "Expired or invalid certificate: ${it.subjectX500Principal.name} - notBefore (${it.notBefore}) and notAfter (${it.notAfter})" }
        validateCertificateChain(chain, trusted).toResult("Failed to validate X5 Chain")
    }

    private fun Boolean.toResult(message: String) = when (this) {
        true -> Result.success(Unit)
        false -> Result.failure(IllegalStateException(message))
    }

    /**
     * Decodes the base64 certificate strings
     * and converts into [X509Certificate]
     */
    private fun generateX509Chain(certificateChain: List<String>): List<X509Certificate> = certificateChain.flatMap {
        certificateFactory.generateCertificates(ByteArrayInputStream(it.base64Decode())).map { it as X509Certificate }
    }

    /**
     * Attempts to validate the [X509Certificate]'s [notBefore][X509Certificate.getNotBefore]
     * and [notAfter][X509Certificate.getNotAfter] against the current date
     * @param certificate the [X509Certificate] certificate
     * @param message optional message lambda
     * @throws [IllegalStateException] with the given [message], if validation fails
     *
     * TODO: potential candidate for common-utils
     * see [private validate()][id.walt.webwallet.service.trust.DefaultTrustValidationService.validate]
     */
    private fun checkDate(certificate: X509Certificate, message: ((X509Certificate) -> String)? = null) = let {
        val notBefore = certificate.notBefore
        val notAfter = certificate.notAfter
        val now = Date.from(Instant.now())
        now in notBefore..notAfter
    }.takeIf { it }?.let { /*nop*/ } ?: throw IllegalStateException(
        message?.invoke(certificate) ?: "Invalid date"
    )

    /**
     * Validates the certificate chain
     * @return true if validation succeeds, otherwise - false
     */
    private fun validateCertificateChain(
        certChain: List<X509Certificate>, additionalTrustedRootCAs: List<X509Certificate>
    ): Boolean = findIssuerCA(certChain.first(), additionalTrustedRootCAs)?.let {
        //todo: validate each certificate date
        validateCertificatePath(it, certificateFactory.generateCertPath(certChain))
    } ?: false

    /**
     * Creates a [TrustAnchor] and attempts to validate the certificate path
     * @return true - if validation succeeds, otherwise - false
     */
    private fun validateCertificatePath(it: X509Certificate, certificatePath: CertPath) = runCatching {
        PKIXParameters(setOf(TrustAnchor(it, null))).apply {
            isRevocationEnabled = false
        }.run {
            certificatePathValidator.validate(certificatePath, this)
        }
    }.fold(onSuccess = { true }, onFailure = { false })

    /**
     * Initializes the trust manager with [trustedCAs]
     * and looks up for a [X509Certificate] of a trusted issuer
     */
    private fun findIssuerCA(cert: X509Certificate, trustedCAs: List<X509Certificate>): X509Certificate? =
        trustManager.trustManagers
            .filterIsInstance<X509TrustManager>()
            .flatMap { it.acceptedIssuers.toList() }//??required
            .plus(trustedCAs)
            .firstOrNull {
                cert.issuerX500Principal.name.equals(it.subjectX500Principal.name)
            }
}