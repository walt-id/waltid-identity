package id.walt.webwallet.usecase.issuer

import id.walt.webwallet.service.issuers.CredentialDataTransferObject
import id.walt.webwallet.service.issuers.IssuerCredentialsDataTransferObject
import id.walt.webwallet.service.issuers.IssuerDataTransferObject
import id.walt.webwallet.service.issuers.IssuersService
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID

class IssuerUseCaseImpl(
    private val service: IssuersService,
    private val http: HttpClient,
) : IssuerUseCase {
    private val json = Json {
        ignoreUnknownKeys
    }

    override fun get(wallet: UUID, did: String): Result<IssuerDataTransferObject> = runCatching {
        service.get(wallet, did) ?: error("Issuer not found")
    }

    override fun list(wallet: UUID): List<IssuerDataTransferObject> = service.list(wallet)

    override fun add(issuer: IssuerDataTransferObject): Result<Boolean> = runCatching {
        service.add(
            issuer.wallet,
            issuer.did,
            issuer.description,
            issuer.uiEndpoint,
            issuer.configurationEndpoint,
            issuer.authorized
        ) > 0
    }

    override fun authorize(wallet: UUID, did: String): Result<Boolean> = runCatching {
        service.authorize(wallet, did) > 0
    }

    override suspend fun credentials(wallet: UUID, did: String): Result<IssuerCredentialsDataTransferObject> =
        runCatching {
            get(wallet, did).getOrThrow().let {
                IssuerCredentialsDataTransferObject(
                    it, fetchCredentials(it.configurationEndpoint)
                )
            }
        }

    private suspend fun fetchCredentials(url: String): List<CredentialDataTransferObject> =
        fetchConfiguration(url).jsonObject["credentials_supported"]!!.jsonArray.map {
            CredentialDataTransferObject(id = it.jsonObject["id"]!!.jsonPrimitive.content,
                format = it.jsonObject["format"]!!.jsonPrimitive.content,
                types = it.jsonObject["types"]!!.jsonArray.map {
                    it.jsonPrimitive.content
                })
        }

    private suspend fun fetchConfiguration(url: String): JsonObject = let {
        json.parseToJsonElement(http.get(url).bodyAsText()).jsonObject
    }
}