package id.walt.crypto.keys.jwk

import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * A provider that fetches, caches, and resolves JSON Web Keys (JWKs) from a JWKS endpoint URL.
 * It is thread-safe and caches the key set in memory to avoid repeated network requests.
 *
 * The default [WebDataFetcherId.JWKS_KEY_PROVIDER] client bounds response size and request time.
 * On JVM and Android it checks destination addresses before connecting. A caller supplying a
 * client owns its destination policy and JSON configuration.
 *
 * @param jwksUri The URL of the JWKS endpoint.
 */
class JwkKeyProvider(
    private val jwksUri: String,
    private val httpClient: HttpClient,
) {
    constructor(jwksUri: String) : this(jwksUri, defaultHttpClient)

    private companion object {
        val defaultHttpClient: HttpClient by lazy { WebDataFetcher(WebDataFetcherId.JWKS_KEY_PROVIDER).httpClient }
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

            val response = runCatching {
                val document = httpClient.get(jwksUri)
                check(document.status.isSuccess()) { "JWKS endpoint returned HTTP ${document.status.value}" }
                document.body<JsonObject>()
            }.getOrElse { throw IllegalArgumentException("Could not retrieve the JWKS document", it) }
            val keysArray = response["keys"] as? JsonArray
                ?: throw IllegalStateException("JWKS response does not contain a 'keys' array.")

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
