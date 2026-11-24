package id.walt.crypto2.kms.csc

import id.walt.crypto2.kms.CredentialReference
import id.walt.crypto2.kms.KmsProviderException
import id.walt.crypto2.kms.executeJson
import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.formUrlEncode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * OAuth 2.0 client-credentials tokens for CSC service authorization (`authType` `oauth2client`), cached until expiry.
 * Each token endpoint and credential reference has its own lock, so a slow token endpoint delays only its own keys.
 */
internal class CscAccessTokens(
    private val client: HttpClient,
    private val now: () -> Instant,
) {
    private val mutex = Mutex()
    private val entries = mutableMapOf<TokenKey, Entry>()

    suspend fun accessToken(
        tokenEndpoint: String,
        reference: CredentialReference,
        credentials: CscCredentials,
    ): String {
        val entry = mutex.withLock { entries.getOrPut(TokenKey(tokenEndpoint, reference.value)) { Entry() } }
        return entry.lock.withLock {
            entry.token?.takeIf { it.expiresAt > now() + 30.seconds }?.value
                ?: fetch(tokenEndpoint, credentials).also { entry.token = it }.value
        }
    }

    /**
     * Drops [token] after the RSSP rejected it, so the next call fetches a new one. A token that another call has
     * already refreshed stays cached.
     */
    suspend fun invalidate(tokenEndpoint: String, reference: CredentialReference, token: String) {
        val entry = mutex.withLock { entries[TokenKey(tokenEndpoint, reference.value)] } ?: return
        entry.lock.withLock { if (entry.token?.value == token) entry.token = null }
    }

    private suspend fun fetch(tokenEndpoint: String, credentials: CscCredentials): CachedToken {
        val response = client.executeJson(
            provider = "CSC authorization server",
            endpoint = tokenEndpoint,
            method = HttpMethod.Post,
            contentType = ContentType.Application.FormUrlEncoded,
            body = Parameters.build {
                append("grant_type", "client_credentials")
                append("client_id", credentials.clientId)
                append("client_secret", credentials.clientSecret)
            }.formUrlEncode(),
        ) ?: throw KmsProviderException("CSC authorization server returned an empty token response")
        val token = (response["access_token"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?.takeIf(String::isNotBlank)
            ?: throw KmsProviderException("CSC token response is missing access_token")
        val expiresIn = response["expires_in"]?.let {
            (it as? JsonPrimitive)?.intOrNull ?: throw KmsProviderException("CSC token expires_in is not an integer")
        } ?: DEFAULT_LIFETIME_SECONDS
        if (expiresIn <= 0) throw KmsProviderException("CSC token lifetime must be positive")
        return CachedToken(token, now() + expiresIn.seconds)
    }

    private data class TokenKey(val tokenEndpoint: String, val credentialReference: String)
    private class CachedToken(val value: String, val expiresAt: Instant)

    /** Guarded by [lock]. */
    private class Entry {
        val lock = Mutex()
        var token: CachedToken? = null
    }

    private companion object {
        /** CSC API v2.2: "If omitted, the default expiration time is 3600". */
        const val DEFAULT_LIFETIME_SECONDS = 3600
    }
}
