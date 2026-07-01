package id.walt.openid4vci.clientauth

data class ClientAuthenticationServiceConfig(
    val methods: List<ClientAuthenticationServiceMethod> = emptyList(),
    val allowedMethodsByEndpoint: Map<ClientAuthenticationEndpoint, Set<String>> = emptyMap(),
) {
    init {
        methods.forEach { method ->
            require(method.name.isNotBlank()) {
                "Client authentication method name must not be blank"
            }
        }

        val duplicateMethod = methods
            .groupingBy { it.name }
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }

        require(duplicateMethod == null) {
            "Duplicate client authentication method configured: ${duplicateMethod?.key}"
        }

        allowedMethodsByEndpoint.values.forEach { allowedMethods ->
            require(allowedMethods.isNotEmpty()) {
                "allowedMethodsByEndpoint entries must not be empty"
            }
        }
    }

    fun withMethod(method: ClientAuthenticationServiceMethod): ClientAuthenticationServiceConfig =
        copy(methods = methods + method)

    fun withDefaultAllowedMethodsByEndpoint(
        defaults: Map<ClientAuthenticationEndpoint, Set<String>>,
    ): ClientAuthenticationServiceConfig =
        if (allowedMethodsByEndpoint.isEmpty()) {
            copy(allowedMethodsByEndpoint = defaults)
        } else {
            this
        }

    fun allowsMethod(
        endpoint: ClientAuthenticationEndpoint,
        method: String,
    ): Boolean =
        allowedMethodsByEndpoint[endpoint]?.contains(method) ?: true
}
