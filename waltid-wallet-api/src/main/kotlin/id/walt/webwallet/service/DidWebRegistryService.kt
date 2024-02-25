package id.walt.webwallet.service

import id.walt.webwallet.db.models.WalletDid
import id.walt.webwallet.db.models.WalletDids
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object DidWebRegistryService {

    fun listRegisteredDids(): List<String> {
        return transaction {
            WalletDids.select {
                WalletDids.did like "did:web:%"
            }.map { WalletDid(it).did }
        }
    }

    fun loadRegisteredDid(id: String): JsonObject {
        return transaction {
            Json.parseToJsonElement(
                WalletDids.select {
                    WalletDids.did like "%:$id"
                }.single().let {
                    WalletDid(it).document
                }).jsonObject
        }
    }
}