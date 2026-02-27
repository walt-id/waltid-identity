package id.walt.openid4vci.handlers.endpoints.authorization

/**
 * Registry for authorize endpoint handlers.
 */
class AuthorizationEndpointHandlers(
    private val handlers: MutableList<AuthorizationEndpointHandler> = mutableListOf(),
) : Iterable<AuthorizationEndpointHandler> {

    fun append(handler: AuthorizationEndpointHandler) {
        handlers.add(handler)
    }

    fun count(): Int = handlers.size

    override fun iterator(): Iterator<AuthorizationEndpointHandler> = handlers.iterator()
}
