package id.walt.openid4vci.handlers.endpoints.par

/**
 * Registry for PAR endpoint handlers.
 */
class PushedAuthorizationEndpointHandlers(
    private val handlers: MutableList<PushedAuthorizationEndpointHandler> = mutableListOf(),
) : Iterable<PushedAuthorizationEndpointHandler> {

    fun append(handler: PushedAuthorizationEndpointHandler) {
        handlers.add(handler)
    }

    fun count(): Int = handlers.size

    override fun iterator(): Iterator<PushedAuthorizationEndpointHandler> = handlers.iterator()
}
