package id.walt.errors

/**
 * An exception that names the HTTP status a service answers it with.
 *
 * For libraries that report errors a service turns into responses (not found, conflict, unauthorized, ...) without
 * depending on the service layer; the services' status pages answer every subclass with its [status].
 */
open class StatusException(
    val status: Int,
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
