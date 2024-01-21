package id.walt.did.dids.resolver

import id.walt.crypto.keys.Key
import id.walt.did.utils.KeyMaterial
import id.walt.did.utils.VerificationMaterial
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class UniresolverResolver : DidResolver {
    @Suppress("MemberVisibilityCanBePrivate")
    //var resolverUrl = "http://localhost:8080/1.0"
    var resolverUrl = "https://dev.uniresolver.io/1.0"


    override val name = "uniresolver @ $resolverUrl"

    override suspend fun getSupportedMethods() = runCatching { lazyOf(getMethods()).value }

    private val http = HttpClient {
        install(ContentNegotiation) {
            json()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30 * 1000
        }
    }

    override suspend fun resolve(did: String): Result<JsonObject> =
        runCatching { http.get("$resolverUrl/identifiers/$did").body() }

    override suspend fun resolveToKey(did: String): Result<Key> = resolve(did).fold(
        onSuccess = {
            VerificationMaterial.get(it)?.let {
                KeyMaterial.get(it)
            } ?: Result.failure(Exception("No verification material found."))
        },
        onFailure = {
            Result.failure(it)
        }
    )

    private suspend fun getMethods(): Set<String> =
        http.get("$resolverUrl/methods") { }
            .body<JsonArray>()
            .map { it.jsonPrimitive.content }
            .toSet()

    private suspend fun scrapeMethods(): List<String> =
        http.get("https://raw.githubusercontent.com/decentralized-identity/universal-resolver/main/README.md")
            .bodyAsText().lines()
            .filter { it.trim().startsWith("| [") }
            .map { it.removePrefix("| [").substringBefore("]").removePrefix("did-") }
}