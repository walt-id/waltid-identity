package id.walt.verifier2.utils

/**
 * Computes the x509_hash audience format from a Base64-encoded X.509 certificate.
 * Per HAIP, when using x509_san_dns with signed requests, the wallet MAY use
 * x509_hash:<base64url-sha256-of-der-cert> as the KB-JWT audience.
 *
 * @param x5cBase64 The Base64-encoded DER certificate (first element of x5c array)
 * @return The x509_hash formatted audience string, or null on error
 */
expect fun computeX509HashAudience(x5cBase64: String): String?
