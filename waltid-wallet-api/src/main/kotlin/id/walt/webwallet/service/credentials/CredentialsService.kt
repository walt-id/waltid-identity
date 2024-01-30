package id.walt.webwallet.service.credentials

import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.db.models.WalletCredentials
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

object CredentialsService {
    fun get(wallet: UUID, credentialId: String): WalletCredential? = getCredential(wallet, credentialId, true)

    fun list(wallet: UUID, filter: CredentialFilterObject) = transaction {
        WalletCredentials.select { WalletCredentials.wallet eq wallet }.orderBy(
            if (filter.showDeleted) WalletCredentials.deletedOn else WalletCredentials.addedOn, SortOrder.DESC
        ).map { WalletCredential(it) }.filter {
            it.delete == filter.showDeleted
        }
//        val filterCredentials = WalletCredentialCategoryMap.innerJoin(
//            otherTable = WalletCategory,
//            onColumn = { WalletCredentialCategoryMap.category },
//            otherColumn = { WalletCategory.id },
//            additionalConstraint = {
//                WalletCategory.wallet eq wallet
//            }).selectAll().filter {
//            categories.contains(it[WalletCategory.name])
//        }.map {
//            it[WalletCategory.name]
//        }
//        WalletCredentials.select { WalletCredentials.wallet eq wallet }
//            .map { WalletCredential(it) }


//        WalletCredentialCategoryMap.innerJoin(
//            otherTable = WalletCredentialCategoryMap,
//            onColumn = { WalletCredentialCategoryMap.category },
//            otherColumn = { WalletCategory.id },
//            additionalConstraint = {
//                WalletCategory.wallet eq wallet
//            }).innerJoin(
//                otherTable = WalletCredentials,
//                onColumn = { WalletCredentialCategoryMap.credential},
//                otherColumn = { WalletCredentials.id},
//                additionalConstraint = {
//                    WalletCredentials.wallet eq wallet
//                }
//            ).selectAll().map {
//                WalletCredential(it)
//        }.filter { !it.delete }

    }

    fun add(wallet: UUID, vararg credentials: WalletCredential) = addAll(wallet, credentials.toList())
    fun addAll(wallet: UUID, credentials: List<WalletCredential>): List<String> =
        WalletCredentials.batchInsert(credentials) { credential: WalletCredential ->
            this[WalletCredentials.wallet] = wallet
            this[WalletCredentials.id] = credential.id
            this[WalletCredentials.document] = credential.document
            this[WalletCredentials.disclosures] = credential.disclosures
            this[WalletCredentials.addedOn] = Clock.System.now().toJavaInstant()
            this[WalletCredentials.manifest] = credential.manifest
            this[WalletCredentials.delete] = credential.delete
        }.map { it[WalletCredentials.id] }

    fun delete(wallet: UUID, credentialId: String, permanent: Boolean): Boolean = (permanent.takeIf {
        it
    }?.let {
        deleteCredential(wallet, credentialId)
    } ?: updateDelete(wallet, credentialId, true)) > 0

    fun restore(wallet: UUID, credentialId: String) = getCredential(wallet, credentialId, true)?.also {
        updateDelete(wallet, credentialId, false)
    }

    /*fun update(account: UUID, did: DidUpdateDataObject): Boolean {
        TO-DO
    }*/


    private fun getCredential(wallet: UUID, credentialId: String, includeDeleted: Boolean) = transaction {
        WalletCredentials.select { (WalletCredentials.wallet eq wallet) and (WalletCredentials.id eq credentialId) }
            .singleOrNull()?.let { WalletCredential(it) }?.takeIf { !it.delete || includeDeleted }
    }
    private fun updateDelete(wallet: UUID, credentialId: String, value: Boolean): Int = transaction {
        WalletCredentials.update({ WalletCredentials.wallet eq wallet and (WalletCredentials.id eq credentialId) }) {
            it[this.delete] = value
            it[this.deletedOn] = value.takeIf { it }?.let { Instant.now() }
        }
    }

    private fun deleteCredential(wallet: UUID, credentialId: String) =
        transaction { WalletCredentials.deleteWhere { (WalletCredentials.wallet eq wallet) and (id eq credentialId) } }

    object Manifest {
        fun get(wallet: UUID, credentialId: String): String? = CredentialsService.get(wallet, credentialId)?.manifest
    }

    object Category {
        fun add(wallet: UUID, credentialId: String, category: String): Int {
            transaction {

            }
            TODO()
        }

        fun delete(wallet: UUID, credentialId: String, category: String): Int {
            TODO()
        }
    }
}

data class CredentialFilterObject(
    val categories: List<String>,
    val showDeleted: Boolean
) {
    companion object {
        val default = CredentialFilterObject(emptyList(), false)
    }
}