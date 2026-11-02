package id.walt.ktornotifications

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Versioned signature for an outbound webhook attempt. The receiver must digest the raw body itself. */
object WebhookSignature {
    fun sign(
        secret: String,
        url: String,
        deliveryId: String,
        timestampEpochSeconds: Long,
        attempt: Int,
        contentDigest: String,
    ): String {
        require(secret.isNotBlank()) { "Webhook signing secret must not be blank" }
        require(attempt > 0) { "Webhook attempt must be positive" }
        val canonical = listOf("POST", url, deliveryId, timestampEpochSeconds.toString(), attempt.toString(), contentDigest)
            .joinToString("\n")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return "v1=" + Base64.getEncoder().encodeToString(mac.doFinal(canonical.toByteArray(StandardCharsets.UTF_8)))
    }

    fun verify(
        signature: String,
        secret: String,
        url: String,
        deliveryId: String,
        timestampEpochSeconds: Long,
        attempt: Int,
        contentDigest: String,
    ): Boolean {
        val supplied = if (signature.startsWith("v1=")) {
            runCatching { Base64.getDecoder().decode(signature.removePrefix("v1=")) }.getOrNull()
        } else null
        if (supplied == null) return false
        val expected = Base64.getDecoder().decode(sign(secret, url, deliveryId, timestampEpochSeconds, attempt, contentDigest).removePrefix("v1="))
        return MessageDigest.isEqual(expected, supplied)
    }
}
