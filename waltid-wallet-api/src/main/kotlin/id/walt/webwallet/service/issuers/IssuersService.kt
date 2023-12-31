package id.walt.webwallet.service.issuers

import id.walt.webwallet.db.models.todo.AccountIssuers
import id.walt.webwallet.db.models.todo.Issuers
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object IssuersService {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    fun get(account: UUID, name: String): IssuerDataTransferObject? = list(account).singleOrNull {
        it.name == name
    }

    fun list(account: UUID): List<IssuerDataTransferObject> = transaction {
        Issuers.innerJoin(
            AccountIssuers,
            onColumn = { Issuers.id },
            otherColumn = { AccountIssuers.issuer },
            additionalConstraint = {
                AccountIssuers.id eq account
            }).selectAll().map {
            IssuerDataTransferObject(
                name = it[Issuers.name],
                description = it[Issuers.description],
                uiEndpoint = it[Issuers.uiEndpoint],
                configurationEndpoint = it[Issuers.configurationEndpoint],
            )
        }
    }

    fun add(name: String, description: String, uiEndpoint: String, configurationEndpoint: String) = transaction {
        Issuers.insert {
            it[Issuers.id] = UUID.generateUUID()
            it[Issuers.name] = name
            it[Issuers.description] = description
            it[Issuers.uiEndpoint] = uiEndpoint
            it[Issuers.configurationEndpoint] = configurationEndpoint
        }[Issuers.id].value
    }

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
