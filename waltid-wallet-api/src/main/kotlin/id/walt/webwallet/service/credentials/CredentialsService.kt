package id.walt.webwallet.service.credentials

import id.walt.webwallet.db.models.WalletCategory
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.db.models.WalletCredentialCategoryMap
import id.walt.webwallet.db.models.WalletCredentials
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

object CredentialsService {
    private val notDeletedItemsCondition = Op.build { WalletCredentials.deletedOn eq null }
    private val deletedItemsCondition = Op.build { WalletCredentials.deletedOn neq null }
    fun get(wallet: UUID, credentialId: String): WalletCredential? = getCredential(wallet, credentialId, true)

    fun list(wallet: UUID, filter: CredentialFilterObject) = transaction {
        WalletCredentials.innerJoin(otherTable = WalletCredentialCategoryMap,
            onColumn = { WalletCredentials.id },
            otherColumn = { WalletCredentialCategoryMap.credential },
            additionalConstraint = {
                WalletCredentials.wallet eq wallet and (WalletCredentialCategoryMap.wallet eq wallet)
            }).innerJoin(otherTable = WalletCategory,
            onColumn = { WalletCredentialCategoryMap.category },
            otherColumn = { WalletCategory.name },
            additionalConstraint = {
                WalletCategory.wallet eq wallet and (WalletCredentialCategoryMap.wallet eq wallet) and (filter.showDeleted.takeIf { it }
                    ?.let { deletedItemsCondition } ?: notDeletedItemsCondition)
                //(WalletCredentials.delete eq filter.showDeleted)
            }).selectAll().orderBy(
            if (filter.showDeleted) WalletCredentials.deletedOn else WalletCredentials.addedOn, SortOrder.DESC
        ).filter {
            filter.categories.isEmpty() || filter.categories.contains(it[WalletCategory.name])
        }.map {
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
//            this[WalletCredentials.delete] = credential.delete
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
        WalletCredentials.select {
            (WalletCredentials.wallet eq wallet) and (WalletCredentials.id eq credentialId)
        }.singleOrNull()?.let { WalletCredential(it) }?.takeIf { it.deletedOn == null || includeDeleted }//?.takeIf { !it.delete || includeDeleted }
    }
    private fun updateDelete(wallet: UUID, credentialId: String, value: Boolean): Int = transaction {
        WalletCredentials.update({ WalletCredentials.wallet eq wallet and (WalletCredentials.id eq credentialId) }) {
//            it[this.delete] = value
            it[this.deletedOn] = value.takeIf { it }?.let { Instant.now() }
        }
    }

    private fun deleteCredential(wallet: UUID, credentialId: String) =
        transaction { WalletCredentials.deleteWhere { (WalletCredentials.wallet eq wallet) and (id eq credentialId) } }

    object Manifest {
        fun get(wallet: UUID, credentialId: String): String? = CredentialsService.get(wallet, credentialId)?.manifest
    }

    object Category {
        fun add(wallet: UUID, credentialId: String, category: String): Int = transaction {
            WalletCredentialCategoryMap.insert {
                it[WalletCredentialCategoryMap.wallet] = wallet
                it[WalletCredentialCategoryMap.credential] = credentialId
                it[WalletCredentialCategoryMap.category] = category
            }
        }.insertedCount

        fun delete(wallet: UUID, credentialId: String, category: String): Int = transaction {
            WalletCredentialCategoryMap.deleteWhere {
                WalletCredentialCategoryMap.wallet eq wallet and (WalletCredentialCategoryMap.credential eq credentialId) and (WalletCredentialCategoryMap.category eq category)
            }
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