package id.walt.did.dids.resolver

import id.walt.crypto.keys.Key
import id.walt.did.dids.DidUtils.methodFromDid
import id.walt.did.dids.resolver.local.DidJwkResolver
import id.walt.did.dids.resolver.local.DidKeyResolver
import id.walt.did.dids.resolver.local.DidWebResolver
import id.walt.did.dids.resolver.local.LocalResolverMethod
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.JsonObject

class LocalResolver : DidResolver {
    override val name = "core-crypto local resolver"
    private val http = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(DidWebResolver.json)
        }
    }

    private val resolvers = listOf(
        DidJwkResolver(),
        DidWebResolver(http),
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
