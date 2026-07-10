package id.walt.verifier2.utils

import java.security.MessageDigest
import java.util.Base64

/**
 * JVM implementation of x509_hash computation.
 * Computes the SHA-256 hash of the DER-encoded certificate and formats it as x509_hash audience.
 */
actual fun computeX509HashAudience(x5cBase64: String): String? = runCatching {
    // Decode the Base64-encoded DER certificate
    val derBytes = Base64.getDecoder().decode(x5cBase64)
    
    // Compute SHA-256 hash
    val sha256 = MessageDigest.getInstance("SHA-256")
    val hashBytes = sha256.digest(derBytes)
    
    // Encode as Base64Url (no padding)
    val base64UrlHash = Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes)
    
    "x509_hash:$base64UrlHash"
}.getOrNull()
