package id.walt.trust.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.io.encoding.Base64

object HashUtils {

    private val log = KotlinLogging.logger {}

    /**
     * Computes the SHA-256 fingerprint of a certificate in PEM or base64-encoded DER format.
     */
    fun computeCertificateSha256(pemOrDer: String): String? = try {
        val certBytes = if (pemOrDer.contains("BEGIN CERTIFICATE")) {
            // PEM format
            val base64Content = pemOrDer
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replace("\\s".toRegex(), "")
            Base64.decode(base64Content)
        } else {
            // Assume base64-encoded DER
            Base64.decode(pemOrDer.replace("\\s".toRegex(), ""))
        }

        SHA256().digest(certBytes)
            .joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    } catch (e: Exception) {
        log.warn(e) { "Failed to compute certificate SHA-256" }
        null
    }

}
