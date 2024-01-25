package id.walt.webwallet.service.credentials

import id.walt.webwallet.db.models.*
import id.walt.webwallet.db.models.WalletCredentialCategoryMap.innerJoin
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

object CredentialsService {
    fun get(wallet: UUID, credentialId: String): WalletCredential? = transaction {
        WalletCredentials.select { (WalletCredentials.wallet eq wallet) and (WalletCredentials.id eq credentialId) }
            .singleOrNull()?.let { WalletCredential(it) }
    }

    fun list(wallet: UUID, categories: List<String>) = transaction {
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
        WalletCredentialCategoryMap.innerJoin(
            otherTable = WalletCredentialCategoryMap,
            onColumn = { WalletCredentialCategoryMap.category },
            otherColumn = { WalletCategory.id },
            additionalConstraint = {
                WalletCategory.wallet eq wallet
            }).innerJoin(
                otherTable = WalletCredentials,
                onColumn = { WalletCredentialCategoryMap.credential},
                otherColumn = { WalletCredentials.id},
                additionalConstraint = {
                    WalletCredentials.wallet eq wallet
                }
            ).selectAll().map {
                WalletCredential(it)
        }
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
        }.map { it[WalletCredentials.id] }

    fun delete(wallet: UUID, credentialId: String): Boolean =
        WalletCredentials.deleteWhere { (WalletCredentials.wallet eq wallet) and (id eq credentialId) } > 0

    /*fun update(account: UUID, did: DidUpdateDataObject): Boolean {
        TO-DO
    }*/

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
