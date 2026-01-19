@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.usecase.issuer

import id.walt.webwallet.service.issuers.CredentialDataTransferObject
import id.walt.webwallet.service.issuers.IssuerCredentialsDataTransferObject
import id.walt.webwallet.service.issuers.IssuerDataTransferObject
import id.walt.webwallet.service.issuers.IssuersService
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


class IssuerUseCaseImpl(
    private val service: IssuersService,
    private val http: HttpClient,
) : IssuerUseCase {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    override fun get(wallet: Uuid, did: String): Result<IssuerDataTransferObject> = runCatching {
        service.get(wallet, did) ?: error("Issuer not found")
    }

    override fun list(wallet: Uuid): List<IssuerDataTransferObject> = service.list(wallet)

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

    override fun authorize(wallet: Uuid, did: String): Result<Boolean> = runCatching {
        service.authorize(wallet, did) > 0
    }

    override suspend fun credentials(wallet: Uuid, did: String): Result<IssuerCredentialsDataTransferObject> =
        runCatching {
            get(wallet, did).getOrThrow().let {
                IssuerCredentialsDataTransferObject(
                    it, fetchCredentials(it.configurationEndpoint)
                )
            }
        }

    suspend fun fetchCredentials(url: String): List<CredentialDataTransferObject> {
        val issuerConfiguration = fetchConfiguration(url).jsonObject
        val credentialConfigurations = (
                issuerConfiguration["credential_configurations_supported"]?.jsonObject?.entries
                    ?: issuerConfiguration["credentials_supported"]?.jsonArray?.associateBy { it.jsonObject["id"]!!.jsonPrimitive.content }?.entries
                )
        return credentialConfigurations?.mapNotNull { (key, value) ->
            value.jsonObject.let { jsonObject ->
                val format = jsonObject["format"]?.jsonPrimitive?.content
                val types = jsonObject["types"]?.jsonArray?.map { it.jsonPrimitive.content }
                    ?: jsonObject["credential_definition"]?.jsonObject?.get("type")?.jsonArray?.map { it.jsonPrimitive.content }
                    ?: jsonObject["vct"]?.jsonPrimitive?.content?.let { listOf(it) }
                    ?: jsonObject["doctype"]?.jsonPrimitive?.content?.let { listOf(it) }

                if (format != null && types != null) {
                    CredentialDataTransferObject(id = key, format = format, types = types)
                } else {
                    null
                }
            }
        } ?: emptyList()
    }

    private suspend fun fetchConfiguration(url: String): JsonObject = let {
        json.parseToJsonElement(http.get(url).bodyAsText()).jsonObject
    }
}
