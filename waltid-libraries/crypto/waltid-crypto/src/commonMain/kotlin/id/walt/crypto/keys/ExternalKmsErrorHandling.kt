package id.walt.crypto.keys

import id.walt.crypto.exceptions.KeyCreationFailed
import id.walt.crypto.exceptions.KeyTypeNotSupportedException
import id.walt.crypto.exceptions.KeyVaultUnavailable

internal fun sanitizeExternalKmsError(message: String): String =
    message
        .replace(Regex("(?i)(client_secret|clientSecret|secret_id|secretId|secretAccessKey|accessKey|token|authorization)\"?\\s*[:=]\\s*\"?[^\"]+\"?")) {
            "${it.groupValues[1]}=<redacted>"
        }
        .replace(Regex("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]+")) {
            "${it.groupValues[1]}<redacted>"
        }
        .take(2_000)

internal fun externalKmsFailure(
    provider: String,
    operation: String,
    message: String,
    cause: Throwable? = null,
): Nothing = throw KeyVaultUnavailable("$provider $operation failed: ${sanitizeExternalKmsError(message)}", cause)

internal fun externalKmsGenerationFailure(
    provider: String,
    keyType: String,
    cause: Throwable,
): Nothing {
    if (cause is KeyTypeNotSupportedException) throw cause

    throw KeyCreationFailed(
        message = "Failed to generate $keyType key using $provider: ${
            sanitizeExternalKmsError(cause.message ?: cause::class.simpleName ?: "unknown error")
        }",
        cause = cause,
    )
}
