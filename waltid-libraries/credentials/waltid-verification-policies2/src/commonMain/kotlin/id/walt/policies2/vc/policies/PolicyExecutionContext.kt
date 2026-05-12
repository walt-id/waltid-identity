package id.walt.policies2.vc.policies

/**
 * Keys for services that can be injected into credential policy execution.
 *
 * Enterprise (or other) integrations register service callbacks under these keys via
 * [PolicyExecutionContext] and pass the context down through the verifier pipeline to
 * [CredentialVerificationPolicy2.verify]. Policies that need an injected service look
 * it up by key; policies that do not simply ignore the context.
 */
enum class PolicyServiceKey {
    /** Key for a [TrustRegistryServiceResolver] used by [ETSITrustListPolicy]. */
    TRUST_REGISTRY_RESOLVER
}

/**
 * Per-request context passed to [CredentialVerificationPolicy2.verify].
 *
 * Allows callers of the verification pipeline (e.g. an enterprise verifier) to inject
 * service callbacks that individual policies may consume without requiring global
 * mutable state, coroutine context elements, or other side channels.
 *
 * Policies that do not need any injected service can ignore this parameter.
 */
data class PolicyExecutionContext(
    val services: Map<PolicyServiceKey, Any> = emptyMap()
) {
    /**
     * Returns the service registered under [key] cast to [T], or `null` if no service
     * is registered or the registered value is not of the expected type.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getService(key: PolicyServiceKey): T? = services[key] as? T?

    companion object {
        /** An empty context with no registered services. */
        val Empty = PolicyExecutionContext()
    }
}
