package id.walt.openid4vci.handlers.endpoints.credential

class CredentialEndpointHandlers(
    private val handlersByFormat: MutableMap<String, CredentialEndpointHandler> = mutableMapOf(),
) {
    fun register(format: String, handler: CredentialEndpointHandler) {
        handlersByFormat[format] = handler
    }

    fun get(format: String): CredentialEndpointHandler? = handlersByFormat[format]
}
