package id.walt.service.issuers

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID

object IssuersService {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    fun get(account: UUID, name: String): IssuerDataTransferObject? = list(account).singleOrNull {
        it.name == name
    }

    fun list(account: UUID): List<IssuerDataTransferObject> = emptyList()/* join(account).let {
        AccountIssuersRepository.query(it) {
            IssuerDataTransferObject(
                name = it[Issuers.name],
                description = it[Issuers.description],
                uiEndpoint = it[Issuers.uiEndpoint],
                configurationEndpoint = it[Issuers.configurationEndpoint],
            )
        }
    }*/

    suspend fun fetchCredentials(url: String): List<CredentialDataTransferObject> =
        fetchConfiguration(url).jsonObject["credentials_supported"]!!.jsonArray.map {
            CredentialDataTransferObject(
                id = it.jsonObject["id"]!!.jsonPrimitive.content,
                format = it.jsonObject["format"]!!.jsonPrimitive.content,
                types = it.jsonObject["types"]!!.jsonArray.map {
                    it.jsonPrimitive.content
                })
        }

    private suspend fun fetchConfiguration(url: String): JsonObject = let {
        Json.parseToJsonElement(client.get(url).bodyAsText()).jsonObject
    }

}
