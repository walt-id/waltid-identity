@file:OptIn(ExperimentalTime::class)

package id.walt.webwallet.service.dids

import id.walt.commons.web.ConflictException
import id.walt.webwallet.db.models.WalletDid
import id.walt.webwallet.db.models.WalletDids
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@OptIn(ExperimentalUuidApi::class)
object DidsService {
    fun get(wallet: Uuid, did: String): WalletDid? = transaction {
        WalletDids.selectAll().where { (WalletDids.wallet eq wallet.toJavaUuid()) and (WalletDids.did eq did.replace("%3A", ":")) }
            .singleOrNull()?.let { WalletDid(it) }
    }

    fun list(wallet: Uuid): List<WalletDid> =
        transaction { WalletDids.selectAll().where { WalletDids.wallet eq wallet.toJavaUuid() }.map { WalletDid(it) } }

    fun getWalletsForDid(did: String): List<Uuid> = transaction {
        WalletDids.selectAll().where { WalletDids.did eq did }.map {
            it[WalletDids.wallet].value.toKotlinUuid()
        }
    }

    fun add(wallet: Uuid, did: String, document: String, keyId: String, alias: String? = null) = transaction {
        val now = Clock.System.now()
        val didExists = WalletDids.selectAll()
            .where { (WalletDids.wallet eq wallet.toJavaUuid()) and (WalletDids.did eq did) }
            .count() > 0L

        if (didExists) {
            throw ConflictException("DID already exists")
        }
        WalletDids.insert {
            it[WalletDids.wallet] = wallet.toJavaUuid()
            it[WalletDids.did] = did.replace("%3D", "=")
            it[WalletDids.document] = document
            it[WalletDids.keyId] = keyId
            it[WalletDids.alias] = alias ?: "Unnamed from $now"
            it[createdOn] = now.toJavaInstant()
        }
    }.insertedCount

    fun delete(wallet: Uuid, did: String): Boolean =
        transaction {
            WalletDids.deleteWhere {
                (WalletDids.wallet eq wallet.toJavaUuid()) and (WalletDids.did eq did.replace("%3A", ":").replace("%3D", "="))
            }
        } > 0


    fun makeDidDefault(wallet: Uuid, newDefaultDid: String) {
        transaction {
            WalletDids.update({ (WalletDids.wallet eq wallet.toJavaUuid()) and (WalletDids.default eq true) }) {
                it[default] = false
            }

            WalletDids.update({ (WalletDids.wallet eq wallet.toJavaUuid()) and (WalletDids.did eq newDefaultDid.replace("%3A", ":")) }) {
                it[default] = true
            }
        }
    }

    fun renameDid(wallet: Uuid, did: String, newName: String) =
        WalletDids.update({ (WalletDids.wallet eq wallet.toJavaUuid()) and (WalletDids.did eq did.replace("%3A", ":")) }) {
            it[alias] = newName
        } > 0
}
