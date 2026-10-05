package id.walt.errors

/**
 * An error that names the HTTP status a service answers it with. The services' status pages answer every
 * [Throwable] implementing it with its [status].
 *
 * Throw [StatusException] or a subclass of it; implement this directly only where the exception needs another base
 * class, e.g. a serializable one.
 */
interface HttpStatusError {
    val status: Int
}
