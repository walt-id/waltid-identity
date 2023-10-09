package id.walt.didlib.did.resolver

import id.walt.core.crypto.keys.Key
import id.walt.didlib.did.DidUtils.methodFromDid
import id.walt.didlib.did.resolver.local.DidJwkResolver
import id.walt.didlib.did.resolver.local.DidKeyResolver
import id.walt.didlib.did.resolver.local.DidWebResolver
import id.walt.didlib.did.resolver.local.LocalResolverMethod
import kotlinx.serialization.json.JsonObject

class LocalResolver : DidResolver {
    override val name = "core-crypto local resolver"

    private val resolvers = listOf(
        DidJwkResolver(),
        DidWebResolver(),
        DidKeyResolver()
    ).associateBy { it.method }.toMutableMap()

    fun deactivateMethod(method: String) {
        resolvers.remove(method)
    }

    override suspend fun getSupportedMethods(): Result<Set<String>> = Result.success(resolvers.keys)

    private fun getResolverForDid(did: String): LocalResolverMethod {
        val method = methodFromDid(did)
        return resolvers[method] ?: throw IllegalArgumentException("No resolver for method: $did")
    }

    override suspend fun resolve(did: String): Result<JsonObject> =
        getResolverForDid(did).resolve(did).map { it.toJsonObject() }

    override suspend fun resolveToKey(did: String): Result<Key> =
        getResolverForDid(did).resolveToKey(did)
}
