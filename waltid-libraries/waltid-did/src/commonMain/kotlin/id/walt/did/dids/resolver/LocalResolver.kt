package id.walt.did.dids.resolver

import id.walt.crypto.keys.Key
import id.walt.did.dids.DidUtils.methodFromDid
import id.walt.did.dids.resolver.local.*
import id.walt.webdatafetching.WebDataFetcher
import kotlinx.serialization.json.JsonObject
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class LocalResolver : DidResolver {
    override val name = "walt.id local resolver"

    // Shared WebDataFetcher: the HTTP engine is selected per-platform (Java on JVM => TLS 1.3,
    // Darwin on iOS/macOS, etc.) instead of relying on engine-less HttpClient {} auto-discovery,
    // which could resolve to CIO (TLS 1.2 only, and unsupported on Kotlin/Native).
    private val fetcher = WebDataFetcher(id = "did-local-resolver")

    private val resolvers = listOf(
        DidJwkResolver(),
        DidWebResolver(fetcher),
        DidKeyResolver(),
        DidEbsiResolver(fetcher),
        DidCheqdResolver(fetcher)
    ).associateBy { it.method }.toMutableMap()

    fun deactivateMethod(method: String) {
        resolvers.remove(method)
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun getSupportedMethods(): Result<Set<String>> = Result.success(resolvers.keys)

    private fun getResolverForDid(did: String): LocalResolverMethod {
        val method = methodFromDid(did)
        return resolvers[method] ?: throw IllegalArgumentException("Local resolver has no resolver for method: $did")
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun resolve(did: String): Result<JsonObject> =
        getResolverForDid(did).resolve(did).map { it.toJsonObject() }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun resolveToKey(did: String): Result<Key> =
        getResolverForDid(did).resolveToKey(did)

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun resolveToKeys(did: String): Result<Set<Key>> =
        getResolverForDid(did).resolveToKeys(did)
}
