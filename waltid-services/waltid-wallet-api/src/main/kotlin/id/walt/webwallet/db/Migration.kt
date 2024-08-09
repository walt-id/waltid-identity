package id.walt.webwallet.db

import id.walt.webwallet.db.models.WalletKey
import id.walt.webwallet.db.models.WalletKeys
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.batchUpsert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object Migration {
    /**
     * Migrate from jwk key string representation to json object
     */
    object Keys {
        fun execute() = transaction {
            val conversion = WalletKeys.selectAll().mapNotNull { row ->
                val wallet = row[WalletKeys.wallet].value
                val key = WalletKey(row)
                convertDocument(key.document)?.let {
                    Pair(
                        wallet, key.copy(
                            document = it
                        )
                    )
                }
            }
            WalletKeys.batchUpsert(conversion) {
                this[WalletKeys.document] = it.second.document
                this[WalletKeys.keyId] = it.second.keyId
                this[WalletKeys.wallet] = it.first
                this[WalletKeys.createdOn] = it.second.createdOn.toJavaInstant()
            }
        }

        private fun convertDocument(document: String): String? = let {
            val json = Json.decodeFromString<JsonObject>(document)
            json["jwk"].takeIf { it is JsonPrimitive }?.let {
                json["jwk"]?.jsonPrimitive?.content?.replace("\\", "")?.let {
                    buildJsonObject {
                        put("type", json["type"]!!)
                        put("jwk", Json.decodeFromString<JsonObject>(it))
                    }
                }?.let {
                    Json.encodeToString(it)
                }
            }
        }
    }
}