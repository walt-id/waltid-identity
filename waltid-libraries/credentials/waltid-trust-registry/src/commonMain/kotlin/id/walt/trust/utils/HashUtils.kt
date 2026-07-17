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
        val certBytes = decodeCertificate(pemOrDer)

        SHA256().digest(certBytes)
            .joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    } catch (e: Exception) {
        log.warn(e) { "Failed to compute certificate SHA-256" }
        null
    }

    /** Normalizes a PEM or Base64-DER certificate to unpadded Base64 DER. */
    fun normalizeCertificateDerBase64(pemOrDer: String): String? = try {
        Base64.encode(decodeCertificate(pemOrDer))
    } catch (e: Exception) {
        log.warn(e) { "Failed to normalize certificate DER" }
        null
    }

    private fun decodeCertificate(pemOrDer: String): ByteArray {
        val base64Content = if (pemOrDer.contains("BEGIN CERTIFICATE")) {
            pemOrDer
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replace("\\s".toRegex(), "")
        } else {
            pemOrDer.replace("\\s".toRegex(), "")
        }
        return Base64.decode(base64Content)
    }

}
