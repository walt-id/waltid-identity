package id.walt.webwallet.utils

import id.walt.crypto.utils.Base64Utils.base64Decode
import java.io.ByteArrayInputStream
import java.security.cert.*
import java.time.Instant
import java.util.*

class X5CValidator(
    pemEncodedTrustedCACertificates: List<String> = emptyList(),
) {
    private val certificateFactory = CertificateFactory.getInstance("X509")
    private val certificatePathValidator = CertPathValidator.getInstance(CertPathValidator.getDefaultType())
    private val trustAnchorSet = parsePEMEncodedX509CertificateList(pemEncodedTrustedCACertificates)
        .map {
            TrustAnchor(it, null)
        }.toSet()

    fun validate(certificates: List<String>): Result<Boolean> = runCatching {
        require(certificates.isNotEmpty()) { "No signing certificate" }
        val chain = generateX509Chain(certificates)
        validateCertificateChainIsTrustworthy(chain)
    }.fold(
        onSuccess = {
            Result.success(true)
        },
        onFailure = {
            when (it) {
                is CertPathValidatorException -> {
                    Result.failure(IllegalStateException("certificate path validation failed: ${it.localizedMessage}: ${it.reason}"))
                }

                else -> Result.failure(IllegalStateException("certificate path validation failed"))
            }
        }
    )

    private fun validateCertificateChainIsTrustworthy(chain: List<X509Certificate>) {
        val pkixValidationParams = PKIXParameters(trustAnchorSet).apply {
            isRevocationEnabled = false
            date = Date.from(Instant.now())
        }
        certificatePathValidator.validate(certificateFactory.generateCertPath(chain), pkixValidationParams)
    }

    /**
     * Decodes the base64 certificate strings
     * and converts into [X509Certificate]
     */
    private fun generateX509Chain(certificateChain: List<String>): List<X509Certificate> = certificateChain.flatMap {
        certificateFactory.generateCertificates(ByteArrayInputStream(it.base64Decode())).map { it as X509Certificate }
    }

    /**
     * @param certificateList a list of PEM encoded X.509 certificates
     * @return a list of [X509Certificate] objects
     */
    private fun parsePEMEncodedX509CertificateList(certificateList: List<String>): List<X509Certificate> =
        certificateList.map {
            certificateFactory.generateCertificate(ByteArrayInputStream(it.toByteArray())) as X509Certificate
        }
}