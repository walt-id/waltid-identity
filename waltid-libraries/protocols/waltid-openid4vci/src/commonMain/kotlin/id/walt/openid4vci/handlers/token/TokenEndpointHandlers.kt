package id.walt.openid4vci.handlers.token

import id.walt.openid4vci.GrantType

/**
 * Registry of token endpoint handlers keyed by grant type.
 */
class TokenEndpointHandlers(
    private val handlersByGrant: MutableMap<String, MutableList<TokenEndpointHandler>> = mutableMapOf(),
) {
    fun appendForGrant(grantType: GrantType, handler: TokenEndpointHandler) {
        val list = handlersByGrant.getOrPut(grantType.value) { mutableListOf() }
        if (list.any { it::class == handler::class }) {
            throw IllegalStateException("Duplicate handler for grant type ${grantType.value}")
        }
        list += handler
    }

    fun forRequestGrantTypes(grantTypes: Set<String>): Sequence<TokenEndpointHandler> =
        grantTypes.asSequence()
            .flatMap { handlersByGrant[it].orEmpty().asSequence() }

    fun toList(): List<TokenEndpointHandler> =
        handlersByGrant.values.flatten()

    fun count(): Int = handlersByGrant.values.sumOf { it.size }
}
