package id.walt.did.dids.resolver

import id.walt.crypto.keys.Key
import id.walt.did.utils.KeyMaterial
import id.walt.did.utils.VerificationMaterial
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetchingConfiguration
import id.walt.webdatafetching.config.TimeoutConfiguration
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.statement.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger { }

/**
 * @param resolverUrl Resolver URL, e.g. "http://localhost:8080/1.0"
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class UniresolverResolver(var resolverUrl: String = DEFAULT_RESOLVER_URL) : DidResolver {

    companion object {
        const val DEFAULT_RESOLVER_URL = "https://dev.uniresolver.io/1.0"
    }

    override val name = "uniresolver @ $resolverUrl"

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun getSupportedMethods() = runCatching { lazyOf(getMethods()).value }

    private val fetcher = WebDataFetcher(
        id = "did-uniresolver",
        defaultConfiguration = WebDataFetchingConfiguration(
            timeouts = TimeoutConfiguration(requestTimeout = 30.seconds)
        )
    )

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun resolve(did: String): Result<JsonObject> =
        runCatching {
            fetcher.fetch<JsonObject>("$resolverUrl/identifiers/$did").body
        }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun resolveToKey(did: String): Result<Key> = resolveToKeys(did).map { keys ->
        keys.firstOrNull() ?: throw Exception("No verification material found.")
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun resolveToKeys(did: String): Result<Set<Key>> = resolve(did).fold(
        onSuccess = { jsonObject ->
            VerificationMaterial.getAll(jsonObject)?.let { materials ->
                val keys = materials.mapNotNull { material ->
                    KeyMaterial.get(material).getOrNull()
                }.toSet()

                if (keys.isNotEmpty()) {
                    Result.success(keys)
                } else {
                    Result.failure(Exception("Could not convert verification materials to keys."))
                }
            } ?: Result.failure(Exception("No verification material found."))
        },
        onFailure = {
            Result.failure(it)
        }
    )

    private suspend fun getMethods(): Set<String> =
        fetcher.fetch<JsonArray>("$resolverUrl/methods").body
            .map { it.jsonPrimitive.content }
            .toSet()

    private suspend fun scrapeMethods(): List<String> =
        fetcher.rawFetch("https://raw.githubusercontent.com/decentralized-identity/universal-resolver/main/README.md")
            .bodyAsText().lines()
            .filter { it.trim().startsWith("| [") }
            .map { it.removePrefix("| [").substringBefore("]").removePrefix("did-") }
}
