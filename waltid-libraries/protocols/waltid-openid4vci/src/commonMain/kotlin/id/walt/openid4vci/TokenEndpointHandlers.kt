package id.walt.openid4vci

/**
 * Registry for token endpoint handlers
 */
class TokenEndpointHandlers internal constructor(
    private val handlers: MutableList<TokenEndpointHandler>,
    private val grantTypeRegistry: MutableMap<String, TokenEndpointHandler>,
) : Iterable<TokenEndpointHandler> {

    constructor() : this(mutableListOf(), mutableMapOf())

    // Register a handler for a specific grant_type. Errors on duplicate grant registrations.
    fun appendForGrant(grantType: GrantType, handler: TokenEndpointHandler) {
        val normalized = grantType.value.lowercase()
        if (grantTypeRegistry.containsKey(normalized)) {
            error("Multiple token endpoint handlers registered for grant_type '${grantType.value}'")
        }
        if (handlers.none { it::class == handler::class }) {
            handlers += handler
        }
        grantTypeRegistry[normalized] = handler
    }

    fun toList(): List<TokenEndpointHandler> = handlers.toList()

    override fun iterator(): Iterator<TokenEndpointHandler> = handlers.iterator()
}
