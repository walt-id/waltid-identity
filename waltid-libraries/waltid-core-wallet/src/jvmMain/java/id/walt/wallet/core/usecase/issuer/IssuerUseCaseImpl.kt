@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.usecase.issuer

import kotlin.uuid.ExperimentalUuidApi


/*
class IssuerUseCaseImpl(
    private val service: IssuersService,
    private val http: HttpClient,
) : IssuerUseCase {
    private val json = Json {
        ignoreUnknownKeys
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

    private suspend fun fetchCredentials(url: String): List<CredentialDataTransferObject> =
        fetchConfiguration(url).jsonObject["credential_configurations_supported"]?.jsonObject?.entries?.mapNotNull { (key, value) ->
            value.jsonObject.let { jsonObject ->
                val format = jsonObject["format"]?.jsonPrimitive?.content
                val types = jsonObject["types"]?.jsonArray?.map { it.jsonPrimitive.content }

                if (format != null && types != null) {
                    CredentialDataTransferObject(id = key, format = format, types = types)
                } else {
                    null
                }
            }
        } ?: emptyList()

    private suspend fun fetchConfiguration(url: String): JsonObject = let {
        json.parseToJsonElement(http.get(url).bodyAsText()).jsonObject
    }
}
*/
