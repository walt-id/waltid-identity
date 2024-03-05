package id.walt.webwallet.service.dids

import id.walt.webwallet.db.models.WalletDid
import id.walt.webwallet.db.models.WalletDids
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * DID Web Registry hosts Decentralized Identifiers according to https://w3c-ccg.github.io/did-method-web/
 * Registered DIDs have to follow the pattern: did:web:{domain}:wallet-api:registry:{id} which will host the
 * DID documents at https://w3c-ccg.github.io/user/alice/did.json
 */
object DidWebRegistryService {

    val DID_WEB_BASE_PATH = "wallet-api:registry"

    fun listRegisteredDids(): List<String> {
        return transaction {
            WalletDids.selectAll().where { WalletDids.did like "%:$DID_WEB_BASE_PATH:%" }.map { WalletDid(it).did }
        }
    }

    fun loadRegisteredDid(id: String): JsonObject {
        return transaction {
            Json.parseToJsonElement(
                WalletDids.selectAll().where { WalletDids.did like "%:$DID_WEB_BASE_PATH:$id" }.single().let {
                    WalletDid(it).document
                }).jsonObject
        }
    }
}
