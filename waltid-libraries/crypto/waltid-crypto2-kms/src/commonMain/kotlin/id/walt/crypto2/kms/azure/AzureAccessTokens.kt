package id.walt.crypto2.kms.azure

import id.walt.crypto2.kms.executeJson
import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.encodeURLPathPart
import io.ktor.http.formUrlEncode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

fun interface AzureAccessTokenProvider {
    suspend fun getAccessToken(options: AzureKeyVaultOptions): String
}

class AzureClientSecretTokenProvider(
    private val client: HttpClient,
    private val credentialResolver: AzureCredentialResolver,
    private val now: () -> Instant = { Clock.System.now() },
) : AzureAccessTokenProvider {
    private val mutex = Mutex()
    private val tokens = mutableMapOf<TokenKey, CachedToken>()

    override suspend fun getAccessToken(options: AzureKeyVaultOptions): String {
        val key = TokenKey(options.authorityUrl, options.tenantId, options.credentialReference.value)
        return mutex.withLock {
            tokens[key]?.takeIf { it.expiresAt > now() + 30.seconds }?.value ?: fetch(options).also {
                tokens[key] = it
            }.value
        }
    }

    private suspend fun fetch(options: AzureKeyVaultOptions): CachedToken {
        val credential = credentialResolver.resolve(options.credentialReference)
        val body = Parameters.build {
            append("client_id", credential.clientId)
            append("client_secret", credential.clientSecret)
            append("grant_type", "client_credentials")
            append("scope", "https://vault.azure.net/.default")
        }.formUrlEncode()
        val response = requireNotNull(
            client.executeJson(
                provider = "Azure identity",
                endpoint = "${options.authorityUrl.trimEnd('/')}/${options.tenantId.encodeURLPathPart()}/oauth2/v2.0/token",
                method = HttpMethod.Post,
                contentType = ContentType.Application.FormUrlEncoded,
                body = body,
            )
        )
        val token = response["access_token"]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
            ?: error("Azure identity response is missing access_token")
        val expiresIn = response["expires_in"]?.jsonPrimitive?.int ?: 3600
        require(expiresIn > 0) { "Azure token lifetime must be positive" }
        return CachedToken(token, now() + expiresIn.seconds)
    }

    private data class TokenKey(val authorityUrl: String, val tenantId: String, val credentialReference: String)
    private data class CachedToken(val value: String, val expiresAt: Instant)
}
