package id.walt.crypto.exceptions

object ExternalKmsError {
    private const val REDACTED = "<redacted>"
    private const val MAX_MESSAGE_LENGTH = 2_000

    private val secretAssignmentPattern =
        Regex("(?i)(client_secret|clientSecret|secret_id|secretId|secretAccessKey|accessKey|token|authorization)\"?\\s*[:=]\\s*\"?[^\"]+\"?")
    private val bearerTokenPattern = Regex("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]+")

    internal fun sanitize(message: String): String =
        message
            .replace(secretAssignmentPattern) {
                "${it.groupValues[1]}=$REDACTED"
            }
            .replace(bearerTokenPattern) {
                "${it.groupValues[1]}$REDACTED"
            }
            .take(MAX_MESSAGE_LENGTH)

    internal fun requestFailed(
        provider: String,
        operation: String,
        message: String,
        cause: Throwable? = null,
    ): Nothing = throw KeyVaultUnavailable("$provider $operation failed: ${sanitize(message)}", cause)

    internal fun generationFailed(
        provider: String,
        keyType: String,
        cause: Throwable,
    ): Nothing {
        if (cause is KeyTypeNotSupportedException) throw cause

        throw KeyCreationFailed(
            message = "Failed to generate $keyType key using $provider: ${sanitize(cause.safeMessage())}",
            cause = cause,
        )
    }

    private fun Throwable.safeMessage(): String =
        message ?: this::class.simpleName ?: "unknown error"
}
