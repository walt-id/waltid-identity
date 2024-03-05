package id.walt.webwallet.service.issuers

import id.walt.webwallet.db.models.Issuers
import id.walt.webwallet.db.models.WalletIssuers
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

object IssuersService {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    fun get(wallet: UUID, name: String): IssuerDataTransferObject? = list(wallet).singleOrNull {
        it.name == name
    }

    fun list(wallet: UUID): List<IssuerDataTransferObject> = transaction {
        Issuers.innerJoin(
            WalletIssuers,
            onColumn = { Issuers.id },
            otherColumn = { issuer },
            additionalConstraint = {
                WalletIssuers.wallet eq wallet
            }).selectAll().map {
            IssuerDataTransferObject(
                name = it[Issuers.name],
                description = it[Issuers.description],
                uiEndpoint = it[Issuers.uiEndpoint],
                configurationEndpoint = it[Issuers.configurationEndpoint],
                authorized = it[WalletIssuers.authorized],
            )
        }
    }

    fun add(name: String, description: String?, uiEndpoint: String, configurationEndpoint: String) = transaction {
        insertQuery(name, description, uiEndpoint, configurationEndpoint)
    }

    fun addToWallet(wallet: UUID, issuer: String, authorized: Boolean? = false) = transaction {
        queryIssuer(issuer)?.let { iss ->
            addToWalletQuery(wallet, iss, false)
        }
    }?.insertedCount ?: 0

    fun authorize(wallet: UUID, issuer: String) = transaction {
        queryIssuer(issuer)?.let {
            addToWalletQuery(wallet, it, true)
        }
    }?.insertedCount ?: 0

    private fun queryIssuer(name: String) =
        Issuers.selectAll().where(Issuers.name eq name).singleOrNull()?.let {
            it[Issuers.id]
        }?.value

    private fun addToWalletQuery(wallet: UUID, issuer: UUID, authorized: Boolean) = WalletIssuers.upsert(
        keys = arrayOf(WalletIssuers.wallet, WalletIssuers.issuer),
        onUpdate = listOf(WalletIssuers.authorized to booleanLiteral(authorized))
    ) {
        it[this.issuer] = issuer
        it[this.wallet] = wallet
        it[this.authorized] = authorized
    }

    private fun insertQuery(name: String, description: String?, uiEndpoint: String, configurationEndpoint: String) =
        Issuers.insert {
            it[Issuers.id] = UUID.generateUUID()
            it[Issuers.name] = name
            it[Issuers.description] = description
            it[Issuers.uiEndpoint] = uiEndpoint
            it[Issuers.configurationEndpoint] = configurationEndpoint
        }[Issuers.id].value

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
