package id.walt.openid4vci

/**
 * Registry for token endpoint handlers
 */
class TokenEndpointHandlers internal constructor(
    private val handlers: MutableList<TokenEndpointHandler>
) : Iterable<TokenEndpointHandler> {

    constructor() : this(mutableListOf())

    fun append(handler: TokenEndpointHandler) {
        if (handlers.any { it::class == handler::class }) {
            return
        }
        handlers += handler
    }

    fun toList(): List<TokenEndpointHandler> = handlers.toList()

    override fun iterator(): Iterator<TokenEndpointHandler> = handlers.iterator()
}
