package id.walt.walletdemo.compose.logic.walletapi2

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

internal class WalletApi2Exception(
    val status: HttpStatusCode,
    override val message: String,
) : Exception(message)

internal class WalletApi2Client(
    private val baseUrl: String,
    private val token: String,
    private val http: HttpClient = authenticatedHttpClient(baseUrl, token),
) {
    suspend fun listWallets(): List<String> =
        request { get("/wallet") }.body()

    suspend fun createWallet(): String =
        request(HttpStatusCode.Created) { post("/wallet") { jsonBody(CreateWalletRequestDto()) } }
            .body<WalletCreatedResponse>()
            .walletId

    suspend fun walletInfo(walletId: String): WalletInfoResponse =
        request { get("/wallet/$walletId") }.body()

    suspend fun deleteWallet(walletId: String) {
        request(HttpStatusCode.NoContent) { delete("/wallet/$walletId") }
    }

    suspend fun generateKey(walletId: String): WalletKeyInfo =
        request(HttpStatusCode.Created) {
            post("/wallet/$walletId/keys/generate") {
                jsonBody(GenerateKeyRequest(backend = "jwk", keyType = "secp256r1"))
            }
        }.body()

    suspend fun listKeys(walletId: String): List<WalletKeyInfo> =
        request { get("/wallet/$walletId/keys") }.body()

    suspend fun setDefaultKey(walletId: String, keyId: String) {
        request(HttpStatusCode.NoContent) { put("/wallet/$walletId/keys/$keyId/set-default") }
    }

    suspend fun createDid(walletId: String, keyId: String): WalletDidEntry =
        request(HttpStatusCode.Created) {
            post("/wallet/$walletId/dids/create") {
                jsonBody(CreateDidRequest(method = "jwk", keyId = keyId))
            }
        }.body()

    suspend fun listDids(walletId: String): List<WalletDidEntry> =
        request { get("/wallet/$walletId/dids") }.body()

    suspend fun setDefaultDid(walletId: String, did: String) {
        request(HttpStatusCode.NoContent) { put("/wallet/$walletId/dids/$did/set-default") }
    }

    suspend fun listCredentialMetadata(walletId: String): List<StoredCredentialMetadataDto> =
        request { get("/wallet/$walletId/credentials") }.body()

    suspend fun getCredential(walletId: String, credentialId: String): JsonObject =
        walletApi2Json.parseToJsonElement(
            request { get("/wallet/$walletId/credentials/$credentialId") }.bodyAsText(),
        ).jsonObject

    suspend fun deleteCredential(walletId: String, credentialId: String): Boolean {
        val response = http.delete("/wallet/$walletId/credentials/$credentialId")
        return when (response.status) {
            HttpStatusCode.NoContent -> true
            HttpStatusCode.NotFound -> false
            else -> throw response.toApiException()
        }
    }

    suspend fun resolveOffer(walletId: String, offerUrl: String): ResolveOfferDetailedResponseDto =
        request {
            post("/wallet/$walletId/credentials/receive/resolve-offer") {
                jsonBody(OfferUrlRequest(offerUrl))
            }
        }.body()

    suspend fun receivePreAuthorized(
        walletId: String,
        offerUrl: String,
        txCode: String?,
        did: String?,
        redirectUri: String,
    ): ReceiveCredentialResultDto =
        request {
            post("/wallet/$walletId/credentials/receive") {
                jsonBody(
                    ReceiveCredentialRequestDto(
                        offerUrl = offerUrl,
                        txCode = txCode,
                        did = did,
                        redirectUri = redirectUri,
                    ),
                )
            }
        }.body()

    suspend fun authorizationUrl(
        walletId: String,
        offerUrl: String,
        redirectUri: String,
    ): GenerateAuthorizationUrlResultDto =
        request {
            post("/wallet/$walletId/credentials/receive/authorization-url") {
                jsonBody(
                    GenerateAuthorizationUrlRequestDto(
                        offerUrl = offerUrl,
                        redirectUri = redirectUri,
                    ),
                )
            }
        }.body()

    suspend fun receiveAuthorized(
        walletId: String,
        request: ReceiveAuthorizedCredentialRequestDto,
    ): ReceiveCredentialResultDto =
        request {
            post("/wallet/$walletId/credentials/receive/authorized") { jsonBody(request) }
        }.body()

    suspend fun previewPresentation(
        walletId: String,
        requestUrl: String,
        keyId: String?,
    ): PresentationPreviewResponseDto =
        request {
            post("/wallet/$walletId/credentials/present/preview") {
                jsonBody(PreviewPresentationRequestDto(requestUrl = requestUrl, keyId = keyId))
            }
        }.body()

    suspend fun buildVpToken(walletId: String, request: BuildVpTokenRequestDto): BuildVpTokenResultDto =
        request {
            post("/wallet/$walletId/credentials/present/build-vp-token") { jsonBody(request) }
        }.body()

    suspend fun sendPresentationResponse(
        walletId: String,
        request: SendAuthorizationResponseRequestDto,
    ): WalletPresentResultDto =
        request {
            post("/wallet/$walletId/credentials/present/send-response") { jsonBody(request) }
        }.body()

    suspend fun rejectPresentation(
        walletId: String,
        requestUrl: String,
    ): WalletPresentResultDto =
        request {
            post("/wallet/$walletId/credentials/present/reject") {
                jsonBody(RejectPresentationRequestDto(requestUrl = requestUrl))
            }
        }.body()

    suspend fun present(walletId: String, requestUrl: String, did: String?): WalletPresentResultDto =
        request {
            post("/wallet/$walletId/credentials/present") {
                jsonBody(PresentCredentialRequestDto(requestUrl = requestUrl, did = did))
            }
        }.body()

    private suspend fun request(
        expected: HttpStatusCode? = null,
        block: suspend HttpClient.() -> HttpResponse,
    ): HttpResponse {
        val response = http.block()
        val ok = expected?.let { response.status == it } ?: response.status.isSuccess()
        if (!ok) throw response.toApiException()
        return response
    }

    private inline fun <reified T> HttpRequestBuilder.jsonBody(body: T) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun HttpResponse.toApiException(): WalletApi2Exception {
        val details = runCatching { bodyAsText() }.getOrNull().orEmpty().ifBlank { status.description }
        return WalletApi2Exception(status, "Wallet API ${status.value}: $details")
    }

    companion object {
        fun defaultHttpClient(baseUrl: String): HttpClient = createHttpClient(baseUrl, token = null)

        fun authenticatedHttpClient(baseUrl: String, token: String): HttpClient =
            createHttpClient(baseUrl, token)

        private fun createHttpClient(baseUrl: String, token: String?): HttpClient = HttpClient(Js) {
            expectSuccess = false
            install(ContentNegotiation) { json(walletApi2Json) }
            defaultRequest {
                url(baseUrl.trimEnd('/') + "/")
                accept(ContentType.Application.Json)
                if (!token.isNullOrBlank()) {
                    bearerAuth(token)
                }
            }
        }
    }
}
