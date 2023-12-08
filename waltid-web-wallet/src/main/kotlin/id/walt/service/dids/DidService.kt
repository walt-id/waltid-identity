package id.walt.service.dids

import id.walt.db.models.WalletDid
import id.walt.db.models.WalletDids
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

object DidsService {
    fun get(wallet: UUID, did: String): WalletDid? = transaction {
        WalletDids.select { (WalletDids.wallet eq wallet) and (WalletDids.did eq did) }
            .singleOrNull()?.let { WalletDid(it) }
    }

    fun list(wallet: UUID): List<WalletDid> = WalletDids.select { WalletDids.wallet eq wallet }.map { WalletDid(it) }

    fun add(wallet: UUID, did: String, document: String, keyId: String, alias: String? = null) {
        val now = Clock.System.now()

        WalletDids.insert {
            it[WalletDids.wallet] = wallet
            it[WalletDids.did] = did
            it[WalletDids.document] = document
            it[WalletDids.keyId] = keyId
            it[WalletDids.alias] = alias ?: "Unnamed from $now"
            it[createdOn] = now.toJavaInstant()
        }
    }

    fun delete(wallet: UUID, did: String): Boolean =
        WalletDids.deleteWhere { (WalletDids.wallet eq wallet) and (WalletDids.did eq did) } > 0


    fun makeDidDefault(wallet: UUID, newDefaultDid: String) {
        transaction {
            WalletDids.update({ (WalletDids.wallet eq wallet) and (WalletDids.default eq true) }) {
                it[default] = false
            }

            WalletDids.update({ (WalletDids.wallet eq wallet) and (WalletDids.did eq newDefaultDid) }) {
                it[default] = true
            }
        }
    }

    fun renameDid(wallet: UUID, did: String, newName: String) =
        WalletDids.update({ (WalletDids.wallet eq wallet) and (WalletDids.did eq did) }) {
            it[alias] = newName
        } > 0
}
