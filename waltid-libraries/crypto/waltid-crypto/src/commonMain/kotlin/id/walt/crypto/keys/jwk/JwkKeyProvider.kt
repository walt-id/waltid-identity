package id.walt.crypto.keys.jwk

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * A provider that fetches, caches, and resolves JSON Web Keys (JWKs) from a JWKS endpoint URL.
 * It is thread-safe and caches the key set in memory to avoid repeated network requests.
 *
 * @param jwksUri The URL of the JWKS endpoint.
 */
class JwkKeyProvider(private val jwksUri: String) {

    private val http = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private var cachedKeys: List<JWKKey>? = null
    private val mutex = Mutex()

    /**
     * Retrieves a specific key by its Key ID (kid) from the JWKS.
     * If the keys are not cached, it fetches them from the JWKS endpoint first.
     *
     * @param kid The Key ID of the key to retrieve.
     * @return The matching [JWKKey], or null if not found.
     */
    suspend fun getKey(kid: String): Result<JWKKey> {
        val keys = getKeys().getOrThrow()
        val key = keys.find { it.getKeyId() == kid }
        return when (key) {
            null -> Result.failure(NoSuchElementException("Did not find a key with key id \"$kid\" in JWKS."))
            else -> Result.success(key)
        }
    }

    /**
     * Fetches and parses all keys from the JWKS endpoint.
     * Uses double-checked locking to ensure the keys are only fetched once.
     *
     * @return A list of all [JWKKey] objects from the endpoint.
     */
    private suspend fun getKeys(): Result<List<JWKKey>> {
        cachedKeys?.let { return Result.success(it) }

        return mutex.withLock {
            // Double-checked locking pattern
            cachedKeys?.let { return Result.success(it) }

            val response = runCatching { http.get(jwksUri).body<JsonObject>() }
                .getOrElse { throw IllegalArgumentException("Could view keys list document at JWKS endpoint", it) }
            val keysArray = response["keys"] as? JsonArray
                ?: throw IllegalStateException("JWKS response from '$jwksUri' does not contain a 'keys' array.")

            runCatching {
                val parsedKeys = keysArray.mapNotNull { keyJson ->
                    JWKKey.importJWK(keyJson.toString()).getOrThrow()
                }
                cachedKeys = parsedKeys
                parsedKeys
            }
        }
    }
}
