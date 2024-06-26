package id.walt.webwallet.service.keys

import id.walt.webwallet.db.models.WalletKey
import id.walt.webwallet.db.models.WalletKeys
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object KeysService {
    fun get(wallet: UUID, keyId: String): WalletKey? = transaction {
        WalletKeys.selectAll().where { (WalletKeys.wallet eq wallet) and (WalletKeys.keyId eq keyId) }
            .firstOrNull()?.let { WalletKey(it) }
    }

    fun get(keyId: String): WalletKey? = transaction {
        WalletKeys.selectAll().where { WalletKeys.keyId eq keyId }.firstOrNull()?.let { WalletKey(it) }
    }

    fun list(wallet: UUID): List<WalletKey> = WalletKeys.selectAll().where { WalletKeys.wallet eq wallet }.map { WalletKey(it) }

    fun add(wallet: UUID, keyId: String, document: String) = transaction {
        WalletKeys.insert {
            it[WalletKeys.wallet] = wallet
            it[WalletKeys.keyId] = keyId
            it[WalletKeys.document] = document
            it[createdOn] = Clock.System.now().toJavaInstant()
        }.insertedCount
    }

    fun delete(wallet: UUID, keyId: String): Boolean =
        transaction { WalletKeys.deleteWhere { (WalletKeys.wallet eq wallet) and (WalletKeys.keyId eq keyId) } } > 0

    /*fun update(wallet: UUID, key: DbKey): Boolean {
        TO-DO
    }*/
}
