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
import io.ktor.http.encodeURLPathPart
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

internal class WalletApi2Exception(
    val status: HttpStatusCode,
    override val message: String,
) : Exception(message)

internal class WalletApi2Client(
    private val baseUrl: String,
    private val token: String,
    private val kind: WalletApiKind = WalletApiKind.OpenSource,
    private val http: HttpClient = authenticatedHttpClient(baseUrl, token),
) {
    suspend fun listWallets(): List<String> {
        require(kind.canManageWallet) { "This API does not list wallets directly" }
        return request { get("/wallet") }.body()
    }

    suspend fun listAccessibleWallets(): List<String> = when (kind) {
        WalletApiKind.OpenSource -> listWallets()
        WalletApiKind.Enterprise -> wallet2TargetsFromResourceTree(
            request { get(EnterpriseResourceTreePath) }.body(),
        )
    }

    suspend fun createWallet(): String {
        require(kind.canManageWallet) { "This API does not create wallets" }
        return request(HttpStatusCode.Created) { post("/wallet") { jsonBody(CreateWalletRequestDto()) } }
            .body<WalletCreatedResponse>()
            .walletId
    }

    suspend fun walletInfo(walletId: String): WalletInfoResponse =
        request { get(walletPath(walletId)) }.body()

    suspend fun deleteWallet(walletId: String) {
        require(kind.canManageWallet) { "This API does not delete wallets" }
        request(HttpStatusCode.NoContent) { delete(walletPath(walletId)) }
    }

    suspend fun generateKey(walletId: String): WalletKeyInfo {
        require(kind.canGenerateIdentity) { "This API does not generate keys" }
        return request(HttpStatusCode.Created) {
            post(walletPath(walletId, "keys/generate")) {
                jsonBody(GenerateKeyRequest(backend = "jwk", keyType = "secp256r1"))
            }
        }.body()
    }

    suspend fun listKeys(walletId: String): List<WalletKeyInfo> =
        request { get(walletPath(walletId, "keys")) }.body()

    suspend fun deleteKey(walletId: String, keyId: String) {
        require(kind.canGenerateIdentity) { "This API does not delete keys" }
        request(HttpStatusCode.NoContent) { delete(walletPath(walletId, "keys/$keyId")) }
    }

    suspend fun deleteDid(walletId: String, did: String) {
        require(kind.canGenerateIdentity) { "This API does not delete DIDs" }
        request(HttpStatusCode.NoContent) { delete(walletPath(walletId, "dids/$did")) }
    }

    suspend fun setDefaultKey(walletId: String, keyId: String) {
        require(kind.canGenerateIdentity) { "This API does not set a default key" }
        request(HttpStatusCode.NoContent) { put(walletPath(walletId, "keys/$keyId/set-default")) }
    }

    suspend fun createDid(walletId: String, keyId: String): WalletDidEntry {
        require(kind.canGenerateIdentity) { "This API does not create DIDs" }
        return request(HttpStatusCode.Created) {
            post(walletPath(walletId, "dids/create")) {
                jsonBody(CreateDidRequest(method = "jwk", keyId = keyId))
            }
        }.body()
    }

    suspend fun listDids(walletId: String): List<WalletDidEntry> =
        request { get(walletPath(walletId, "dids")) }.body()

    suspend fun setDefaultDid(walletId: String, did: String) {
        require(kind.canGenerateIdentity) { "This API does not set a default DID" }
        request(HttpStatusCode.NoContent) { put(walletPath(walletId, "dids/$did/set-default")) }
    }

    suspend fun listCredentialMetadata(walletId: String): List<StoredCredentialMetadataDto> =
        request { get(walletPath(walletId, "credentials")) }.body()

    suspend fun getCredential(walletId: String, credentialId: String): JsonObject =
        walletApi2Json.parseToJsonElement(
            request { get(walletPath(walletId, "credentials/$credentialId")) }.bodyAsText(),
        ).jsonObject

    suspend fun deleteCredential(walletId: String, credentialId: String): Boolean {
        require(kind.canDeleteCredential) { "This API does not delete credentials" }
        val response = http.delete(walletPath(walletId, "credentials/$credentialId"))
        return when (response.status) {
            HttpStatusCode.NoContent -> true
            HttpStatusCode.NotFound -> false
            else -> throw response.toApiException()
        }
    }

    suspend fun resolveOffer(walletId: String, offerUrl: String): ResolveBatchOfferResponseDto =
        request {
            post(walletPath(walletId, "credentials/receive/resolve-offer/batch")) {
                jsonBody(OfferUrlRequest(offerUrl))
            }
        }.body()

    suspend fun receivePreAuthorized(
        walletId: String,
        offerUrl: String,
        txCode: String?,
        did: String?,
        redirectUri: String,
        credentials: List<IssuanceCredentialSelectionDto>,
        keyId: String? = null,
    ): ReceiveCredentialResultDto =
        receiveResult {
            post(walletPath(walletId, "credentials/receive")) {
                jsonBody(
                    ReceiveCredentialRequestDto(
                        offerUrl = offerUrl,
                        txCode = txCode,
                        did = did,
                        redirectUri = redirectUri,
                        credentials = credentials,
                        keyId = keyId,
                    ),
                )
            }
        }

    suspend fun authorizationUrl(
        walletId: String,
        offerUrl: String,
        redirectUri: String,
        credentialConfigurationIds: List<String>,
    ): GenerateAuthorizationUrlResultDto =
        request {
            post(walletPath(walletId, "credentials/receive/authorization-url/batch")) {
                jsonBody(
                    GenerateAuthorizationUrlRequestDto(
                        offerUrl = offerUrl,
                        redirectUri = redirectUri,
                        credentialConfigurationIds = credentialConfigurationIds,
                    ),
                )
            }
        }.body()

    suspend fun receiveAuthorized(
        walletId: String,
        request: ReceiveAuthorizedCredentialRequestDto,
    ): ReceiveCredentialResultDto =
        receiveResult {
            post(walletPath(walletId, "credentials/receive/authorized/batch")) { jsonBody(request) }
        }

    suspend fun listDeferred(walletId: String): List<DeferredCredentialHandleDto> =
        request { get(walletPath(walletId, "credentials/receive/deferred")) }.body()

    suspend fun resumeDeferred(walletId: String, deferredCredentialId: String): DeferredIssuanceOutcomeDto =
        request {
            post(walletPath(walletId, "credentials/receive/deferred/${deferredCredentialId.encodeURLPathPart()}"))
        }.body()

    suspend fun rejectIssuedCredential(
        walletId: String,
        request: RejectIssuedCredentialRequestDto,
    ) {
        request(HttpStatusCode.NoContent) {
            post("/wallet/$walletId/credentials/receive/reject") { jsonBody(request) }
        }
    }

    private suspend fun receiveResult(block: suspend HttpClient.() -> HttpResponse): ReceiveCredentialResultDto {
        val response = http.block()
        if (response.status.isSuccess()) return response.body()
        val body = response.bodyAsText()
        if (response.status.value in setOf(422, 500, 502)) {
            val result = runCatching { walletApi2Json.decodeFromString<ReceiveCredentialResultDto>(body) }.getOrNull()
            if (result?.failure != null) return result
        }
        throw WalletApi2Exception(response.status, "Wallet API ${response.status.value}: ${body.ifBlank { response.status.description }}")
    }

    suspend fun previewPresentation(
        walletId: String,
        requestUrl: String,
        keyId: String?,
    ): PresentationPreviewResponseDto =
        request {
            post(walletPath(walletId, "credentials/present/preview")) {
                jsonBody(PreviewPresentationRequestDto(requestUrl = requestUrl, keyId = keyId))
            }
        }.body()

    suspend fun buildVpToken(walletId: String, request: BuildVpTokenRequestDto): BuildVpTokenResultDto =
        request {
            post(walletPath(walletId, "credentials/present/build-vp-token")) { jsonBody(request) }
        }.body()

    suspend fun sendPresentationResponse(
        walletId: String,
        request: SendAuthorizationResponseRequestDto,
    ): WalletPresentResultDto =
        request {
            post(walletPath(walletId, "credentials/present/send-response")) { jsonBody(request) }
        }.body()

    suspend fun rejectPresentation(
        walletId: String,
        requestUrl: String,
    ): WalletPresentResultDto =
        request {
            post(walletPath(walletId, "credentials/present/reject")) {
                jsonBody(RejectPresentationRequestDto(requestUrl = requestUrl))
            }
        }.body()

    suspend fun present(
        walletId: String,
        requestUrl: String,
        did: String?,
        keyId: String? = null,
    ): WalletPresentResultDto =
        request {
            post(walletPath(walletId, "credentials/present")) {
                jsonBody(PresentCredentialRequestDto(requestUrl = requestUrl, did = did, keyId = keyId))
            }
        }.body()

    private fun walletPath(walletId: String, operation: String = "") =
        walletApiOperationPath(kind, walletId, operation)

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
        val element = walletApi2Json.encodeToJsonElement(body)
        setBody(if (element is JsonObject) adaptWalletRequestBody(kind, element) else element)
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
