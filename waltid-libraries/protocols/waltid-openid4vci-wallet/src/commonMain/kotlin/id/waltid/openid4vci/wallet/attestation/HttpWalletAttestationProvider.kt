package id.waltid.openid4vci.wallet.attestation

import id.walt.crypto.keys.Key
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

class HttpWalletAttestationProvider(
    private val enterpriseBaseUrl: String,
    private val attesterPath: String,
    private val bearerToken: String = "",
    private val enterpriseHostHeader: String = "",
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    },
) : WalletAttestationProvider {

    private val mutex = Mutex()
    private var cachedJwt: String? = null
    private var cachedExpiresAt: Long = 0

    private val endpoint: String
        get() = "${enterpriseBaseUrl.trimEnd('/')}/v1/${attesterPath.trim()}/client-attester-api/attest"

    override suspend fun getAttestationJwt(instanceKey: Key, clientId: String): String {
        mutex.withLock {
            val now = ProofBuilderUtils.currentTimestampSeconds()
            cachedJwt?.let { jwt ->
                if (now < cachedExpiresAt - 60) {
                    log.debug { "Using cached attestation JWT (expires in ${cachedExpiresAt - now}s)" }
                    return jwt
                }
            }

            log.info { "Obtaining wallet attestation from $endpoint" }

            val instanceKeyJwk = Json.parseToJsonElement(instanceKey.getPublicKey().exportJWK()) as JsonObject
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
                enterpriseHostHeader.trim().takeIf { it.isNotEmpty() }?.let {
                    header(HttpHeaders.Host, it)
                }
            }

            if (!response.status.isSuccess()) {
                error("Attestation request failed: ${response.status}")
            }

            val result = response.body<AttestationObtainResponse>()
            cachedJwt = result.clientAttestationJwt
            cachedExpiresAt = result.expiresAt

            log.info { "Obtained attestation JWT (expires at ${result.expiresAt})" }
            return result.clientAttestationJwt
        }
    }
}
