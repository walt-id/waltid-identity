package id.waltid.openid4vci.wallet.attestation

import id.walt.crypto2.jose.Jwk
import id.walt.crypto.keys.Key
import id.walt.crypto2.keys.EncodedKey
import id.waltid.openid4vci.wallet.proof.ProofBuilderUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val log = KotlinLogging.logger {}

@Serializable
data class AttestationObtainResponse(
    @SerialName("clientAttestationJwt")
    val clientAttestationJwt: String,
    @SerialName("expiresAt")
    val expiresAt: Long,
    @SerialName("storedAt")
    val storedAt: String? = null,
)

/**
 * Enterprise wallet attester provider.
 */
class HttpWalletAttestationProvider(
    private val baseUrl: String,
    private val attesterPath: String,
    private val bearerToken: String = "",
    private val hostHeader: String = "",
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    },
) : WalletAttestationProvider {

    private val mutex = Mutex()
    private var cachedKey: AttestationCacheKey? = null
    private var cachedJwt: String? = null
    private var cachedExpiresAt: Long = 0

    private val endpoint: String
        get() = "${baseUrl.trimEnd('/')}/v1/${attesterPath.trim()}/client-attester-api/attest"

    override suspend fun getAttestationJwt(instancePublicKeyJwk: EncodedKey.Jwk, clientId: String): String {
        mutex.withLock {
            val instanceKeyJwk = instancePublicKeyJwk.requirePublicJwk()
            val now = ProofBuilderUtils.currentTimestampSeconds()
            val cacheKey = AttestationCacheKey(clientId, Jwk.sha256Thumbprint(instancePublicKeyJwk))
            cachedJwt?.takeIf { cachedKey == cacheKey }?.let { jwt ->
                if (now < cachedExpiresAt - 60) {
                    log.debug { "Using cached attestation JWT (expires in ${cachedExpiresAt - now}s)" }
                    return jwt
                }
            }

            log.info { "Obtaining wallet attestation from $endpoint" }

            val requestBody = buildJsonObject {
                put("clientId", clientId)
                put("instancePublicKeyJwk", instanceKeyJwk)
            }

            val response = httpClient.post(endpoint) {
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
                bearerToken.trim().takeIf { it.isNotEmpty() }?.let {
                    header(HttpHeaders.Authorization, "Bearer $it")
                }
                hostHeader.trim().takeIf { it.isNotEmpty() }?.let {
                    header(HttpHeaders.Host, it)
                }
            }

            if (!response.status.isSuccess()) {
                error("Attestation request failed: ${response.status}")
            }

            val result = response.body<AttestationObtainResponse>()
            cachedKey = cacheKey
            cachedJwt = result.clientAttestationJwt
            cachedExpiresAt = result.expiresAt

            log.info { "Obtained attestation JWT (expires at ${result.expiresAt})" }
            return result.clientAttestationJwt
        }
    }

    @Deprecated("Use the EncodedKey.Jwk overload")
    override suspend fun getAttestationJwt(instanceKey: Key, clientId: String): String =
        getAttestationJwt(instanceKey.exportPublicCrypto2Jwk(), clientId)

    private data class AttestationCacheKey(
        val clientId: String,
        val publicKeyThumbprint: String,
    )
}
