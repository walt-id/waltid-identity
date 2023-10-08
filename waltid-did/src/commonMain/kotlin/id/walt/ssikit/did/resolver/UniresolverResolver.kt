package id.walt.ssikit.did.resolver

import id.walt.core.crypto.keys.Key
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

    override suspend fun resolve(did: String): Result<JsonObject> = runCatching { http.get("$resolverUrl/identifiers/$did").body() }

    override suspend fun resolveToKey(did: String): Result<Key> {
        TODO("Not yet implemented")
    }
}

suspend fun main() {
    println(UniresolverResolver().getSupportedMethods() ?: "Resolver disabled")
    println(UniresolverResolver().resolve("did:key:z6Mkfriq1MqLBoPWecGoDLjguo1sB9brj6wT3qZ5BxkKpuP6"))
    println(UniresolverResolver().resolveToKey("did:key:z6Mkfriq1MqLBoPWecGoDLjguo1sB9brj6wT3qZ5BxkKpuP6"))
}
