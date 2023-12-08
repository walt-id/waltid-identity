package id.walt.service

import id.walt.config.ConfigManager
import id.walt.config.RemoteWalletConfig
import id.walt.db.models.*
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.service.dids.DidsService
import id.walt.service.dto.LinkedWalletDataTransferObject
import id.walt.service.dto.WalletDataTransferObject
import id.walt.service.issuers.IssuerDataTransferObject
import id.walt.utils.JsonUtils.toJsonPrimitive
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URLDecoder
import java.nio.charset.Charset

class WalletKitWalletService(accountId: UUID, walletId: UUID) : WalletService(accountId, walletId) {

    private var token: Lazy<String> = lazy { runBlocking { auth() } }

    companion object {
        private val walletConfig = ConfigManager.getConfig<RemoteWalletConfig>()

        val http = HttpClient(CIO) {
            install(ContentNegotiation) { json() }
            // expectSuccess = false
            install(Logging) {
                level = LogLevel.BODY
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 2 * 60 * 1000
            }

            defaultRequest {
                url(walletConfig.remoteWallet)
            }
        }
    }

    private val userEmail: String by lazy {
        transaction {
            Accounts.select { Accounts.id eq this@WalletKitWalletService.walletId }
                .single()[Accounts.email]
        } ?: throw IllegalArgumentException("No such account: ${this.walletId}")
    }

    private fun HttpResponse.checkValid(resend: () -> HttpResponse): HttpResponse =
        if (status == HttpStatusCode.Unauthorized) {
            runBlocking { reauth() }
            resend.invoke()
        } else this

    private suspend fun authenticatedJsonGet(path: String, block: HttpRequestBuilder.() -> Unit = {}): HttpResponse =
        http.get(path) {
            bearerAuth(token.value)
            contentType(ContentType.Application.Json)
            block.invoke(this)
        }.checkValid { runBlocking { authenticatedJsonGet(path, block) } }

    private suspend fun authenticatedJsonDelete(path: String, block: HttpRequestBuilder.() -> Unit = {}): HttpResponse =
        http.delete(path) {
            bearerAuth(token.value)
            contentType(ContentType.Application.Json)
            block.invoke(this)
        }.checkValid { runBlocking { authenticatedJsonDelete(path, block) } }

    private suspend inline fun <reified T> authenticatedJsonPost(
        path: String,
        body: T,
        crossinline block: HttpRequestBuilder.() -> Unit = {}
    ): HttpResponse =
        http.post(path) {
            bearerAuth(token.value)
            contentType(ContentType.Application.Json)
            setBody(body)
            block.invoke(this)
        }.checkValid {
            runBlocking {
                http.post(path) {
                    bearerAuth(token.value)
                    contentType(ContentType.Application.Json)
                    setBody(body)
                    block.invoke(this)
                }
            }
        }

    @Serializable
    data class AuthRequest(val id: String, val password: String)

    private suspend fun reauth() {
        println("Token invalid - reauthenticating!")
        token = lazy { runBlocking { auth() } }
    }

    private suspend fun auth(): String =
        (http.post("/api/auth/login") {
            setBody(AuthRequest(userEmail, userEmail))
            contentType(ContentType.Application.Json)
        }.body<JsonObject>()["token"] ?: throw IllegalStateException("Could not login: $userEmail")
                ).jsonPrimitive.content

    /* WalletCredentials */

    override fun listCredentials() = runBlocking {
        authenticatedJsonGet("/api/wallet/credentials/list")
            .body<JsonObject>()["list"]!!.jsonArray.toList().map { WalletCredential(walletId, it.jsonObject["id"]!!.jsonPrimitive.content, it.toString(), null, Instant.DISTANT_PAST) }
    }

    override suspend fun listRawCredentials(): List<String> {
        TODO("Not yet implemented")
    }

    //private val prettyJson = Json { prettyPrint = true }

    override suspend fun deleteCredential(id: String) =
        authenticatedJsonDelete("/api/wallet/credentials/delete/$id").status.isSuccess()

    override suspend fun getCredential(credentialId: String) = WalletCredential(
        wallet = walletId,
        id = credentialId,
        document = listCredentials().first { it.parsedDocument?.get("id")?.jsonPrimitive?.content == credentialId }.toString(),
        disclosures = null,
        addedOn = Instant.DISTANT_PAST
    )

    override fun matchCredentialsByPresentationDefinition(presentationDefinition: PresentationDefinition): List<WalletCredential> {
        TODO("Not yet implemented")
    }
    /*prettyJson.encodeToString(*/
    //)
    /* override suspend fun getCredential(credentialId: String): String =
         authenticatedJsonGet("/api/wallet/credentials/$credentialId")
             .bodyAsText()*/


    private fun getQueryParams(url: String): Map<String, MutableList<String>> {
        val params: MutableMap<String, MutableList<String>> = HashMap()
        val urlParts = url.split("\\?".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        if (urlParts.size <= 1)
            return params

        val query = urlParts[1]
        for (param in query.split("&".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            val pair = param.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val key = URLDecoder.decode(pair[0], "UTF-8")
            var value = ""
            if (pair.size > 1) {
                value = URLDecoder.decode(pair[1], "UTF-8")
            }
            var values = params[key]
            if (values == null) {
                values = ArrayList()
                params[key] = values
            }
            values.add(value)
        }
        return params
    }


    /* SIOP */
    @Serializable
    data class PresentationResponse(
        val vp_token: String,
        val presentation_submission: String,
        val id_token: String?,
        val state: String?,
        val fulfilled: Boolean,
        val rp_response: String?
    )

    @Serializable
    data class SIOPv2Response(
        val vp_token: String,
        val presentation_submission: String,
        val id_token: String?,
        val state: String?
    )

    override suspend fun usePresentationRequest(request: String, did: String, selectedCredentialIds: List<String>, disclosures: Map<String, List<String>>?): Result<String?> {
        val decoded = URLDecoder.decode(request, Charset.defaultCharset())
        val queryParams = getQueryParams(decoded)
        val redirectUri = queryParams["redirect_uri"]?.first()
            ?: throw IllegalArgumentException("Could not get redirect_uri from request!")

        val sessionId = authenticatedJsonPost(
            "/api/wallet/presentation/startPresentation",
            mapOf("oidcUri" to request)
        ).bodyAsText()

        val presentableCredentials = authenticatedJsonGet("/api/wallet/presentation/continue") {
            url {
                parameters.apply {
                    append("sessionId", sessionId)
                    append("did", did)
                }
            }
        }.body<JsonObject>()["presentableCredentials"]!!.jsonArray

        val fulfillResponse = authenticatedJsonPost("/api/wallet/presentation/fulfill", presentableCredentials) {
            url {
                parameters.apply {
                    append("sessionId", sessionId)
                }
            }
        }

        val presentationResponse = fulfillResponse.body<PresentationResponse>()
        println("Presentation response: $presentationResponse")

        println("Posting to redirect uri: $redirectUri")
        val redirectUriResp = http.submitForm(redirectUri,
            formParameters = parameters {
                append("vp_token", presentationResponse.vp_token)
                append("presentation_submission", presentationResponse.presentation_submission)
                append("id_token", presentationResponse.id_token.toString())
                append("state", presentationResponse.state.toString())
            }
        )

        val redirect = redirectUriResp.headers[HttpHeaders.Location]

        return Result.success(redirect)
    }

    override suspend fun resolvePresentationRequest(request: String): String {
        return request
    }

    override suspend fun useOfferRequest(offer: String, did: String) {
        val sessionId = authenticatedJsonPost(
            "/api/wallet/issuance/startIssuerInitiatedIssuance",
            mapOf("oidcUri" to offer)
        ).bodyAsText()

        authenticatedJsonGet("/api/wallet/issuance/continueIssuerInitiatedIssuance") {
            url {
                parameters.apply {
                    append("sessionId", sessionId)
                    append("did", did)
                }
            }
        }
    }

    /* DIDs */

    override suspend fun createDid(method: String, args: Map<String, JsonPrimitive>): String {
        val createParams = mutableMapOf("method" to method.toJsonPrimitive())

        createParams.putAll(args.mapKeys {
            val k = it.key
            when {
                method == "ebsi" && k == "bearerToken" -> "didEbsiBearerToken"
                method == "ebsi" && k == "version" -> "didEbsiVersion"

                method == "web" && k == "domain" -> "didWebDomain"
                method == "web" && k == "path" -> "didWebPath"

                else -> it.key
            }
        })

        return authenticatedJsonPost("/api/wallet/did/create", createParams).bodyAsText()
    }

    override suspend fun listDids() = authenticatedJsonGet("/api/wallet/did/list")
        .body<List<WalletDid>>()

    override suspend fun loadDid(did: String) = authenticatedJsonGet("/api/wallet/did/$did")
        .body<JsonObject>()

    override suspend fun deleteDid(did: String) =
        authenticatedJsonDelete("/api/wallet/did/delete/$did").status.isSuccess()

    override suspend fun setDefault(did: String) = DidsService.makeDidDefault(walletId, did)


    /* Keys */

    override suspend fun loadKey(alias: String) = authenticatedJsonGet("/api/wallet/keys/$alias").body<JsonObject>()


    override suspend fun exportKey(alias: String, format: String, private: Boolean): String =
        authenticatedJsonPost(
            "/api/wallet/keys/export", mapOf(
                "keyAlias" to JsonPrimitive(alias),
                "format" to JsonPrimitive(format),
                "exportPrivate" to JsonPrimitive(private)
            ).toMutableMap()
        )
            .body<String>()

    override suspend fun listKeys() = authenticatedJsonGet("/api/wallet/keys/list")
        .body<JsonObject>()["list"]!!.jsonArray.map { Json.decodeFromJsonElement<SingleKeyResponse>(it) }

    override suspend fun generateKey(type: String) =
        authenticatedJsonPost("/api/wallet/keys/generate", type).body<String>()


    override suspend fun importKey(jwkOrPem: String) =
        authenticatedJsonPost("/api/wallet/keys/import", body = jwkOrPem)
            .body<String>()

    override suspend fun deleteKey(alias: String) =
        authenticatedJsonDelete("/api/wallet/keys/delete/$alias").status.isSuccess()


    fun addToHistory() {
        // data from
        // https://wallet.walt-test.cloud/api/wallet/issuance/info?sessionId=SESSION_ID
        // after taking up issuance offer
    }
// TODO
//fun infoAboutOfferRequest

    override fun getHistory(limit: Int, offset: Int): List<WalletOperationHistory> = transaction {
        WalletOperationHistories
            .select { WalletOperationHistories.account eq walletId }
            .orderBy(WalletOperationHistories.timestamp)
            .limit(10)
            .map { WalletOperationHistory(it) }
    }

    override suspend fun addOperationHistory(operationHistory: WalletOperationHistory) {
        transaction {
            WalletOperationHistories.insert {
                it[wallet] = walletId
                it[account] = accountId
                it[timestamp] = operationHistory.timestamp.toJavaInstant()
                it[operation] = operationHistory.operation
                it[data] = Json.encodeToString(operationHistory.data)
            }
        }
    }

    override suspend fun linkWallet(wallet: WalletDataTransferObject): LinkedWalletDataTransferObject =
        Web3WalletService.link(walletId, wallet)

    override suspend fun unlinkWallet(wallet: UUID) = Web3WalletService.unlink(walletId, wallet)

    override suspend fun getLinkedWallets(): List<LinkedWalletDataTransferObject> =
        Web3WalletService.getLinked(walletId)

    override suspend fun connectWallet(walletId: UUID) = Web3WalletService.connect(this.walletId, walletId)

    override suspend fun disconnectWallet(wallet: UUID) = Web3WalletService.disconnect(walletId, wallet)

    override suspend fun listIssuers(): List<IssuerDataTransferObject> {
        TODO("Not yet implemented")
    }

    override suspend fun getIssuer(name: String): IssuerDataTransferObject {
        TODO("Not yet implemented")
    }

    override fun getCredentialsByIds(credentialIds: List<String>): List<WalletCredential> {
        TODO("Not yet implemented")
    }
}

