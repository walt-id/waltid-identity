package id.walt.service.credentials

import id.walt.db.models.WalletCredential
import id.walt.db.models.WalletCredentials
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object CredentialsService {
    fun get(wallet: UUID, credentialId: String): WalletCredential? = transaction {
        WalletCredentials.select { (WalletCredentials.wallet eq wallet) and (WalletCredentials.id eq credentialId) }
            .singleOrNull()?.let { WalletCredential(it) }
    }

    fun list(wallet: UUID) = transaction {
        WalletCredentials.select { WalletCredentials.wallet eq wallet }
            .map { WalletCredential(it) }
    }

    fun add(wallet: UUID, vararg credentials: WalletCredential) = addAll(wallet, credentials.toList())
    fun addAll(wallet: UUID, credentials: List<WalletCredential>): List<String> =
        WalletCredentials.batchInsert(credentials) { credential: WalletCredential ->
            this[WalletCredentials.wallet] = wallet
            this[WalletCredentials.id] = credential.id
            this[WalletCredentials.document] = credential.document
            this[WalletCredentials.disclosures] = credential.disclosures
            this[WalletCredentials.addedOn] = Clock.System.now().toJavaInstant()
        }.map { it[WalletCredentials.id] }

    fun delete(wallet: UUID, credentialId: String): Boolean =
        WalletCredentials.deleteWhere { (WalletCredentials.wallet eq wallet) and (id eq credentialId) } > 0

    /*fun update(account: UUID, did: DidUpdateDataObject): Boolean {
        TO-DO
    }*/
}
