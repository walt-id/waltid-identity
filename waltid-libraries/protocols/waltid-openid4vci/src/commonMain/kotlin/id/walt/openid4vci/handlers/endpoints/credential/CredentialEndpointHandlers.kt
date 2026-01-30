package id.walt.openid4vci.handlers.endpoints.credential

import id.walt.openid4vci.CredentialFormat

class CredentialEndpointHandlers(
    private val handlersByFormat: MutableMap<CredentialFormat, CredentialEndpointHandler> = mutableMapOf(),
) {
    fun register(format: CredentialFormat, handler: CredentialEndpointHandler) {
        handlersByFormat[format] = handler
    }

    fun get(format: CredentialFormat): CredentialEndpointHandler? = handlersByFormat[format]
}
