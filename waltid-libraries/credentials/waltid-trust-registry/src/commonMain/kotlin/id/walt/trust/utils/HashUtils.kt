package id.walt.trust.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.io.encoding.Base64

object HashUtils {

    private val log = KotlinLogging.logger {}

    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    /**
     * Computes the RFC 7638 SHA-256 JWK thumbprint for a public key JWK.
     *
     * Only the required members for the key's `kty` are included in the canonical
     * form (lexicographic member order per RFC 7638), so this matches the thumbprint
     * of any other JWK representation of the same key regardless of extra members
     * (e.g. `kid`, `use`, `alg`) present on either side of a comparison.
     */
    fun computeJwkSha256Thumbprint(jwk: JsonElement): String? = try {
        val jwkObject = jwk.jsonObject
        val keyType = jwkObject.stringOrNull("kty")
        val requiredNames = when (keyType) {
            "EC" -> listOf("crv", "kty", "x", "y")
            "OKP" -> listOf("crv", "kty", "x")
            "RSA" -> listOf("e", "kty", "n")
            "oct" -> listOf("k", "kty")
            else -> null
        }
        if (requiredNames == null) {
            null
        } else {
            val members = requiredNames.map { name -> name to jwkObject.stringOrNull(name) }
            if (members.any { it.second == null }) {
                null
            } else {
                val canonical = members.joinToString(separator = ",", prefix = "{", postfix = "}") { (name, value) ->
                    "\"$name\":\"$value\""
                }
                val digest = SHA256().digest(canonical.encodeToByteArray())
                base64Url.encode(digest)
            }
        }
    } catch (e: Exception) {
        log.warn(e) { "Failed to compute JWK SHA-256 thumbprint" }
        null
    }

    private fun JsonObject.stringOrNull(name: String): String? =
        (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content

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
