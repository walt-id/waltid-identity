package id.walt.db.models

import id.walt.crypto.utils.JwsUtils.decodeJws
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object WalletCredentials : Table("credentials") {
    val wallet = reference("wallet", Wallets)
    val id = varchar("id", 256)

    val document = text("document")
    val disclosures = text("disclosures").nullable()

    val addedOn = timestamp("added_on")

    override val primaryKey = PrimaryKey(wallet, id)
}

@Serializable
data class WalletCredential(
    val wallet: UUID,
    val id: String,
    val document: String,
    val disclosures: String?,
    val addedOn: Instant,

    val parsedDocument: JsonObject? = parseDocument(document, id)
) {

    companion object {
        fun parseDocument(document: String, id: String) =
            runCatching {
                when {
                    document.startsWith("{") -> Json.parseToJsonElement(document).jsonObject
                    document.startsWith("ey") -> document.decodeJws().payload
                        .run { jsonObject["vc"]?.jsonObject ?: jsonObject }

                    else -> throw IllegalArgumentException("Unknown credential format")
                }.toMutableMap().also {
                    it.putIfAbsent("id", JsonPrimitive(id))
                }.let {
                    JsonObject(it)
                }
            }.onFailure { it.printStackTrace() }
                .getOrNull()
    }

    /*
    val parsedDocument: JsonObject?
        get() =
            runCatching {
                when {
                    document.startsWith("{") -> Json.parseToJsonElement(document).jsonObject
                    document.startsWith("ey") -> document.decodeJws().payload
                        .run { jsonObject["vc"]?.jsonObject ?: jsonObject }

                    else -> throw IllegalArgumentException("Unknown credential format")
                }.toMutableMap().also {
                    it.putIfAbsent("id", JsonPrimitive(id))
                }.let {
                    JsonObject(it)
                }
            }.onFailure { it.printStackTrace() }
                .getOrNull()*/


    constructor(result: ResultRow) : this(
        wallet = result[WalletCredentials.wallet].value,
        id = result[WalletCredentials.id],
        document = result[WalletCredentials.document],
        disclosures = result[WalletCredentials.disclosures],
        addedOn = result[WalletCredentials.addedOn].toKotlinInstant(),
    )
}
