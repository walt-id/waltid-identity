@file:OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)

package id.walt.webwallet.service.keys

import id.walt.webwallet.db.models.WalletKey
import id.walt.webwallet.db.models.WalletKeys
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

object KeysService {
    fun get(wallet: Uuid, keyId: String): WalletKey? = transaction {
        WalletKeys.selectAll().where { (WalletKeys.wallet eq wallet.toJavaUuid()) and (WalletKeys.keyId eq keyId) }
            .firstOrNull()?.let { WalletKey(it) }
    }

    fun get(keyId: String): WalletKey? = transaction {
        WalletKeys.selectAll().where { WalletKeys.keyId eq keyId }.firstOrNull()?.let { WalletKey(it) }
    }

    fun list(wallet: Uuid): List<WalletKey> =
        WalletKeys.selectAll().where { WalletKeys.wallet eq wallet.toJavaUuid() }.map { WalletKey(it) }

    fun add(wallet: Uuid, keyId: String, document: String, name: String? = null) = transaction {
        WalletKeys.insert {
            it[WalletKeys.wallet] = wallet.toJavaUuid()
            it[WalletKeys.keyId] = keyId
            it[WalletKeys.document] = document
            it[WalletKeys.name] = name
            it[createdOn] = Clock.System.now().toJavaInstant()
        }.insertedCount
    }

    fun exists(wallet: Uuid, keyId: String): Boolean = transaction {
        WalletKeys.selectAll().where { (WalletKeys.wallet eq wallet.toJavaUuid()) and (WalletKeys.keyId eq keyId) }.count() > 0
    }

    fun delete(wallet: Uuid, keyId: String): Boolean =
        transaction { WalletKeys.deleteWhere { (WalletKeys.wallet eq wallet.toJavaUuid()) and (WalletKeys.keyId eq keyId) } } > 0

    /*fun update(wallet: Uuid, key: DbKey): Boolean {
        TO-DO
    }*/
}
