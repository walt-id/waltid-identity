package id.walt.openid4vci

/**
 * Registry for authorize endpoint handlers
 */
class AuthorizeEndpointHandlers internal constructor(
    private val handlers: MutableList<AuthorizeEndpointHandler>,
) : Iterable<AuthorizeEndpointHandler> {

    constructor() : this(mutableListOf())

    fun append(handler: AuthorizeEndpointHandler) {
        if (handlers.any { it::class == handler::class }) {
            return
        }
        handlers += handler
    }

    override fun iterator(): Iterator<AuthorizeEndpointHandler> = handlers.iterator()
}
