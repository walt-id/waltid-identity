package id.walt.openid4vci.clientauth

data class ClientAuthenticationServiceConfig(
    val methods: List<ClientAuthenticationServiceMethod> = emptyList(),
    val methodsByEndpoint: Map<ClientAuthenticationEndpoint, Set<String>> = emptyMap(),
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

        methodsByEndpoint.values.forEach { methods ->
            require(methods.isNotEmpty()) {
                "methodsByEndpoint entries must not be empty"
            }
            require(methods.none { it.isBlank() }) {
                "methodsByEndpoint method names must not be blank"
            }
        }
    }

    fun withMethod(method: ClientAuthenticationServiceMethod): ClientAuthenticationServiceConfig =
        copy(methods = methods + method)

    fun withDefaultMethodsByEndpoint(
        defaults: Map<ClientAuthenticationEndpoint, Set<String>>,
    ): ClientAuthenticationServiceConfig =
        if (methodsByEndpoint.isEmpty()) {
            copy(methodsByEndpoint = defaults)
        } else {
            this
        }

    fun methodsForEndpoint(endpoint: ClientAuthenticationEndpoint): Set<String> =
        methodsByEndpoint[endpoint].orEmpty()
}
