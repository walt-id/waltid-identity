package id.walt.errors

/**
 * An exception that names the HTTP status a service answers it with ([HttpStatusError]).
 *
 * For libraries that report errors a service turns into responses (not found, conflict, unauthorized, ...) without
 * depending on the service layer.
 */
open class StatusException(
    override val status: Int,
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause), HttpStatusError
