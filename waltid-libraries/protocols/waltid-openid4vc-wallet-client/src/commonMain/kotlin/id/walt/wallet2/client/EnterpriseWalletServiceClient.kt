package id.walt.wallet2.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class WalletClientAttestationView(
    @SerialName("clientAttestationJwt")
    val clientAttestationJwt: String,
    @SerialName("expiresAt")
    val expiresAt: Long,
    @SerialName("attesterServiceRef")
    val attesterServiceRef: String,
)

@Serializable
data class WalletClientAttestationObtainResult(
    @SerialName("clientAttestationJwt")
    val clientAttestationJwt: String,
    @SerialName("expiresAt")
    val expiresAt: Long,
    @SerialName("storedAt")
    val storedAt: String,
)

/** HTTP client for the walt.id Enterprise wallet-service API (attestation, receive, present). */
class EnterpriseWalletServiceClient(
    private val environment: WalletClientEnvironment,
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    },
) {
    suspend fun getCurrentAttestation(): WalletClientAttestationView =
        httpClient.get("${environment.normalizedBaseUrl()}/v1/${environment.walletPath.trim()}/wallet-service-api/client-attestation/current") {
            applyEnterpriseHeaders()
        }.body()

    suspend fun obtainAttestation(request: JsonObject): WalletClientAttestationObtainResult =
        httpClient.post("${environment.normalizedBaseUrl()}/v1/${environment.walletPath.trim()}/wallet-service-api/client-attestation/obtain") {
            applyEnterpriseHeaders()
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun receivePreAuthorized(
        offerUrl: String,
        keyReference: String,
    ): JsonElement =
        httpClient.post("${environment.normalizedBaseUrl()}/v2/${environment.walletPath.trim()}/wallet-service-api/credentials/receive/pre-authorized") {
            applyEnterpriseHeaders()
            contentType(ContentType.Application.Json)
            setBody(
                EnterpriseWalletReceiveRequest(
                    offerUrl = offerUrl,
                    keyReference = keyReference,
                    runPolicies = false,
                    useClientAttestation = true,
                )
            )
        }.body()

    suspend fun present(
        requestUrl: String,
        keyReference: String,
    ): JsonElement =
        httpClient.post("${environment.normalizedBaseUrl()}/v1/${environment.walletPath.trim()}/wallet-service-api/credentials/present") {
            applyEnterpriseHeaders()
            contentType(ContentType.Application.Json)
            setBody(
                EnterpriseWalletPresentRequest(
                    requestUrl = requestUrl,
                    keyReference = keyReference,
                )
            )
        }.body()

    private fun HttpRequestBuilder.applyEnterpriseHeaders() {
        environment.enterpriseHostHeader.trim().takeIf { it.isNotEmpty() }?.let {
            header(HttpHeaders.Host, it)
        }
        environment.bearerToken.trim().takeIf { it.isNotEmpty() }?.let {
            header(HttpHeaders.Authorization, "Bearer $it")
        }
    }
}

@Serializable
private data class EnterpriseWalletReceiveRequest(
    val offerUrl: String,
    val keyReference: String,
    val runPolicies: Boolean,
    val useClientAttestation: Boolean,
)

@Serializable
private data class EnterpriseWalletPresentRequest(
    val requestUrl: String,
    val keyReference: String,
)
