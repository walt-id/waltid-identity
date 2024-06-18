package id.walt.webwallet.service.dids

import id.walt.webwallet.db.models.WalletDid
import id.walt.webwallet.db.models.WalletDids
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

object DidsService {
    fun get(wallet: UUID, did: String): WalletDid? = transaction {
        WalletDids.selectAll().where { (WalletDids.wallet eq wallet) and (WalletDids.did eq did.replace("%3A", ":")) }
            .singleOrNull()?.let { WalletDid(it) }
    }

    fun list(wallet: UUID): List<WalletDid> = WalletDids.selectAll().where { WalletDids.wallet eq wallet }.map { WalletDid(it) }

    fun getWalletsForDid(did: String): List<UUID> = transaction {
        WalletDids.selectAll().where { WalletDids.did eq did }.map {
            it[WalletDids.wallet].value
        }
    }

    fun add(wallet: UUID, did: String, document: String, keyId: String, alias: String? = null) = transaction {
        val now = Clock.System.now()

        WalletDids.insert {
            it[WalletDids.wallet] = wallet
            it[WalletDids.did] = did.replace("%3A", ":")
            it[WalletDids.document] = document
            it[WalletDids.keyId] = keyId
            it[WalletDids.alias] = alias ?: "Unnamed from $now"
            it[createdOn] = now.toJavaInstant()
        }
    }.insertedCount

    fun delete(wallet: UUID, did: String): Boolean =
        transaction { WalletDids.deleteWhere { (WalletDids.wallet eq wallet) and (WalletDids.did eq did.replace("%3A", ":")) } } > 0


    fun makeDidDefault(wallet: UUID, newDefaultDid: String) {
        transaction {
            WalletDids.update({ (WalletDids.wallet eq wallet) and (WalletDids.default eq true) }) {
                it[default] = false
            }

            WalletDids.update({ (WalletDids.wallet eq wallet) and (WalletDids.did eq newDefaultDid.replace("%3A", ":")) }) {
                it[default] = true
            }
        }
    }

    fun renameDid(wallet: UUID, did: String, newName: String) =
        WalletDids.update({ (WalletDids.wallet eq wallet) and (WalletDids.did eq did.replace("%3A", ":")) }) {
            it[alias] = newName
        } > 0
}
